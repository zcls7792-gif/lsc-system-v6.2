package com.lianshengtong.admin.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI网关 Feign 客户端 - 管理员异常操作监控
 */
@FeignClient(name = "lsc-ai-gateway", contextId = "adminAiGatewayClient")
public interface AiGatewayFeignClient {

    /**
     * 管理员操作异常检测
     *
     * @param adminId 管理员ID
     * @param module  操作模块
     * @param action  操作类型
     * @param detail  操作详情
     * @return 风险评分(0~100)
     */
    @PostMapping("/api/ai/admin/monitor")
    R<Integer> monitorAdminAction(@RequestParam("adminId") Long adminId,
                                  @RequestParam("module") String module,
                                  @RequestParam("action") String action,
                                  @RequestParam("detail") String detail);
}
