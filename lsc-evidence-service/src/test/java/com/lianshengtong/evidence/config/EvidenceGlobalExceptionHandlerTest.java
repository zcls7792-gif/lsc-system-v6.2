package com.lianshengtong.evidence.config;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EvidenceGlobalExceptionHandler 单元测试")
class EvidenceGlobalExceptionHandlerTest {

    private EvidenceGlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new EvidenceGlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    @DisplayName("BizException - 返回对应错误码和消息")
    void handleBizException() {
        BizException ex = new BizException(404, "记录不存在");
        ResponseEntity<R<Void>> resp = handler.handleBizException(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(404, resp.getBody().getCode());
        assertEquals("记录不存在", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException - 聚合字段错误")
    void handleMethodArgumentNotValid() {
        org.springframework.validation.BeanPropertyBindingResult errors =
                new org.springframework.validation.BeanPropertyBindingResult(new Object(), "cmd");
        errors.addError(new FieldError("cmd", "name", "姓名不能为空"));
        errors.addError(new FieldError("cmd", "age", "年龄必须大于0"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mock(org.springframework.core.MethodParameter.class),
                errors
        );

        ResponseEntity<R<Map<String, String>>> resp = handler.handleMethodArgumentNotValid(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Map<String, String>> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        assertTrue(r.getMessage().contains("姓名不能为空"));
        assertTrue(r.getMessage().contains("年龄必须大于0"));
        assertNotNull(r.getData());
        assertEquals("姓名不能为空", r.getData().get("name"));
        assertEquals("年龄必须大于0", r.getData().get("age"));
    }

    @Test
    @DisplayName("BindException - 字段错误聚合")
    void handleBindException() {
        org.springframework.validation.BeanPropertyBindingResult errors =
                new org.springframework.validation.BeanPropertyBindingResult(new Object(), "cmd");
        errors.addError(new FieldError("cmd", "x", "X 非法"));
        errors.addError(new FieldError("cmd", "y", "Y 非法"));

        BindException ex = new BindException(errors);
        ResponseEntity<R<Map<String, String>>> resp = handler.handleBindException(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Map<String, String>> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        assertNotNull(r.getData());
        assertEquals("X 非法", r.getData().get("x"));
        assertEquals("Y 非法", r.getData().get("y"));
    }

    @Test
    @DisplayName("ConstraintViolationException - 多个约束违规")
    void handleConstraintViolation() {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.add(mockViolation("bizType", "业务类型不能为空"));
        violations.add(mockViolation("bizId", "业务ID不能为空"));

        ConstraintViolationException ex = new ConstraintViolationException("校验失败", violations);
        ResponseEntity<R<Map<String, String>>> resp = handler.handleConstraintViolation(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Map<String, String>> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        assertTrue(r.getMessage().contains("业务类型不能为空"));
        assertTrue(r.getMessage().contains("业务ID不能为空"));
        assertNotNull(r.getData());
    }

    @Test
    @DisplayName("ConstraintViolationException - 空路径场景")
    void handleConstraintViolation_nullPath() {
        ConstraintViolation<?> cv = mock(ConstraintViolation.class);
        when(cv.getPropertyPath()).thenReturn(null);
        when(cv.getMessage()).thenReturn("msg");

        Set<ConstraintViolation<?>> violations = Set.of(cv);
        ConstraintViolationException ex = new ConstraintViolationException("msg", violations);
        ResponseEntity<R<Map<String, String>>> resp = handler.handleConstraintViolation(ex, request);
        assertNotNull(resp);
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    @DisplayName("ConstraintViolationException - 带多级路径")
    void handleConstraintViolation_nestedPath() {
        ConstraintViolation<?> cv = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("order.bizType");
        when(cv.getPropertyPath()).thenReturn(path);
        when(cv.getMessage()).thenReturn("必须大写");

        Set<ConstraintViolation<?>> violations = Set.of(cv);
        ConstraintViolationException ex = new ConstraintViolationException("x", violations);
        ResponseEntity<R<Map<String, String>>> resp = handler.handleConstraintViolation(ex, request);
        R<Map<String, String>> r = resp.getBody();
        assertNotNull(r);
        assertEquals("必须大写", r.getData().get("bizType"));
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException - 返回类型错误")
    void handleTypeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, new NumberFormatException("数字格式错误")
        );
        ResponseEntity<R<Void>> resp = handler.handleTypeMismatch(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Void> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        assertTrue(r.getMessage().contains("id"));
        assertTrue(r.getMessage().contains("Long"));
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException - 空 requiredType")
    void handleTypeMismatch_nullType() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "x", null, "f", null, new IllegalArgumentException()
        );
        ResponseEntity<R<Void>> resp = handler.handleTypeMismatch(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Void> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        assertTrue(r.getMessage().contains("未知"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException - 最具体原因")
    void handleNotReadable_withCause() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "parse error", new RuntimeException("unexpected token"));
        ResponseEntity<R<Void>> resp = handler.handleHttpMessageNotReadable(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Void> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        // S10-fix: 不泄露内部异常详情，返回通用错误消息
        assertTrue(r.getMessage().contains("请求体格式错误"));
        assertFalse(r.getMessage().contains("unexpected token"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException - 无具体原因")
    void handleNotReadable_noCause() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad body",
                (Throwable) null);
        ResponseEntity<R<Void>> resp = handler.handleHttpMessageNotReadable(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Void> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        // S10-fix: 不泄露内部异常详情
        assertTrue(r.getMessage().contains("请求体格式错误"));
        assertFalse(r.getMessage().contains("bad body"));
    }

    @Test
    @DisplayName("IllegalArgumentException - 返回 400")
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("非法参数");
        ResponseEntity<R<Void>> resp = handler.handleIllegalArgument(ex, request);
        assertEquals(400, resp.getStatusCodeValue());
        R<Void> r = resp.getBody();
        assertNotNull(r);
        assertEquals(400, r.getCode());
        // S10-fix: 不泄露内部异常详情
        assertTrue(r.getMessage().contains("请求参数不合法"));
        assertFalse(r.getMessage().contains("非法参数"));
    }

    @Test
    @DisplayName("Exception - 兜底返回 500")
    void handleException() {
        RuntimeException ex = new RuntimeException("未知错误");
        ResponseEntity<R<Void>> resp = handler.handleException(ex, request);
        assertEquals(500, resp.getStatusCodeValue());
        R<Void> r = resp.getBody();
        assertNotNull(r);
        assertEquals(500, r.getCode());
        assertEquals("系统错误，请稍后重试", r.getMessage());
    }

    // ==================== 辅助 ====================
    private ConstraintViolation<?> mockViolation(String property, String msg) {
        ConstraintViolation<?> cv = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(property);
        when(cv.getPropertyPath()).thenReturn(path);
        when(cv.getMessage()).thenReturn(msg);
        return cv;
    }
}
