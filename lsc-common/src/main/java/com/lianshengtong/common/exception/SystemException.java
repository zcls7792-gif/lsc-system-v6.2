package com.lianshengtong.common.exception;

public class SystemException extends RuntimeException {

    private final int code;

    public SystemException(String message) {
        super(message);
        this.code = 500;
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public SystemException(int code, String message) {
        super(message);
        this.code = code;
    }

    public SystemException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
