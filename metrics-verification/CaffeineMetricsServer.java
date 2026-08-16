import com.sun.net.httpserver.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class CaffeineMetricsServer {

    // ============ 内存缓存（模拟 Caffeine Cache 接口） ============
    static class SimpleCache {
        final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
        final int maxSize;
        final AtomicLong hitCount = new AtomicLong();
        final AtomicLong missCount = new AtomicLong();
        final AtomicLong evictCount = new AtomicLong();
        final AtomicLong putCount = new AtomicLong();

        SimpleCache(int maxSize) { this.maxSize = maxSize; }

        void put(String key, Object value) {
            putCount.incrementAndGet();
            if (map.size() >= maxSize) {
                Iterator<String> it = map.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                    evictCount.incrementAndGet();
                }
            }
            map.put(key, value);
        }

        Object get(String key) {
            Object v = map.get(key);
            if (v != null) {
                hitCount.incrementAndGet();
            } else {
                missCount.incrementAndGet();
            }
            return v;
        }

        long hitCount() { return hitCount.get(); }
        long missCount() { return missCount.get(); }
        long evictionCount() { return evictCount.get(); }
        long putCount() { return putCount.get(); }
        long size() { return map.size(); }

        void clear() { map.clear(); }
    }

    // ============ Prometheus 格式化输出 ============
    static String toPrometheusFormat(MeterRegistry registry) {
        StringBuilder sb = new StringBuilder();
        List<Meter> meters = new ArrayList<>();
        registry.getMeters().forEach(meters::add);

        for (Meter meter : meters) {
            if (meter instanceof Counter) {
                sb.append(formatCounter(meter.getId(), ((Counter) meter).count()));
            } else if (meter instanceof FunctionCounter) {
                sb.append(formatCounter(meter.getId(), ((FunctionCounter) meter).count()));
            } else if (meter instanceof Gauge) {
                sb.append(formatGauge(meter.getId(), ((Gauge) meter).value()));
            }
        }
        return sb.toString();
    }

    static String formatCounter(Meter.Id id, double value) {
        StringBuilder sb = new StringBuilder();
        String metricName = sanitizeName(id.getName());
        sb.append("# HELP ").append(metricName).append(' ')
                .append(id.getDescription() != null ? id.getDescription() : "")
                .append('\n');
        sb.append("# TYPE ").append(metricName).append(" counter\n");
        sb.append(metricName).append(formatLabels(id)).append(' ')
                .append(value).append('\n');
        return sb.toString();
    }

    static String formatGauge(Meter.Id id, double value) {
        StringBuilder sb = new StringBuilder();
        String metricName = sanitizeName(id.getName());
        sb.append("# HELP ").append(metricName).append(' ')
                .append(id.getDescription() != null ? id.getDescription() : "")
                .append('\n');
        sb.append("# TYPE ").append(metricName).append(" gauge\n");
        sb.append(metricName).append(formatLabels(id)).append(' ')
                .append(value).append('\n');
        return sb.toString();
    }

    static String sanitizeName(String name) {
        return name.replace('.', '_').replace('-', '_');
    }

    static String formatLabels(Meter.Id id) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        List<Tag> tags = id.getTags();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            Tag tag = tags.get(i);
            sb.append(sanitizeName(tag.getKey())).append("=\"").append(tag.getValue()).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    // ============ HTTP Server ============
    public static void main(String[] args) throws Exception {
        int port = 8113;
        String contextPath = "/lsc-evidence";
        int cacheMaxSize = 10000;

        if (args.length >= 1) cacheMaxSize = Integer.parseInt(args[0]);
        if (args.length >= 2) port = Integer.parseInt(args[1]);

        SimpleCache cache = new SimpleCache(cacheMaxSize);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // Register cache metrics via MeterRegistry API
        Tags commonTags = Tags.of("application", "lsc-evidence-service", "cache.type", "caffeine");

        FunctionCounter.builder("caffeine.cache.hit.count", cache, c -> c.hitCount())
                .description("The number of cache hits")
                .tag("name", "evidence")
                .tags(commonTags)
                .register(meterRegistry);

        FunctionCounter.builder("caffeine.cache.miss.count", cache, c -> c.missCount())
                .description("The number of cache misses")
                .tag("name", "evidence")
                .tags(commonTags)
                .register(meterRegistry);

        FunctionCounter.builder("caffeine.cache.eviction.count", cache, c -> c.evictionCount())
                .description("The number of cache evictions")
                .tag("name", "evidence")
                .tags(commonTags)
                .register(meterRegistry);

        FunctionCounter.builder("caffeine.cache.put.count", cache, c -> c.putCount())
                .description("The number of cache puts")
                .tag("name", "evidence")
                .tags(commonTags)
                .register(meterRegistry);

        Gauge.builder("caffeine.cache.size", cache, c -> (double) c.size())
                .description("The number of entries in the cache")
                .tag("name", "evidence")
                .tags(commonTags)
                .register(meterRegistry);

        // Simulate cache operations
        System.out.println("[Startup] Simulating cache operations...");
        for (int i = 0; i < 500; i++) {
            cache.put("key-" + i, "value-" + i);
        }
        for (int i = 0; i < 350; i++) {
            cache.get("key-" + (int) (Math.random() * 500));
        }
        for (int i = 0; i < 50; i++) {
            cache.get("nonexistent-" + i);
        }
        System.out.println("[Startup] Cache warmed up: size=" + cache.size() +
                ", hits=" + cache.hitCount() +
                ", misses=" + cache.missCount());

        // ---- HTTP Server ----
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Prometheus endpoint
        server.createContext(contextPath + "/actuator/prometheus", exchange -> {
            String response = toPrometheusFormat(meterRegistry);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Health endpoint
        server.createContext(contextPath + "/actuator/health", exchange -> {
            String response = "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"},\"diskSpace\":{\"status\":\"UP\"}}}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Metrics endpoint
        server.createContext(contextPath + "/actuator/metrics", exchange -> {
            List<String> names = new ArrayList<>();
            meterRegistry.getMeters().forEach(m -> names.add(m.getId().getName()));
            StringBuilder json = new StringBuilder("{\"names\":[");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(names.get(i)).append('"');
            }
            json.append("]}");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Cache API: stats
        server.createContext(contextPath + "/api/cache/stats", exchange -> {
            String response = String.format(
                    "{\"size\":%d,\"hits\":%d,\"misses\":%d,\"evictions\":%d,\"puts\":%d}",
                    cache.size(), cache.hitCount(), cache.missCount(),
                    cache.evictionCount(), cache.putCount());
            sendJson(exchange, response);
        });

        // Cache API: put
        server.createContext(contextPath + "/api/cache/put", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("key=")) {
                String key = "", value = "";
                for (String p : query.split("&")) {
                    if (p.startsWith("key=")) key = p.substring(4);
                    if (p.startsWith("value=")) value = p.substring(6);
                }
                cache.put(key, value);
                sendJson(exchange, "{\"status\":\"ok\",\"operation\":\"put\",\"key\":\"" + key + "\"}");
            } else {
                sendJson(exchange, "{\"error\":\"missing key parameter\"}");
            }
        });

        // Cache API: get
        server.createContext(contextPath + "/api/cache/get", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String key = (query != null && query.contains("key=")) ?
                    query.split("key=")[1].split("&")[0] : "";
            Object value = cache.get(key);
            String response = "{\"key\":\"" + key + "\",\"value\":" +
                    (value != null ? "\"" + value + "\"" : "null") + "}";
            sendJson(exchange, response);
        });

        server.setExecutor(null);
        server.start();

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║   Caffeine Cache + Micrometer Metrics 验证服务已启动          ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.printf("║   HTTP:        http://localhost:%d%s%n", port, contextPath);
        System.out.printf("║   Prometheus:  http://localhost:%d%s/actuator/prometheus%n", port, contextPath);
        System.out.printf("║   Health:      http://localhost:%d%s/actuator/health%n", port, contextPath);
        System.out.printf("║   Metrics:     http://localhost:%d%s/actuator/metrics%n", port, contextPath);
        System.out.printf("║   Cache API:   http://localhost:%d%s/api/cache/stats%n", port, contextPath);
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("按 Ctrl+C 停止服务...");
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
