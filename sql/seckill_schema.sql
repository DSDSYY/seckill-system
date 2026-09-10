-- =====================================================================
-- 秒杀系统 数据库设计（阶段一）
-- 适用：MySQL 8.x / InnoDB / utf8mb4
-- 说明：脚本可直接在 Navicat / DataGrip 中执行；包结构占位包名 com.example
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `seckill`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `seckill`;

-- ---------------------------------------------------------------------
-- 1. 商品表 product（普通商品基础资料）
--    为什么单独拆一张表：
--      ① 商品是"稳定资料"，秒杀是"营销玩法"，一个商品可参加多轮秒杀活动；
--      ② 活动表只冗余 product_id，商品改名/改价不会牵连历史活动与订单；
--      ③ 未来做普通下单/购物车时，商品表可直接复用。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '商品ID(主键)',
    `product_name`    VARCHAR(100)     NOT NULL                COMMENT '商品名称',
    `subtitle`        VARCHAR(200)     NOT NULL DEFAULT ''     COMMENT '副标题/核心卖点',
    `main_image`      VARCHAR(500)     NOT NULL DEFAULT ''     COMMENT '主图URL',
    `detail`          TEXT             NULL                    COMMENT '商品详情(富文本)',
    `original_price`  DECIMAL(10, 2)   NOT NULL                COMMENT '原价(市场价)',
    `status`          TINYINT          NOT NULL DEFAULT 1      COMMENT '状态: 0-下架 1-上架',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT          NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),               -- 按上下架状态筛选商品
    KEY `idx_create_time` (`create_time`)      -- 商品列表按时间倒序分页
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '商品表(普通商品)';

-- ---------------------------------------------------------------------
-- 2. 秒杀活动表 seckill_activity
--    为什么库存放"活动表"而不是"商品表"：
--      ① 秒杀库存/秒杀价都是活动属性，不同轮次相互独立；
--      ② 扣库存只命中活动这一行，行锁粒度小、SQL 简单，天然支持原子扣减。
--    库存字段分工：
--      total_stock    总库存：创建时写入后不再修改，用于展示进度/对账；
--      seckill_stock  剩余库存：真正参与并发扣减的"计数器"，防超卖核心字段。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `seckill_activity`;
CREATE TABLE `seckill_activity` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '秒杀活动ID',
    `product_id`      BIGINT UNSIGNED  NOT NULL                COMMENT '关联商品ID(product.id)',
    `seckill_price`   DECIMAL(10, 2)   NOT NULL                COMMENT '秒杀价',
    `seckill_stock`   INT              NOT NULL DEFAULT 0      COMMENT '秒杀剩余库存(并发扣减以它为准)',
    `total_stock`     INT              NOT NULL DEFAULT 0      COMMENT '秒杀总库存(初始化后不变)',
    `start_time`      DATETIME         NOT NULL                COMMENT '秒杀开始时间',
    `end_time`        DATETIME         NOT NULL                COMMENT '秒杀结束时间',
    `status`          TINYINT          NOT NULL DEFAULT 0      COMMENT '状态: 0-未开始 1-进行中 2-已结束 3-已下架',
    `version`         INT              NOT NULL DEFAULT 0      COMMENT '乐观锁版本号(配合@Version防并发覆盖)',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT          NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_start` (`product_id`, `start_time`),  -- 同一商品同一开始时间只能有一条，防止活动重复创建
    KEY `idx_product_id` (`product_id`),                         -- 按商品查其秒杀活动
    KEY `idx_status_start_time` (`status`, `start_time`),        -- 接口查"当前可抢活动列表" status=1 且时间在区间内
    KEY `idx_start_time` (`start_time`)                          -- 定时任务扫描"即将开始"的活动去预热 Redis 缓存
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '秒杀活动表';

-- ---------------------------------------------------------------------
-- 3. 秒杀订单表 seckill_order
--    为什么冗余 product_name / seckill_price 快照：
--      订单是"交易凭证"，必须展示下单那一刻的商品名与价格；
--      若下单后商品改名或活动改价，实时 join 会让历史订单显示错误。
--    唯一索引 uk_user_activity 实现"一人一单"：
--      同一用户对同一活动只能下一单，是防超卖/防重复下单的数据库级兜底。
--    设计取舍：不建 idx_user_id，因为 uk_user_activity 已以 user_id 作为
--      联合索引最左前缀，可覆盖"查某用户所有订单"，避免无谓的写放大。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `seckill_order`;
CREATE TABLE `seckill_order` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`       VARCHAR(32)      NOT NULL                COMMENT '业务订单号(雪花算法生成, 对外展示/幂等)',
    `user_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '用户ID(小程序openid映射后的系统用户ID)',
    `activity_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '秒杀活动ID(seckill_activity.id)',
    `product_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '商品ID(product.id)',
    `product_name`   VARCHAR(100)     NOT NULL                COMMENT '商品名称快照(下单时冗余)',
    `seckill_price`  DECIMAL(10, 2)   NOT NULL                COMMENT '秒杀价快照(下单时冗余)',
    `quantity`       INT              NOT NULL DEFAULT 1      COMMENT '购买数量(秒杀通常限购1件)',
    `status`         TINYINT          NOT NULL DEFAULT 0      COMMENT '订单状态: 0-待支付 1-已支付 2-已取消/超时关闭 3-已退款',
    `pay_time`       DATETIME         NULL                    COMMENT '支付时间',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT          NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),                     -- 订单号全局唯一：幂等、对账、防重复提交
    UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`),  -- ★ 一人一单：防同一用户重复下单(防超卖DB兜底)
    KEY `idx_activity_id` (`activity_id`),                     -- 查某活动订单/统计销量
    KEY `idx_status_create_time` (`status`, `create_time`)     -- 定时任务扫描"超时未支付"订单做关单
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '秒杀订单表';

-- ---------------------------------------------------------------------
-- 说明：本设计刻意"不建物理外键"。
--   互联网高并发场景下外键会带来额外的校验与锁开销，且未来分库分表后
--   外键约束失效；表间一致性由"应用层事务 + 索引"保证。
-- ---------------------------------------------------------------------