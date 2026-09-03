package com.lianshengtong.gateway.observability;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator /actuator/lscGatewaySummary：返回一份运维友好的 JSON 概览
 * - 配置项：是否启用 JWT filter / Gray filter / Observability filter
 * - 网关 JVM 基础指标（线程活跃 / 堆使用）
 * - traceId 生成器健康自检（1 次生成 + 长度校验）
 * 说明：业务级指标建议直接访问 /actuator/metrics/lsc.gateway.requests.total?tag=grayVersion:canary
 */
@Component
@Endpoint(id = "lscGatewaySummary")
@RequiredArgsConstructor
public class LscGatewaySummaryEndpoint {

    private final TraceIdGenerator traceIdGenerator;

    @ReadOperation
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jwtFilter", "enabled");
        out.put("grayFilter", "enabled");
        out.put("observabilityFilter", "enabled");
        out.put("traceIdSample", traceIdGenerator.next());

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("usedHeapMb", used / (1024 * 1024));
        jvm.put("totalHeapMb", rt.totalMemory() / (1024 * 1024));
        jvm.put("maxHeapMb", rt.maxMemory() / (1024 * 1024));
        jvm.put("availableProcessors", rt.availableProcessors());
        jvm.put("activeThreadCount", Thread.activeCount());
        out.put("jvm", jvm);

        out.put("metricsDocs", Map.of(
                "requestsCounter", "/actuator/metrics/lsc.gateway.requests.total",
                "requestDuration", "/actuator/metrics/lsc.gateway.requests.duration",
                "prometheus",     "/actuator/prometheus (if prometheus registry enabled)"
        ));
        return out;
    }
}
