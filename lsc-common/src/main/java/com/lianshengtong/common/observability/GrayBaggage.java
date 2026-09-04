package com.lianshengtong.common.observability;

import org.slf4j.MDC;

import java.util.List;

/**
 * Phase L2：灰度上下文 Baggage 工具。
 * <p>
 * 约定：
 * <ul>
 *   <li>网关注入请求头：{@code X-Gray-Policy} / {@code X-Gray-Version}（见 GrayReleaseGlobalFilter）；
 *       网关同时用 Micrometer Baggage/W3C 把它们加到 baggage：{@code gray.policy_id} / {@code gray.version}。</li>
 *   <li>下游服务（order/user/merchant 等）入口 servlet filter / webflux filter：从 header 读 → 放入 MDC 同名键，
 *       并再次作为响应头写回，便于链路追踪系统关联。</li>
 *   <li>Feign/RestTemplate 下游调用：从 MDC 再读出来，加请求头，保证 9 层下游依然持有灰度归属。</li>
 *   <li>日志 Logback pattern 使用 {@code %mdc{grayPolicyId}} / {@code %mdc{grayVersion}}。</li>
 * </ul>
 *
 * @see TraceIdHolder 成对设计
 */
public final class GrayBaggage {

    public static final String HEADER_POLICY  = "X-Gray-Policy";
    public static final String HEADER_VERSION = "X-Gray-Version";
    public static final String HEADER_CANARY_RATIO = "X-Gray-Weight";   // 可选：向业务透传权重

    public static final String MDC_POLICY  = "grayPolicyId";
    public static final String MDC_VERSION = "grayVersion";

    public static final String W3C_BAGGAGE_POLICY  = "gray.policy_id";
    public static final String W3C_BAGGAGE_VERSION = "gray.version";

    public static final List<String> HEADERS = List.of(HEADER_POLICY, HEADER_VERSION, HEADER_CANARY_RATIO);

    private GrayBaggage() {}

    /** 入口写入：从请求头提取放入 MDC；都为空时，不写入（避免 MDC 污染非灰度流量）。 */
    public static void captureFromHeaders(String headerPolicy, String headerVersion) {
        set(headerPolicy, headerVersion);
    }

    public static void set(String policyId, String version) {
        if (policyId == null || policyId.isBlank()) MDC.remove(MDC_POLICY);
        else MDC.put(MDC_POLICY, policyId);
        if (version == null || version.isBlank()) MDC.remove(MDC_VERSION);
        else MDC.put(MDC_VERSION, version);
    }

    public static String policyId() { return MDC.get(MDC_POLICY); }
    public static String version()  { return MDC.get(MDC_VERSION); }

    public static void clear() {
        MDC.remove(MDC_POLICY);
        MDC.remove(MDC_VERSION);
    }
}
