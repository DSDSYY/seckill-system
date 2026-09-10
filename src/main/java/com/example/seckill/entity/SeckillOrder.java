package com.example.seckill.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体 —— 对应表 seckill_order
 *
 * <p>为什么冗余 product_name / seckill_price 快照：
 * <ul>
 *   <li>订单是"交易凭证"，必须展示下单那一刻的商品名与价格；
 *   <li>若下单后商品改名或活动改价，实时 join 会让历史订单显示错误；
 *   <li>快照以空间换一致性，是订单中心的标准做法。
 * </ul>
 *
 * <p>表级唯一索引 uk_user_activity 实现"一人一单"：
 * 同一用户对同一活动只能存在一条有效订单，从数据库层面拦截重复下单，
 * 是防超卖/防脚本刷单的最后一道防线（接口层校验在前，这里兜底）。
 */
@Data
@TableName("seckill_order")
public class SeckillOrder {

    /** 订单ID：数据库自增主键（仅内部使用，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号：雪花算法生成，全局唯一，对外展示/幂等/对账 */
    private String orderNo;

    /** 用户ID：小程序 openid 登录后映射的系统用户ID */
    private Long userId;

    /** 秒杀活动ID（seckill_activity.id） */
    private Long activityId;

    /** 商品ID（product.id），便于订单列表直接展示商品信息 */
    private Long productId;

    /** 商品名称快照（下单时冗余） */
    private String productName;

    /** 秒杀价快照（下单时冗余，防止活动改价影响历史订单） */
    private BigDecimal seckillPrice;

    /** 购买数量：秒杀通常限购 1 件，字段保留以便扩展 */
    private Integer quantity;

    /** 订单状态：0-待支付 1-已支付 2-已取消/超时关闭 3-已退款 */
    private Integer status;

    /** 支付时间（未支付为 null） */
    private LocalDateTime payTime;

    /** 下单时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间：状态流转时自动更新 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}