package com.lianshengtong.user.controller;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.user.dto.LoginDTO;
import com.lianshengtong.user.dto.RegisterDTO;
import com.lianshengtong.user.dto.VerifyDTO;
import com.lianshengtong.user.entity.User;
import com.lianshengtong.user.service.UserService;
import com.lianshengtong.user.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * 用户服务 Controller
 *
 * @author lsc
 */
@Slf4j
@Tag(name = "用户服务")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final String BLACKLIST_KEY_PREFIX = RedisKeyPrefix.TOKEN_BLACKLIST;

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Operation(summary = "用户注册（手机号查重/BCrypt加密/雪花ID/推荐仅一级）")
    @PostMapping("/register")
    public R<User> register(@Valid @RequestBody RegisterDTO dto) {
        return R.ok(userService.register(dto));
    }

    @Operation(summary = "用户登录（JWT）")
    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(userService.login(dto));
    }

    @Operation(summary = "退出登录（加入 Redis token 黑名单）")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                String rawToken = token.substring(7);
                Claims claims = jwtUtil.parseToken(rawToken);
                long ttlMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
                if (ttlMillis > 0) {
                    // 将 token 加入黑名单，TTL 为其剩余有效期，到期自动清理
                    stringRedisTemplate.opsForValue()
                            .set(BLACKLIST_KEY_PREFIX + rawToken, "1", Duration.ofMillis(ttlMillis));
                    log.info("[logout] 用户 {} 退出登录，token 已加入黑名单", claims.getSubject());
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            // token 无效也返回成功，避免泄露 token 状态
            log.debug("[logout] token 解析失败，可能已过期或无效");
        }
        return R.ok();
    }

    @Operation(summary = "修改登录密码")
    @PostMapping("/change-password")
    public R<Void> changePassword(@RequestHeader("Authorization") String token,
                                   @RequestBody Map<String, String> body) {
        Long userId = parseUserId(token);
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            throw new BizException(400, "旧密码/新密码不能为空");
        }
        userService.changePassword(userId, oldPassword, newPassword);
        return R.ok();
    }

    @Operation(summary = "实名认证（身份证AES加密）")
    @PostMapping("/verify")
    public R<Void> verify(@Valid @RequestBody VerifyDTO dto) {
        userService.verify(dto);
        return R.ok();
    }

    @Operation(summary = "用户信息查询")
    @GetMapping("/info")
    public R<User> info(@RequestParam Long userId) {
        return R.ok(userService.getUserInfo(userId));
    }

    /** 从 Authorization Header 解析 userId */
    private Long parseUserId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!jwtUtil.validateToken(token)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Token无效或已过期");
        }
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token))) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Token已注销，请重新登录");
        }
        try {
            return Long.parseLong(jwtUtil.getSubject(token));
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Token无效");
        }
    }
}
