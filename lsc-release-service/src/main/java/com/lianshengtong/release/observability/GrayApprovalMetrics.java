package com.lianshengtong.release.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 灰度审批模块统一 Metrics 埋点入口。
 * <p>
 * 所有埋点前缀 {@code gray_approval_*}，与详细设计文档 §11.1 一致。
 * </p>
 */
@Slf4j
@Component
public class GrayApprovalMetrics {

    private final MeterRegistry registry;

    // ----- Counters -----
    private final Map<String, Counter> flowCreatedCounters    = new ConcurrentHashMap<>();
    private final Map<String, Counter> nodeDecisionCounters   = new ConcurrentHashMap<>();
    private final Map<String, Counter> executeFailCounters    = new ConcurrentHashMap<>();
    private final Map<String, Counter> lockContentionCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> auditWriteCounters     = new ConcurrentHashMap<>();

    // ----- Gauges: 按状态统计当前审批单数量 -----
    private final Map<String, AtomicLong> statusGauges = new ConcurrentHashMap<>();

    // ----- Timers: 网关执行延迟 -----
    private final Map<String, Timer> executeTimers = new ConcurrentHashMap<>();

    public GrayApprovalMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 预注册 8 种主状态 gauge，避免首次懒注册时序问题
        for (String s : new String[]{"DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED",
                "CANCELLED", "EXECUTING", "SUCCEEDED", "EXECUTE_FAILED"}) {
            getStatusGauge(s);
        }
    }

    // ====================================================================
    // Counter 埋点
    // ====================================================================
    public void incFlowCreated(String flowType) {
        counter(flowCreatedCounters, "gray_approval_flow_created_total",
                "flowType", flowType).increment();
    }

    public void incNodeDecision(String decision, String approverRole) {
        String key = decision + "|" + approverRole;
        counter(nodeDecisionCounters, "gray_approval_node_decision_total",
                new String[]{"decision", "approverRole"},
                new String[]{decision, approverRole}).increment();
    }

    public void incExecuteFail(String flowType, String exceptionClass) {
        String key = flowType + "|" + exceptionClass;
        counter(executeFailCounters, "gray_approval_execute_fail_total",
                new String[]{"flowType", "exceptionClass"},
                new String[]{flowType, exceptionClass}).increment();
    }

    public void incLockContention(String lockType, boolean success) {
        String key = lockType + "|" + success;
        counter(lockContentionCounters, "gray_approval_lock_contention_total",
                new String[]{"lockType", "success"},
                new String[]{lockType, String.valueOf(success)}).increment();
    }

    public void incAuditWrite(String action) {
        counter(auditWriteCounters, "gray_approval_audit_write_total",
                "action", action).increment();
    }

    // ====================================================================
    // Gauge 埋点（当前值观察）
    // ====================================================================
    public void setFlowStatusCount(String status, long count) {
        getStatusGauge(status).set(count);
    }

    private AtomicLong getStatusGauge(String status) {
        return statusGauges.computeIfAbsent(status, k -> {
            AtomicLong al = new AtomicLong(0);
            Gauge.builder("gray_approval_flow_status_total", al, AtomicLong::get)
                    .tag("status", k)
                    .description("Current number of approval flows grouped by status")
                    .register(registry);
            return al;
        });
    }

    // ====================================================================
    // Timer 埋点
    // ====================================================================
    public Timer.Sample startExecuteSample() {
        return Timer.start(registry);
    }

    public void stopExecuteSample(Timer.Sample sample, String flowType, boolean success) {
        String key = flowType + "|" + success;
        Timer timer = executeTimers.computeIfAbsent(key, k -> Timer.builder("gray_approval_execute_latency_ms")
                .description("Latency for gray gateway execution (graduate/weight-change/rollback)")
                .tag("flowType", flowType)
                .tag("success", String.valueOf(success))
                .publishPercentileHistogram()
                .register(registry));
        sample.stop(timer);
    }

    // ====================================================================
    // helpers
    // ====================================================================
    private Counter counter(Map<String, Counter> cache, String name, String tagK, String tagV) {
        return cache.computeIfAbsent(tagV, k -> Counter.builder(name)
                .tag(tagK, k)
                .register(registry));
    }

    private Counter counter(Map<String, Counter> cache, String name, String[] tags, String[] values) {
        String key = String.join("|", values);
        return cache.computeIfAbsent(key, k -> {
            Counter.Builder b = Counter.builder(name);
            for (int i = 0; i < tags.length; i++) b.tag(tags[i], values[i]);
            return b.register(registry);
        });
    }

    /** 仅用于测试快速重置计数。 */
    public Supplier<Void> _noop() { return () -> null; }
}
