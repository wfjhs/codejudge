package com.codejudge.web.handler;

import com.codejudge.common.exception.BizException;
import com.codejudge.common.result.Result;
import com.codejudge.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 * <p>
 * 设计取舍：<b>所有响应统一 HTTP 200，业务结果放在 body.code 里</b>。
 * 这样前端 axios 拦截器只需要处理一种响应格式。
 * 代价是丢失了 HTTP 状态码的语义（网关按状态码做熔断、监控按状态码告警都会失效），
 * 如果后面接入 Nginx/网关的基于状态码的策略，需要改回语义化状态码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：可预期，只记 warn，不打堆栈。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} -> code={}, msg={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * @RequestBody 参数校验失败。
     * 只返回第一条错误：前端表单一次展示一条更符合使用习惯，也避免泄露过多内部规则。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMessage();
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 表单绑定校验失败（非 JSON 提交时走这里）。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMessage();
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 方法参数上的约束校验失败（如 @RequestParam @Min(1)）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ResultCode.PARAM_ERROR, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(ResultCode.PARAM_ERROR, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED, "不支持 " + e.getMethod() + " 请求");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    /**
     * 兜底：未预料的异常一律 500，日志打全堆栈但不能把堆栈返回给前端。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}] {}", request.getMethod(), request.getRequestURI(), e);
        return Result.fail(ResultCode.ERROR);
    }
}
