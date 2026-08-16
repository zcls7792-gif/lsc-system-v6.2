package com.lianshengtong.common.exception;

public class DataAccessException extends SystemException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(int code, String message) {
        super(code, message);
    }
}
