package com.lianshengtong.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("安全模块边界用例测试")
class SecurityEdgeCaseTest {

    @Nested
    @DisplayName("XssProtectionFilter 测试")
    class XssProtectionFilterTests {

        @Mock
        private HttpServletRequest request;
        @Mock
        private FilterChain filterChain;
        @Mock
        private ServletResponse response;

        @Test
        @DisplayName("doFilter: 过滤器禁用时直接放行")
        void filter_disabled_passesThrough() throws Exception {
            XssProtectionFilter filter = new XssProtectionFilter(false);
            ServletRequest req = mock(ServletRequest.class);
            filter.doFilter(req, response, filterChain);
            verify(filterChain).doFilter(req, response);
        }

        @Test
        @DisplayName("doFilter: 非HttpServletRequest类型直接放行")
        void filter_nonHttpRequest_passesThrough() throws Exception {
            XssProtectionFilter filter = new XssProtectionFilter(true);
            ServletRequest req = mock(ServletRequest.class);
            filter.doFilter(req, response, filterChain);
            verify(filterChain).doFilter(req, response);
        }

        @Test
        @DisplayName("doFilter: multipart/form-data请求跳过XSS包装")
        void filter_multipart_skipsWrapping() throws Exception {
            XssProtectionFilter filter = new XssProtectionFilter(true);
            when(request.getContentType()).thenReturn("multipart/form-data; boundary=---123");
            filter.doFilter(request, response, filterChain);
            verify(filterChain).doFilter(eq(request), eq(response));
        }

        @Test
        @DisplayName("doFilter: content-type为null时正常包装")
        void filter_nullContentType_wrapsRequest() throws Exception {
            XssProtectionFilter filter = new XssProtectionFilter(true);
            when(request.getContentType()).thenReturn(null);
            filter.doFilter(request, response, filterChain);
            verify(filterChain).doFilter(any(XssRequestWrapper.class), eq(response));
        }

        @Test
        @DisplayName("doFilter: 普通表单请求包装为XssRequestWrapper")
        void filter_normalRequest_wrapsRequest() throws Exception {
            XssProtectionFilter filter = new XssProtectionFilter(true);
            when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");
            filter.doFilter(request, response, filterChain);
            verify(filterChain).doFilter(any(XssRequestWrapper.class), eq(response));
        }

        @Test
        @DisplayName("setEnabled/getEnabled: 动态开关属性设置正确")
        void filter_getterSetter_works() {
            XssProtectionFilter filter = new XssProtectionFilter();
            assertTrue(filter.getEnabled());
            filter.setEnabled(false);
            assertFalse(filter.getEnabled());
        }
    }

    @Nested
    @DisplayName("XssRequestWrapper 测试")
    class XssRequestWrapperTests {

        @Mock
        private HttpServletRequest request;

        @Test
        @DisplayName("getParameter: 含XSS脚本的参数值被清洗")
        void getParameter_xssValue_sanitized() {
            Map<String, String[]> params = new HashMap<>();
            params.put("name", new String[]{"<script>alert(1)</script>test"});
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            String val = wrapper.getParameter("name");
            assertNotNull(val);
            assertFalse(val.contains("<script>"));
            assertTrue(val.contains("test"));
        }

        @Test
        @DisplayName("getParameter: 参数名含XSS标签时也被清洗后查找")
        void getParameter_xssKey_sanitizedLookup() {
            Map<String, String[]> params = new HashMap<>();
            params.put("safeName", new String[]{"value"});
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            String val = wrapper.getParameter("<b>safeName</b>");
            assertEquals("value", val);
        }

        @Test
        @DisplayName("getParameter: 不存在的参数返回null")
        void getParameter_missingParam_returnsNull() {
            Map<String, String[]> params = new HashMap<>();
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            assertNull(wrapper.getParameter("nonexistent"));
        }

        @Test
        @DisplayName("getParameterValues: 返回清洗后的数组")
        void getParameterValues_sanitizedArray() {
            Map<String, String[]> params = new HashMap<>();
            params.put("items", new String[]{"<img src=x>apple", "<b>banana</b>"});
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            String[] values = wrapper.getParameterValues("items");
            assertNotNull(values);
            assertEquals(2, values.length);
            assertFalse(values[0].contains("<img"));
            assertFalse(values[1].contains("<b>"));
        }

        @Test
        @DisplayName("getParameterValues: 不存在参数返回null")
        void getParameterValues_missing_returnsNull() {
            Map<String, String[]> params = new HashMap<>();
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            assertNull(wrapper.getParameterValues("nope"));
        }

        @Test
        @DisplayName("getParameterMap: 返回不可修改的Map")
        void getParameterMap_unmodifiable() {
            Map<String, String[]> params = new HashMap<>();
            params.put("key", new String[]{"val"});
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            Map<String, String[]> map = wrapper.getParameterMap();
            assertThrows(UnsupportedOperationException.class, () -> map.put("new", new String[]{"v"}));
        }

        @Test
        @DisplayName("getParameterNames: 返回枚举所有清洗后的键")
        void getParameterNames_enumeration() {
            Map<String, String[]> params = new HashMap<>();
            params.put("a", new String[]{"1"});
            params.put("b", new String[]{"2"});
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            Enumeration<String> names = wrapper.getParameterNames();
            List<String> nameList = Collections.list(names);
            assertEquals(2, nameList.size());
            assertTrue(nameList.contains("a"));
            assertTrue(nameList.contains("b"));
        }

        @Test
        @DisplayName("getHeader: 含XSS的Header值被清洗")
        void getHeader_xssValue_sanitized() {
            Map<String, String[]> params = new HashMap<>();
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader("X-Custom")).thenReturn("<script>alert('xss')</script>");

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            String header = wrapper.getHeader("X-Custom");
            assertNotNull(header);
            assertFalse(header.contains("<script>"));
        }

        @Test
        @DisplayName("getHeader: null值安全处理")
        void getHeader_nullValue_returnsNull() {
            Map<String, String[]> params = new HashMap<>();
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader("X-Missing")).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            assertNull(wrapper.getHeader("X-Missing"));
        }

        @Test
        @DisplayName("getHeaders: 多值Header均被清洗")
        void getHeaders_multipleValues_sanitized() {
            Map<String, String[]> params = new HashMap<>();
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeaders("X-Vals")).thenReturn(
                    Collections.enumeration(Arrays.asList("<b>val1</b>", "normal")));

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            Enumeration<String> headers = wrapper.getHeaders("X-Vals");
            List<String> headerList = Collections.list(headers);
            assertEquals(2, headerList.size());
            assertFalse(headerList.get(0).contains("<b>"));
            assertEquals("normal", headerList.get(1));
        }

        @Test
        @DisplayName("getHeaders: 空枚举安全返回")
        void getHeaders_emptyEnumeration() {
            Map<String, String[]> params = new HashMap<>();
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeaders("X-Empty")).thenReturn(Collections.emptyEnumeration());

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            Enumeration<String> headers = wrapper.getHeaders("X-Empty");
            assertFalse(headers.hasMoreElements());
        }

        @Test
        @DisplayName("构造器: 空参数Map也可正常工作")
        void constructor_emptyParams_works() {
            Map<String, String[]> params = new HashMap<>();
            when(request.getParameterMap()).thenReturn(params);
            when(request.getHeader(anyString())).thenReturn(null);

            XssRequestWrapper wrapper = new XssRequestWrapper(request);
            assertNotNull(wrapper);
            assertNull(wrapper.getParameter("any"));
            assertTrue(!wrapper.getParameterNames().hasMoreElements());
        }
    }

    @Nested
    @DisplayName("CsrfTokenManager 测试")
    class CsrfTokenManagerTests {

        @Mock
        private StringRedisTemplate stringRedisTemplate;
        @Mock
        private ValueOperations<String, String> valueOperations;

        private CsrfTokenManager csrfTokenManager;

        @BeforeEach
        void setUp() {
            csrfTokenManager = new CsrfTokenManager(stringRedisTemplate);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("generateToken: 成功生成token并存入Redis")
        void generateToken_success() {
            String token = csrfTokenManager.generateToken("session-123", "user-456");
            assertNotNull(token);
            assertFalse(token.isEmpty());
            verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("generateToken: userId为null时仅存token")
        void generateToken_nullUserId_storesTokenOnly() {
            String token = csrfTokenManager.generateToken("session-1", null);
            assertNotNull(token);
            verify(valueOperations).set(anyString(), eq(token), anyLong(), any());
        }

        @Test
        @DisplayName("generateToken: 相同sessionId和userId每次生成不同token")
        void generateToken_uniqueEachTime() {
            String t1 = csrfTokenManager.generateToken("s1", "u1");
            String t2 = csrfTokenManager.generateToken("s1", "u1");
            assertNotEquals(t1, t2);
        }

        @Test
        @DisplayName("validateToken: sessionId为null返回false")
        void validateToken_nullSession_returnsFalse() {
            assertFalse(csrfTokenManager.validateToken(null, "token"));
        }

        @Test
        @DisplayName("validateToken: token为null返回false")
        void validateToken_nullToken_returnsFalse() {
            assertFalse(csrfTokenManager.validateToken("session", null));
        }

        @Test
        @DisplayName("validateToken: Redis中无对应key返回false")
        void validateToken_keyNotFound_returnsFalse() {
            when(valueOperations.get(anyString())).thenReturn(null);
            assertFalse(csrfTokenManager.validateToken("session", "token"));
        }

        @Test
        @DisplayName("validateToken: token匹配返回true")
        void validateToken_match_returnsTrue() {
            when(valueOperations.get(anyString())).thenReturn("user-123:myToken");
            assertTrue(csrfTokenManager.validateToken("session", "myToken"));
        }

        @Test
        @DisplayName("validateToken: token不匹配返回false")
        void validateToken_mismatch_returnsFalse() {
            when(valueOperations.get(anyString())).thenReturn("user-123:wrongToken");
            assertFalse(csrfTokenManager.validateToken("session", "myToken"));
        }

        @Test
        @DisplayName("validateToken: 存储值不含冒号时纯token匹配")
        void validateToken_noColon_matchesToken() {
            when(valueOperations.get(anyString())).thenReturn("pureTokenValue");
            assertTrue(csrfTokenManager.validateToken("session", "pureTokenValue"));
        }

        @Test
        @DisplayName("invalidateToken: sessionId为null时不执行删除")
        void invalidateToken_nullSession_noDelete() {
            csrfTokenManager.invalidateToken(null);
            verify(stringRedisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("invalidateToken: 正常删除Redis中的token")
        void invalidateToken_success() {
            csrfTokenManager.invalidateToken("session-1");
            verify(stringRedisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("getTokenHeaderName: 返回固定Header名")
        void getTokenHeaderName_returnsConstant() {
            assertEquals("X-CSRF-Token", CsrfTokenManager.getTokenHeaderName());
        }

        @Test
        @DisplayName("getTokenCookieName: 返回固定Cookie名")
        void getTokenCookieName_returnsConstant() {
            assertEquals("XSRF-TOKEN", CsrfTokenManager.getTokenCookieName());
        }

        @Test
        @DisplayName("setStringRedisTemplate/getStringRedisTemplate: 属性存取正常")
        void getterSetter_works() {
            CsrfTokenManager mgr = new CsrfTokenManager();
            assertNull(mgr.getStringRedisTemplate());
            mgr.setStringRedisTemplate(stringRedisTemplate);
            assertSame(stringRedisTemplate, mgr.getStringRedisTemplate());
        }
    }
}