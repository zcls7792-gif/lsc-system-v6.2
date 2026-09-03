package com.lianshengtong.gateway.observability;

import com.lianshengtong.gateway.gray.GrayReleaseGlobalFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关可观测性全局过滤器（order=-80，灰度之后）。
 * <p>做三件事：
 * <ol>
 *   <li>给每个请求分配并透传 traceId（X-Trace-Id，优先沿用上游、否则生成 Snowflake / UUID）</li>
 *   <li>把 traceId 放进响应头 + 把 {traceId, routeId, grayVersion} 放进日志 MDC（MDC 在 Reactor 线程下使用 contextWrite 下游配合；这里在 log.info 直接打印结构化 KV 以便 ELK/EFK 检索）</li>
 *   <li>Micrometer 埋点（Counter / Timer）：
 *       <ul>
 *         <li>lsc_gateway_requests_total{routeId, grayVersion, statusFamily} Counter</li>
 *         <li>lsc_gateway_requests_duration_seconds{routeId, grayVersion} Timer</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObservabilityGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String UNKNOWN_ROUTE = "unknown";

    private final MeterRegistry registry;
    private final TraceIdGenerator traceIdGenerator;

    @Override public int getOrder() { return -80; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final long startNs = System.nanoTime();
        final String traceId = resolveOrCreateTraceId(exchange);
        final Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        final String routeId = route == null ? UNKNOWN_ROUTE : route.getId();
        final String gray = exchange.getAttributeOrDefault(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION, "none");

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header(HEADER_TRACE_ID, traceId))
                .build();
        mutated.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);

        // 结构化日志 (JSON-serialize friendly KV)
        log.info("traceId={} routeId={} gray={} method={} path={} remote={}",
                traceId, routeId, gray,
                mutated.getRequest().getMethod(),
                mutated.getRequest().getURI().getPath(),
                clientIpOf(mutated));

        return chain.filter(mutated).doFinally(sig -> {
            long durationNs = System.nanoTime() - startNs;
            ServerHttpResponse resp = mutated.getResponse();
            Integer codeRaw = resp.getStatusCode() == null ? null : resp.getStatusCode().value();
            int code = codeRaw == null ? 0 : codeRaw;
            String family = familyOf(code);
            String routeTag = route == null ? UNKNOWN_ROUTE : route.getId();
            String grayTag = mutated.getAttributeOrDefault(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION, "none");

            Counter.builder("lsc.gateway.requests.total")
                    .description("Gateway total request count")
                    .tag("routeId", routeTag)
                    .tag("grayVersion", grayTag)
                    .tag("status", family)
                    .register(registry)
                    .increment();
            Timer.builder("lsc.gateway.requests.duration")
                    .description("Gateway request duration")
                    .tag("routeId", routeTag)
                    .tag("grayVersion", grayTag)
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(durationNs, java.util.concurrent.TimeUnit.NANOSECONDS);
            if (code >= 400) {
                log.warn("traceId={} routeId={} gray={} status={} family={} elapsedMs={}",
                        traceId, routeTag, grayTag, code, family,
                        String.format("%.2f", durationNs / 1_000_000d));
            }
        });
    }

    private String resolveOrCreateTraceId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        if (existing != null && !existing.isBlank()) return existing;
        // 兼容 OpenTelemetry 约定头 (traceparent / uber-trace-id)
        String otp = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (otp != null && otp.length() >= 32) {
            String[] parts = otp.split("-");
            if (parts.length >= 2 && parts[1].length() == 32) return parts[1];
        }
        String uber = exchange.getRequest().getHeaders().getFirst("uber-trace-id");
        if (uber != null && !uber.isBlank()) {
            String p = uber.split(":")[0];
            if (!p.isBlank()) return p;
        }
        return traceIdGenerator.next();
    }

    private static String familyOf(int code) {
        if (code <= 0) return "UNKNOWN";
        if (code < 400) return (code < 300) ? "2xx" : "3xx";
        if (code < 500) return "4xx";
        return "5xx";
    }

    private static String clientIpOf(ServerWebExchange exc) {
        String xff = exc.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = exc.getRequest().getHeaders().getFirst("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        if (exc.getRequest().getRemoteAddress() != null
                && exc.getRequest().getRemoteAddress().getAddress() != null) {
            return exc.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
