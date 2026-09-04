package com.lianshengtong.release.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.result.R;
import com.lianshengtong.release.alert.AlertChannel;
import com.lianshengtong.release.config.GrayApprovalProperties;
import com.lianshengtong.release.dto.GrayApprovalDTO;
import com.lianshengtong.release.entity.gray.GrayApprovalAudit;
import com.lianshengtong.release.entity.gray.GrayApprovalFlow;
import com.lianshengtong.release.feign.GrayGatewayClient;
import com.lianshengtong.release.mapper.gray.GrayApprovalAuditMapper;
import com.lianshengtong.release.mapper.gray.GrayApprovalFlowMapper;
import com.lianshengtong.release.service.GrayApprovalService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 灰度审批自愈定时任务（Phase M-可靠性）。
 * <p>
 * 针对"审批状态与网关执行结果不一致"的两类异常：
 * <ol>
 *   <li><b>EXECUTING 卡住</b>：写了 EXECUTING 但 Feign 调用后 DB 回滚（或 OOM Kill）。表现：status=EXECUTING 超过 N 秒。
 *       处理：查询 gateway policyStats，按 policy 真实状态回填 SUCCEEDED / EXECUTE_FAILED。</li>
 *   <li><b>EXECUTE_FAILED 自动重试</b>：网关抖动导致临时 5xx，无需人工介入。
 *       处理：失败次数 < executeRetryMax 且距上次执行 > retryInterval 秒 → 自动 retryExecute。</li>
 * </ol>
 *
 * <h3>XXL-JOB 控制台配置</h3>
 * <pre>
 * cron: 0 0/5 * * * ?      (每 5 分钟执行一次)
 * JobHandler: grayApprovalSelfHealJob
 * </pre>
 *
 * @author lsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayApprovalSelfHealJob {

    private final GrayApprovalFlowMapper flowMapper;
    private final GrayApprovalAuditMapper auditMapper;
    private final GrayGatewayClient gatewayClient;
    private final GrayApprovalService approvalService;
    private final GrayApprovalProperties props;
    private final AlertChannel alertChannel;

    @Value("${release.alert-receivers:admin-super-001,admin-super-002}")
    private String alertReceivers;

    private static final String OPERATOR = "system:selfheal";

    // ==================================================================
    // 主入口
    // ==================================================================
    @XxlJob("grayApprovalSelfHealJob")
    @Transactional
    public void grayApprovalSelfHeal() {
        log.info("[gray-selfheal] start. staleExecutingSec={}, retryMax={}, retryIntervalSec={}",
                props.getStaleExecutingSeconds(), props.getExecuteRetryMax(), props.getSelfHealRetryIntervalSeconds());

        int stuckFixed = fixStaleExecuting();
        int retried = autoRetryExecuteFailed();

        XxlJobHelper.log(String.format(
                "grayApprovalSelfHeal done: stuckExecutingFixed=%d, autoRetried=%d", stuckFixed, retried));
        log.info("[gray-selfheal] done. stuckFixed={}, autoRetried={}", stuckFixed, retried);
    }

    // ==================================================================
    // 1) EXECUTING -> check gateway and reconcile
    // ==================================================================
    private int fixStaleExecuting() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(props.getStaleExecutingSeconds());
        List<GrayApprovalFlow> stuck = flowMapper.selectList(new LambdaQueryWrapper<GrayApprovalFlow>()
                .eq(GrayApprovalFlow::getStatus, GrayApprovalFlow.Status.EXECUTING.name())
                .lt(GrayApprovalFlow::getUpdatedAt, cutoff));
        if (stuck.isEmpty()) return 0;

        int fixed = 0;
        for (GrayApprovalFlow flow : stuck) {
            try {
                reconcileOne(flow);
                fixed++;
            } catch (Exception ex) {
                log.warn("[gray-selfheal] reconcile flowId={} failed: {}", flow.getId(), ex.getMessage());
            }
        }
        return fixed;
    }

    private void reconcileOne(GrayApprovalFlow flow) {
        // 查询网关当前策略状态
        R<Map<String, Object>> statsR = safeCall(() -> gatewayClient.policyStats(flow.getPolicyId()));
        if (statsR == null || !statsR.isSuccess() || statsR.getData() == null) {
            log.warn("[gray-selfheal] cannot get policyStats for flowId={} policyId={}, gateway down? skip.",
                    flow.getId(), flow.getPolicyId());
            return;
        }
        Map<String, Object> stats = statsR.getData();
        // 常见 key: status (ACTIVE/PAUSED/ROLLED_BACK/GRADUATED), weightPercent etc.
        String policyStatus = String.valueOf(stats.getOrDefault("status", "UNKNOWN"));
        String flowType = flow.getFlowType();

        // 根据 flowType + 网关状态推断"是否真实已执行成功"
        boolean actuallySucceeded = switch (GrayApprovalFlow.Type.valueOf(flowType)) {
            case GRADUATE       -> "GRADUATED".equals(policyStatus);
            case ROLLBACK       -> "ROLLED_BACK".equals(policyStatus);
            case WEIGHT_CHANGE  -> {
                int expected = 0;
                try {
                    Map<String,Object> payload = parseJson(flow.getPayloadJson());
                    if (payload != null) expected = Integer.parseInt(String.valueOf(payload.get("targetWeight")));
                } catch (Exception ignore) {}
                Object actualW = stats.get("canaryWeightPercent");
                yield actualW != null && Integer.parseInt(String.valueOf(actualW)) == expected;
            }
            case LAUNCH -> false;
        };

        flow.setStatus(actuallySucceeded
                ? GrayApprovalFlow.Status.SUCCEEDED.name()
                : GrayApprovalFlow.Status.EXECUTE_FAILED.name());
        flow.setUpdatedBy(OPERATOR);
        if (flow.getExecuteCostMs() == null) {
            flow.setExecuteCostMs(Duration.between(flow.getUpdatedAt(), LocalDateTime.now()).toMillis());
        }
        flow.setExecuteResponse("{\"reconciled\":true,\"policyStatus\":\"" + policyStatus
                + "\",\"policyStats\":" + safeJson(stats) + "}");
        flowMapper.updateById(flow);
        appendAudit(flow, actuallySucceeded ? "FLOW_SUCCEEDED" : "FLOW_EXECUTE_FAILED",
                Map.of("reconciled", true, "policyStatus", policyStatus));
        alert("P1", "自愈：修复审批单 " + flow.getFlowNo() + " EXECUTING 卡死",
                "flowId=" + flow.getId() + ", policyId=" + flow.getPolicyId()
                        + ", 通过 policyStats 对比判定实际结果=" + actuallySucceeded
                        + " (SUCCEEDED=" + actuallySucceeded + ", 否则 EXECUTE_FAILED)");
    }

    // ==================================================================
    // 2) EXECUTE_FAILED -> auto retry
    // ==================================================================
    private int autoRetryExecuteFailed() {
        LocalDateTime retryCutoff = LocalDateTime.now().minusSeconds(props.getSelfHealRetryIntervalSeconds());
        // 取所有 EXECUTE_FAILED 且距上次更新超过间隔
        List<GrayApprovalFlow> failed = flowMapper.selectList(new LambdaQueryWrapper<GrayApprovalFlow>()
                .eq(GrayApprovalFlow::getStatus, GrayApprovalFlow.Status.EXECUTE_FAILED.name())
                .lt(GrayApprovalFlow::getUpdatedAt, retryCutoff));
        if (failed.isEmpty()) return 0;

        int retried = 0;
        for (GrayApprovalFlow flow : failed) {
            int pastFails = countPastExecuteFailures(flow.getId());
            if (pastFails > props.getExecuteRetryMax()) {
                log.warn("[gray-selfheal] flowId={} failCount={} > {}, abort auto retry; alert admin",
                        flow.getId(), pastFails, props.getExecuteRetryMax());
                alert("P0", "自愈放弃：审批单 " + flow.getFlowNo() + " 失败次数超限",
                        "flowId=" + flow.getId() + ", policyId=" + flow.getPolicyId()
                                + ", 已失败 " + pastFails + " 次 > 阈值 " + props.getExecuteRetryMax()
                                + "，请运维手动 retryExecute 排查。");
                continue;
            }
            try {
                GrayApprovalDTO.RetryExecuteRequest req = new GrayApprovalDTO.RetryExecuteRequest();
                req.flowId = flow.getId();
                req.operator = OPERATOR;
                approvalService.retryExecute(req);
                retried++;
                log.info("[gray-selfheal] auto-retry flowId={} done. pastFails={}", flow.getId(), pastFails);
            } catch (Exception ex) {
                log.warn("[gray-selfheal] auto-retry flowId={} error: {}", flow.getId(), ex.getMessage());
            }
        }
        return retried;
    }

    private int countPastExecuteFailures(Long flowId) {
        return auditMapper.selectCount(new LambdaQueryWrapper<GrayApprovalAudit>()
                .eq(GrayApprovalAudit::getFlowId, flowId)
                .eq(GrayApprovalAudit::getAction, "FLOW_EXECUTE_FAILED")).intValue();
    }

    // ==================================================================
    // helpers
    // ==================================================================
    private void appendAudit(GrayApprovalFlow flow, String action, Map<String,?> detail) {
        GrayApprovalAudit a = new GrayApprovalAudit();
        a.setFlowId(flow.getId());
        a.setFlowNo(flow.getFlowNo());
        a.setAction(action);
        a.setOperator(OPERATOR);
        a.setDetailJson(safeJson(detail));
        auditMapper.insert(a);
    }

    private String safeJson(Object o) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
        catch (Exception e) { return String.valueOf(o); }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    private <T> T safeCall(SafeSupplier<T> s) {
        try { return s.get(); } catch (Exception ex) { return null; }
    }
    @FunctionalInterface
    private interface SafeSupplier<T> { T get() throws Exception; }

    private void alert(String level, String title, String detail) {
        log.error("[SELFHEAL-ALERT-{}] {}\n  detail: {}", level, title, detail);
        try { alertChannel.send(alertReceivers, "[" + level + "] " + title, detail); }
        catch (Exception ex) { log.warn("[gray-selfheal] alert warn: {}", ex.getMessage()); }
    }
}
