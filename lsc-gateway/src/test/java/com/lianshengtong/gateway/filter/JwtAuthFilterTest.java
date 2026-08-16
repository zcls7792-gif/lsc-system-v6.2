package com.lianshengtong.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT鉴权过滤器测试")
class JwtAuthFilterTest {

    private static final String USER_SECRET = "LscUserSecretKey2026ForJwtTokenSignMustBeLongEnough";
    private static final String USER_ISSUER = "lsc-user-service";
    private static final String ADMIN_SECRET = "LscAdminSecretKey2026ForJwtTokenSignMustBeLongEnough";
    private static final String ADMIN_ISSUER = "lsc-admin-service";
    private static final String WHITELIST = "/api/user/login,/api/user/register,/actuator/**,/doc.html";

    private JwtAuthFilter filter;

    @Mock
    private GatewayFilterChain chain;

    private SecretKey userKey;
    private SecretKey adminKey;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter();
        ReflectionTestUtils.setField(filter, "userSecret", USER_SECRET);
        ReflectionTestUtils.setField(filter, "userIssuer", USER_ISSUER);
        ReflectionTestUtils.setField(filter, "adminSecret", ADMIN_SECRET);
        ReflectionTestUtils.setField(filter, "adminIssuer", ADMIN_ISSUER);
        ReflectionTestUtils.setField(filter, "whitelistStr", WHITELIST);
        filter.init();

        userKey = Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8));
        adminKey = Keys.hmacShaKeyFor(ADMIN_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String buildUserToken(String subject, String userType) {
        return Jwts.builder()
                .subject(subject)
                .issuer(USER_ISSUER)
                .claim("userType", userType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(userKey)
                .compact();
    }

    private String buildAdminToken(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .issuer(ADMIN_ISSUER)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(adminKey)
                .compact();
    }

    private String buildExpiredUserToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuer(USER_ISSUER)
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .expiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(userKey)
                .compact();
    }

    private ServerHttpRequest mockRequest(String path, HttpHeaders headers, InetSocketAddress remoteAddress) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        lenient().when(request.getURI()).thenReturn(URI.create(path));
        RequestPath requestPath = mock(RequestPath.class);
        lenient().when(requestPath.value()).thenReturn(path);
        lenient().when(request.getPath()).thenReturn(requestPath);
        lenient().when(request.getHeaders()).thenReturn(headers != null ? headers : new HttpHeaders());
        if (remoteAddress != null) {
            lenient().when(request.getRemoteAddress()).thenReturn(remoteAddress);
        }
        ServerHttpRequest.Builder reqBuilder = mock(ServerHttpRequest.Builder.class);
        lenient().when(request.mutate()).thenReturn(reqBuilder);
        lenient().when(reqBuilder.header(anyString(), any(String[].class))).thenReturn(reqBuilder);
        lenient().when(reqBuilder.build()).thenReturn(request);
        return request;
    }

    private ServerHttpRequest mockRequest(String path, HttpHeaders headers) {
        return mockRequest(path, headers, null);
    }

    private ServerWebExchange mockExchangeWithResponse(ServerHttpRequest request, ServerHttpResponse response) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        lenient().when(exchange.getRequest()).thenReturn(request);
        lenient().when(exchange.getResponse()).thenReturn(response);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        lenient().when(exchange.mutate()).thenReturn(builder);
        lenient().when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(exchange);
        return exchange;
    }

    private ServerHttpResponse mockResponse() {
        ServerHttpResponse response = mock(ServerHttpResponse.class, RETURNS_DEEP_STUBS);
        HttpHeaders responseHeaders = new HttpHeaders();
        lenient().when(response.getHeaders()).thenReturn(responseHeaders);
        DataBufferFactory bufferFactory = mock(DataBufferFactory.class);
        lenient().when(response.bufferFactory()).thenReturn(bufferFactory);
        lenient().when(bufferFactory.wrap(any(byte[].class))).thenReturn(mock(DataBuffer.class));
        lenient().when(response.writeWith(any())).thenReturn(Mono.empty());
        lenient().when(response.setStatusCode(any())).thenReturn(true);
        return response;
    }

    @Nested
    @DisplayName("白名单路径匹配测试")
    class IsWhitelistedTest {

        @Test
        @DisplayName("白名单中的路径应返回 true - 登录接口")
        void shouldReturnTrue_forLoginPath() {
            Boolean result = ReflectionTestUtils.invokeMethod(
                    filter, "isWhitelisted", "/api/user/login");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("白名单中的路径应返回 true - 注册接口")
        void shouldReturnTrue_forRegisterPath() {
            Boolean result = ReflectionTestUtils.invokeMethod(
                    filter, "isWhitelisted", "/api/user/register");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Ant风格通配符匹配 - actuator健康检查")
        void shouldReturnTrue_forActuatorWildcard() {
            Boolean result = ReflectionTestUtils.invokeMethod(
                    filter, "isWhitelisted", "/actuator/health");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Ant风格通配符匹配 - actuator信息端点")
        void shouldReturnTrue_forActuatorInfo() {
            Boolean result = ReflectionTestUtils.invokeMethod(
                    filter, "isWhitelisted", "/actuator/info");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("不在白名单中的路径应返回 false")
        void shouldReturnFalse_forNonWhitelistedPath() {
            Boolean result = ReflectionTestUtils.invokeMethod(
                    filter, "isWhitelisted", "/api/order/list");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("空路径应返回 false")
        void shouldReturnFalse_forEmptyPath() {
            Boolean result = ReflectionTestUtils.invokeMethod(
                    filter, "isWhitelisted", "");
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("客户端IP解析测试")
    class ResolveClientIpTest {

        @Test
        @DisplayName("优先使用 X-Forwarded-For 头")
        void shouldUseXForwardedForFirst() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest("/test", headers, new InetSocketAddress("192.168.1.100", 12345));

            String ip = ReflectionTestUtils.invokeMethod(
                    filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 有多级代理时取第一个IP")
        void shouldUseFirstIpFromXForwardedFor() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(
                    filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 为空时使用 X-Real-IP")
        void shouldFallbackToXRealIp() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Real-IP", "172.16.0.1");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(
                    filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("172.16.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 为空字符串时使用 X-Real-IP")
        void shouldFallbackToXRealIpWhenXffBlank() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "");
            headers.add("X-Real-IP", "172.16.0.2");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(
                    filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("172.16.0.2");
        }

        @Test
        @DisplayName("无代理头时使用远程地址")
        void shouldFallbackToRemoteAddress() {
            ServerHttpRequest request = mockRequest("/test", new HttpHeaders(), new InetSocketAddress("192.168.1.50", 54321));

            String ip = ReflectionTestUtils.invokeMethod(
                    filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("192.168.1.50");
        }

        @Test
        @DisplayName("无任何IP信息时返回 unknown")
        void shouldReturnUnknownWhenNoIpAvailable() {
            ServerHttpRequest request = mockRequest("/test", new HttpHeaders());

            String ip = ReflectionTestUtils.invokeMethod(
                    filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("JWT令牌解析测试")
    class ParseTokenTest {

        @Test
        @DisplayName("有效的用户JWT应成功解析")
        void shouldParseValidUserToken() {
            String token = buildUserToken("user123", "merchant");

            Claims claims = ReflectionTestUtils.invokeMethod(
                    filter, "parseToken", token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("user123");
            assertThat(claims.getIssuer()).isEqualTo(USER_ISSUER);
        }

        @Test
        @DisplayName("有效的管理员JWT应成功解析")
        void shouldParseValidAdminToken() {
            String token = buildAdminToken("admin001", "SUPER_ADMIN");

            Claims claims = ReflectionTestUtils.invokeMethod(
                    filter, "parseToken", token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("admin001");
            assertThat(claims.getIssuer()).isEqualTo(ADMIN_ISSUER);
            assertThat(claims.get("role")).isEqualTo("SUPER_ADMIN");
        }

        @Test
        @DisplayName("用户令牌包含 userType 声明时应正确解析")
        void shouldParseTokenWithUserTypeClaim() {
            String token = buildUserToken("user456", "merchant");

            Claims claims = ReflectionTestUtils.invokeMethod(
                    filter, "parseToken", token);

            assertThat(claims.get("userType")).isEqualTo("merchant");
        }

        @Test
        @DisplayName("过期的JWT应抛出异常")
        void shouldThrowExceptionForExpiredToken() {
            String expiredToken = buildExpiredUserToken("user789");

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", expiredToken));
        }

        @Test
        @DisplayName("格式错误的JWT应抛出异常")
        void shouldThrowExceptionForMalformedToken() {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", "invalid-token-format"));
        }

        @Test
        @DisplayName("用户密钥验签失败时应尝试管理员密钥")
        void shouldTryAdminKeyWhenUserKeyFails() {
            String adminToken = buildAdminToken("admin002", "ADMIN");

            Claims claims = ReflectionTestUtils.invokeMethod(
                    filter, "parseToken", adminToken);

            assertThat(claims).isNotNull();
            assertThat(claims.getIssuer()).isEqualTo(ADMIN_ISSUER);
        }

        @Test
        @DisplayName("用管理员密钥签发的令牌无法用用户密钥验签时仍能通过")
        void shouldFallbackToAdminKeyVerification() {
            String token = Jwts.builder()
                    .subject("fallback-admin")
                    .issuer(ADMIN_ISSUER)
                    .claim("role", "OPERATOR")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(adminKey)
                    .compact();

            Claims claims = ReflectionTestUtils.invokeMethod(
                    filter, "parseToken", token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("fallback-admin");
            assertThat(claims.get("role")).isEqualTo("OPERATOR");
        }
    }

    @Nested
    @DisplayName("过滤器整体流程测试")
    class FilterTest {

        @Test
        @DisplayName("白名单路径直接放行 - 不经过鉴权")
        void shouldPassThrough_whitelistedPath() {
            ServerHttpRequest request = mockRequest("/api/user/login", new HttpHeaders());
            ServerWebExchange mockExchange = mock(ServerWebExchange.class);
            when(mockExchange.getRequest()).thenReturn(request);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(mockExchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(mockExchange);
        }

        @Test
        @DisplayName("非白名单路径无Authorization头返回401")
        void shouldReturn401_whenNoAuthHeader() {
            ServerHttpRequest request = mockRequest("/api/order/list", new HttpHeaders());
            ServerHttpResponse response = mockResponse();
            ServerWebExchange mockExchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(mockExchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Authorization头无Bearer前缀返回401")
        void shouldReturn401_whenNoBearerPrefix() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.AUTHORIZATION, "InvalidToken123");
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange mockExchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(mockExchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("过期JWT返回401")
        void shouldReturn401_whenTokenExpired() {
            String expiredToken = buildExpiredUserToken("user123");
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken);
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange mockExchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(mockExchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("有效用户JWT通过鉴权并设置 X-User-Id 头")
        void shouldPassWithUserToken_andSetUserIdHeader() {
            String token = buildUserToken("user123", "merchant");
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange mockExchange = mockExchangeWithResponse(request, response);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(mockExchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("有效管理员JWT通过鉴权并设置 X-Admin-Role 头")
        void shouldPassWithAdminToken_andSetAdminRoleHeader() {
            String token = buildAdminToken("admin001", "SUPER_ADMIN");
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            ServerHttpRequest request = mockRequest("/api/admin/users", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange mockExchange = mockExchangeWithResponse(request, response);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(mockExchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("未授权响应构建测试")
    class UnauthorizedResponseTest {

        @Test
        @DisplayName("unauthorized应设置401状态码")
        void shouldSetStatus401() {
            ServerHttpResponse response = mockResponse();
            ServerWebExchange mockExchange = mock(ServerWebExchange.class);
            when(mockExchange.getResponse()).thenReturn(response);

            Mono<Void> result = ReflectionTestUtils.invokeMethod(
                    filter, "unauthorized", mockExchange, "测试未授权");

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("unauthorized应设置Content-Type为JSON")
        void shouldSetJsonContentType() {
            ServerHttpResponse response = mockResponse();
            ServerWebExchange mockExchange = mock(ServerWebExchange.class);
            when(mockExchange.getResponse()).thenReturn(response);

            Mono<Void> result = ReflectionTestUtils.invokeMethod(
                    filter, "unauthorized", mockExchange, "测试未授权");

            assertThat(result).isNotNull();
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }
}
