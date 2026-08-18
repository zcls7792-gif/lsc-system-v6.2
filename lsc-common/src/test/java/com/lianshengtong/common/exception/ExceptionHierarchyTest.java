package com.lianshengtong.common.exception;

import com.lianshengtong.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("异常体系测试")
class ExceptionHierarchyTest {

    @Nested
    @DisplayName("BizException 测试")
    class BizExceptionTests {

        @Test
        @DisplayName("构造器 - message")
        void constructorWithMessage() {
            BizException ex = new BizException("业务异常");
            assertEquals("业务异常", ex.getMessage());
            assertEquals(500, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - code + message")
        void constructorWithCodeAndMessage() {
            BizException ex = new BizException(4001, "自定义错误");
            assertEquals("自定义错误", ex.getMessage());
            assertEquals(4001, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - ResultCode")
        void constructorWithResultCode() {
            BizException ex = new BizException(ResultCode.PARAM_ERROR);
            assertEquals("参数错误", ex.getMessage());
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - ResultCode + customMessage")
        void constructorWithResultCodeAndCustomMessage() {
            BizException ex = new BizException(ResultCode.PARAM_ERROR, "自定义参数错误");
            assertEquals("自定义参数错误", ex.getMessage());
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("getCode 返回正确的 code")
        void getCodeReturnsCorrectValue() {
            BizException ex = new BizException(ResultCode.SUCCESS);
            assertEquals(0, ex.getCode());

            BizException ex2 = new BizException(999, "test");
            assertEquals(999, ex2.getCode());
        }

        @Test
        @DisplayName("继承自 RuntimeException")
        void extendsRuntimeException() {
            BizException ex = new BizException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    @Nested
    @DisplayName("BusinessException 测试")
    class BusinessExceptionTests {

        @Test
        @DisplayName("构造器 - message")
        void constructorWithMessage() {
            BusinessException ex = new BusinessException("业务异常");
            assertEquals("业务异常", ex.getMessage());
            assertEquals(500, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - code + message")
        void constructorWithCodeAndMessage() {
            BusinessException ex = new BusinessException(4001, "业务错误");
            assertEquals("业务错误", ex.getMessage());
            assertEquals(4001, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - ResultCode")
        void constructorWithResultCode() {
            BusinessException ex = new BusinessException(ResultCode.PARAM_ERROR);
            assertEquals("参数错误", ex.getMessage());
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - ResultCode + customMessage")
        void constructorWithResultCodeAndCustomMessage() {
            BusinessException ex = new BusinessException(ResultCode.PARAM_ERROR, "自定义消息");
            assertEquals("自定义消息", ex.getMessage());
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - ResultCode + Throwable")
        void constructorWithResultCodeAndCause() {
            Throwable cause = new RuntimeException("root cause");
            BusinessException ex = new BusinessException(ResultCode.PARAM_ERROR, cause);
            assertEquals("参数错误", ex.getMessage());
            assertEquals(400, ex.getCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("继承自 BizException")
        void extendsBizException() {
            BusinessException ex = new BusinessException("test");
            assertInstanceOf(BizException.class, ex);
        }
    }

    @Nested
    @DisplayName("SystemException 测试")
    class SystemExceptionTests {

        @Test
        @DisplayName("构造器 - message")
        void constructorWithMessage() {
            SystemException ex = new SystemException("系统异常");
            assertEquals("系统异常", ex.getMessage());
            assertEquals(500, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - message + cause")
        void constructorWithMessageAndCause() {
            Throwable cause = new RuntimeException("root cause");
            SystemException ex = new SystemException("系统异常", cause);
            assertEquals("系统异常", ex.getMessage());
            assertEquals(500, ex.getCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("构造器 - code + message")
        void constructorWithCodeAndMessage() {
            SystemException ex = new SystemException(5001, "自定义系统错误");
            assertEquals("自定义系统错误", ex.getMessage());
            assertEquals(5001, ex.getCode());
        }

        @Test
        @DisplayName("构造器 - code + message + cause")
        void constructorWithCodeMessageAndCause() {
            Throwable cause = new RuntimeException("root cause");
            SystemException ex = new SystemException(5001, "自定义系统错误", cause);
            assertEquals("自定义系统错误", ex.getMessage());
            assertEquals(5001, ex.getCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("getCode 返回正确的 code")
        void getCodeReturnsCorrectValue() {
            SystemException ex = new SystemException(1234, "test");
            assertEquals(1234, ex.getCode());
        }

        @Test
        @DisplayName("继承自 RuntimeException")
        void extendsRuntimeException() {
            SystemException ex = new SystemException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    @Nested
    @DisplayName("DataAccessException 测试")
    class DataAccessExceptionTests {

        @Test
        @DisplayName("构造器 - message")
        void constructorWithMessage() {
            DataAccessException ex = new DataAccessException("数据访问异常");
            assertEquals("数据访问异常", ex.getMessage());
        }

        @Test
        @DisplayName("构造器 - message + cause")
        void constructorWithMessageAndCause() {
            Throwable cause = new RuntimeException("db error");
            DataAccessException ex = new DataAccessException("数据访问异常", cause);
            assertEquals("数据访问异常", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("构造器 - code + message")
        void constructorWithCodeAndMessage() {
            DataAccessException ex = new DataAccessException(5001, "自定义数据错误");
            assertEquals("自定义数据错误", ex.getMessage());
            assertEquals(5001, ex.getCode());
        }

        @Test
        @DisplayName("继承自 SystemException")
        void extendsSystemException() {
            DataAccessException ex = new DataAccessException("test");
            assertInstanceOf(SystemException.class, ex);
        }
    }

    @Nested
    @DisplayName("NetworkOperationException 测试")
    class NetworkOperationExceptionTests {

        @Test
        @DisplayName("构造器 - endpoint + message")
        void constructorWithEndpointAndMessage() {
            NetworkOperationException ex = new NetworkOperationException("http://example.com/api", "网络错误");
            assertEquals("http://example.com/api", ex.getEndpoint());
            assertEquals("网络错误", ex.getMessage());
            assertEquals(0, ex.getHttpStatus());
        }

        @Test
        @DisplayName("构造器 - endpoint + httpStatus + message")
        void constructorWithEndpointHttpStatusAndMessage() {
            NetworkOperationException ex = new NetworkOperationException("http://example.com/api", 503, "服务不可用");
            assertEquals("http://example.com/api", ex.getEndpoint());
            assertEquals(503, ex.getHttpStatus());
            assertEquals("服务不可用", ex.getMessage());
        }

        @Test
        @DisplayName("构造器 - endpoint + message + cause")
        void constructorWithEndpointMessageAndCause() {
            Throwable cause = new RuntimeException("connection timeout");
            NetworkOperationException ex = new NetworkOperationException("http://example.com/api", "超时", cause);
            assertEquals("http://example.com/api", ex.getEndpoint());
            assertEquals(0, ex.getHttpStatus());
            assertEquals("超时", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("getEndpoint 返回正确的 endpoint")
        void getEndpointReturnsCorrectValue() {
            NetworkOperationException ex = new NetworkOperationException("http://test.com", "error");
            assertEquals("http://test.com", ex.getEndpoint());
        }

        @Test
        @DisplayName("getHttpStatus 返回正确的 httpStatus")
        void getHttpStatusReturnsCorrectValue() {
            NetworkOperationException ex = new NetworkOperationException("http://test.com", 404, "not found");
            assertEquals(404, ex.getHttpStatus());
        }

        @Test
        @DisplayName("继承自 SystemException")
        void extendsSystemException() {
            NetworkOperationException ex = new NetworkOperationException("http://test.com", "error");
            assertInstanceOf(SystemException.class, ex);
        }
    }

    @Nested
    @DisplayName("SecurityOperationException 测试")
    class SecurityOperationExceptionTests {

        @Test
        @DisplayName("构造器 - message (默认 AUTHENTICATION)")
        void constructorWithMessage() {
            SecurityOperationException ex = new SecurityOperationException("安全异常");
            assertEquals("安全异常", ex.getMessage());
            assertEquals(SecurityOperationException.SecurityType.AUTHENTICATION, ex.getType());
        }

        @Test
        @DisplayName("构造器 - SecurityType + message")
        void constructorWithTypeAndMessage() {
            SecurityOperationException ex = new SecurityOperationException(
                    SecurityOperationException.SecurityType.AUTHORIZATION, "无权限");
            assertEquals("无权限", ex.getMessage());
            assertEquals(SecurityOperationException.SecurityType.AUTHORIZATION, ex.getType());
        }

        @Test
        @DisplayName("构造器 - SecurityType + message + cause")
        void constructorWithTypeMessageAndCause() {
            Throwable cause = new RuntimeException("auth failed");
            SecurityOperationException ex = new SecurityOperationException(
                    SecurityOperationException.SecurityType.TOKEN_EXPIRED, "Token过期", cause);
            assertEquals("Token过期", ex.getMessage());
            assertEquals(SecurityOperationException.SecurityType.TOKEN_EXPIRED, ex.getType());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("getType 返回正确的 SecurityType")
        void getTypeReturnsCorrectValue() {
            SecurityOperationException ex = new SecurityOperationException(
                    SecurityOperationException.SecurityType.ENCRYPTION, "加密错误");
            assertEquals(SecurityOperationException.SecurityType.ENCRYPTION, ex.getType());
        }

        @Test
        @DisplayName("SecurityType 枚举所有值")
        void securityTypeEnumValues() {
            SecurityOperationException.SecurityType[] values = SecurityOperationException.SecurityType.values();
            assertEquals(6, values.length);
            assertEquals(SecurityOperationException.SecurityType.AUTHENTICATION, values[0]);
            assertEquals(SecurityOperationException.SecurityType.AUTHORIZATION, values[1]);
            assertEquals(SecurityOperationException.SecurityType.ENCRYPTION, values[2]);
            assertEquals(SecurityOperationException.SecurityType.TOKEN_EXPIRED, values[3]);
            assertEquals(SecurityOperationException.SecurityType.TOKEN_BLACKLISTED, values[4]);
            assertEquals(SecurityOperationException.SecurityType.RATE_LIMITED, values[5]);
        }

        @Test
        @DisplayName("SecurityType valueOf")
        void securityTypeValueOf() {
            assertEquals(SecurityOperationException.SecurityType.AUTHENTICATION,
                    SecurityOperationException.SecurityType.valueOf("AUTHENTICATION"));
            assertEquals(SecurityOperationException.SecurityType.RATE_LIMITED,
                    SecurityOperationException.SecurityType.valueOf("RATE_LIMITED"));
        }

        @Test
        @DisplayName("继承自 SystemException")
        void extendsSystemException() {
            SecurityOperationException ex = new SecurityOperationException("test");
            assertInstanceOf(SystemException.class, ex);
        }
    }
}