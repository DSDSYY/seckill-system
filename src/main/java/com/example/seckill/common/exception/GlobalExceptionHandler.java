package com.example.seckill.common.exception;

import com.example.seckill.common.api.Result;
import com.example.seckill.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：把各类异常统一转为 Result，避免堆栈暴露给前端
 *
 * <p>覆盖：业务异常 / 参数校验异常 / 兜底异常，保证任何接口都返回标准结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按业务错误码返回（售罄/未开始/重复下单/限流等），只记录 warn 不刷堆栈 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验异常：@Valid 校验失败时抛出，返回第一个字段错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数错误" : fieldError.getDefaultMessage();
        log.warn("[参数校验失败] {}", message);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /** 兜底异常：记录完整堆栈，统一返回"系统繁忙"，不泄露内部细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常]", e);
        return Result.fail(ResultCode.SERVER_ERROR);
    }
}