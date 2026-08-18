package com.lianshengtong.common.exception;

import com.lianshengtong.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @Mock
    private MethodArgumentNotValidException validException;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleBizException 测试")
    class HandleBizExceptionTests {

        @Test
        @DisplayName("处理 BizException 返回正确的 code 和 message")
        void handleBizExceptionReturnsCorrectResponse() {
            when(request.getRequestURI()).thenReturn("/api/test");
            BizException ex = new BizException(4001, "业务错误");

            R<Void> result = handler.handleBizException(ex, request);

            assertEquals(4001, result.getCode());
            assertEquals("业务错误", result.getMessage());
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("处理 BizException - 默认 code 500")
        void handleBizExceptionWithDefaultCode() {
            when(request.getRequestURI()).thenReturn("/api/test");
            BizException ex = new BizException("默认错误");

            R<Void> result = handler.handleBizException(ex, request);

            assertEquals(500, result.getCode());
            assertEquals("默认错误", result.getMessage());
        }
    }

    @Nested
    @DisplayName("handleValidException 测试")
    class HandleValidExceptionTests {

        @Test
        @DisplayName("处理 MethodArgumentNotValidException - 单字段错误")
        void handleValidExceptionWithSingleFieldError() {
            BindException bindException = new BindException(new TestDto(), "testDto");
            bindException.rejectValue("name", "NotBlank", "名称不能为空");
            BindingResult bindingResult = bindException.getBindingResult();
            when(validException.getBindingResult()).thenReturn(bindingResult);

            R<Void> result = handler.handleValidException(validException);

            assertEquals(400, result.getCode());
            assertEquals("名称不能为空", result.getMessage());
        }

        @Test
        @DisplayName("处理 MethodArgumentNotValidException - 多字段错误拼接")
        void handleValidExceptionWithMultipleFieldErrors() {
            BindException bindException = new BindException(new TestDto(), "testDto");
            bindException.rejectValue("name", "NotBlank", "名称不能为空");
            bindException.rejectValue("age", "Min", "年龄不能小于0");
            BindingResult bindingResult = bindException.getBindingResult();
            when(validException.getBindingResult()).thenReturn(bindingResult);

            R<Void> result = handler.handleValidException(validException);

            assertEquals(400, result.getCode());
            assertTrue(result.getMessage().contains("名称不能为空"));
            assertTrue(result.getMessage().contains("年龄不能小于0"));
        }

        @Test
        @DisplayName("处理 MethodArgumentNotValidException - 空 BindingResult")
        void handleValidExceptionWithEmptyBindingResult() {
            BindException bindException = new BindException(new TestDto(), "testDto");
            BindingResult bindingResult = bindException.getBindingResult();
            when(validException.getBindingResult()).thenReturn(bindingResult);

            R<Void> result = handler.handleValidException(validException);

            assertEquals(400, result.getCode());
            assertEquals("", result.getMessage());
        }
    }

    @Nested
    @DisplayName("handleBindException 测试")
    class HandleBindExceptionTests {

        @Test
        @DisplayName("处理 BindException - 单字段错误")
        void handleBindExceptionWithSingleFieldError() {
            BindException ex = new BindException(new TestDto(), "testDto");
            ex.rejectValue("email", "Email", "邮箱格式不正确");

            R<Void> result = handler.handleBindException(ex);

            assertEquals(400, result.getCode());
            assertEquals("邮箱格式不正确", result.getMessage());
        }

        @Test
        @DisplayName("处理 BindException - 多字段错误拼接")
        void handleBindExceptionWithMultipleFieldErrors() {
            BindException ex = new BindException(new TestDto(), "testDto");
            ex.rejectValue("field1", "Error1", "字段1错误");
            ex.rejectValue("field2", "Error2", "字段2错误");

            R<Void> result = handler.handleBindException(ex);

            assertEquals(400, result.getCode());
            assertTrue(result.getMessage().contains("字段1错误"));
            assertTrue(result.getMessage().contains("字段2错误"));
        }

        @Test
        @DisplayName("处理 BindException - 无字段错误")
        void handleBindExceptionWithNoFieldErrors() {
            BindException ex = new BindException(new TestDto(), "testDto");

            R<Void> result = handler.handleBindException(ex);

            assertEquals(400, result.getCode());
            assertEquals("", result.getMessage());
        }
    }

    @Nested
    @DisplayName("handleConstraintViolationException 测试")
    class HandleConstraintViolationExceptionTests {

        @Test
        @DisplayName("处理 ConstraintViolationException - 单条约束违反")
        void handleConstraintViolationWithSingleViolation() {
            @SuppressWarnings("unchecked")
            ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
            when(violation.getMessage()).thenReturn("不能为空");

            Set<ConstraintViolation<?>> violations = new HashSet<>();
            violations.add(violation);

            ConstraintViolationException ex = new ConstraintViolationException("校验失败", violations);

            R<Void> result = handler.handleConstraintViolationException(ex);

            assertEquals(400, result.getCode());
            assertEquals("不能为空", result.getMessage());
        }

        @Test
        @DisplayName("处理 ConstraintViolationException - 多条约束违反拼接")
        void handleConstraintViolationWithMultipleViolations() {
            @SuppressWarnings("unchecked")
            ConstraintViolation<Object> v1 = mock(ConstraintViolation.class);
            when(v1.getMessage()).thenReturn("不能为空");

            @SuppressWarnings("unchecked")
            ConstraintViolation<Object> v2 = mock(ConstraintViolation.class);
            when(v2.getMessage()).thenReturn("长度不能超过10");

            Set<ConstraintViolation<?>> violations = new HashSet<>();
            violations.add(v1);
            violations.add(v2);

            ConstraintViolationException ex = new ConstraintViolationException("校验失败", violations);

            R<Void> result = handler.handleConstraintViolationException(ex);

            assertEquals(400, result.getCode());
            assertTrue(result.getMessage().contains("不能为空"));
            assertTrue(result.getMessage().contains("长度不能超过10"));
        }

        @Test
        @DisplayName("处理 ConstraintViolationException - 空约束集合")
        void handleConstraintViolationWithEmptySet() {
            ConstraintViolationException ex = new ConstraintViolationException(
                    "校验失败", Collections.emptySet());

            R<Void> result = handler.handleConstraintViolationException(ex);

            assertEquals(400, result.getCode());
            assertEquals("", result.getMessage());
        }
    }

    @Nested
    @DisplayName("handleIllegalArgument 测试")
    class HandleIllegalArgumentTests {

        @Test
        @DisplayName("处理 IllegalArgumentException")
        void handleIllegalArgumentReturnsCorrectResponse() {
            IllegalArgumentException ex = new IllegalArgumentException("非法参数");

            R<Void> result = handler.handleIllegalArgument(ex);

            assertEquals(400, result.getCode());
            assertEquals("请求参数不合法", result.getMessage());
        }

        @Test
        @DisplayName("处理 IllegalArgumentException - 空消息")
        void handleIllegalArgumentWithEmptyMessage() {
            IllegalArgumentException ex = new IllegalArgumentException();

            R<Void> result = handler.handleIllegalArgument(ex);

            assertEquals(400, result.getCode());
            assertEquals("请求参数不合法", result.getMessage());
        }
    }

    @Nested
    @DisplayName("handleException 测试")
    class HandleExceptionTests {

        @Test
        @DisplayName("处理通用 Exception")
        void handleGenericExceptionReturnsCorrectResponse() {
            when(request.getRequestURI()).thenReturn("/api/unknown");
            Exception ex = new Exception("未知错误");

            R<Void> result = handler.handleException(ex, request);

            assertEquals(500, result.getCode());
            assertEquals("系统错误，请稍后重试", result.getMessage());
        }

        @Test
        @DisplayName("处理 RuntimeException")
        void handleRuntimeException() {
            when(request.getRequestURI()).thenReturn("/api/runtime");
            RuntimeException ex = new RuntimeException("运行时异常");

            R<Void> result = handler.handleException(ex, request);

            assertEquals(500, result.getCode());
            assertEquals("系统错误，请稍后重试", result.getMessage());
        }

        @Test
        @DisplayName("处理自定义异常")
        void handleCustomException() {
            when(request.getRequestURI()).thenReturn("/api/custom");
            Exception ex = new Exception("自定义异常") {};

            R<Void> result = handler.handleException(ex, request);

            assertEquals(500, result.getCode());
            assertEquals("系统错误，请稍后重试", result.getMessage());
        }
    }

    static class TestDto {
        private String name;
        private String email;
        private int age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
}