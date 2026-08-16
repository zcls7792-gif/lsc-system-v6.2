package com.lianshengtong.common.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PerformanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitor.class);

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> latencies = new ConcurrentHashMap<>();

    public void record(String operation, long latencyMs) {
        counters.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
        latencies.computeIfAbsent(operation, k -> new AtomicLong(0)).addAndGet(latencyMs);
    }

    @Scheduled(fixedRate = 60000)
    public void report() {
        if (counters.isEmpty()) return;
        StringBuilder sb = new StringBuilder("[PerfMonitor] ");
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            String op = entry.getKey();
            long count = entry.getValue().getAndSet(0);
            long totalLat = latencies.getOrDefault(op, new AtomicLong(0)).getAndSet(0);
            long avgLat = count > 0 ? totalLat / count : 0;
            sb.append(String.format("%s: count=%d avgLat=%dms; ", op, count, avgLat));
        }
        log.info(sb.toString());
    }

    public long getCount(String operation) {
        return counters.getOrDefault(operation, new AtomicLong(0)).get();
    }


    public PerformanceMonitor() {}

    public Map<String, AtomicLong> getCounters() { return counters; }
    public Map<String, AtomicLong> getLatencies() { return latencies; }
}