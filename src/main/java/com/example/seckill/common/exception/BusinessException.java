package com.example.seckill.common.exception;

import com.example.seckill.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带业务错误码，由 {@code GlobalExceptionHandler} 统一转成 Result
 *
 * <p>为什么用异常而非到处 return null / 错误码：
 * Service 方法只表达"正常流程"，失败路径抛异常快速上抛，
 * Controller 保持干净，错误码集中管理，避免散落魔法数字。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}