package com.lianshengtong.common.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Phase L2：业务服务端（Servlet / Spring MVC）灰度 baggage + traceId 统一入口 Filter。
 * <p>
 * 注入：
 * <pre>
 *   @Bean public GrayObservabilityFilter grayObservabilityFilter() { return new GrayObservabilityFilter(); }
 * </pre>
 *
 * <h3>做了哪些事情？</h3>
 * <ol>
 *   <li>读 {@code X-Trace-Id} / {@code traceparent}（W3C） → 放入 {@link TraceIdHolder}</li>
 *   <li>读 {@code X-Gray-Policy} / {@code X-Gray-Version} → 放入 {@link GrayBaggage}</li>
 *   <li>放入 Micrometer Observation Context（若存在 {@link ServerRequestObservationContext}，可被 Otel/Zipkin exporter 携带）</li>
 *   <li>写响应头：{@code X-Trace-Id} + {@code X-Gray-Version}（便于前端在 Network 面板快速区分）</li>
 *   <li>finally 清理 MDC（线程复用时防串）</li>
 * </ol>
 */
@Slf4j
public class GrayObservabilityFilter extends OncePerRequestFilter implements Ordered {

    private int order = -100;   // 越靠前越好，避免 Spring MVC 业务处理完再注入为时已晚

    public void setOrder(int order) { this.order = order; }
    @Override public int getOrder() { return order; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = firstOf(request.getHeader(TraceIdHolder.HEADER), extractFromTraceparent(request.getHeader("traceparent")));
        if (traceId == null || traceId.isBlank()) traceId = TraceIdHolder.create();
        TraceIdHolder.set(traceId);

        String policy  = request.getHeader(GrayBaggage.HEADER_POLICY);
        String version = request.getHeader(GrayBaggage.HEADER_VERSION);
        GrayBaggage.captureFromHeaders(policy, version);

        // 响应头
        response.setHeader(TraceIdHolder.HEADER, traceId);
        if (version != null && !version.isBlank()) response.setHeader(GrayBaggage.HEADER_VERSION, version);
        if (policy  != null && !policy.isBlank())  response.setHeader(GrayBaggage.HEADER_POLICY, policy);

        try {
            filterChain.doFilter(request, response);
        } finally {
            GrayBaggage.clear();
            TraceIdHolder.clear();
        }
    }

    private static String firstOf(String... candidates) {
        for (String c : candidates) if (c != null && !c.isBlank()) return c;
        return null;
    }

    /**
     * W3C traceparent：{@code version-traceid-parentid-traceflags}
     * traceid 是 32hex 字符；直接取作为 traceId 传递给 Zipkin/Grafana Tempo 完全一致。
     */
    static String extractFromTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) return null;
        String[] parts = traceparent.split("-");
        if (parts.length < 3) return null;
        return parts[1];
    }
}
