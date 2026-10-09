package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.risk.ComplianceGateService;
import com.zcls.lsc.risk.ComplianceInspectionService;
import com.zcls.lsc.risk.ComplianceInspectionService.InspectionResultRow;
import com.zcls.lsc.risk.ConfigAuditService;
import com.zcls.lsc.risk.ConfigAuditService.ActiveConfig;
import com.zcls.lsc.risk.ConfigAuditService.AuditLogRow;
import com.zcls.lsc.risk.NotificationService;
import com.zcls.lsc.risk.NotificationService.PendingDelivery;
import com.zcls.lsc.risk.RiskService;
import com.zcls.lsc.risk.enums.RiskEnums.AppealStatus;
import com.zcls.lsc.risk.enums.RiskEnums.AuditResult;
import com.zcls.lsc.risk.enums.RiskEnums.CaseReviewStatus;
import com.zcls.lsc.risk.enums.RiskEnums.GateScope;
import com.zcls.lsc.risk.enums.RiskEnums.ProposedAction;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 第13章 风控与配置审计接口（管理端 + 用户端申诉）。
 *
 *  - 风控案件立案 / 48h 复核 / 申诉 5 工作日答复
 *  - 配置变更双签 / 版本生效 / 管理员审计日志查询
 *  - 合规门禁申请 / 批准 / 校验（AI 启用 / 上线发布前置）
 *  - 通知投递入队 / 状态回报
 */
@RestController
@RequestMapping("/v1")
public class RiskConfigController {

    private final RiskService riskService;
    private final ConfigAuditService configAuditService;
    private final ComplianceGateService gateService;
    private final NotificationService notificationService;
    private final ComplianceInspectionService inspectionService;

    public RiskConfigController(RiskService riskService, ConfigAuditService configAuditService,
                               ComplianceGateService gateService, NotificationService notificationService,
                               ComplianceInspectionService inspectionService) {
        this.riskService = riskService;
        this.configAuditService = configAuditService;
        this.gateService = gateService;
        this.notificationService = notificationService;
        this.inspectionService = inspectionService;
    }

    // ===== 风控案件 =====

    @PostMapping("/admin/risk/cases")
    public ApiResponse<Long> createRiskCase(
            @RequestParam long userId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(defaultValue = "false") boolean aiFlag,
            @RequestParam(required = false) String evidenceRef,
            @RequestParam String proposedAction) {
        return ApiResponse.ok(riskService.createCase(userId, ruleId, aiFlag, evidenceRef,
                ProposedAction.valueOf(proposedAction)));
    }

    @PostMapping("/admin/risk/cases/{id}/review")
    public ApiResponse<Void> reviewCase(
            @PathVariable long id,
            @RequestParam long reviewerId,
            @RequestParam String decision,
            @RequestParam(required = false) String reason) {
        riskService.reviewCase(id, reviewerId, CaseReviewStatus.valueOf(decision), reason);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/risk/cases/overdue")
    public ApiResponse<List<Long>> findOverdueCases() {
        return ApiResponse.ok(riskService.findOverdueCases());
    }

    @GetMapping("/admin/risk/cases/blocking")
    public ApiResponse<List<Long>> findBlockingCases(@RequestParam long userId) {
        return ApiResponse.ok(riskService.findBlockingCases(userId));
    }

    // ===== 申诉 =====

    @PostMapping("/user/appeals")
    public ApiResponse<Long> submitAppeal(
            @RequestParam long caseId,
            @RequestParam long userId) {
        return ApiResponse.ok(riskService.submitAppeal(caseId, userId));
    }

    @PostMapping("/admin/appeals/{id}/accept")
    public ApiResponse<Void> acceptAppeal(
            @PathVariable long id, @RequestParam long reviewerId) {
        riskService.acceptAppeal(id, reviewerId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/appeals/{id}/reply")
    public ApiResponse<Void> replyAppeal(
            @PathVariable long id,
            @RequestParam long reviewerId,
            @RequestParam String decision,
            @RequestParam(required = false) String decisionRef) {
        riskService.replyAppeal(id, reviewerId, AppealStatus.valueOf(decision), decisionRef);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/appeals/overdue")
    public ApiResponse<List<Long>> findOverdueAppeals() {
        return ApiResponse.ok(riskService.findOverdueAppeals());
    }

    // ===== 配置变更双签 =====

    @PostMapping("/admin/config-changes")
    public ApiResponse<Long> submitConfigChange(
            @RequestParam String configGroup,
            @RequestBody String proposedJson,
            @RequestParam long requesterId,
            @RequestParam(required = false) String reason,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate) {
        return ApiResponse.ok(configAuditService.submitChange(configGroup, proposedJson,
                requesterId, reason, effectiveDate));
    }

    @PostMapping("/admin/config-changes/{id}/approve")
    public ApiResponse<Void> approveConfigChange(
            @PathVariable long id, @RequestParam long approverId) {
        configAuditService.approveChange(id, approverId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/config-changes/{id}/reject")
    public ApiResponse<Void> rejectConfigChange(
            @PathVariable long id,
            @RequestParam long approverId,
            @RequestParam(required = false) String reason) {
        configAuditService.rejectChange(id, approverId, reason);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/config-changes/{id}/withdraw")
    public ApiResponse<Void> withdrawConfigChange(
            @PathVariable long id, @RequestParam long requesterId) {
        configAuditService.withdrawChange(id, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/config-changes/{id}/apply")
    public ApiResponse<Long> applyConfigChange(@PathVariable long id) {
        return ApiResponse.ok(configAuditService.applyChange(id));
    }

    @PostMapping("/admin/config-changes/apply-due")
    public ApiResponse<Integer> applyDueConfigChanges() {
        return ApiResponse.ok(configAuditService.applyDueChanges());
    }

    @GetMapping("/admin/config/active")
    public ApiResponse<ActiveConfig> getActiveConfig(@RequestParam String configGroup) {
        return ApiResponse.ok(configAuditService.getActiveConfig(configGroup));
    }

    // ===== 管理员审计日志 =====

    @PostMapping("/admin/audit-logs")
    public ApiResponse<Long> writeAuditLog(
            @RequestParam long actorId,
            @RequestParam String action,
            @RequestParam String resourceType,
            @RequestParam long resourceId,
            @RequestParam(required = false) String requestId,
            @RequestParam String result,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String beforeHash,
            @RequestParam(required = false) String afterHash) {
        return ApiResponse.ok(configAuditService.writeAuditLog(actorId, action, resourceType,
                resourceId, requestId, AuditResult.valueOf(result), reason, beforeHash, afterHash));
    }

    @GetMapping("/admin/audit-logs")
    public ApiResponse<List<AuditLogRow>> queryAuditLog(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "0") long resourceId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(configAuditService.queryAuditLog(actorId, resourceType, resourceId, limit));
    }

    // ===== 合规门禁 =====

    @PostMapping("/admin/compliance-gates")
    public ApiResponse<String> requestGate(
            @RequestParam String gateCode,
            @RequestParam String scope,
            @RequestParam(required = false) String evidenceRef,
            @RequestParam long ownerId) {
        return ApiResponse.ok(gateService.requestGate(gateCode, GateScope.valueOf(scope),
                evidenceRef, ownerId));
    }

    @PostMapping("/admin/compliance-gates/{code}/approve")
    public ApiResponse<Void> approveGate(
            @PathVariable String code,
            @RequestParam long approverId,
            @RequestParam(defaultValue = "4") long ttlHours) {
        gateService.approveGate(code, approverId, ttlHours);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/compliance-gates/{code}/reject")
    public ApiResponse<Void> rejectGate(
            @PathVariable String code, @RequestParam long approverId) {
        gateService.rejectGate(code, approverId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/compliance-gates/{code}/revoke")
    public ApiResponse<Void> revokeGate(@PathVariable String code) {
        gateService.revokeGate(code);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/compliance-gates/{code}/verify")
    public ApiResponse<Boolean> verifyGate(@PathVariable String code) {
        return ApiResponse.ok(gateService.verifyGate(code));
    }

    @PostMapping("/admin/compliance-gates/sweep")
    public ApiResponse<Integer> sweepExpiredGates() {
        return ApiResponse.ok(gateService.sweepExpiredGates());
    }

    // ===== 通知投递 =====

    @PostMapping("/admin/notifications")
    public ApiResponse<Long> enqueueNotification(
            @RequestParam long userId,
            @RequestParam String templateCode,
            @RequestParam String businessKey,
            @RequestParam String channel) {
        return ApiResponse.ok(notificationService.enqueue(userId, templateCode, businessKey, channel));
    }

    @GetMapping("/admin/notifications/pending")
    public ApiResponse<List<PendingDelivery>> findPendingNotifications(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(notificationService.findPending(limit));
    }

    @PostMapping("/admin/notifications/{id}/sent")
    public ApiResponse<Void> markNotificationSent(@PathVariable long id) {
        notificationService.markSent(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/notifications/{id}/failed")
    public ApiResponse<Void> markNotificationFailed(@PathVariable long id) {
        notificationService.markFailed(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/notifications/sweep")
    public ApiResponse<Integer> sweepStuckNotifications() {
        return ApiResponse.ok(notificationService.sweepStuckDeliveries());
    }

    @GetMapping("/admin/notifications/dead")
    public ApiResponse<List<PendingDelivery>> findDeadNotifications(
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(notificationService.findDeadDeliveries(limit));
    }

    // ===== 合规巡检 R01-R12 =====

    /**
     * 手动触发合规巡检（执行全部 R01-R12）。
     * 返回 true 表示所有 CRITICAL 规则均通过；false 表示存在 CRITICAL 违规。
     */
    @PostMapping("/admin/compliance/inspect")
    public ApiResponse<Boolean> runComplianceInspection(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        LocalDate date = businessDate != null ? businessDate : LocalDate.now();
        return ApiResponse.ok(inspectionService.runInspection(date));
    }

    /**
     * 查询指定业务日的合规巡检结果。
     */
    @GetMapping("/admin/compliance/inspection-results")
    public ApiResponse<List<InspectionResultRow>> getInspectionResults(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(inspectionService.queryResults(businessDate));
    }
}
