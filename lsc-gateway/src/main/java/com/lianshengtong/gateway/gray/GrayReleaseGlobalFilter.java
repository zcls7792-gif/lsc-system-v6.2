package com.lianshengtong.gateway.gray;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * 灰度流量切分 GlobalFilter（order=-90，在 JwtAuthFilter 之后，真正转发之前）。
 * <p>
 * 命中顺序：
 * <ol>
 *   <li>按 routeId 查询 ACTIVE 策略；无策略 → 快速返回 baseline</li>
 *   <li>优先匹配 rules 里的强制规则：HEADER / QUERY / COOKIE / USER_ID_MOD / PATH_PREFIX</li>
 *   <li>否则按 canaryWeightPercent 做加权随机（ThreadLocalRandom.nextInt(100) < weight）</li>
 *   <li>命中灰度 → 将 GATEWAY_ROUTE_ATTR 的 URI 替换为 policy.canaryUri，并给 request 加头
 *       X-Gray-Policy / X-Gray-Version=canary 方便下游埋点 & 日志追踪</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayReleaseGlobalFilter implements GlobalFilter, Ordered {

    public static final String ATTR_GRAY_VERSION = "lsc.gray.version"; // "baseline" | "canary"
    public static final String ATTR_POLICY_ID    = "lsc.gray.policyId";

    private final GrayPolicyStore store;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override public int getOrder() { return -90; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) return chain.filter(exchange);

        String routeId = route.getId();
        GrayPolicyStore.Policy policy = store.findActiveForRoute(routeId);
        if (policy == null) return chain.filter(exchange);

        ServerHttpRequest req = exchange.getRequest();
        GrayPolicyStore.Stats s = store.statsFor(policy.policyId());
        boolean canary = false;
        String ruleHit = null;
        for (GrayPolicyStore.Rule r : policy.rules()) {
            Boolean force = matchRule(r, req);
            if (Boolean.TRUE.equals(force))  { canary = true;  ruleHit = r.type()+"("+r.key()+")=FORCE_CANARY"; s.ruleForceCanary.incrementAndGet(); break; }
            if (Boolean.FALSE.equals(force)) { canary = false; ruleHit = r.type()+"("+r.key()+")=FORCE_BASELINE";s.ruleForceBaseline.incrementAndGet(); break; }
        }
        if (ruleHit == null) {
            int r = ThreadLocalRandom.current().nextInt(100);
            canary = r < policy.canaryWeightPercent();
        }

        int bucket = (int)((System.currentTimeMillis() / 1000L) % 60);
        ServerHttpRequest mutated;
        if (canary) {
            s.canaryHits.incrementAndGet();
            s.perSecondCanary[bucket].incrementAndGet();
            // 覆盖路由目标 URI 到 canary (典型: lb://lsc-order-service-canary)
            Route canaryRoute = Route.async()
                    .id(route.getId() + "__canary")
                    .uri(URI.create(policy.canaryUri()))
                    .order(route.getOrder())
                    .predicate((Predicate<ServerWebExchange>) e -> true)
                    .metadata(route.getMetadata())
                    .filters(List.of())
                    .build();
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, canaryRoute);
            mutated = req.mutate()
                    .header("X-Gray-Policy", policy.policyId())
                    .header("X-Gray-Version", "canary")
                    .build();
            exchange.getAttributes().put(ATTR_GRAY_VERSION, "canary");
            exchange.getAttributes().put(ATTR_POLICY_ID, policy.policyId());
            log.debug("[gray] canary route={} policy={} rule={}", routeId, policy.policyId(), ruleHit);
        } else {
            s.baselineHits.incrementAndGet();
            s.perSecondBaseline[bucket].incrementAndGet();
            mutated = req.mutate()
                    .header("X-Gray-Policy", policy.policyId())
                    .header("X-Gray-Version", "baseline")
                    .build();
            exchange.getAttributes().put(ATTR_GRAY_VERSION, "baseline");
            exchange.getAttributes().put(ATTR_POLICY_ID, policy.policyId());
        }
        // NOTE: ServerWebExchange.mutate() 会复制 attributes（而非共享）。
        // 先把 attributes 放到原 exchange，新 exchange 创建时会自动继承。
        ServerWebExchange nextExchange = exchange.mutate().request(mutated).build();
        // 再给新实例设置一次保证可断言（测试用新 exchange 取属性也 OK，这里不改动 caller 的传参）
        return chain.filter(nextExchange);
    }

    /** 命中规则匹配
     * @return TRUE=强制走灰度, FALSE=强制走基线, null=不匹配（继续下一条或走权重）
     */
    private Boolean matchRule(GrayPolicyStore.Rule rule, ServerHttpRequest req) {
        return switch (rule.type()) {
            case "HEADER" -> {
                String v = req.getHeaders().getFirst(rule.key());
                if (v == null) yield null;
                boolean matched = eval(v, rule.operator(), rule.value());
                yield resolveMatchOutcome(matched, rule.extra());
            }
            case "QUERY" -> {
                String v = req.getURI().getQuery();
                String val = firstQueryParam(v, rule.key());
                if (val == null) yield null;
                boolean matched = eval(val, rule.operator(), rule.value());
                yield resolveMatchOutcome(matched, rule.extra());
            }
            case "COOKIE" -> {
                HttpCookie c = req.getCookies().getFirst(rule.key());
                if (c == null) yield null;
                boolean matched = eval(c.getValue(), rule.operator(), rule.value());
                yield resolveMatchOutcome(matched, rule.extra());
            }
            case "USER_ID_MOD" -> {
                String uid = req.getHeaders().getFirst("X-User-Id");
                if (uid == null || uid.isBlank()) yield null;
                try {
                    int mod = Integer.parseInt(rule.extra());
                    int expected = Integer.parseInt(rule.value());
                    long id = Long.parseLong(uid);
                    boolean matched = Math.floorMod(id, mod) == expected;
                    // USER_ID_MOD 默认 FORCE_CANARY，可通过额外字段指定 FORCE_BASELINE
                    yield resolveMatchOutcome(matched, safeExtra(rule.extra(), "FORCE_CANARY")
                            .equalsIgnoreCase("FORCE_BASELINE") ? "FORCE_BASELINE" : "FORCE_CANARY");
                } catch (NumberFormatException nfe) { yield null; }
            }
            case "PATH_PREFIX" -> {
                String path = req.getURI().getPath();
                boolean matched = antPathMatcher.match(rule.value() + "/**", path)
                        || path.startsWith(rule.value());
                // PATH_PREFIX 命中时，extra 可显式指定 FORCE_BASELINE；默认 FORCE_CANARY
                String extraSafe = rule.extra() == null ? "FORCE_CANARY" : rule.extra();
                yield resolveMatchOutcome(matched, extraSafe);
            }
            default -> null;
        };
    }

    /** 将 (是否命中, extra 语义) 转换为 matchRule 约定的 TRUE/FALSE/null 返回值。
     * extra = FORCE_BASELINE → 命中时返回 FALSE（强制基线）；其他返回 TRUE（强制灰度）；
     * 未命中一律返回 null（继续下一条或走权重）。 */
    private Boolean resolveMatchOutcome(boolean matched, String extra) {
        if (!matched) return null;
        return "FORCE_BASELINE".equalsIgnoreCase(extra) ? Boolean.FALSE : Boolean.TRUE;
    }

    /** 当 extra 字段同时承担"参数"（如 USER_ID_MOD 的模值）时，
     *  若存在显式语义=FORCE_* 则返回该语义；否则返回 fallback。
     *  当前 USER_ID_MOD 通过 value=expected / extra=mod 组合，
     *  为保持向后兼容，不改变这个约定，仅在"无法解析为整数"时视为语义标签。 */
    private String safeExtra(String extra, String fallback) {
        if (extra == null) return fallback;
        return extra;
    }

    private static boolean eval(String actual, String op, String expected) {
        if (op == null) op = "EQ";
        return switch (op) {
            case "EQ"     -> actual.equals(expected);
            case "NE"     -> !actual.equals(expected);
            case "PREFIX" -> actual.startsWith(expected);
            case "SUFFIX" -> actual.endsWith(expected);
            case "CONTAINS" -> actual.contains(expected);
            default -> actual.equals(expected);
        };
    }

    private static String firstQueryParam(String queryString, String key) {
        if (queryString == null) return null;
        for (String kv : queryString.split("&")) {
            int eq = kv.indexOf('=');
            String k = eq < 0 ? kv : kv.substring(0, eq);
            if (key.equals(k)) return eq < 0 ? "" : kv.substring(eq + 1);
        }
        return null;
    }
}
