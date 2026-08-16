package com.lianshengtong.evidence.config;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class EvidenceGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EvidenceGlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[BizException] {} code={} msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Map<String, String>>> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                                HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                errors.put(fe.getField(), fe.getDefaultMessage())
        );
        String msg = errors.values().stream().collect(Collectors.joining("; "));
        log.warn("[MethodArgumentNotValidException] {} errors={}", request.getRequestURI(), errors);
        R<Map<String, String>> r = R.fail(400, msg);
        r.setData(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Map<String, String>>> handleBindException(BindException e, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getFieldErrors().forEach(fe ->
                errors.put(fe.getField(), fe.getDefaultMessage())
        );
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[BindException] {} errors={}", request.getRequestURI(), errors);
        R<Map<String, String>> r = R.fail(400, msg);
        r.setData(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Map<String, String>>> handleConstraintViolation(ConstraintViolationException e,
                                                                           HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> cv : e.getConstraintViolations()) {
            String path = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "unknown";
            int lastDot = path.lastIndexOf('.');
            if (lastDot >= 0) {
                path = path.substring(lastDot + 1);
            }
            errors.put(path, cv.getMessage());
        }
        String msg = errors.values().stream().collect(Collectors.joining("; "));
        log.warn("[ConstraintViolationException] {} errors={}", request.getRequestURI(), errors);
        R<Map<String, String>> r = R.fail(400, msg);
        r.setData(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String paramName = e.getName();
        Class<?> requiredType = e.getRequiredType();
        String typeName = requiredType != null ? requiredType.getSimpleName() : "未知";
        String msg = String.format("参数[%s]类型不匹配，期望类型：%s", paramName, typeName);
        log.warn("[MethodArgumentTypeMismatchException] {} {}", request.getRequestURI(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        String detail = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        log.warn("[HttpMessageNotReadableException] {} {}", request.getRequestURI(), detail);
        // S10-fix: 不泄露内部异常详情给客户端
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, "请求体格式错误，请检查 JSON 格式是否正确"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        String msg = String.format("缺少必要参数：%s", e.getParameterName());
        log.warn("[MissingServletRequestParameterException] {} {}", request.getRequestURI(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[IllegalArgumentException] {} {}", request.getRequestURI(), e.getMessage());
        // S10-fix: 不泄露内部异常详情给客户端
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, "请求参数不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("[Exception] {}: {}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(R.fail(500, "系统错误，请稍后重试"));
    }
}
