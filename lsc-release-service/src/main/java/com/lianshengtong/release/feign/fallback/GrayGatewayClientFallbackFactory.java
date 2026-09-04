package com.lianshengtong.release.feign.fallback;

import com.lianshengtong.common.result.R;
import com.lianshengtong.release.feign.GrayGatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GrayGatewayClient Sentinel/Feign 降级工厂。
 * <p>
 * 当 lsc-gateway 不可达（熔断 / 超时 / 实例下线）时，通过该工厂返回统一的失败响应。
 * 注意：
 *   - Service 层会对返回 R.success=false 视为执行失败，将审批单置为 EXECUTE_FAILED。
 *   - 不允许静默"假装成功"，否则会出现审批单 SUCCEEDED 但灰度策略实际未执行的"假成功"。
 * </p>
 * @see GrayGatewayClient
 */
@Slf4j
@Component
public class GrayGatewayClientFallbackFactory implements FallbackFactory<GrayGatewayClient> {

    @Override
    public GrayGatewayClient create(Throwable cause) {
        String reason = cause == null ? "unknown" : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        log.warn("[gray-approval] GrayGatewayClient fallback triggered. reason={}", reason);
        return new GrayGatewayClientFallback(reason);
    }

    /** 内部 fallback 实现：每个方法都返回 R.fail(503)。 */
    private record GrayGatewayClientFallback(String reason) implements GrayGatewayClient {

        private R<Map<String, Object>> fail(String op) {
            String msg = "GrayGatewayClient." + op + " fallback: " + reason;
            return R.fail(503, msg);
        }

        @Override public R<Map<String, Object>> graduate(String policyId, String operator, Map<String, Object> reasonBody) {
            return fail("graduate(" + policyId + ")");
        }
        @Override public R<Map<String, Object>> changeWeight(String policyId, String operator, int weightPercent) {
            return fail("changeWeight(" + policyId + "," + weightPercent + ")");
        }
        @Override public R<Map<String, Object>> rollback(String policyId, String operator, String reason) {
            return fail("rollback(" + policyId + ")");
        }
        @Override public R<Map<String, Object>> policyStats(String policyId) {
            return fail("policyStats(" + policyId + ")");
        }
        @Override public R<Map<String, Object>> summary() { return fail("summary"); }
        @Override public R<Map<String, Object>> rolloutStatus() { return fail("rolloutStatus"); }
        @Override public R<Map<String, Object>> rolloutDetail(String policyId) { return fail("rolloutDetail(" + policyId + ")"); }
        @Override public R<Map<String, Object>> rolloutAdvanceStep(String policyId, String operator, String reason) {
            return fail("rolloutAdvanceStep(" + policyId + ")");
        }
    }
}
