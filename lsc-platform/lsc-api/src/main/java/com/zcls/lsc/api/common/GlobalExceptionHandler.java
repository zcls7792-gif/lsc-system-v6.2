package com.zcls.lsc.api.common;

import com.zcls.lsc.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 第13.2章 全局异常处理。
 *
 * 业务码（42206-50301）记入内部处理结果，HTTP 状态按语义映射：
 *  - 42206-42209 -> 422 Unprocessable Entity
 *  - 40901-40903 -> 409 Conflict
 *  - 42301      -> 423 Locked
 *  - 50301-50302 -> 503 Service Unavailable
 *  - 其他业务异常 -> 400 Bad Request
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("business error: code={} detail={}", e.code(), e.getMessage());
        int code = e.code();
        HttpStatus status = mapHttpStatus(code);
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(code, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(40000, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(50000, "internal server error"));
    }

    private HttpStatus mapHttpStatus(int code) {
        if (code >= 42206 && code <= 42209) return HttpStatus.UNPROCESSABLE_ENTITY;
        if (code >= 40901 && code <= 40903) return HttpStatus.CONFLICT;
        if (code == 42301) return HttpStatus.LOCKED;
        if (code >= 50301 && code <= 50399) return HttpStatus.SERVICE_UNAVAILABLE;
        if (code >= 40300 && code < 40400) return HttpStatus.FORBIDDEN;
        if (code >= 40400 && code < 40500) return HttpStatus.NOT_FOUND;
        return HttpStatus.BAD_REQUEST;
    }
}
