package com.zcls.lsc.risk.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.risk.enums.RiskEnums.RuleStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 第10.8章 风控规则版本管理。
 *
 *  - 同一 rule_code 可有多个 version，同一时刻仅一个 ACTIVE
 *  - 新建版本时旧 ACTIVE 置为 SUPERSEDED
 *  - checksum = SHA-256(condition_json + action + score + threshold) 防篡改
 */
@Service
public class RuleVersionService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RuleVersionService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * 创建规则新版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createRule(String ruleCode, String name, String description,
                           String conditionJson, String matchMode,
                           int score, int threshold, String action,
                           int priority, long createdBy) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "rule_code is required");
        }
        // 校验 conditionJson 是合法 JSON 数组
        validateConditionJson(conditionJson);

        // 查当前最大版本号
        Integer maxVersion = jdbc.query(
                "SELECT MAX(version) FROM risk_rule WHERE rule_code=?",
                (rs, rowNum) -> rs.getInt(1), ruleCode).stream().findFirst().orElse(0);
        int version = (maxVersion == null ? 0 : maxVersion) + 1;

        String checksum = sha256(conditionJson + action + score + threshold);
        long ruleId = nextId();

        jdbc.update(
                "INSERT INTO risk_rule(rule_id, rule_code, version, name, description, "
                        + "condition_json, match_mode, score, threshold, action, priority, "
                        + "status, created_by, checksum, created_at, updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ruleId, ruleCode, version, name, description, conditionJson, matchMode,
                score, threshold, action, priority, RuleStatus.DRAFT.name(),
                createdBy, checksum,
                Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
        return ruleId;
    }

    /**
     * 激活指定规则版本：将其置为 ACTIVE，同 code 的其他 ACTIVE 置为 SUPERSEDED。
     */
    @Transactional(rollbackFor = Exception.class)
    public void activateRule(long ruleId) {
        // 查 rule_code
        String ruleCode = jdbc.queryForObject(
                "SELECT rule_code FROM risk_rule WHERE rule_id=?", String.class, ruleId);
        if (ruleCode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "rule not found: " + ruleId);
        }
        // 同 code 的 ACTIVE 置为 SUPERSEDED
        jdbc.update("UPDATE risk_rule SET status='SUPERSEDED', updated_at=? "
                + "WHERE rule_code=? AND status='ACTIVE'",
                Timestamp.valueOf(LocalDateTime.now()), ruleCode);
        // 当前版本置为 ACTIVE
        int updated = jdbc.update(
                "UPDATE risk_rule SET status='ACTIVE', effective_at=?, updated_at=? "
                        + "WHERE rule_id=? AND status IN ('DRAFT','DISABLED')",
                Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), ruleId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "rule is not in DRAFT/DISABLED status: " + ruleId);
        }
    }

    /**
     * 停用规则。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableRule(long ruleId) {
        int updated = jdbc.update(
                "UPDATE risk_rule SET status='DISABLED', updated_at=? WHERE rule_id=? AND status='ACTIVE'",
                Timestamp.valueOf(LocalDateTime.now()), ruleId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "active rule not found: " + ruleId);
        }
    }

    /**
     * 查询所有生效规则（按 priority 升序）。
     */
    public List<Map<String, Object>> listActiveRules() {
        return jdbc.queryForList(
                "SELECT rule_id, rule_code, version, name, condition_json, match_mode, "
                        + "score, threshold, action, priority FROM risk_rule "
                        + "WHERE status='ACTIVE' ORDER BY priority ASC");
    }

    /**
     * 按 rule_code 查当前生效版本。
     */
    public Map<String, Object> getActiveRule(String ruleCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM risk_rule WHERE rule_code=? AND status='ACTIVE' LIMIT 1", ruleCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 校验 condition_json 是合法的条件数组。
     * 条件格式: [{"field":"refund_rate","op":">=","value":0.3}]
     */
    private void validateConditionJson(String conditionJson) {
        try {
            List<Map<String, Object>> conditions = mapper.readValue(
                    conditionJson, new TypeReference<List<Map<String, Object>>>() {});
            if (conditions == null || conditions.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "condition_json must contain at least one condition");
            }
            for (Map<String, Object> c : conditions) {
                if (!c.containsKey("field") || !c.containsKey("op") || !c.containsKey("value")) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                            "each condition must have field, op, value");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "invalid condition_json: " + e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
