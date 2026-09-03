package com.lianshengtong.common.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 统一全链路 TraceId 工具（业务服务端侧使用，不依赖 WebFlux，任何模块都可以用）。
 * <p>
 * 使用约定：
 * <ul>
 *   <li>请求入口（Filter / Interceptor / AOP）：从请求头 X-Trace-Id 读，没有就生成，放入 MDC + 重新写回响应头</li>
 *   <li>下游调用（RestTemplate / Feign / Async）：从 MDC.get(TraceIdHolder.KEY) 取出，加在下游请求头</li>
 *   <li>日志 pattern：推荐 <code>%mdc{traceId}</code>（logback/log4j2 都支持）</li>
 * </ul>
 */
public final class TraceIdHolder {

    public static final String KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceIdHolder() {}

    /** 若 MDC 里已有则返回，否则新建并放入 MDC */
    public static String currentOrCreate() {
        String t = MDC.get(KEY);
        if (t != null && !t.isBlank()) return t;
        t = create();
        MDC.put(KEY, t);
        return t;
    }

    /** 读或空（不自动写入 MDC） */
    public static String get() { return MDC.get(KEY); }

    public static void set(String traceId) {
        if (traceId == null || traceId.isBlank()) MDC.remove(KEY);
        else MDC.put(KEY, traceId);
    }

    public static void clear() { MDC.remove(KEY); }

    /** 生成 32 字符十六进制 traceId（和网关实现格式等价：网关是 snowflake-like，服务端是 UUID.hex；两者长度相同，便于检索） */
    public static String create() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
