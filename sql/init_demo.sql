-- =====================================================================
-- 秒杀系统 一键初始化脚本（MySQL 8.x）
-- 作用：建库 + 建表 + 插入演示数据 + 创建后端专用账号
-- 用法（在 PowerShell 里执行，会提示输入你的 MySQL root 密码）：
--   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p < "C:\Users\34493\Documents\Codex\2026-09-07\n-h\seckill-backend\sql\init_demo.sql"
-- 说明：脚本可重复执行（IF NOT EXISTS / INSERT IGNORE），不会清掉已有数据
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `seckill`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `seckill`;

-- ---------------- 1. 商品表 ----------------
CREATE TABLE IF NOT EXISTS `product` (
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
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品表(普通商品)';

-- ---------------- 2. 秒杀活动表 ----------------
CREATE TABLE IF NOT EXISTS `seckill_activity` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '秒杀活动ID',
    `product_id`      BIGINT UNSIGNED  NOT NULL                COMMENT '关联商品ID',
    `seckill_price`   DECIMAL(10, 2)   NOT NULL                COMMENT '秒杀价',
    `seckill_stock`   INT              NOT NULL DEFAULT 0      COMMENT '秒杀剩余库存',
    `total_stock`     INT              NOT NULL DEFAULT 0      COMMENT '秒杀总库存',
    `start_time`      DATETIME         NOT NULL                COMMENT '秒杀开始时间',
    `end_time`        DATETIME         NOT NULL                COMMENT '秒杀结束时间',
    `status`          TINYINT          NOT NULL DEFAULT 0      COMMENT '状态: 0-未开始 1-进行中 2-已结束 3-已下架',
    `version`         INT              NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT          NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_start` (`product_id`, `start_time`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_status_start_time` (`status`, `start_time`),
    KEY `idx_start_time` (`start_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '秒杀活动表';

-- ---------------- 3. 秒杀订单表 ----------------
CREATE TABLE IF NOT EXISTS `seckill_order` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`       VARCHAR(32)      NOT NULL                COMMENT '业务订单号',
    `user_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '用户ID',
    `activity_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '秒杀活动ID',
    `product_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '商品ID',
    `product_name`   VARCHAR(100)     NOT NULL                COMMENT '商品名称快照',
    `seckill_price`  DECIMAL(10, 2)   NOT NULL                COMMENT '秒杀价快照',
    `quantity`       INT              NOT NULL DEFAULT 1      COMMENT '购买数量',
    `status`         TINYINT          NOT NULL DEFAULT 0      COMMENT '订单状态: 0-待支付 1-已支付 2-已取消 3-已退款',
    `pay_time`       DATETIME         NULL                    COMMENT '支付时间',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT          NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`),
    KEY `idx_activity_id` (`activity_id`),
    KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '秒杀订单表';

-- ---------------- 4. 演示数据（可重复执行）----------------
-- 商品 1 + 活动 1：从现在起倒推 1 小时开始、2 小时后结束（保证活动处于"进行中"），库存 5
INSERT IGNORE INTO `product` (id, product_name, subtitle, original_price, status)
VALUES (1, '秒杀测试手机', '校招简历项目演示商品', 6999.00, 1);

INSERT IGNORE INTO `seckill_activity`
    (id, product_id, seckill_price, seckill_stock, total_stock, start_time, end_time, status)
VALUES (1, 1, 4999.00, 5, 5, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 2 HOUR, 1);

-- ---------------- 5. 后端专用账号 seckill / seckill123 ----------------
CREATE USER IF NOT EXISTS 'seckill'@'localhost' IDENTIFIED BY 'seckill123';
ALTER USER 'seckill'@'localhost' IDENTIFIED BY 'seckill123';
GRANT ALL PRIVILEGES ON `seckill`.* TO 'seckill'@'localhost';
FLUSH PRIVILEGES;

SELECT '初始化完成' AS msg;