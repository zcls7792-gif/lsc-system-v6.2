package com.zcls.lsc.common.error;

import java.util.Objects;

/**
 * 业务异常。任何余额不足、抵扣越界、互斥违反整笔回滚。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode").message()
                + (detail == null || detail.isEmpty() ? "" : ": " + detail),
                cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public int code() {
        return errorCode.code();
    }

    public String detail() {
        return detail;
    }
}
