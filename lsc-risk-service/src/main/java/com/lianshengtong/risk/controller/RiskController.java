package com.lianshengtong.risk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.security.RequireAdminRole;
import com.lianshengtong.risk.dto.RiskCheckDTO;
import com.lianshengtong.risk.entity.RiskLog;
import com.lianshengtong.risk.service.RiskControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 风控服务接口
 */
@Tag(name = "风控服务", description = "固定规则+AI动态风控，高风险自动限制+人工审核")
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskControlService riskControlService;

    @Operation(summary = "风控检测")
    @PostMapping("/check")
    public R<RiskLog> check(@RequestBody RiskCheckDTO dto) {
        return R.ok(riskControlService.check(dto));
    }

    @Operation(summary = "风控日志列表")
    @GetMapping("/logs")
    public R<IPage<RiskLog>> logs(@RequestParam(required = false, defaultValue = "1") Integer page,
                                  @RequestParam(required = false, defaultValue = "20") Integer size,
                                  @RequestParam(required = false) Long userId,
                                  @RequestParam(required = false) Integer riskLevel,
                                  @RequestParam(required = false) Integer handleStatus) {
        return R.ok(riskControlService.logs(page, size, userId, riskLevel, handleStatus));
    }

    @Operation(summary = "风控事件详情")
    @GetMapping("/logs/{id}")
    public R<RiskLog> detail(@PathVariable("id") Long id) {
        return R.ok(riskControlService.getById(id));
    }

    @Operation(summary = "风控仪表盘统计")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(riskControlService.dashboard());
    }

    @Operation(summary = "人工处理风控事件")
    @PostMapping("/logs/{id}/handle")
    @RequireAdminRole(2)
    public R<Void> handleByPath(@PathVariable("id") Long id,
                                @RequestBody Map<String, String> body) {
        String action = body.get("action");
        String remark = body.get("remark");
        Integer handleStatus = parseAction(action);
        riskControlService.handle(id, handleStatus, remark);
        return R.ok();
    }

    @Operation(summary = "人工处理风控事件(兼容旧接口)")
    @PostMapping("/handle")
    @RequireAdminRole(2)
    public R<Void> handle(@RequestParam("id") Long id,
                          @RequestParam("handleStatus") Integer handleStatus,
                          @RequestParam(value = "handleRemark", required = false) String handleRemark) {
        riskControlService.handle(id, handleStatus, handleRemark);
        return R.ok();
    }

    /** 前端 action 字符串映射到 handleStatus: ignore->3 unblock->4 push->2 其他尝试解析为数字 */
    private Integer parseAction(String action) {
        if (action == null || action.isEmpty()) {
            return 3;
        }
        switch (action.toLowerCase()) {
            case "ignore": return 3;
            case "unblock": return 4;
            case "push": return 2;
            default:
                try {
                    return Integer.parseInt(action);
                } catch (NumberFormatException e) {
                    return 3;
                }
        }
    }
}
