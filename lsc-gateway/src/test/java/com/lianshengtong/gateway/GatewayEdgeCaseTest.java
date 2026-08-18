package com.lianshengtong.gateway;

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
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
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
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("网关过滤与限流 - 边界场景测试")
class GatewayEdgeCaseTest {

    private static final String USER_SECRET = "LscUserSecretKey2026ForJwtTokenSignMustBeLongEnough";
    private static final String USER_ISSUER = "lsc-user-service";
    private static final String ADMIN_SECRET = "LscAdminSecretKey2026ForJwtTokenSignMustBeLongEnough";
    private static final String ADMIN_ISSUER = "lsc-admin-service";
    private static final String WHITELIST = "/api/user/login,/api/user/register,/actuator/**,/doc.html";

    private JwtAuthFilter filter;
    private RateLimitConfig rateLimitConfig;

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

        rateLimitConfig = new RateLimitConfig();
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

    private String buildExpiredAdminToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuer(ADMIN_ISSUER)
                .claim("role", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .expiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(adminKey)
                .compact();
    }

    private String buildTokenWithDifferentIssuer(String subject, String issuer, SecretKey key) {
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
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

    private ServerWebExchange mockExchange(ServerHttpRequest request) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        lenient().when(exchange.getRequest()).thenReturn(request);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        lenient().when(exchange.mutate()).thenReturn(builder);
        lenient().when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(exchange);
        return exchange;
    }

    // ==================== JwtAuthFilter - init() 边界场景 ====================

    @Nested
    @DisplayName("JwtAuthFilter init() 边界场景测试")
    class InitEdgeCaseTest {

        @Test
        @DisplayName("init: userSecret为null时抛IllegalStateException")
        void init_nullUserSecret_throws() {
            JwtAuthFilter testFilter = new JwtAuthFilter();
            ReflectionTestUtils.setField(testFilter, "userSecret", null);
            ReflectionTestUtils.setField(testFilter, "userIssuer", USER_ISSUER);
            ReflectionTestUtils.setField(testFilter, "adminSecret", ADMIN_SECRET);
            ReflectionTestUtils.setField(testFilter, "adminIssuer", ADMIN_ISSUER);
            ReflectionTestUtils.setField(testFilter, "whitelistStr", WHITELIST);

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, testFilter::init);
        }

        @Test
        @DisplayName("init: userSecret为空白字符串时抛IllegalStateException")
        void init_blankUserSecret_throws() {
            JwtAuthFilter testFilter = new JwtAuthFilter();
            ReflectionTestUtils.setField(testFilter, "userSecret", "   ");
            ReflectionTestUtils.setField(testFilter, "userIssuer", USER_ISSUER);
            ReflectionTestUtils.setField(testFilter, "adminSecret", ADMIN_SECRET);
            ReflectionTestUtils.setField(testFilter, "adminIssuer", ADMIN_ISSUER);
            ReflectionTestUtils.setField(testFilter, "whitelistStr", WHITELIST);

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, testFilter::init);
        }

        @Test
        @DisplayName("init: adminSecret为null时抛IllegalStateException")
        void init_nullAdminSecret_throws() {
            JwtAuthFilter testFilter = new JwtAuthFilter();
            ReflectionTestUtils.setField(testFilter, "userSecret", USER_SECRET);
            ReflectionTestUtils.setField(testFilter, "userIssuer", USER_ISSUER);
            ReflectionTestUtils.setField(testFilter, "adminSecret", null);
            ReflectionTestUtils.setField(testFilter, "adminIssuer", ADMIN_ISSUER);
            ReflectionTestUtils.setField(testFilter, "whitelistStr", WHITELIST);

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, testFilter::init);
        }

        @Test
        @DisplayName("init: adminSecret为空白字符串时抛IllegalStateException")
        void init_blankAdminSecret_throws() {
            JwtAuthFilter testFilter = new JwtAuthFilter();
            ReflectionTestUtils.setField(testFilter, "userSecret", USER_SECRET);
            ReflectionTestUtils.setField(testFilter, "userIssuer", USER_ISSUER);
            ReflectionTestUtils.setField(testFilter, "adminSecret", "\t");
            ReflectionTestUtils.setField(testFilter, "adminIssuer", ADMIN_ISSUER);
            ReflectionTestUtils.setField(testFilter, "whitelistStr", WHITELIST);

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, testFilter::init);
        }

        @Test
        @DisplayName("init: whitelistStr为null时白名单为空列表")
        void init_nullWhitelistStr_emptyList() {
            JwtAuthFilter testFilter = new JwtAuthFilter();
            ReflectionTestUtils.setField(testFilter, "userSecret", USER_SECRET);
            ReflectionTestUtils.setField(testFilter, "userIssuer", USER_ISSUER);
            ReflectionTestUtils.setField(testFilter, "adminSecret", ADMIN_SECRET);
            ReflectionTestUtils.setField(testFilter, "adminIssuer", ADMIN_ISSUER);
            ReflectionTestUtils.setField(testFilter, "whitelistStr", null);
            testFilter.init();

            java.util.List<String> whitelist = ReflectionTestUtils.getField(testFilter, "whitelist");
            assertThat(whitelist).isEmpty();
        }

        @Test
        @DisplayName("init: whitelistStr为空字符串时白名单为空列表")
        void init_emptyWhitelistStr_emptyList() {
            JwtAuthFilter testFilter = new JwtAuthFilter();
            ReflectionTestUtils.setField(testFilter, "userSecret", USER_SECRET);
            ReflectionTestUtils.setField(testFilter, "userIssuer", USER_ISSUER);
            ReflectionTestUtils.setField(testFilter, "adminSecret", ADMIN_SECRET);
            ReflectionTestUtils.setField(testFilter, "adminIssuer", ADMIN_ISSUER);
            ReflectionTestUtils.setField(testFilter, "whitelistStr", "");
            testFilter.init();

            java.util.List<String> whitelist = ReflectionTestUtils.getField(testFilter, "whitelist");
            assertThat(whitelist).isEmpty();
        }

        @Test
        @DisplayName("init: whitelistStr只有逗号分隔空项时白名单为空")
        void init_commasOnlyWhitelist_emptyList() {
            JwtAuthFilter testFilter = new JwtAuthFilter();
            ReflectionTestUtils.setField(testFilter, "userSecret", USER_SECRET);
            ReflectionTestUtils.setField(testFilter, "userIssuer", USER_ISSUER);
            ReflectionTestUtils.setField(testFilter, "adminSecret", ADMIN_SECRET);
            ReflectionTestUtils.setField(testFilter, "adminIssuer", ADMIN_ISSUER);
            ReflectionTestUtils.setField(testFilter, "whitelistStr", ",,,");
            testFilter.init();

            java.util.List<String> whitelist = ReflectionTestUtils.getField(testFilter, "whitelist");
            assertThat(whitelist).isEmpty();
        }
    }

    // ==================== JwtAuthFilter - 白名单边界场景 ====================

    @Nested
    @DisplayName("JwtAuthFilter 白名单路径边界匹配测试")
    class WhitelistEdgeCaseTest {

        @Test
        @DisplayName("白名单为null时任何路径都不放行")
        void isWhitelisted_nullWhitelist_returnsFalse() {
            ReflectionTestUtils.setField(filter, "whitelist", null);

            Boolean result = ReflectionTestUtils.invokeMethod(filter, "isWhitelisted", "/api/user/login");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("白名单为空列表时任何路径都不放行")
        void isWhitelisted_emptyWhitelist_returnsFalse() {
            ReflectionTestUtils.setField(filter, "whitelist", java.util.List.of());

            Boolean result = ReflectionTestUtils.invokeMethod(filter, "isWhitelisted", "/api/user/login");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Ant通配符-双路径通配符匹配")
        void isWhitelisted_antDoubleWildcard() {
            Boolean result = ReflectionTestUtils.invokeMethod(filter, "isWhitelisted", "/actuator/health");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Ant通配符-单路径通配符匹配失败")
        void isWhitelisted_antSingleWildcard_noMatch() {
            Boolean result = ReflectionTestUtils.invokeMethod(filter, "isWhitelisted", "/api/test/login");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("精确匹配-完整路径匹配")
        void isWhitelisted_exactMatch() {
            Boolean result = ReflectionTestUtils.invokeMethod(filter, "isWhitelisted", "/api/user/login");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("路径前缀不完全匹配返回false")
        void isWhitelisted_prefixNotMatch() {
            Boolean result = ReflectionTestUtils.invokeMethod(filter, "isWhitelisted", "/api/user/loginextra");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("大小写敏感-不同大小写不匹配")
        void isWhitelisted_caseSensitive() {
            Boolean result = ReflectionTestUtils.invokeMethod(filter, "isWhitelisted", "/API/user/login");
            assertThat(result).isFalse();
        }
    }

    // ==================== JwtAuthFilter - Token解析边界场景 ====================

    @Nested
    @DisplayName("JwtAuthFilter Token解析边界场景测试")
    class ParseTokenEdgeCaseTest {

        @Test
        @DisplayName("过期的用户令牌(已过期2小时)抛异常")
        void parseToken_expiredUserToken_throws() {
            String expiredToken = buildExpiredUserToken("user_expired");

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", expiredToken));
        }

        @Test
        @DisplayName("过期的管理员令牌抛异常")
        void parseToken_expiredAdminToken_throws() {
            String expiredToken = buildExpiredAdminToken("admin_expired");

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", expiredToken));
        }

        @Test
        @DisplayName("格式错误的令牌(随机字符串)抛异常")
        void parseToken_malformedToken_throws() {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", "this-is-not-a-valid-jwt-token"));
        }

        @Test
        @DisplayName("空字符串作为令牌抛异常")
        void parseToken_emptyString_throws() {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", ""));
        }

        @Test
        @DisplayName("仅包含Bearer前缀无实际内容时解析失败")
        void parseToken_bearerOnly_throws() {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", "Bearer "));
        }

        @Test
        @DisplayName("用户密钥签发但issuer为管理员的令牌解析失败")
        void parseToken_userKeyAdminIssuer_throws() {
            String wrongIssuerToken = buildTokenWithDifferentIssuer("user1", ADMIN_ISSUER, userKey);

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", wrongIssuerToken));
        }

        @Test
        @DisplayName("管理员密钥签发但issuer为用户的令牌解析失败")
        void parseToken_adminKeyUserIssuer_throws() {
            String wrongIssuerToken = buildTokenWithDifferentIssuer("admin1", USER_ISSUER, adminKey);

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", wrongIssuerToken));
        }

        @Test
        @DisplayName("使用密钥对调的令牌解析失败")
        void parseToken_swappedKeys_throws() {
            String wrongKeyToken = Jwts.builder()
                    .subject("swap-user")
                    .issuer(USER_ISSUER)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(adminKey)
                    .compact();

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", wrongKeyToken));
        }

        @Test
        @DisplayName("用户令牌无userType声明正常解析")
        void parseToken_userTokenNoUserType_parsesSuccessfully() {
            String token = Jwts.builder()
                    .subject("user_no_type")
                    .issuer(USER_ISSUER)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(userKey)
                    .compact();

            Claims claims = ReflectionTestUtils.invokeMethod(filter, "parseToken", token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("user_no_type");
        }

        @Test
        @DisplayName("管理员令牌无role声明仍可解析")
        void parseToken_adminTokenNoRole_parsesSuccessfully() {
            String token = Jwts.builder()
                    .subject("admin_no_role")
                    .issuer(ADMIN_ISSUER)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(adminKey)
                    .compact();

            Claims claims = ReflectionTestUtils.invokeMethod(filter, "parseToken", token);

            assertThat(claims).isNotNull();
            assertThat(claims.get("role")).isNull();
        }

        @Test
        @DisplayName("解析null令牌抛异常")
        void parseToken_nullToken_throws() {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(filter, "parseToken", (String) null));
        }
    }

    // ==================== JwtAuthFilter - resolveClientIp边界场景 ====================

    @Nested
    @DisplayName("JwtAuthFilter IP解析边界场景测试")
    class ResolveClientIpEdgeCaseTest {

        @Test
        @DisplayName("X-Forwarded-For包含IPv6地址正确解析")
        void resolveClientIp_ipv6FromXForwardedFor() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "2001:db8::1");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("2001:db8::1");
        }

        @Test
        @DisplayName("X-Forwarded-For包含IPv6多级代理正确解析首个")
        void resolveClientIp_ipv6Chain_firstIp() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "2001:db8::1, 10.0.0.1");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("2001:db8::1");
        }

        @Test
        @DisplayName("X-Forwarded-For为localhost(127.0.0.1)")
        void resolveClientIp_localhostFromXff() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "127.0.0.1");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For为IPv6本地(::1)")
        void resolveClientIp_ipv6LoopbackFromXff() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "::1");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("::1");
        }

        @Test
        @DisplayName("无代理头且remoteAddress为null返回unknown")
        void resolveClientIp_allNull_returnsUnknown() {
            ServerHttpRequest request = mockRequest("/test", new HttpHeaders(), null);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("unknown");
        }

        @Test
        @DisplayName("X-Forwarded-For含空格的IP正确trim")
        void resolveClientIp_trimSpaces() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "  10.0.0.1  ");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("X-Real-IP含空格正确trim")
        void resolveClientIp_trimXRealIpSpaces() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Real-IP", "  192.168.1.1  ");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("192.168.1.1");
        }

        @Test
        @DisplayName("X-Forwarded-For为纯空格回退到X-Real-IP")
        void resolveClientIp_xffOnlySpaces_fallbackToRealIp() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "   ");
            headers.add("X-Real-IP", "172.16.0.99");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("172.16.0.99");
        }

        @Test
        @DisplayName("X-Forwarded-For多级代理含IPv6正确取第一个IPv6")
        void resolveClientIp_mixedIpv4Ipv6Chain_first() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "2001:db8::1, 10.0.0.1, 192.168.1.1");
            ServerHttpRequest request = mockRequest("/test", headers);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("2001:db8::1");
        }

        @Test
        @DisplayName("remoteAddress为IPv6地址正确解析")
        void resolveClientIp_remoteIpv6() {
            InetSocketAddress ipv6Addr = InetSocketAddress.createUnresolved("2001:db8::1", 8080);
            ServerHttpRequest request = mockRequest("/test", new HttpHeaders(), ipv6Addr);

            String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", request);
            assertThat(ip).isEqualTo("2001:db8::1");
        }
    }

    // ==================== JwtAuthFilter - 过滤器流程边界场景 ====================

    @Nested
    @DisplayName("JwtAuthFilter 过滤器流程边界场景测试")
    class FilterEdgeCaseTest {

        @Test
        @DisplayName("Authorization头为null返回401")
        void filter_nullAuthHeader_returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", null);
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Authorization头仅为Bearer(无空格)返回401")
        void filter_bearerNoSpace_returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer");
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Authorization头为Bearer+空格+空token返回401")
        void filter_bearerWithEmptyToken_returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer ");
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Authorization头为Bearer+null字符串token返回401")
        void filter_bearerWithNullToken_returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + null);
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("过期管理员令牌返回401")
        void filter_expiredAdminToken_returns401() {
            String expiredToken = buildExpiredAdminToken("admin_expired");
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + expiredToken);
            ServerHttpRequest request = mockRequest("/api/admin/users", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("有效管理员令牌无role声明正常通过不设置X-Admin-Role")
        void filter_adminTokenNoRole_passesWithoutAdminRole() {
            String token = Jwts.builder()
                    .subject("admin_no_role")
                    .issuer(ADMIN_ISSUER)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(adminKey)
                    .compact();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + token);
            ServerHttpRequest request = mockRequest("/api/admin/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("有效用户令牌无userType声明正常通过")
        void filter_userTokenNoUserType_passes() {
            String token = Jwts.builder()
                    .subject("user_no_type")
                    .issuer(USER_ISSUER)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(userKey)
                    .compact();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + token);
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("白名单-文档页面直接放行")
        void filter_docPage_whitelisted() {
            ServerHttpRequest request = mockRequest("/doc.html", new HttpHeaders());
            ServerWebExchange exchange = mockExchange(request);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("白名单-Actuator子路径直接放行")
        void filter_actuatorSubPath_whitelisted() {
            ServerHttpRequest request = mockRequest("/actuator/metrics", new HttpHeaders());
            ServerWebExchange exchange = mockExchange(request);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("白名单-Actuator根路径直接放行")
        void filter_actuatorRoot_whitelisted() {
            ServerHttpRequest request = mockRequest("/actuator", new HttpHeaders());
            ServerWebExchange exchange = mockExchange(request);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("非白名单路径且Authorization格式错误返回401")
        void filter_invalidAuthFormat_returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Token some-invalid-format");
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Authorization头为纯Bearer字符串(无token)返回401")
        void filter_bearerOnlyString_returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer");
            ServerHttpRequest request = mockRequest("/api/order/list", headers);
            ServerHttpResponse response = mockResponse();
            ServerWebExchange exchange = mockExchangeWithResponse(request, response);

            Mono<Void> result = filter.filter(exchange, chain);

            assertThat(result).isNotNull();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================== RateLimitConfig - resolveIp边界场景 ====================

    @Nested
    @DisplayName("RateLimitConfig IP解析边界场景测试")
    class RateLimitResolveIpEdgeCaseTest {

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

        @Test
        @DisplayName("X-Forwarded-For为IPv6地址正确解析")
        void resolveIp_ipv6FromXff() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "2001:db8::1");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("2001:db8::1");
        }

        @Test
        @DisplayName("X-Forwarded-For为IPv6多级代理取第一个")
        void resolveIp_ipv6Chain_first() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "fe80::1, fe80::2");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("fe80::1");
        }

        @Test
        @DisplayName("X-Forwarded-For为localhost(127.0.0.1)")
        void resolveIp_localhostFromXff() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "127.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For为IPv6 loopback(::1)")
        void resolveIp_ipv6Loopback() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "::1");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("::1");
        }

        @Test
        @DisplayName("X-Forwarded-For为纯空格回退X-Real-IP")
        void resolveIp_xffSpaces_fallbackToRealIp() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "   ");
            headers.add("X-Real-IP", "172.16.0.100");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("172.16.0.100");
        }

        @Test
        @DisplayName("X-Real-IP为纯空格回退remoteAddress")
        void resolveIp_realIpSpaces_fallbackToRemote() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Real-IP", "   ");
            InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.200", 12345);
            ServerHttpRequest request = mockRequest(headers, remoteAddress);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("192.168.1.200");
        }

        @Test
        @DisplayName("所有header为null且remoteAddress为null返回unknown")
        void resolveIp_allNull_returnsUnknown() {
            ServerHttpRequest request = mockRequest(new HttpHeaders(), null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("unknown");
        }

        @Test
        @DisplayName("remoteAddress为IPv6地址正确解析")
        void resolveIp_remoteIpv6() {
            InetSocketAddress ipv6Addr = InetSocketAddress.createUnresolved("2001:db8::100", 9090);
            ServerHttpRequest request = mockRequest(new HttpHeaders(), ipv6Addr);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("2001:db8::100");
        }

        @Test
        @DisplayName("remoteAddress为null返回unknown")
        void resolveIp_remoteAddrNull_returnsUnknown() {
            ServerHttpRequest request = mockRequest(new HttpHeaders(), null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("unknown");
        }

        @Test
        @DisplayName("X-Forwarded-For含空格IP正确trim")
        void resolveIp_trimSpacesInIp() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "  10.10.10.10  , 10.10.10.11");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("10.10.10.10");
        }

        @Test
        @DisplayName("混合IPv4/IPv6链路取第一个")
        void resolveIp_mixedV4V6Chain_first() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "192.168.1.1, 2001:db8::1");
            ServerHttpRequest request = mockRequest(headers, null);

            String ip = ReflectionTestUtils.invokeMethod(rateLimitConfig, "resolveIp", request);
            assertThat(ip).isEqualTo("192.168.1.1");
        }
    }

    // ==================== RateLimitConfig - KeyResolver边界场景 ====================

    @Nested
    @DisplayName("RateLimitConfig KeyResolver边界场景测试")
    class KeyResolverEdgeCaseTest {

        @Mock
        private ServerWebExchange exchange;

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

        // --- ipKeyResolver ---

        @Test
        @DisplayName("ipKeyResolver: IPv6地址作为key")
        void ipKeyResolver_ipv6Key() {
            KeyResolver resolver = rateLimitConfig.ipKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "2001:db8::1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("2001:db8::1");
        }

        @Test
        @DisplayName("ipKeyResolver: localhost作为key")
        void ipKeyResolver_localhostKey() {
            KeyResolver resolver = rateLimitConfig.ipKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "127.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("ipKeyResolver: 无IP信息返回unknown")
        void ipKeyResolver_unknownIpKey() {
            KeyResolver resolver = rateLimitConfig.ipKeyResolver();
            ServerHttpRequest request = mockRequest(new HttpHeaders(), null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("unknown");
        }

        // --- userKeyResolver ---

        @Test
        @DisplayName("userKeyResolver: userId为空白字符串回退到IP")
        void userKeyResolver_blankUserId_fallbackToIp() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "   ");
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:10.0.0.1");
        }

        @Test
        @DisplayName("userKeyResolver: userId为0正常使用")
        void userKeyResolver_zeroUserId_used() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "0");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:0");
        }

        @Test
        @DisplayName("userKeyResolver: userId含空格正常使用(不去空格)")
        void userKeyResolver_userIdWithSpaces_usedAsIs() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", " user123 ");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user: user123 ");
        }

        @Test
        @DisplayName("userKeyResolver: userId为null回退到IP")
        void userKeyResolver_nullUserId_fallbackToIp() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "10.0.0.2");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:10.0.0.2");
        }

        @Test
        @DisplayName("userKeyResolver: userId存在但无IP头组合使用unknown")
        void userKeyResolver_userIdNoIp_usesUnknown() {
            KeyResolver resolver = rateLimitConfig.userKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "merchant1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:merchant1");
        }

        // --- ipUserKeyResolver ---

        @Test
        @DisplayName("ipUserKeyResolver: userId为空白回退纯IP")
        void ipUserKeyResolver_blankUserId_fallbackToIp() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", " ");
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:10.0.0.1");
        }

        @Test
        @DisplayName("ipUserKeyResolver: userId为0正常组合")
        void ipUserKeyResolver_zeroUserId_combined() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "0");
            headers.add("X-Forwarded-For", "10.0.0.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:0@10.0.0.1");
        }

        @Test
        @DisplayName("ipUserKeyResolver: IPv6与userId组合")
        void ipUserKeyResolver_ipv6WithUser_combined() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", "admin1");
            headers.add("X-Forwarded-For", "2001:db8::1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user:admin1@2001:db8::1");
        }

        @Test
        @DisplayName("ipUserKeyResolver: 无userId且无IP返回unknown")
        void ipUserKeyResolver_noUserNoIp_unknown() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            ServerHttpRequest request = mockRequest(new HttpHeaders(), null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:unknown");
        }

        @Test
        @DisplayName("ipUserKeyResolver: userId为null回退到IP")
        void ipUserKeyResolver_nullUserId_fallbackToIp() {
            KeyResolver resolver = rateLimitConfig.ipUserKeyResolver();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "192.168.1.1");
            ServerHttpRequest request = mockRequest(headers, null);
            when(exchange.getRequest()).thenReturn(request);

            String key = resolver.resolve(exchange).block();
            assertThat(key).isEqualTo("ip:192.168.1.1");
        }
    }
}