package com.lianshengtong.admin.controller;

import cn.hutool.core.util.IdUtil;
import com.lianshengtong.common.result.R;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端接口（V7.7.2 第十三章）
 * 配置变更、审批、释放参数管理
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    // 简化：内存存储配置变更（实际应落库 config_change_request / config_version）
    private final Map<Long, Map<String, Object>> changeStore = new ConcurrentHashMap<>();

    /** 获取当前释放参数（来自 application.yml 的默认值） */
    @GetMapping("/config/release")
    public R<Map<String, Object>> getReleaseConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("wMinPpm", 10000);
        data.put("wMaxPpm", 20000);
        data.put("rMinPpb", 500000);
        data.put("rMaxPpb", 1000000);
        return R.ok(data);
    }

    /** 发起配置变更 */
    @PostMapping("/config-changes")
    public R<Map<String, Object>> createConfigChange(@RequestBody Map<String, Object> body) {
        Long changeId = IdUtil.getSnowflakeNextId();
        Map<String, Object> change = new HashMap<>();
        change.put("changeId", changeId);
        change.put("configGroup", body.get("configGroup"));
        change.put("proposedJson", body.get("proposedJson"));
        change.put("reason", body.get("reason"));
        change.put("effectiveDate", body.getOrDefault("effectiveDate", LocalDate.now().plusDays(1).toString()));
        change.put("status", "PENDING");
        change.put("requesterId", 1L); // 简化
        change.put("createdAt", new Date());
        changeStore.put(changeId, change);

        Map<String, Object> result = new HashMap<>();
        result.put("changeId", changeId);
        result.put("status", "PENDING");
        return R.ok(result);
    }

    /** 审批配置变更（不得自批） */
    @PostMapping("/config-changes/{changeId}/approve")
    public R<Void> approveConfigChange(@PathVariable Long changeId,
                                        @RequestAttribute("adminId") Long adminId) {
        Map<String, Object> change = changeStore.get(changeId);
        if (change == null) {
            return R.fail(404, "变更单不存在");
        }
        // 简化：实际应校验 requesterId != approverId
        change.put("status", "APPROVED");
        change.put("approverId", adminId);
        change.put("approvalAt", new Date());
        return R.ok();
    }

    /** 查询配置变更列表 */
    @GetMapping("/config-changes")
    public R<List<Map<String, Object>>> listConfigChanges(
            @RequestParam(required = false) String configGroup) {
        List<Map<String, Object>> list = new ArrayList<>(changeStore.values());
        if (configGroup != null && !configGroup.isEmpty()) {
            list = list.stream()
                    .filter(c -> configGroup.equals(c.get("configGroup")))
                    .toList();
        }
        return R.ok(list);
    }
}
