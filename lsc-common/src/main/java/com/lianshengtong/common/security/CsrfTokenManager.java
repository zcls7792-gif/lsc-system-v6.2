package com.lianshengtong.common.security;

import com.lianshengtong.common.utils.LogSanitizer;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class CsrfTokenManager {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenManager.class);

    private static final String TOKEN_HEADER_NAME = "X-CSRF-Token";
    private static final String TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final long TOKEN_TTL_SECONDS = 3600;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String generateToken(String sessionId, String userId) {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String key = RedisKeyPrefix.key("auth", "csrf", sessionId);
        String value = userId != null ? userId + ":" + token : token;
        stringRedisTemplate.opsForValue().set(key, value, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        log.debug("[CSRF] Token generated for session={}, user={}",
                LogSanitizer.sanitizeForLog(sessionId, 8),
                LogSanitizer.sanitizeForLog(userId, 8));
        return token;
    }

    public boolean validateToken(String sessionId, String token) {
        if (sessionId == null || token == null) {
            return false;
        }
        String key = RedisKeyPrefix.key("auth", "csrf", sessionId);
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (stored == null) {
            log.warn("[CSRF] Token not found for session={}", LogSanitizer.sanitizeForLog(sessionId, 8));
            return false;
        }
        String storedToken = stored.contains(":") ? stored.substring(stored.indexOf(':') + 1) : stored;
        boolean valid = storedToken.equals(token);
        if (!valid) {
            log.warn("[CSRF] Token mismatch for session={}", LogSanitizer.sanitizeForLog(sessionId, 8));
        }
        return valid;
    }

    public void invalidateToken(String sessionId) {
        if (sessionId != null) {
            String key = RedisKeyPrefix.key("auth", "csrf", sessionId);
            stringRedisTemplate.delete(key);
            log.debug("[CSRF] Token invalidated for session={}", LogSanitizer.sanitizeForLog(sessionId, 8));
        }
    }

    public static String getTokenHeaderName() {
        return TOKEN_HEADER_NAME;
    }

    public static String getTokenCookieName() {
        return TOKEN_COOKIE_NAME;
    }


    public CsrfTokenManager() {}

    public CsrfTokenManager(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public StringRedisTemplate getStringRedisTemplate() { return stringRedisTemplate; }
    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) { this.stringRedisTemplate = stringRedisTemplate; }
}
