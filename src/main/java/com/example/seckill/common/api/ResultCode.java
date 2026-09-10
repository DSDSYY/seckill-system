package com.example.seckill.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务错误码
 * 200~599 对齐 HTTP 语义；1000+ 为秒杀业务错误码（前端据此提示用户）
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "请求的资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    SERVER_ERROR(500, "系统繁忙，请稍后重试"),

    ACTIVITY_NOT_FOUND(1001, "秒杀活动不存在"),
    NOT_START(1002, "秒杀活动尚未开始"),
    ENDED(1003, "秒杀活动已结束"),
    SOLD_OUT(1004, "手慢了，商品已售罄"),
    QUEUE_FULL(1005, "当前抢购人数过多，请重试"),
    REPEAT_SUBMIT(1006, "您已参与过该秒杀，请勿重复下单"),
    PRODUCT_NOT_FOUND(1007, "商品不存在或已下架");

    private final int code;
    private final String message;
}