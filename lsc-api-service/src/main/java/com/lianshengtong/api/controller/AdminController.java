package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/audit/logs")
    public ApiResponse<List<Map<String, Object>>> auditLogs(
            @RequestParam(required = false) Integer adminId,
            @RequestParam(required = false) String module) {
        List<Map<String, Object>> logs = new ArrayList<>();
        String[] actions = {"商家审核", "商品审核", "核销审批", "释放配置", "信用调整"};
        for (int i = 0; i < 10; i++) {
            logs.add(Map.of(
                    "id", i + 1, "adminName", "超级管理员",
                    "module", actions[i % actions.length],
                    "action", actions[i % actions.length] + "操作",
                    "targetId", 10001 + i,
                    "ip", "192.168.1." + (100 + i),
                    "createdAt", java.time.LocalDateTime.now().minusMinutes(i * 30).toString()
            ));
        }
        return ApiResponse.success(logs);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createAdmin(@RequestBody Map<String, Object> body) {
        body.put("id", MockData.admins.size() + 1);
        body.put("status", 1);
        body.put("createdAt", java.time.LocalDateTime.now().toString());
        MockData.admins.add(body);
        return ApiResponse.success(body);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAdmin(@PathVariable int id) {
        MockData.admins.removeIf(a -> ((Integer) a.get("id")) == id);
        return ApiResponse.success(null);
    }
}
