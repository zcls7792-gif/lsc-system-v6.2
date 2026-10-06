package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.auth.JwtUtil;
import com.zcls.lsc.api.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 第14.1章 鉴权接口（C 端登录/换 token）。
 *
 * 开发期简化：用 phone + 短信验证码登录（验证码固定为 1234，生产环境对接短信网关）。
 * 生产环境应替换为：
 *  - 短信验证码（接腾讯云/阿里云短信）
 *  - 微信小程序 code2session 换 openid
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final JdbcTemplate jdbc;

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
     * POST /v1/auth/login/wechat — 微信小程序登录（code 换 token）。
     * 开发期占位：直接返回 token，不调 code2session。
     */
    @PostMapping("/login/wechat")
    public ApiResponse<Map<String, Object>> loginByWechat(@RequestParam String code) {
        // 开发期：用 code 哈希作为临时 userId
        long userId = Math.abs(code.hashCode()) & 0xFFFFFFFFL;
        if (userId == 0) userId = 1L;
        String token = JwtUtil.issue(userId);
        return ApiResponse.ok(Map.of(
                "token", token,
                "userId", userId,
                "expiresIn", 7L * 24 * 3600
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
