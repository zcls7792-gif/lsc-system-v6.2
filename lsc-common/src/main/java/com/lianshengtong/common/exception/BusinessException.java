package com.lianshengtong.common.exception;

import com.lianshengtong.common.result.ResultCode;

public class BusinessException extends BizException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(int code, String message) {
        super(code, message);
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode);
    }

    public BusinessException(ResultCode resultCode, String customMessage) {
        super(resultCode, customMessage);
    }

    public BusinessException(ResultCode resultCode, Throwable cause) {
        super(resultCode.getCode(), resultCode.getMessage());
        initCause(cause);
    }
}
