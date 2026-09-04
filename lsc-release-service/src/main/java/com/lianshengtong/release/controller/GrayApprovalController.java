package com.lianshengtong.release.controller;

import com.lianshengtong.common.dto.PageResult;
import com.lianshengtong.common.result.R;
import com.lianshengtong.release.dto.GrayApprovalDTO;
import com.lianshengtong.release.entity.gray.GrayApprovalFlow;
import com.lianshengtong.release.service.GrayApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Phase M：灰度审批工作流 REST API。
 * <p>
 * 典型链路（审批自动毕业）：
 * <pre>
 *   1. POST /api/release/gray/approvals     创建审批单（指定 flowType=GRADUATE, policyId=xxx）
 *   2. PUT  /approvals/{id}/approve         由 2+ 审批人调用（requiredApprovals=2 默认）
 *      └→ 第 2 次通过 → Service 状态 → APPROVED → EXECUTING
 *      └→ 通过 Feign 调 lsc-gateway POST /api/gateway/gray/policies/{id}/graduate
 *      └→ 成功 → SUCCEEDED；失败 → EXECUTE_FAILED（可 retryExecute 重试）
 * </pre>
 */
@Tag(name = "Gray Approval", description = "灰度审批工作流（毕业/放量/回滚）")
@RestController
@RequestMapping("/api/release/gray")
@RequiredArgsConstructor
public class GrayApprovalController {

    private final GrayApprovalService service;

    @Operation(summary = "创建审批单（默认立即提交进入审批流程）")
    @PostMapping("/approvals")
    public R<GrayApprovalFlow> create(@Valid @RequestBody GrayApprovalDTO.CreateRequest req) {
        return R.ok(service.create(req));
    }

    @Operation(summary = "审批通过 / 拒绝（单次一个审批节点）")
    @PutMapping("/approvals/action/approve")
    public R<GrayApprovalFlow> approve(@Valid @RequestBody GrayApprovalDTO.ApproveRequest req) {
        return R.ok(service.approveOrReject(req));
    }

    @Operation(summary = "撤销审批单（仅 DRAFT / PENDING）")
    @PutMapping("/approvals/action/cancel")
    public R<GrayApprovalFlow> cancel(@Valid @RequestBody GrayApprovalDTO.CancelRequest req) {
        return R.ok(service.cancel(req));
    }

    @Operation(summary = "重试网关执行（EXECUTE_FAILED / APPROVED 未执行时）")
    @PutMapping("/approvals/action/retry-execute")
    public R<GrayApprovalFlow> retryExecute(@Valid @RequestBody GrayApprovalDTO.RetryExecuteRequest req) {
        return R.ok(service.retryExecute(req));
    }

    @Operation(summary = "分页查询审批单列表")
    @GetMapping("/approvals")
    public R<PageResult<GrayApprovalFlow>> query(GrayApprovalDTO.Query q) {
        return R.ok(service.query(q));
    }

    @Operation(summary = "审批单详情：flow + nodes + audits")
    @Parameter(name = "flowId", description = "审批单 ID")
    @GetMapping("/approvals/{flowId}")
    public R<GrayApprovalDTO.Detail> detail(@PathVariable("flowId") Long flowId) {
        return R.ok(service.detail(flowId));
    }

    /** 测试 / 排障接口：强制改状态（生产建议加权限 ROLE_SUPER_ADMIN）。 */
    @Operation(summary = "[测试] 强制改状态")
    @PutMapping("/approvals/{flowId}/force-status")
    public R<GrayApprovalFlow> forceStatus(@PathVariable Long flowId,
                                            @RequestParam GrayApprovalFlow.Status target,
                                            @RequestParam(defaultValue = "system") String operator) {
        return R.ok(service.forceStatus(flowId, target, operator));
    }
}
