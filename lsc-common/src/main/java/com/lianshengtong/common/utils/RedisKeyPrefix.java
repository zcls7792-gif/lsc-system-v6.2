package com.lianshengtong.common.utils;

/**
 * Redis Key 前缀规范工具类
 * <p>
 * 统一管理 Redis Key 前缀，防止 key 冲突，便于批量管理和清理。
 * 命名规范: lsc:{module}:{type}:{identifier}
 * </p>
 */
public final class RedisKeyPrefix {

    private RedisKeyPrefix() {
    }

    public static final String APP_PREFIX = "lsc";

    public static final String TOKEN_BLACKLIST = APP_PREFIX + ":auth:token:blacklist:";
    public static final String USER_TOKEN = APP_PREFIX + ":auth:user:token:";
    public static final String ADMIN_TOKEN = APP_PREFIX + ":auth:admin:token:";
    public static final String MERCHANT_TOKEN_BLACKLIST = APP_PREFIX + ":merchant:token:blacklist:";

    public static final String DISTRIBUTED_LOCK = APP_PREFIX + ":lock:";
    public static final String LOCK_LEADER = APP_PREFIX + ":lock:leader:";

    public static final String IDEMPOTENT = APP_PREFIX + ":idempotent:";

    public static final String RATE_LIMIT_IP = APP_PREFIX + ":ratelimit:ip:";
    public static final String RATE_LIMIT_USER = APP_PREFIX + ":ratelimit:user:";
    public static final String RATE_LIMIT_GLOBAL = APP_PREFIX + ":ratelimit:global:";

    public static final String LEDGER_BALANCE = APP_PREFIX + ":ledger:balance:";
    public static final String LEDGER_FREEZE = APP_PREFIX + ":ledger:freeze:";
    public static final String LEDGER_LOCK = APP_PREFIX + ":ledger:lock:";

    public static final String ORDER_CACHE = APP_PREFIX + ":order:cache:";
    public static final String ORDER_STATUS = APP_PREFIX + ":order:status:";

    public static final String B2B_ORDER = APP_PREFIX + ":b2b:order:";
    public static final String B2B_VERIFY = APP_PREFIX + ":b2b:verify:";

    public static final String MERCHANT_STATS = APP_PREFIX + ":merchant:stats:";
    public static final String MERCHANT_DAILY = APP_PREFIX + ":merchant:daily:";
    public static final String MERCHANT_VIOLATION = APP_PREFIX + ":merchant:violation:";
    public static final String MERCHANT_ADDR_COUNT = APP_PREFIX + ":merchant:addr:count:";

    public static final String WRITE_OFF_APPLY = APP_PREFIX + ":writeoff:apply:";
    public static final String WRITE_OFF_QUOTA = APP_PREFIX + ":writeoff:quota:";

    public static final String RELEASE_DAILY = APP_PREFIX + ":release:daily:";
    public static final String RELEASE_LOCK = APP_PREFIX + ":release:lock:";

    public static final String MEDIA_URL = APP_PREFIX + ":media:url:";
    public static final String MEDIA_META = APP_PREFIX + ":media:meta:";
    public static final String MEDIA_VIDEO_STATUS = APP_PREFIX + ":media:video-status:";
    public static final String MEDIA_VIDEO_META = APP_PREFIX + ":media:video-meta:";

    public static final String MAP_GEO = APP_PREFIX + ":map:geo:";
    public static final String MAP_REVERSE_GEO = APP_PREFIX + ":map:regeo:";
    public static final String MAP_DISTANCE = APP_PREFIX + ":map:distance:";

    public static final String RISK_LOG = APP_PREFIX + ":risk:log:";
    public static final String RISK_COUNTER = APP_PREFIX + ":risk:counter:";
    public static final String RISK_BATCH = APP_PREFIX + ":risk:batch:";
    public static final String RISK_HYBRID_STREAK = APP_PREFIX + ":risk:hybrid-streak:";
    public static final String RISK_ARB = APP_PREFIX + ":risk:arb:";
    public static final String RISK_GEO = APP_PREFIX + ":risk:geo:";

    public static final String PROMOTION_CACHE = APP_PREFIX + ":promotion:cache:";
    public static final String PROMOTION_DAILY = APP_PREFIX + ":promotion:daily:";
    public static final String PROMOTION_FIRST_ORDER = APP_PREFIX + ":promotion:first-order:";

    public static final String RECONCILIATION_LOCK = APP_PREFIX + ":reconcile:lock:";
    public static final String ADMIN_PARAM_APPROVE = APP_PREFIX + ":admin:param-approve:";
    public static final String EVIDENCE_PENDING_COUNT = APP_PREFIX + ":evidence:pending-count";

    public static final String SYSTEM_CONFIG = APP_PREFIX + ":system:config:";
    public static final String SYSTEM_DICT = APP_PREFIX + ":system:dict:";

    public static String key(String module, String type, String... parts) {
        StringBuilder sb = new StringBuilder(APP_PREFIX).append(':').append(module).append(':').append(type);
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                sb.append(':').append(part);
            }
        }
        return sb.toString();
    }

    public static String key(String prefix, String identifier) {
        return prefix + identifier;
    }

    public static String[] scanPatterns(String prefix) {
        return new String[]{prefix + "*"};
    }

    public static String cleanIdentifier(String id) {
        if (id == null) {
            return "";
        }
        return id.replaceAll("[:\\s]+", "_");
    }
}
