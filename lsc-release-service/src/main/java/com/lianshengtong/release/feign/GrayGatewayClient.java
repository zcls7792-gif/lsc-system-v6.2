package com.lianshengtong.release.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Phase M：Feign 客户端 —— 调用 lsc-gateway 的灰度接口。
 * <p>
 * 若 Feign + Nacos 未启用，也可以通过 {@code GrayGatewayHttpClient} 走 RestTemplate/OkHttp 兜底；
 * 但既然 lsc-release-service 的 pom 已引入 openfeign，这里走 Feign（Sentinel 可加 fallbackFactory）。
 */
@FeignClient(
        name = "lsc-gateway",
        path = "/api/gateway/gray",
        contextId = "grayGateway"
        // fallbackFactory = GrayGatewayClientFallbackFactory.class  (可选)
)
public interface GrayGatewayClient {

    @PostMapping("/policies/{policyId}/graduate")
    R<Map<String, Object>> graduate(@PathVariable("policyId") String policyId,
                                     @RequestHeader("X-Admin-User") String operator,
                                     @RequestBody(required = false) Map<String, Object> reasonBody);

    /** 通用：改权重 / 暂停 / 恢复 / 回滚。 */
    @PutMapping("/policies/{policyId}/weight")
    R<Map<String, Object>> changeWeight(@PathVariable("policyId") String policyId,
                                         @RequestHeader("X-Admin-User") String operator,
                                         @RequestParam("weightPercent") int weightPercent);

    @PostMapping("/policies/{policyId}/rollback")
    R<Map<String, Object>> rollback(@PathVariable("policyId") String policyId,
                                    @RequestHeader("X-Admin-User") String operator,
                                    @RequestParam(value = "reason", required = false) String reason);

    /** 查询单策略详情 + stats（用于审批前做"错误率门禁"校验）。 */
    @GetMapping("/policies/{policyId}/stats")
    R<Map<String, Object>> policyStats(@PathVariable("policyId") String policyId);

    @GetMapping("/summary")
    R<Map<String, Object>> summary();

    /** Phase N：rollout 状态（leader/last tick/运行中策略数/approaching rollback 数/默认门限快照）。 */
    @GetMapping("/rollout/status")
    R<Map<String, Object>> rolloutStatus();

    /** Phase N：单策略 rollout 详情（步进/保持时间/SLO 各大门/合并配置）。 */
    @GetMapping("/policies/{policyId}/rollout")
    R<Map<String, Object>> rolloutDetail(@PathVariable("policyId") String policyId);

    /** Phase N：运维手动推进下一步（跳过 minMinutesAtStep 保持时间）。 */
    @PostMapping("/policies/{policyId}/rollout/advance-step")
    R<Map<String, Object>> rolloutAdvanceStep(@PathVariable("policyId") String policyId,
                                               @RequestHeader("X-Admin-User") String operator,
                                               @RequestParam(value = "reason", required = false) String reason);
}
