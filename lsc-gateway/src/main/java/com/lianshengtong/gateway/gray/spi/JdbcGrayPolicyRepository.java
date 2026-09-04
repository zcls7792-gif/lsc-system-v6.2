package com.lianshengtong.gateway.gray.spi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.gateway.gray.GrayPolicyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC / MySQL 持久化实现。
 * <p>
 * 表（见 src/main/resources/db/gray-schema.sql）：
 * <ul>
 *   <li>{@code gray_policy}：policyId PK, routeId, baselineUri, canaryUri, weight, rules(JSON), meta(JSON), status, created_at, updated_at, updated_by</li>
 *   <li>{@code gray_policy_history}：自增 ID, ts, policy_id(INDEX), operator, action, detail</li>
 * </ul>
 * 规则：
 * <ol>
 *   <li>策略 upsert 使用 MySQL {@code INSERT ... ON DUPLICATE KEY UPDATE}；对 H2 回退 MERGE。</li>
 *   <li>history 仅 append，不更新。</li>
 *   <li>反序列化失败（JSON 字段损坏）→ 记录 ERROR 并跳过该策略（不影响其他策略），保证网关不因脏数据启动失败。</li>
 * </ol>
 * 本类依赖 spring-boot-starter-jdbc + mysql-connector-j（均为 optional），
 * 当运行期不存在 JdbcTemplate / DataSource 时，{@code isAvailable()}=false，上层 GrayReleaseConfig 会跳过 JDBC bean，
 * 回退到纯内存仓储（仍由 GrayPolicyStore 本地提供）。
 */
@Slf4j
public class JdbcGrayPolicyRepository implements GrayPolicyRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcGrayPolicyRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbcTemplate;
        this.mapper = objectMapper;
    }

    @Override public boolean isAvailable() { return jdbc != null; }

    @Override public List<GrayPolicyStore.Policy> loadAll() {
        return jdbc.query(
                "SELECT policy_id, route_id, baseline_uri, canary_uri, canary_weight_percent, " +
                        "rules_json, meta_json, status, created_at, updated_at, updated_by " +
                        "FROM gray_policy ORDER BY created_at ASC",
                policyMapper());
    }

    @Override public void save(GrayPolicyStore.Policy p) {
        String rulesJson = toJson(p.rules());
        String metaJson = toJson(p.meta());
        try {
            // MySQL 优先；H2 / 其他库若语法不兼容会抛异常，catch 后用 UPDATE+INSERT 兜底
            jdbc.update(
                    "INSERT INTO gray_policy " +
                            "(policy_id, route_id, baseline_uri, canary_uri, canary_weight_percent, " +
                            " rules_json, meta_json, status, created_at, updated_at, updated_by) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            " route_id=VALUES(route_id), baseline_uri=VALUES(baseline_uri), " +
                            " canary_uri=VALUES(canary_uri), canary_weight_percent=VALUES(canary_weight_percent), " +
                            " rules_json=VALUES(rules_json), meta_json=VALUES(meta_json), status=VALUES(status), " +
                            " updated_at=VALUES(updated_at), updated_by=VALUES(updated_by)",
                    p.policyId(), p.routeId(), p.baselineUri(), p.canaryUri(), p.canaryWeightPercent(),
                    rulesJson, metaJson, p.status().name(),
                    Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()), p.updatedBy());
        } catch (Exception mysqlSyntax) {
            // fallback: try update first, then insert (H2 / PostgreSQL compatible)
            int updated = jdbc.update(
                    "UPDATE gray_policy SET route_id=?, baseline_uri=?, canary_uri=?, canary_weight_percent=?, " +
                            "rules_json=?, meta_json=?, status=?, updated_at=?, updated_by=? WHERE policy_id=?",
                    p.routeId(), p.baselineUri(), p.canaryUri(), p.canaryWeightPercent(),
                    rulesJson, metaJson, p.status().name(),
                    Timestamp.from(p.updatedAt()), p.updatedBy(), p.policyId());
            if (updated == 0) {
                jdbc.update(
                        "INSERT INTO gray_policy " +
                                "(policy_id, route_id, baseline_uri, canary_uri, canary_weight_percent, " +
                                " rules_json, meta_json, status, created_at, updated_at, updated_by) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        p.policyId(), p.routeId(), p.baselineUri(), p.canaryUri(), p.canaryWeightPercent(),
                        rulesJson, metaJson, p.status().name(),
                        Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()), p.updatedBy());
            }
        }
    }

    @Override public Optional<GrayPolicyStore.Policy> findById(String policyId) {
        List<GrayPolicyStore.Policy> list = jdbc.query(
                "SELECT policy_id, route_id, baseline_uri, canary_uri, canary_weight_percent, " +
                        "rules_json, meta_json, status, created_at, updated_at, updated_by " +
                        "FROM gray_policy WHERE policy_id=?",
                policyMapper(), policyId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override public void delete(String policyId) {
        // 物理删除；历史仍保留（用于审计），若需软删除，可增加 deleted_at 列。
        jdbc.update("DELETE FROM gray_policy WHERE policy_id=?", policyId);
    }

    @Override public void appendHistory(GrayPolicyStore.History h) {
        jdbc.update(
                "INSERT INTO gray_policy_history (ts, policy_id, operator, action, detail) VALUES (?,?,?,?,?)",
                Timestamp.from(h.ts()), h.policyId(), h.operator(), h.action(), h.detail());
    }

    @Override public List<GrayPolicyStore.History> listHistory(String policyId, int limit) {
        if (policyId == null) {
            return jdbc.query(
                    "SELECT ts, policy_id, operator, action, detail FROM gray_policy_history " +
                            "ORDER BY ts DESC LIMIT ?",
                    historyMapper(), limit);
        }
        return jdbc.query(
                "SELECT ts, policy_id, operator, action, detail FROM gray_policy_history " +
                        "WHERE policy_id=? ORDER BY ts DESC LIMIT ?",
                historyMapper(), policyId, limit);
    }

    // ------------ helpers ------------
    private RowMapper<GrayPolicyStore.Policy> policyMapper() {
        return (rs, rowNum) -> {
            try {
                List<GrayPolicyStore.Rule> rules = parseRules(rs.getString("rules_json"));
                Map<String, String> meta = parseMeta(rs.getString("meta_json"));
                GrayPolicyStore.Status status = parseStatus(rs.getString("status"));
                return new GrayPolicyStore.Policy(
                        rs.getString("policy_id"),
                        rs.getString("route_id"),
                        rs.getString("baseline_uri"),
                        rs.getString("canary_uri"),
                        rs.getInt("canary_weight_percent"),
                        rules, meta, status,
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at")),
                        rs.getString("updated_by"),
                        parseRolloutConfig(safeString(rs, "rollout_config_json"))
                );
            } catch (Exception ex) {
                log.error("Failed to deserialize gray policy row, policy_id={}, skipping",
                        safeString(rs, "policy_id"), ex);
                return null; // 最终在 list 中通过 filter(Objects::nonNull) 过滤
            }
        };
    }

    private RowMapper<GrayPolicyStore.History> historyMapper() {
        return (rs, rowNum) -> new GrayPolicyStore.History(
                toInstant(rs.getTimestamp("ts")),
                rs.getString("policy_id"),
                rs.getString("operator"),
                rs.getString("action"),
                rs.getString("detail"));
    }

    private String toJson(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception ex) { throw new IllegalStateException("Gray policy JSON serialize failed: " + ex, ex); }
    }

    private List<GrayPolicyStore.Rule> parseRules(String s) throws Exception {
        if (s == null || s.isBlank()) return List.of();
        return mapper.readValue(s, new TypeReference<List<GrayPolicyStore.Rule>>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseMeta(String s) throws Exception {
        if (s == null || s.isBlank()) return Map.of();
        return (Map<String, String>) mapper.readValue(s, Map.class);
    }

    private static GrayPolicyStore.Status parseStatus(String s) {
        if (s == null || s.isBlank()) return GrayPolicyStore.Status.ACTIVE;
        try { return GrayPolicyStore.Status.valueOf(s); }
        catch (IllegalArgumentException e) { return GrayPolicyStore.Status.PAUSED; }
    }

    /** Phase N：rollout_config_json → RolloutConfig；空/null/异常 → null=继承全局默认。 */
    @SuppressWarnings("unchecked")
    private GrayPolicyStore.RolloutConfig parseRolloutConfig(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            Map<String, Object> m = mapper.readValue(s, new TypeReference<Map<String, Object>>() {});
            List<Integer> steps = null;
            if (m.get("steps") instanceof List<?> l && !l.isEmpty()) {
                steps = new java.util.ArrayList<>(l.size());
                for (Object x : l) steps.add(x instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(x)));
            }
            return new GrayPolicyStore.RolloutConfig(
                    steps,
                    m.get("minMinutesAtStep") instanceof Number n ? n.intValue() : null,
                    m.get("maxErrorDriftPct") instanceof Number n ? n.doubleValue() : null,
                    m.get("maxP95Ratio") instanceof Number n ? n.doubleValue() : null,
                    m.get("minSamplesThreshold") instanceof Number n ? n.longValue() : null,
                    m.get("maxConsecutiveFailuresBeforeRollback") instanceof Number n ? n.intValue() : null,
                    m.get("enabled") instanceof Boolean b ? b : null
            );
        } catch (Exception ex) {
            log.warn("parse rollout_config_json failed, use global default: {}", ex.getMessage());
            return null;
        }
    }

    private static Instant toInstant(Timestamp ts) { return ts == null ? Instant.now() : ts.toInstant(); }

    private static String safeString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException ignored) { return "<unknown>"; }
    }
}
