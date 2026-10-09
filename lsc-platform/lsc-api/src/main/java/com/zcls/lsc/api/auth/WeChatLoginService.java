package com.zcls.lsc.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

/**
 * 微信登录服务。
 *
 * 支持两种场景：
 *  1) 小程序 code2session：前端 wx.login() 拿到 code，调用 /v1/auth/login/wechat
 *  2) H5 OAuth2 回调：微信网页授权后带 code 回调，调用 /v1/auth/login/wechat/h5
 *
 * 若未配置 WX_MP_APP_ID/WX_MP_APP_SECRET，则进入开发期模式：用 code 哈希作为
 * openid，不调用微信 API，保证本地开发不阻断。
 */
@Service
public class WeChatLoginService {

    private static final Logger log = LoggerFactory.getLogger(WeChatLoginService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${wx.mp.app-id:}")
    private String appId;

    @Value("${wx.mp.app-secret:}")
    private String appSecret;

    public WeChatLoginService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    /**
     * 构建 HttpClient，自动读取 HTTP(S)_PROXY 环境变量。
     * 生产环境若需直连可不设代理变量；沙箱/容器环境通过代理出口访问微信 API。
     */
    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy == null || proxy.isBlank()) proxy = System.getenv("https_proxy");
        if (proxy == null || proxy.isBlank()) proxy = System.getenv("HTTP_PROXY");
        if (proxy == null || proxy.isBlank()) proxy = System.getenv("http_proxy");
        if (proxy != null && !proxy.isBlank()) {
            try {
                java.net.URL proxyUrl = new java.net.URL(proxy);
                builder.proxy(java.net.ProxySelector.of(
                        new java.net.InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort())));
                log.info("WeChat HTTP client using proxy: {}:{}", proxyUrl.getHost(), proxyUrl.getPort());
            } catch (Exception e) {
                log.warn("Invalid proxy URL {}, ignoring: {}", proxy, e.getMessage());
            }
        }
        return builder.build();
    }

    /**
     * 启动时校验微信配置是否生效。
     * 若 appid/secret 为空或疑似未解析的占位符（含 "${"），打印 WARN 提示并保持开发期模式。
     */
    @PostConstruct
    public void validateConfig() {
        boolean appIdValid = appId != null && !appId.isBlank() && !appId.contains("${");
        boolean secretValid = appSecret != null && !appSecret.isBlank() && !appSecret.contains("${");
        if (appIdValid && secretValid) {
            log.info("WeChat config OK: appId={}*** (len={}), secret=*** (len={})",
                    appId.substring(0, Math.min(4, appId.length())), appId.length(), appSecret.length());
        } else {
            log.warn("WeChat config NOT set — running in DEV mode. "
                    + "Set WX_MP_APP_ID and WX_MP_APP_SECRET env vars for production.");
        }
    }

    /**
     * 小程序 code2session 登录。
     * 返回 { userId, token, openid, expiresIn }
     */
    public Map<String, Object> loginByMiniAppCode(String code) {
        String openid;
        if (isConfigured()) {
            // 调用微信 code2session
            String url = "https://api.weixin.qq.com/sns/jscode2session"
                    + "?appid=" + appId
                    + "&secret=" + appSecret
                    + "&js_code=" + code
                    + "&grant_type=authorization_code";
            JsonNode resp = httpGet(url);
            if (resp == null || resp.has("errcode")) {
                int errCode = resp == null ? -1 : resp.path("errcode").asInt();
                String errMsg = resp == null ? "null response" : resp.path("errmsg").asText("unknown");
                log.error("WeChat code2session failed: errcode={}, errmsg={}, raw={}", errCode, errMsg, resp);
                throw new RuntimeException("wechat code2session failed: errcode=" + errCode + ", " + errMsg);
            }
            openid = resp.path("openid").asText();
        } else {
            // 开发期：用 code 哈希作为 openid
            openid = "dev_" + sha256(code).substring(0, 24);
            log.info("[dev-mode] wechat code2session openid={}", openid);
        }
        return issueTokenByOpenid(openid, null);
    }

    /**
     * H5 网页授权 OAuth2 登录。
     * 先 sns/oauth2/access_token 换 access_token+openid，再 sns/userinfo 取昵称头像。
     */
    public Map<String, Object> loginByH5Code(String code) {
        String openid;
        String unionid = null;
        String nickname = null;
        String avatar = null;

        if (isConfigured()) {
            String tokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token"
                    + "?appid=" + appId
                    + "&secret=" + appSecret
                    + "&code=" + code
                    + "&grant_type=authorization_code";
            JsonNode tokenResp = httpGet(tokenUrl);
            if (tokenResp == null || tokenResp.has("errcode")) {
                int errCode = tokenResp == null ? -1 : tokenResp.path("errcode").asInt();
                String errMsg = tokenResp == null ? "null response" : tokenResp.path("errmsg").asText("unknown");
                log.error("WeChat oauth2 access_token failed: errcode={}, errmsg={}, raw={}", errCode, errMsg, tokenResp);
                throw new RuntimeException("wechat oauth2 failed: errcode=" + errCode + ", " + errMsg);
            }
            String accessToken = tokenResp.path("access_token").asText();
            openid = tokenResp.path("openid").asText();
            unionid = tokenResp.has("unionid") ? tokenResp.path("unionid").asText() : null;

            // 拉取用户信息
            String userUrl = "https://api.weixin.qq.com/sns/userinfo"
                    + "?access_token=" + accessToken
                    + "&openid=" + openid
                    + "&lang=zh_CN";
            JsonNode userResp = httpGet(userUrl);
            if (userResp != null && !userResp.has("errcode")) {
                nickname = userResp.path("nickname").asText(null);
                avatar = userResp.path("headimgurl").asText(null);
            }
        } else {
            openid = "dev_" + sha256(code).substring(0, 24);
            log.info("[dev-mode] wechat h5 oauth2 openid={}", openid);
        }
        return issueTokenByOpenid(openid, unionid, nickname, avatar);
    }

    // ===== 内部方法 =====

    private boolean isConfigured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    private Map<String, Object> issueTokenByOpenid(String openid, String unionid) {
        return issueTokenByOpenid(openid, unionid, null, null);
    }

    private Map<String, Object> issueTokenByOpenid(String openid, String unionid,
                                                    String nickname, String avatar) {
        // 按 openid 查询用户
        Long userId;
        try {
            userId = jdbc.queryForObject(
                    "SELECT user_id FROM user WHERE openid = ? LIMIT 1",
                    Long.class, openid);
        } catch (Exception e) {
            // 未找到，自动注册
            userId = Math.abs(System.currentTimeMillis());
            jdbc.update(
                    "INSERT INTO user(user_id, mobile_enc, mobile_lookup_hash, nickname, "
                            + "openid, unionid, user_type, account_status, privacy_version) "
                            + "VALUES(?, ?, ?, ?, ?, ?, 'C', 'NORMAL', 'v1')",
                    userId,
                    new byte[0],
                    sha256("wx_" + openid),
                    nickname,
                    openid,
                    unionid);
            log.info("wechat auto-register user userId={} openid={}", userId, openid);
        }

        // 若 unionid/nickname 有值但库里没存，补一下
        if (unionid != null || nickname != null) {
            try {
                jdbc.update(
                        "UPDATE user SET unionid = COALESCE(?, unionid), "
                                + "nickname = COALESCE(?, nickname) WHERE user_id = ?",
                        unionid, nickname, userId);
            } catch (Exception ignore) {
            }
        }

        String token = JwtUtil.issue(userId);
        return Map.of(
                "token", token,
                "userId", userId,
                "openid", openid,
                "expiresIn", 7L * 24 * 3600
        );
    }

    private JsonNode httpGet(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            log.error("http get failed url={}", url, e);
            return null;
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
