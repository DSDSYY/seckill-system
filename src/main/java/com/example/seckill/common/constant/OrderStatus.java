package com.example.seckill.common.constant;

/**
 * 秒杀订单状态常量（与建表 SQL 注释保持一致，避免魔法数字）
 */
public final class OrderStatus {

    private OrderStatus() {
    }

    /** 待支付（下单成功，等待 15 分钟内支付） */
    public static final int PENDING_PAY = 0;

    /** 已支付 */
    public static final int PAID = 1;

    /** 已取消/超时关闭（超时未支付，自动回滚库存） */
    public static final int CANCELED = 2;

    /** 已退款 */
    public static final int REFUNDED = 3;
}