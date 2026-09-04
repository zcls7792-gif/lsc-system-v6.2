package com.lianshengtong.release.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Phase M：Feign 客户端 —— 调用 lsc-gateway 的灰度接口。
 * <p>
 * 路由策略（Spring Cloud OpenFeign 4.x 原生占位符配置）：
 *   <ul>
 *     <li>生产：<b>不设置</b> {@code gray.gateway-url} → 走 {@code name=服务名} + Spring Cloud LoadBalancer + Nacos 服务发现；</li>
 *     <li>沙箱/联调：<b>设置</b> {@code gray.gateway-url=http(s)://host:port} → 走直连 URL，<b>完全不依赖</b> LB/Nacos。</li>
 *   </ul>
 * 若目标网关不可达，FallbackFactory {@link GrayGatewayClientFallbackFactory} 返回空结果降级，业务继续。
 */
@FeignClient(
        name = "lsc-gateway",
        url  = "${gray.gateway-url:}",
        path = "/api/gateway/gray",
        contextId = "grayGateway",
        fallbackFactory = com.lianshengtong.release.feign.fallback.GrayGatewayClientFallbackFactory.class
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
