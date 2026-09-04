package com.lianshengtong.release.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.dto.PageResult;
import com.lianshengtong.common.result.R;
import com.lianshengtong.release.dto.GrayApprovalDTO;
import com.lianshengtong.release.entity.gray.GrayApprovalAudit;
import com.lianshengtong.release.entity.gray.GrayApprovalFlow;
import com.lianshengtong.release.entity.gray.GrayApprovalNode;
import com.lianshengtong.release.feign.GrayGatewayClient;
import com.lianshengtong.release.mapper.gray.GrayApprovalAuditMapper;
import com.lianshengtong.release.mapper.gray.GrayApprovalFlowMapper;
import com.lianshengtong.release.mapper.gray.GrayApprovalNodeMapper;
import com.lianshengtong.release.service.GrayApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Phase M：灰度审批工作流实现。
 * <p>
 * 关键设计：
 * <ol>
 *   <li>任何状态变更都会写 GrayApprovalAudit（不可变流水）。</li>
 *   <li>flowId 级 Redisson 公平锁：避免并发重复审批或重复执行 graduate。</li>
 *   <li>requiredApprovals > approvedCount 时状态仍 PENDING；相等 → APPROVED → 自动 EXECUTING → 执行网关接口 → SUCCEEDED/EXECUTE_FAILED。</li>
 *   <li>Gateway 调用失败：记录 executeResponse + 状态 EXECUTE_FAILED，提供 retryExecute 手动重试。</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GrayApprovalServiceImpl implements GrayApprovalService {

    private final GrayApprovalFlowMapper   flowMapper;
    private final GrayApprovalNodeMapper   nodeMapper;
    private final GrayApprovalAuditMapper  auditMapper;
    private final GrayGatewayClient        gatewayClient;
    private final ObjectMapper             objectMapper;
    private final org.springframework.beans.factory.ObjectProvider<RedissonClient> redissonProvider;

    private static final String LOCK_KEY = "gray:approval:flow:";
    /** 进程内兜底：同一 JVM 内并发重复调用审批时仍能串行化。key=flowId */
    private static final ConcurrentHashMap<Long, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    // ========= 1. 创建审批单 =========
    @Override
    @Transactional
    public GrayApprovalFlow create(GrayApprovalDTO.CreateRequest req) {
        GrayApprovalFlow flow = new GrayApprovalFlow();
        flow.setFlowType(req.flowType.name());
        flow.setPolicyId(req.policyId);
        flow.setApplicant(req.applicant);
        flow.setTitle(StringUtils.hasText(req.title) ? req.title : defaultTitle(req));
        flow.setApplyReason(req.applyReason);
        flow.setStatus(GrayApprovalFlow.Status.DRAFT.name());
        int required = req.requiredApprovals == null ? 2 : req.requiredApprovals;
        flow.setRequiredApprovals(Math.max(1, Math.min(5, required)));
        flow.setApprovedCount(0);
        flow.setTotalNodes(flow.getRequiredApprovals());
        flow.setUpdatedBy(req.applicant);
        flow.setPayloadJson(toJson(req.payload));
        flow.setFlowNo(generateFlowNo());
        flowMapper.insert(flow);

        // 按 requiredApprovals 生成审批节点
        List<String> approvers = req.approvers;
        for (int i = 0; i < flow.getRequiredApprovals(); i++) {
            GrayApprovalNode n = new GrayApprovalNode();
            n.setFlowId(flow.getId());
            n.setNodeOrder(i + 1);
            n.setApproverRole("ROLE_RELEASE_ADMIN");
            n.setApprover(approvers != null && approvers.size() > i ? approvers.get(i) : null);
            n.setNodeStatus(GrayApprovalNode.NodeStatus.WAITING.name());
            nodeMapper.insert(n);
        }

        audit(flow.getId(), flow.getFlowNo(), "FLOW_CREATED", req.applicant, Map.of(
                "flowType", flow.getFlowType(),
                "policyId", flow.getPolicyId(),
                "requiredApprovals", flow.getRequiredApprovals()
        ));
        // 默认立刻进入 PENDING_APPROVAL（相当于 DRAFT 只在前端草稿栏显示）
        flow.setStatus(GrayApprovalFlow.Status.PENDING_APPROVAL.name());
        flow.setUpdatedBy(req.applicant);
        flowMapper.updateById(flow);
        audit(flow.getId(), flow.getFlowNo(), "FLOW_SUBMITTED", req.applicant, Map.of());
        return flow;
    }

    // ========= 2. 审批通过/拒绝 =========
    @Override
    @Transactional
    public GrayApprovalFlow approveOrReject(GrayApprovalDTO.ApproveRequest req) {
        try (AcquiredLock _l = tryLock(req.flowId)) {
            GrayApprovalFlow flow = mustFlow(req.flowId);
            if (!(GrayApprovalFlow.Status.PENDING_APPROVAL.name().equals(flow.getStatus()))) {
                throw new IllegalStateException("flow not pending: " + flow.getStatus());
            }
            // 找到审批人对应的 WAITING 节点：优先匹配 approver；不匹配则在剩余 WAITING 中取第一个（审批人池）
            GrayApprovalNode node = nodeMapper.selectOne(new LambdaQueryWrapper<GrayApprovalNode>()
                    .eq(GrayApprovalNode::getFlowId, flow.getId())
                    .and(w -> w.eq(GrayApprovalNode::getApprover, req.approver)
                            .or().isNull(GrayApprovalNode::getApprover))
                    .eq(GrayApprovalNode::getNodeStatus, GrayApprovalNode.NodeStatus.WAITING.name())
                    .orderByAsc(GrayApprovalNode::getNodeOrder)
                    .last("LIMIT 1"));
            if (node == null) {
                // 兜底：该审批人已审批过（重复点击）或审批人池没有 WAITING 节点
                node = nodeMapper.selectOne(new LambdaQueryWrapper<GrayApprovalNode>()
                        .eq(GrayApprovalNode::getFlowId, flow.getId())
                        .eq(GrayApprovalNode::getNodeStatus, GrayApprovalNode.NodeStatus.WAITING.name())
                        .orderByAsc(GrayApprovalNode::getNodeOrder)
                        .last("LIMIT 1"));
                if (node == null) throw new IllegalStateException("no waiting node");
            }
            node.setApprover(req.approver);
            node.setDecidedAt(LocalDateTime.now());
            node.setComment(req.comment);
            node.setSignature(req.signature);
            node.setNodeStatus(Boolean.TRUE.equals(req.approved)
                    ? GrayApprovalNode.NodeStatus.APPROVED.name()
                    : GrayApprovalNode.NodeStatus.REJECTED.name());
            nodeMapper.updateById(node);

            // 拒绝 → 整单 REJECTED
            if (!Boolean.TRUE.equals(req.approved)) {
                flow.setStatus(GrayApprovalFlow.Status.REJECTED.name());
                flow.setUpdatedBy(req.approver);
                flowMapper.updateById(flow);
                audit(flow.getId(), flow.getFlowNo(), "FLOW_REJECTED", req.approver, Map.of("comment", or(req.comment,"")));
                return flow;
            }

            // 新的 approvedCount
            int approved = nodeMapper.selectCount(new LambdaQueryWrapper<GrayApprovalNode>()
                    .eq(GrayApprovalNode::getFlowId, flow.getId())
                    .eq(GrayApprovalNode::getNodeStatus, GrayApprovalNode.NodeStatus.APPROVED.name())).intValue();
            flow.setApprovedCount(approved);
            flow.setUpdatedBy(req.approver);

            if (approved >= flow.getRequiredApprovals()) {
                flow.setStatus(GrayApprovalFlow.Status.APPROVED.name());
                flow.setApprovedAt(LocalDateTime.now());
                flowMapper.updateById(flow);
                audit(flow.getId(), flow.getFlowNo(), "FLOW_APPROVED", req.approver, Map.of("approvedTotal", approved));
                // 异步（或同步）调用网关执行
                try {
                    return executeApprovedFlow(flow, req.approver);
                } catch (Exception ex) {
                    log.warn("[gray-approval] auto-execute flow={} failed: {}", flow.getId(), ex.getMessage());
                    return flowMapper.selectById(flow.getId());
                }
            } else {
                flowMapper.updateById(flow);
                audit(flow.getId(), flow.getFlowNo(), "NODE_APPROVED", req.approver, Map.of("nodeOrder", node.getNodeOrder()));
                return flow;
            }
        }
    }

    // ========= 3. 撤销 =========
    @Override
    @Transactional
    public GrayApprovalFlow cancel(GrayApprovalDTO.CancelRequest req) {
        try (AcquiredLock ignored = tryLock(req.flowId)) {
            GrayApprovalFlow flow = mustFlow(req.flowId);
            if (!(flow.getStatus().equals(GrayApprovalFlow.Status.DRAFT.name())
                    || flow.getStatus().equals(GrayApprovalFlow.Status.PENDING_APPROVAL.name()))) {
                throw new IllegalStateException("cannot cancel: status=" + flow.getStatus());
            }
            flow.setStatus(GrayApprovalFlow.Status.CANCELLED.name());
            flow.setUpdatedBy(req.operator);
            flowMapper.updateById(flow);
            audit(flow.getId(), flow.getFlowNo(), "FLOW_CANCELLED", req.operator, Map.of("reason", or(req.reason,"")));
            return flow;
        }
    }

    // ========= 4. 重试执行 =========
    @Override
    @Transactional
    public GrayApprovalFlow retryExecute(GrayApprovalDTO.RetryExecuteRequest req) {
        try (AcquiredLock _l = tryLock(req.flowId)) {
            GrayApprovalFlow flow = mustFlow(req.flowId);
            if (!flow.getStatus().equals(GrayApprovalFlow.Status.EXECUTE_FAILED.name())
                    && !flow.getStatus().equals(GrayApprovalFlow.Status.APPROVED.name())) {
                throw new IllegalStateException("not retryable status: " + flow.getStatus());
            }
            return executeApprovedFlow(flow, req.operator);
        }
    }

    // ========= 5. 查询 & 详情 =========
    @Override
    public PageResult<GrayApprovalFlow> query(GrayApprovalDTO.Query q) {
        LambdaQueryWrapper<GrayApprovalFlow> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.keyword)) {
            String kw = "%" + q.keyword + "%";
            w.and(x -> x.like(GrayApprovalFlow::getFlowNo, q.keyword)
                    .or().like(GrayApprovalFlow::getTitle, q.keyword)
                    .or().like(GrayApprovalFlow::getPolicyId, q.keyword));
        }
        if (StringUtils.hasText(q.status))    w.eq(GrayApprovalFlow::getStatus, q.status);
        if (StringUtils.hasText(q.flowType))    w.eq(GrayApprovalFlow::getFlowType, q.flowType);
        if (StringUtils.hasText(q.applicant))   w.eq(GrayApprovalFlow::getApplicant, q.applicant);
        w.orderByDesc(GrayApprovalFlow::getId);
        Page<GrayApprovalFlow> page = flowMapper.selectPage(
                new Page<>(Math.max(1, q.pageNo), Math.min(200, Math.max(1, q.pageSize))), w);
        return PageResult.of(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public GrayApprovalDTO.Detail detail(Long flowId) {
        GrayApprovalFlow flow = mustFlow(flowId);
        List<GrayApprovalNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<GrayApprovalNode>()
                .eq(GrayApprovalNode::getFlowId, flowId)
                .orderByAsc(GrayApprovalNode::getNodeOrder));
        List<GrayApprovalAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<GrayApprovalAudit>()
                .eq(GrayApprovalAudit::getFlowId, flowId)
                .orderByDesc(GrayApprovalAudit::getId));
        GrayApprovalDTO.Detail d = new GrayApprovalDTO.Detail();
        d.flow = flow; d.nodes = nodes; d.audits = audits;
        return d;
    }

    // 测试专用：强制改状态（比如在单测里跳过审批流程直接验证 executeApprovedFlow）。
    @Override
    @Transactional
    public GrayApprovalFlow forceStatus(Long flowId, GrayApprovalFlow.Status target, String operator) {
        GrayApprovalFlow flow = mustFlow(flowId);
        flow.setStatus(target.name());
        flow.setUpdatedBy(operator);
        if (target == GrayApprovalFlow.Status.APPROVED) flow.setApprovedAt(LocalDateTime.now());
        flowMapper.updateById(flow);
        audit(flow.getId(), flow.getFlowNo(), "FLOW_FORCE_STATUS_" + target.name(), operator, Map.of());
        return flow;
    }

    // ========= internals =========

    /** 执行审批通过后的动作：GRADUATE → /graduate；WEIGHT_CHANGE → /weight；ROLLBACK → /rollback；LAUNCH → /policies（暂不支持通过审批单直接创建）。 */
    private GrayApprovalFlow executeApprovedFlow(GrayApprovalFlow flow, String operator) {
        flow.setStatus(GrayApprovalFlow.Status.EXECUTING.name());
        flow.setUpdatedBy(operator);
        flowMapper.updateById(flow);
        audit(flow.getId(), flow.getFlowNo(), "FLOW_EXECUTING", operator, Map.of());

        long start = System.currentTimeMillis();
        String resp;
        boolean success;
        try {
            R<Map<String,Object>> r = switch (GrayApprovalFlow.Type.valueOf(flow.getFlowType())) {
                case GRADUATE -> gatewayClient.graduate(flow.getPolicyId(), operator, Map.of("approvalFlowNo", flow.getFlowNo()));
                case WEIGHT_CHANGE -> {
                    Map<String,Object> payload = parseJson(flow.getPayloadJson());
                    int w = payload == null ? 0 : Integer.parseInt(String.valueOf(payload.getOrDefault("targetWeight", "0")));
                    yield gatewayClient.changeWeight(flow.getPolicyId(), operator, w);
                }
                case ROLLBACK -> {
                    Map<String,Object> payload = parseJson(flow.getPayloadJson());
                    String reason = payload == null ? null : (String) payload.getOrDefault("reason", null);
                    yield gatewayClient.rollback(flow.getPolicyId(), operator, reason);
                }
                case LAUNCH -> throw new UnsupportedOperationException("LAUNCH type apply via policy upsert API directly; not supported.");
            };
            resp = toJson(Map.of("success", r.isSuccess(), "code", r.getCode(), "data", r.getData(), "msg", r.getMessage()));
            success = r.isSuccess();
        } catch (Exception ex) {
            resp = toJson(Map.of("success", false, "exception", ex.getClass().getSimpleName(), "message", ex.getMessage()));
            success = false;
        }
        long cost = System.currentTimeMillis() - start;
        flow.setExecuteCostMs(cost);
        flow.setExecuteResponse(resp);
        flow.setUpdatedBy(operator);
        if (success) {
            flow.setStatus(GrayApprovalFlow.Status.SUCCEEDED.name());
        } else {
            flow.setStatus(GrayApprovalFlow.Status.EXECUTE_FAILED.name());
        }
        flowMapper.updateById(flow);
        audit(flow.getId(), flow.getFlowNo(), success ? "FLOW_SUCCEEDED" : "FLOW_EXECUTE_FAILED", operator,
                Map.of("costMs", cost, "success", success));
        return flow;
    }

    // ========= helpers =========
    private GrayApprovalFlow mustFlow(Long flowId) {
        GrayApprovalFlow flow = flowMapper.selectById(flowId);
        if (flow == null) throw new IllegalArgumentException("flow not found id=" + flowId);
        return flow;
    }

    private AcquiredLock tryLock(Long flowId) {
        // 优先 Redisson 分布式锁
        RedissonClient r = redissonProvider.getIfAvailable();
        if (r != null) {
            RLock lock = r.getFairLock(LOCK_KEY + flowId);
            try {
                boolean ok = lock.tryLock(10, 60, TimeUnit.SECONDS);
                if (!ok) throw new IllegalStateException("cannot acquire distributed lock for flow " + flowId);
                return new AcquiredLock(null, lock, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("lock interrupted");
            }
        }
        // 降级：JVM 级 ReentrantLock（单实例部署仍安全；多实例不保证，但不会造成数据脏写，因为 DB 层事务 + 乐观判断）
        ReentrantLock mutex = JVM_LOCKS.computeIfAbsent(flowId, k -> new ReentrantLock());
        boolean ok = mutex.tryLock();
        if (!ok) throw new IllegalStateException("cannot acquire jvm mutex for flow " + flowId);
        return new AcquiredLock(mutex, null, false);
    }

    /** 轻量级锁释放句柄：try-with-resources 语义，不再依赖具体 RLock/ReentrantLock 接口。 */
    private static final class AcquiredLock implements AutoCloseable {
        final ReentrantLock jvm;
        final RLock dist;
        final boolean distributed;
        final AtomicBoolean closed = new AtomicBoolean(false);
        AcquiredLock(ReentrantLock jvm, RLock dist, boolean distributed) {
            this.jvm = jvm; this.dist = dist; this.distributed = distributed;
        }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                if (distributed && dist != null) { if (dist.isHeldByCurrentThread()) dist.unlock(); }
                else if (jvm != null)            { if (jvm.isHeldByCurrentThread()) jvm.unlock(); }
            } catch (Exception ex) { log.warn("[gray-approval] unlock warn: {}", ex.getMessage()); }
        }
    }

    private void audit(Long flowId, String flowNo, String action, String operator, Map<String,?> detail) {
        GrayApprovalAudit a = new GrayApprovalAudit();
        a.setFlowId(flowId); a.setFlowNo(flowNo); a.setAction(action);
        a.setOperator(operator);
        a.setDetailJson(toJson(detail));
        auditMapper.insert(a);
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try { return objectMapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return String.valueOf(o); }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    private static String defaultTitle(GrayApprovalDTO.CreateRequest req) {
        return req.flowType + "/" + req.policyId;
    }

    private static String or(String s, String def) { return s == null ? def : s; }

    /** 生成 GA + yyyyMMdd + 6 位 sequence（简化版：毫秒级时间戳）。 */
    private static String generateFlowNo() {
        return "GA" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%06d", (System.nanoTime() % 1_000_000));
    }
}
