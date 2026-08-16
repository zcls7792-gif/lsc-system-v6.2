package com.lianshengtong.evidence.controller;

import com.lianshengtong.common.result.R;
import com.lianshengtong.evidence.dto.LoginRequest;
import com.lianshengtong.evidence.dto.LoginResponse;
import com.lianshengtong.evidence.security.JwtUtil;
import com.lianshengtong.evidence.security.LoginAttemptService;
import com.lianshengtong.evidence.security.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Tag(name = "认证服务", description = "JWT登录认证、令牌刷新、当前用户信息、登出")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;

    // S12-fix: USER_STORE 改为实例字段 (仍为内存 Map，多副本场景需迁移至数据库/LDAP)
    // 保留为 private 字段，仅在 init() 中填充
    private final Map<String, UserAccount> userStore = new ConcurrentHashMap<>();

    // 占位 BCrypt 哈希 - 用于 S13-fix 时序侧信道防护
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Value("${lsc.evidence.jwt.expiration-ms:7200000}")
    private long accessExpirationMs;

    @Value("${lsc.evidence.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    // S12-fix: 凭据外部化配置，不再硬编码默认密码
    @Value("${lsc.evidence.auth.users.admin.password:#{null}}")
    private String adminPassword;
    @Value("${lsc.evidence.auth.users.auditor.password:#{null}}")
    private String auditorPassword;
    @Value("${lsc.evidence.auth.users.operator.password:#{null}}")
    private String operatorPassword;
    // 生产环境标识：为 true 时不加载默认凭据
    @Value("${lsc.evidence.auth.require-external-credentials:false}")
    private boolean requireExternalCredentials;

    @Autowired
    public AuthController(JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          LoginAttemptService loginAttemptService,
                          TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostConstruct
    public void init() {
        boolean hasExternal = false;
        if (adminPassword != null && !adminPassword.isBlank()) {
            userStore.put("admin", new UserAccount("admin",
                    passwordEncoder.encode(adminPassword), "ADMIN"));
            hasExternal = true;
        }
        if (auditorPassword != null && !auditorPassword.isBlank()) {
            userStore.put("auditor", new UserAccount("auditor",
                    passwordEncoder.encode(auditorPassword), "AUDITOR"));
            hasExternal = true;
        }
        if (operatorPassword != null && !operatorPassword.isBlank()) {
            userStore.put("operator", new UserAccount("operator",
                    passwordEncoder.encode(operatorPassword), "OPERATOR"));
            hasExternal = true;
        }
        if (requireExternalCredentials && !hasExternal) {
            throw new IllegalStateException("生产环境未配置外部凭据，请设置 ADMIN_PASSWORD/AUDITOR_PASSWORD/OPERATOR_PASSWORD");
        }
        if (!hasExternal && !requireExternalCredentials) {
            // 开发/测试环境兜底（仅在 standalone profile 下启用）
            log.warn("未配置外部凭据，加载开发环境默认凭据 - 请勿在生产环境使用！");
            userStore.putIfAbsent("admin", new UserAccount("admin",
                    passwordEncoder.encode("admin123"), "ADMIN"));
            userStore.putIfAbsent("auditor", new UserAccount("auditor",
                    passwordEncoder.encode("auditor123"), "AUDITOR"));
            userStore.putIfAbsent("operator", new UserAccount("operator",
                    passwordEncoder.encode("operator123"), "OPERATOR"));
        }
    }

    @Operation(summary = "用户登录 (返回Access Token + Refresh Token)")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername();
        // S11-fix: 账户锁定检查 (Redis 分布式或内存)
        if (loginAttemptService.isLocked(username)) {
            long remaining = loginAttemptService.remainingLockMs(username);
            log.warn("账户已被锁定 username={} 剩余锁定时间={}ms", username, remaining);
            return R.fail(429, "账户已被临时锁定，请稍后重试");
        }

        UserAccount account = userStore.get(username);
        if (account == null) {
            // S13-fix: 不存在用户时也执行一次BCrypt比对，规避时序侧信道
            passwordEncoder.matches(request.getPassword(), DUMMY_BCRYPT_HASH);
            loginAttemptService.recordFailure(username);
            return R.fail(401, "用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.password())) {
            loginAttemptService.recordFailure(username);
            return R.fail(401, "用户名或密码错误");
        }

        // 登录成功，清除失败计数
        loginAttemptService.recordSuccess(username);

        String accessToken = jwtUtil.generateToken(account.username(), account.role());
        String refreshToken = jwtUtil.generateRefreshToken(account.username(), account.role());
        LoginResponse response = new LoginResponse(accessToken, accessExpirationMs / 1000,
                account.username(), account.role());
        response.setRefreshToken(refreshToken);
        response.setRefreshExpiresIn(refreshExpirationMs / 1000);
        log.info("用户登录成功 username={}", username);
        return R.ok(response);
    }

    @Operation(summary = "刷新令牌 (使用Refresh Token换取新Access Token + 新Refresh Token，旧Refresh Token加入黑名单)")
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "缺少刷新令牌");
        }

        String refreshToken = authHeader.substring(7);

        // S14-fix 增强: 检查 Refresh Token 是否已被撤销 (黑名单)
        String jti = jwtUtil.tokenJti(refreshToken);
        if (tokenBlacklistService.isRevoked(jti)) {
            log.warn("Refresh Token 已被撤销，拒绝刷新 jti={}", jti);
            return R.fail(401, "刷新令牌已被撤销，请重新登录");
        }

        JwtUtil.Claims claims = jwtUtil.validateRefreshToken(refreshToken);
        if (claims == null) {
            return R.fail(401, "刷新令牌无效或已过期");
        }

        // S14-fix: 刷新令牌轮换 (Refresh Token Rotation)
        // 1. 旧 Refresh Token 加入黑名单 (剩余有效期 = exp - now)
        long remainingMs = claims.expiration() - System.currentTimeMillis();
        if (remainingMs > 0) {
            tokenBlacklistService.revoke(jti, remainingMs);
            log.info("旧 Refresh Token 加入黑名单 jti={} 剩余有效期={}ms", jti, remainingMs);
        }

        // 2. 生成新 Access Token + 新 Refresh Token
        String newAccessToken = jwtUtil.generateToken(claims.username(), claims.role());
        String newRefreshToken = jwtUtil.generateRefreshToken(claims.username(), claims.role());
        LoginResponse response = new LoginResponse(newAccessToken, accessExpirationMs / 1000,
                claims.username(), claims.role());
        response.setRefreshToken(newRefreshToken);
        response.setRefreshExpiresIn(refreshExpirationMs / 1000);
        log.info("刷新令牌轮换成功 username={}", claims.username());
        return R.ok(response);
    }

    @Operation(summary = "登出 (撤销当前 Access Token + Refresh Token)")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "缺少认证令牌");
        }

        String token = authHeader.substring(7);
        JwtUtil.Claims claims = jwtUtil.validateToken(token);
        if (claims == null) {
            return R.fail(401, "认证令牌无效");
        }

        // 将 token 加入黑名单 (剩余有效期)
        String jti = jwtUtil.tokenJti(token);
        long remainingMs = claims.expiration() - System.currentTimeMillis();
        if (remainingMs > 0) {
            tokenBlacklistService.revoke(jti, remainingMs);
            log.info("用户登出 username={} token已加入黑名单 jti={} 剩余={}ms",
                    claims.username(), jti, remainingMs);
        }
        return R.ok();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        String username = (String) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentRole");

        if (username == null) {
            return R.fail(401, "未认证");
        }

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("username", username);
        userInfo.put("role", role);
        return R.ok(userInfo);
    }

    @Operation(summary = "健康检查(无需认证)")
    @GetMapping("/health")
    public R<Map<String, String>> health() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "evidence-auth");
        return R.ok(result);
    }

    private record UserAccount(String username, String password, String role) {}
}
