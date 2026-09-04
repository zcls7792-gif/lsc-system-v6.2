package com.lianshengtong.release.service;

import com.lianshengtong.common.dto.PageResult;
import com.lianshengtong.release.dto.GrayApprovalDTO;
import com.lianshengtong.release.entity.gray.GrayApprovalFlow;

/**
 * Phase M：灰度审批工作流 Service 接口。
 */
public interface GrayApprovalService {

    /** 创建审批单（DRAFT 可修改；默认直接提交 -> PENDING_APPROVAL）。 */
    GrayApprovalFlow create(GrayApprovalDTO.CreateRequest req);

    /** 单审批人操作：通过 / 拒绝。 */
    GrayApprovalFlow approveOrReject(GrayApprovalDTO.ApproveRequest req);

    /** 撤销审批（仅 DRAFT / PENDING_APPROVAL）。 */
    GrayApprovalFlow cancel(GrayApprovalDTO.CancelRequest req);

    /** 审批通过后立即执行（或 EXECUTE_FAILED 时重试）。 */
    GrayApprovalFlow retryExecute(GrayApprovalDTO.RetryExecuteRequest req);

    /** 分页查询 */
    PageResult<GrayApprovalFlow> query(GrayApprovalDTO.Query q);

    /** 审批单详情（flow + nodes + audits）。 */
    GrayApprovalDTO.Detail detail(Long flowId);

    /** 仅用于测试 / 手动推进：把审批单状态推进到指定状态（不写入审批节点）。 */
    GrayApprovalFlow forceStatus(Long flowId, GrayApprovalFlow.Status target, String operator);
}
