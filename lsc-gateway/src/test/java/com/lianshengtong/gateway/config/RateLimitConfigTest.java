package com.lianshengtong.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("限流配置测试")
class RateLimitConfigTest {

    private RateLimitConfig rateLimitConfig;

    @Mock
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
    }

    private ServerHttpRequest mockRequest(HttpHeaders headers, InetSocketAddress remoteAddress) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        lenient().when(request.getURI()).thenReturn(URI.create("/test"));
        RequestPath requestPath = mock(RequestPath.class);
        lenient().when(requestPath.value()).thenReturn("/test");
        lenient().when(request.getPath()).thenReturn(requestPath);
        when(request.getHeaders()).thenReturn(headers != null ? headers : new HttpHeaders());
        if (remoteAddress != null) {
            lenient().when(request.getRemoteAddress()).thenReturn(remoteAddress);
        } else {
            lenient().when(request.getRemoteAddress()).thenReturn(null);
        }
        return request;
    }

    @Nested
    @DisplayName("IP解析(resolveIp)测试")
    class ResolveIpTest {

        @Test
        @DisplayName("优先使用 X-Forwarded-For 头获取IP")
        void shouldResolveIpFromXForwardedFor() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(
                    rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 有多级代理时取第一个IP")
        void shouldResolveFirstIpFromXForwardedForChain() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(
                    rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 为空时使用 X-Real-IP")
        void shouldFallbackToXRealIp() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Real-IP", "172.16.0.1");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(
                    rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("172.16.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 为空字符串时使用 X-Real-IP")
        void shouldFallbackToXRealIpWhenXffBlank() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "");
            headers.add("X-Real-IP", "172.16.0.2");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(
                    rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("172.16.0.2");
        }

        @Test
        @DisplayName("无代理头时使用远程地址")
        void shouldFallbackToRemoteAddress() {
            InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.50", 54321);
            ServerHttpRequest request = mockRequest(new HttpHeaders(), remoteAddress);

            String ip = ReflectionTestUtils.invokeMethod(
                    rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("192.168.1.50");
        }

        @Test
        @DisplayName("无任何IP信息时返回 unknown")
        void shouldReturnUnknownWhenNoIpAvailable() {
            ServerHttpRequest request = mockRequest(new HttpHeaders(), null);

            String ip = ReflectionTestUtils.invokeMethod(
                    rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("IP限流键解析器(ipKeyResolver)测试")
    class IpKeyResolverTest {

        @Test
        @DisplayName("ipKeyResolver 返回 X-Forwarded-For 中的IP")
        void shouldResolveIpFromXForwardedFor() {
            KeyResolver resolver = rateLimitConfig.ipKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("ipKeyResolver 返回 X-Real-IP 中的IP")
        void shouldResolveIpFromXRealIp() {
            KeyResolver resolver = rateLimitConfig.ipKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Real-IP", "172.16.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("172.16.0.1");
        }

        @Test
        @DisplayName("ipKeyResolver 返回远程地址作为兜底")
        void shouldResolveIpFromRemoteAddress() {
            KeyResolver resolver = rateLimitConfig.ipKeyResolver();
            InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.100", 12345);
            ServerHttpRequest request = mockRequest(new HttpHeaders(), remoteAddress);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("192.168.1.100");
        }
    }

    @Nested
    @DisplayName("用户限流键解析器(userKeyResolver)测试")
    class UserKeyResolverTest {

        @Test
        @DisplayName("存在 X-User-Id 时使用用户ID作为限流键")
        void shouldUseUserIdWhenHeaderPresent() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "user123");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:user123");
        }

        @Test
        @DisplayName("无 X-User-Id 时回退到IP限流")
        void shouldFallbackToIpWhenNoUserHeader() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:10.0.0.1");
        }

        @Test
        @DisplayName("X-User-Id 为空字符串时回退到IP限流")
        void shouldFallbackToIpWhenUserHeaderBlank() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "");
            headers.add("X-Forwarded-For", "10.0.0.2");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:10.0.0.2");
        }

        @Test
        @DisplayName("有用户ID但无代理头时仍正确组合")
        void shouldUseUserPrefixWithRemoteIp() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "merchant456");
            InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.200", 33333);
            ServerHttpRequest request = mockRequest(headers, remoteAddress);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:merchant456");
        }
    }

    @Nested
    @DisplayName("用户+IP组合限流键解析器(ipUserKeyResolver)测试")
    class IpUserKeyResolverTest {

        @Test
        @DisplayName("有用户ID时组合用户和IP作为限流键")
        void shouldCombineUserAndIpWhenUserPresent() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "user789");
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:user789@10.0.0.1");
        }

        @Test
        @DisplayName("无用户ID时回退到纯IP限流")
        void shouldFallbackToIpOnlyWhenNoUser() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.2");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:10.0.0.2");
        }

        @Test
        @DisplayName("有用户ID但无代理头时使用远程地址组合")
        void shouldCombineUserWithRemoteAddress() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "admin001");
            InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.10", 11111);
            ServerHttpRequest request = mockRequest(headers, remoteAddress);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:admin001@192.168.1.10");
        }

        @Test
        @DisplayName("用户ID为空时回退到纯IP限流")
        void shouldFallbackToIpWhenUserHeaderBlank() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", " ");
            headers.add("X-Real-IP", "172.16.0.5");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:172.16.0.5");
        }
    }
}
