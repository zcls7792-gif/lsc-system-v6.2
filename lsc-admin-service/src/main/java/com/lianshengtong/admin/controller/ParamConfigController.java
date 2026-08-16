package com.lianshengtong.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.admin.entity.ParamChangeApproval;
import com.lianshengtong.admin.service.AdminAuditService;
import com.lianshengtong.admin.service.ParamChangeService;
import com.lianshengtong.admin.util.AdminJwtUtil;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.security.RequireAdminRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 参数配置接口
 * <p>关键参数变更双人审批流程：发起 -> 双人签名 -> 链上存证 -> 生效。</p>
 */
@Tag(name = "参数配置", description = "关键参数变更双人审批")
@RestController
@RequestMapping("/api/admin/param")
@RequiredArgsConstructor
public class ParamConfigController {

    private final ParamChangeService paramChangeService;
    private final AdminAuditService adminAuditService;
    private final AdminJwtUtil adminJwtUtil;

    @Operation(summary = "发起参数变更审批")
    @PostMapping("/submit")
    public R<ParamChangeApproval> submit(@RequestHeader("Authorization") String token,
                                         @RequestParam("configKey") String configKey,
                                         @RequestParam("newValue") String newValue,
                                         @RequestParam(value = "remark", required = false) String remark,
                                         HttpServletRequest request) {
        Long adminId = parseAdminId(token);
        ParamChangeApproval approval = paramChangeService.submit(configKey, newValue, adminId, remark);
        adminAuditService.record(adminId, "param", "submit", String.valueOf(approval.getId()),
                "发起参数变更 key=" + configKey + " newValue=" + newValue, request.getRemoteAddr());
        return R.ok(approval);
    }

    @Operation(summary = "审批签名(双人)")
    @PostMapping("/approve")
    @RequireAdminRole(2)
    public R<ParamChangeApproval> approve(@RequestHeader("Authorization") String token,
                                          @RequestParam("approvalId") Long approvalId,
                                          @RequestParam("signature") String signature,
                                          HttpServletRequest request) {
        Long adminId = parseAdminId(token);
        ParamChangeApproval approval = paramChangeService.approve(approvalId, adminId, signature);
        adminAuditService.record(adminId, "param", "approve", String.valueOf(approvalId),
                "参数变更签名 status=" + approval.getStatus(), request.getRemoteAddr());
        return R.ok(approval);
    }

    @Operation(summary = "拒绝参数变更")
    @PostMapping("/reject")
    @RequireAdminRole(2)
    public R<Void> reject(@RequestHeader("Authorization") String token,
                          @RequestParam("approvalId") Long approvalId,
                          @RequestParam(value = "reason", required = false) String reason,
                          HttpServletRequest request) {
        Long adminId = parseAdminId(token);
        paramChangeService.reject(approvalId, adminId, reason);
        adminAuditService.record(adminId, "param", "reject", String.valueOf(approvalId),
                "拒绝参数变更 reason=" + reason, request.getRemoteAddr());
        return R.ok();
    }

    @Operation(summary = "审批列表")
    @GetMapping("/list")
    public R<IPage<ParamChangeApproval>> list(@RequestHeader("Authorization") String token,
                                              @RequestParam(required = false, defaultValue = "1") Integer page,
                                              @RequestParam(required = false, defaultValue = "20") Integer size,
                                              @RequestParam(required = false) Integer status) {
        parseAdminId(token);
        return R.ok(paramChangeService.list(page, size, status));
    }

    private Long parseAdminId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!adminJwtUtil.validateToken(token)) {
            throw new BizException(401, "Token无效或已过期");
        }
        return adminJwtUtil.getAdminId(token);
    }
}
