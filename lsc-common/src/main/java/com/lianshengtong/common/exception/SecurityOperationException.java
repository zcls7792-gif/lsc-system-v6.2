package com.lianshengtong.common.exception;

public class SecurityOperationException extends SystemException {

    private final SecurityType type;

    public enum SecurityType {
        AUTHENTICATION,
        AUTHORIZATION,
        ENCRYPTION,
        TOKEN_EXPIRED,
        TOKEN_BLACKLISTED,
        RATE_LIMITED
    }

    public SecurityOperationException(String message) {
        super(message);
        this.type = SecurityType.AUTHENTICATION;
    }

    public SecurityOperationException(SecurityType type, String message) {
        super(message);
        this.type = type;
    }

    public SecurityOperationException(SecurityType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public SecurityType getType() {
        return type;
    }
}
