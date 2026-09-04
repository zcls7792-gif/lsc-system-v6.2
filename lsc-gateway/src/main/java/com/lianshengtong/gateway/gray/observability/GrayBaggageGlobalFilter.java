package com.lianshengtong.gateway.gray.observability;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.util.context.ContextView;

import java.util.function.Consumer;

/**
 * Phase L2：把网关 {@link com.lianshengtong.gateway.gray.GrayReleaseGlobalFilter} 写入的
 * exchange attributes（grayPolicyId / grayVersion）转成：
 * <ol>
 *   <li>Micrometer Baggage → 自动注入 W3C {@code baggage} 头，被下游服务接收</li>
 *   <li>MDC（通过 ContextSnapshot 自动桥接 Reactor → MDC），网关侧日志也能看到 gray 字段</li>
 *   <li>HTTP 响应头 {@code X-Gray-*} 回传给前端/客户端（便于前端日志采集）</li>
 * </ol>
 *
 * <b>Order</b>：比 GrayReleaseGlobalFilter (-90) 晚一点，保证能读到 ATTR_GRAY_VERSION。
 * 这里用 -95（更靠近业务 filter 执行，但在路由真正转发之前，避免 baggage 创建太晚）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayBaggageGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -95;

    /** ObjectProvider：无 tracing OTel 时 Tracer bean 缺失，这里允许 null 降级只走 MDC / 响应头。 */
    private final org.springframework.beans.factory.ObjectProvider<Tracer> tracerProvider;

    @Override public int getOrder() { return ORDER; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String policy  = (String) exchange.getAttribute(com.lianshengtong.gateway.gray.GrayReleaseGlobalFilter.ATTR_POLICY_ID);
        String version = (String) exchange.getAttribute(com.lianshengtong.gateway.gray.GrayReleaseGlobalFilter.ATTR_GRAY_VERSION);
        if (policy == null && version == null) return chain.filter(exchange);

        return chain.filter(exchange)
                .contextWrite(ctx -> {
                    reactor.util.context.Context next = reactor.util.context.Context.of(ctx);
                    if (policy  != null) next = next.put("grayPolicyId", policy);
                    if (version != null) next = next.put("grayVersion",  version);
                    return next;
                })
                .doOnEach(signal -> {
                    // 仅 onNext/onSubscribe/onError 三态会有 Context：为每一线程放入 MDC，保证 reactor 日志线程正确
                    if (!signal.isOnComplete()) {
                        try {
                            ContextView cv = signal.getContextView();
                            if (cv.hasKey("grayPolicyId")) MDC.put("grayPolicyId", cv.get("grayPolicyId"));
                            else MDC.remove("grayPolicyId");
                            if (cv.hasKey("grayVersion")) MDC.put("grayVersion", cv.get("grayVersion"));
                            else MDC.remove("grayVersion");
                        } catch (Exception ignored) { /* ContextView 不可用时（极少） */ }
                    }
                })
                // Baggage：请求未被灰度时不走，避免 Tracer 频繁创建 baggage
                .then(Mono.fromRunnable(() -> applyBaggageAndResponse(exchange, policy, version)));
    }

    private void applyBaggageAndResponse(ServerWebExchange exchange, String policy, String version) {
        // 响应头（供前端采日志）
        if (policy  != null) exchange.getResponse().getHeaders().set(com.lianshengtong.common.observability.GrayBaggage.HEADER_POLICY,  policy);
        if (version != null) exchange.getResponse().getHeaders().set(com.lianshengtong.common.observability.GrayBaggage.HEADER_VERSION, version);
        // Micrometer Baggage：创建作用域（请求生命周期结束即释放）
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) return;
        Consumer<BaggageInScope> close = io.micrometer.tracing.BaggageInScope::close;
        try {
            if (policy != null) {
                BaggageInScope scope = tracer.createBaggageInScope(
                        com.lianshengtong.common.observability.GrayBaggage.W3C_BAGGAGE_POLICY, policy);
                // 注册请求完成清理
                exchange.getResponse().beforeCommit(() -> { scope.close(); return Mono.empty(); });
            }
            if (version != null) {
                BaggageInScope scope = tracer.createBaggageInScope(
                        com.lianshengtong.common.observability.GrayBaggage.W3C_BAGGAGE_VERSION, version);
                exchange.getResponse().beforeCommit(() -> { scope.close(); return Mono.empty(); });
            }
        } catch (Exception ex) {
            log.warn("[gray-baggage] failed to create tracer baggage: {}", ex.getMessage());
        }
    }
}
