package com.hify.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。业务失败统一 throw new BizException(ErrorCode.XXX)，
 * 由 GlobalExceptionHandler 统一转换为 Result，禁止用 RuntimeException 裸传业务语义。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getDesc());
        this.errorCode = errorCode;
    }

    /**
     * 用自定义 message 覆盖错误码默认描述（errorCode 的 code 不变）。
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
