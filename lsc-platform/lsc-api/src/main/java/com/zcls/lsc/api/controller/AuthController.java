package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.auth.JwtUtil;
import com.zcls.lsc.api.auth.WeChatLoginService;
import com.zcls.lsc.api.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 第14.1章 鉴权接口（C 端登录/换 token）。
 *
 * 登录方式：
 *  - 短信验证码登录：POST /v1/auth/login/sms（开发期验证码 1234）
 *  - 微信小程序登录：POST /v1/auth/login/wechat（code2session 换 openid）
 *  - 微信 H5 授权登录：POST /v1/auth/login/wechat/h5（OAuth2 code 换 openid）
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final JdbcTemplate jdbc;
    private final WeChatLoginService weChatLoginService;

    public AuthController(JdbcTemplate jdbc, WeChatLoginService weChatLoginService) {
        this.jdbc = jdbc;
        this.weChatLoginService = weChatLoginService;
    }

    /**
     * POST /v1/auth/login/sms — 短信验证码登录。
     * 开发期验证码固定为 1234。
     */
    @PostMapping("/login/sms")
    public ApiResponse<Map<String, Object>> loginBySms(
            @RequestParam String phone,
            @RequestParam String code) {
        // 开发期验证码校验
        if (!"1234".equals(code)) {
            return ApiResponse.fail(40101, "invalid sms code");
        }

        // 用 mobile_lookup_hash 查询（V1 表设计：手机号哈希索引）
        String hash = sha256(phone);
        Long userId;
        try {
            userId = jdbc.queryForObject(
                    "SELECT user_id FROM user WHERE mobile_lookup_hash = ? LIMIT 1",
                    Long.class, hash);
        } catch (Exception e) {
            // 用户不存在则自动注册（开发期，mobile_enc 占位）
            userId = Math.abs(System.currentTimeMillis());
            jdbc.update("INSERT INTO user(user_id, mobile_enc, mobile_lookup_hash, user_type, "
                    + "account_status, privacy_version) VALUES(?, ?, ?, 'C', 'NORMAL', 'v1')",
                    userId, phone.getBytes(java.nio.charset.StandardCharsets.UTF_8), hash);
        }

        String token = JwtUtil.issue(userId);
        return ApiResponse.ok(Map.of(
                "token", token,
                "userId", userId,
                "expiresIn", 7L * 24 * 3600
        ));
    }

    /**
     * POST /v1/auth/login/wechat — 微信小程序登录（code2session 换 openid）。
     * 前端 wx.login() 拿到 code 后调用此接口。
     * 未配置 appid/appsecret 时走开发期模式。
     */
    @PostMapping("/login/wechat")
    public ApiResponse<Map<String, Object>> loginByWechat(@RequestParam String code) {
        return ApiResponse.ok(weChatLoginService.loginByMiniAppCode(code));
    }

    /**
     * POST /v1/auth/login/wechat/h5 — 微信 H5 网页授权登录（OAuth2 code 换 openid）。
     * 前端在微信内打开授权页，回调带 code 后调用此接口。
     */
    @PostMapping("/login/wechat/h5")
    public ApiResponse<Map<String, Object>> loginByWechatH5(@RequestParam String code) {
        return ApiResponse.ok(weChatLoginService.loginByH5Code(code));
    }

    /**
     * GET /v1/auth/wechat/h5/auth-url — 获取微信 H5 网页授权跳转 URL。
     * redirectUri 可省略：省略时使用 wx.mp.callback-domain 配置 + 默认回调路径 /wechat/callback。
     */
    @GetMapping("/wechat/h5/auth-url")
    public ApiResponse<Map<String, String>> wechatH5AuthUrl(
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false, defaultValue = "snsapi_userinfo") String scope,
            @RequestParam(required = false) String state) {
        if (redirectUri == null || redirectUri.isBlank()) {
            // 用配置的回调域名兜底
            if (callbackDomain != null && !callbackDomain.isBlank()) {
                redirectUri = callbackDomain.replaceAll("/+$", "") + "/wechat/callback";
            } else {
                redirectUri = "/wechat/callback";
            }
        }
        String url = "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + weChatAppId()
                + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + scope
                + (state != null ? "&state=" + state : "")
                + "#wechat_redirect";
        return ApiResponse.ok(Map.of("url", url));
    }

    @Value("${wx.mp.app-id:}")
    private String wxAppId;

    @Value("${wx.mp.callback-domain:}")
    private String callbackDomain;

    @Value("${wx.mp.server-domain:}")
    private String serverDomain;

    private String weChatAppId() {
        return wxAppId != null ? wxAppId : "";
    }

    /** GET /v1/auth/wechat/config — 返回微信配置的非敏感信息（供前端/运维核对）。 */
    @GetMapping("/wechat/config")
    public ApiResponse<Map<String, Object>> wechatConfig() {
        return ApiResponse.ok(Map.of(
                "appIdConfigured", wxAppId != null && !wxAppId.isBlank(),
                "callbackDomain", callbackDomain != null ? callbackDomain : "",
                "serverDomain", serverDomain != null ? serverDomain : ""
        ));
    }

    /** GET /v1/auth/me — 查询当前登录用户（需 token）。 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestAttribute("userId") long userId) {
        try {
            Map<String, Object> user = jdbc.queryForMap(
                    "SELECT user_id, user_type, account_status, created_at FROM user WHERE user_id = ?",
                    userId);
            return ApiResponse.ok(user);
        } catch (Exception e) {
            return ApiResponse.ok(Map.of("userId", userId));
        }
    }

    /** SHA-256 哈希（用于 mobile_lookup_hash）。 */
    private static String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
