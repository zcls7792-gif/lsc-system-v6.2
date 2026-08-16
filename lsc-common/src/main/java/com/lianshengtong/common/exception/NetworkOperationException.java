package com.lianshengtong.common.exception;

public class NetworkOperationException extends SystemException {

    private final String endpoint;
    private final int httpStatus;

    public NetworkOperationException(String endpoint, String message) {
        super(message);
        this.endpoint = endpoint;
        this.httpStatus = 0;
    }

    public NetworkOperationException(String endpoint, int httpStatus, String message) {
        super(message);
        this.endpoint = endpoint;
        this.httpStatus = httpStatus;
    }

    public NetworkOperationException(String endpoint, String message, Throwable cause) {
        super(message, cause);
        this.endpoint = endpoint;
        this.httpStatus = 0;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
