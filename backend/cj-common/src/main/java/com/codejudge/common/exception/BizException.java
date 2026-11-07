package com.codejudge.common.exception;

import com.codejudge.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 * <p>
 * 约定：业务上可预期的失败（用户名重复、密码错误等）一律抛这个异常，
 * 由全局异常处理器统一转成 Result，避免在 Controller 里写大量 if-else 返回。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
