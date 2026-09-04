package com.lianshengtong.release.exception;

import com.lianshengtong.common.result.R;
import com.lianshengtong.release.observability.GrayApprovalMetrics;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 灰度审批模块统一异常处理器。
 * <p>
 * 将业务异常 / 参数校验异常映射到详细设计 §15 错误码全集，
 * 同时确保响应结构为 {@code R<?>} 以便前端通用拦截层统一解析。
 * </p>
 * 与全局异常处理器（若 lsc-common 中存在）互补：
 * 本类通过 {@code basePackages} 缩小作用域，不影响其它模块。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.lianshengtong.release.controller")
@RequiredArgsConstructor
public class GrayApprovalExceptionHandler {

    private final GrayApprovalMetrics metrics;

    // ============================================================
    // 业务状态冲突（核心审批流状态机校验）
    // ============================================================
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<R<Void>> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage();
        int code = 409;
        if (msg != null) {
            if (msg.contains("not pending"))       code = 409;
            else if (msg.contains("no waiting node")) code = 409;
            else if (msg.contains("not retryable status")) code = 409;
            else if (msg.contains("cannot cancel")) code = 409;
            else if (msg.contains("cannot acquire")) code = 400;
            else if (msg.contains("lock interrupted")) code = 400;
        }
        warn("[GRAY-APPROVAL-CONFLICT]", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(R.fail(code, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        warn("[GRAY-APPROVAL-NOTFOUND]", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(404, ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<R<Void>> handleUnsupported(UnsupportedOperationException ex) {
        warn("[GRAY-APPROVAL-LAUNCH-NOTSUPPORTED]", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, ex.getMessage()));
    }

    // ============================================================
    // 参数校验
    // ============================================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        warn("[GRAY-APPROVAL-VALID]", ex);
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        warn("[GRAY-APPROVAL-VALID]", ex);
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<R<Void>> handleReq(Exception ex) {
        warn("[GRAY-APPROVAL-REQ]", ex);
        return ResponseEntity.badRequest().body(R.fail(400, ex.getMessage()));
    }

    // ============================================================
    // 兜底
    // ============================================================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleAny(Exception ex) {
        log.error("[GRAY-APPROVAL-UNEXPECTED] unexpected error", ex);
        metrics.incExecuteFail("unknown", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, ex.getClass().getSimpleName() + ": " + ex.getMessage()));
    }

    private void warn(String tag, Exception ex) {
        log.warn("{} {}", tag, ex.getMessage());
    }
}
