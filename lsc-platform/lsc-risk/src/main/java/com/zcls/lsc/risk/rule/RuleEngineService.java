package com.zcls.lsc.risk.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcls.lsc.risk.RiskService;
import com.zcls.lsc.risk.enums.RiskEnums.MatchMode;
import com.zcls.lsc.risk.enums.RiskEnums.ProposedAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 第10.8章 风控规则引擎。
 *
 * 评估流程（语义冻结）：
 *  1. 加载所有 ACTIVE 规则（按 priority 升序）
 *  2. 可选：调用 AI 模型获取 aiFlag + aiScore
 *  3. 逐条规则评估 condition_json：
 *     - match_mode=ALL: 所有条件满足才命中
 *     - match_mode=ANY: 任一条件满足即命中
 *  4. 命中规则累加 score 到 totalScore
 *  5. totalScore >= threshold 时触发 action
 *  6. 触发动作映射为 ProposedAction，调用 RiskService.createCase 立案
 *  7. 写 risk_rule_execution_log
 */
@Service
public class RuleEngineService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RuleVersionService ruleVersionService;
    private final RiskService riskService;
    private final AIService aiService;

    public RuleEngineService(JdbcTemplate jdbc, ObjectMapper mapper,
                             RuleVersionService ruleVersionService,
                             RiskService riskService, AIService aiService) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.ruleVersionService = ruleVersionService;
        this.riskService = riskService;
        this.aiService = aiService;
    }

    /**
     * 评估用户风险并自动立案。
     *
     * @param userId     被评估用户
     * @param context    行为特征上下文
     * @param useAI      是否启用 AI 辅助判定
     * @return 评估结果（命中规则数、是否触发动作、创建的案件ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public EvaluationResult evaluateAndEnforce(long userId, Map<String, Object> context, boolean useAI) {
        List<Map<String, Object>> rules = ruleVersionService.listActiveRules();
        if (rules.isEmpty()) {
            return new EvaluationResult(0, 0, false, null);
        }

        // AI 辅助判定
        boolean aiFlag = false;
        double aiScore = 0.0;
        String aiEvidence = null;
        if (useAI) {
            AIService.AIResult aiResult = aiService.evaluate(userId, context);
            aiFlag = aiResult.aiFlag();
            aiScore = aiResult.score();
            aiEvidence = aiResult.evidenceRef();
        }

        int totalScore = aiFlag ? (int) (aiScore * 100) : 0;
        int matchedCount = 0;
        boolean triggered = false;
        Long createdCaseId = null;

        for (Map<String, Object> rule : rules) {
            long ruleId = ((Number) rule.get("rule_id")).longValue();
            String ruleCode = (String) rule.get("rule_code");
            String conditionJson = (String) rule.get("condition_json");
            String matchModeStr = (String) rule.get("match_mode");
            int score = ((Number) rule.get("score")).intValue();
            int threshold = ((Number) rule.get("threshold")).intValue();
            String action = (String) rule.get("action");

            boolean matched = matchRule(conditionJson, matchModeStr, context);
            if (matched) {
                matchedCount++;
                totalScore += score;
            }

            // 写执行日志
            boolean ruleTriggered = totalScore >= threshold;
            writeExecutionLog(ruleId, ruleCode, userId, matched,
                    matched ? score : 0, totalScore, ruleTriggered, aiFlag);

            // 达到阈值则触发动作
            if (ruleTriggered && !triggered) {
                triggered = true;
                ProposedAction proposedAction = parseAction(action);
                if (proposedAction != null && isBlockingAction(proposedAction)) {
                    createdCaseId = riskService.createCase(
                            userId, ruleCode, aiFlag, aiEvidence, proposedAction);
                }
            }
        }

        return new EvaluationResult(matchedCount, totalScore, triggered, createdCaseId);
    }

    /**
     * 仅评估不立案（用于预览/调试）。
     */
    public EvaluationResult evaluateOnly(long userId, Map<String, Object> context, boolean useAI) {
        List<Map<String, Object>> rules = ruleVersionService.listActiveRules();
        if (rules.isEmpty()) {
            return new EvaluationResult(0, 0, false, null);
        }

        boolean aiFlag = false;
        double aiScore = 0.0;
        if (useAI) {
            AIService.AIResult aiResult = aiService.evaluate(userId, context);
            aiFlag = aiResult.aiFlag();
            aiScore = aiResult.score();
        }

        int totalScore = aiFlag ? (int) (aiScore * 100) : 0;
        int matchedCount = 0;
        boolean triggered = false;

        for (Map<String, Object> rule : rules) {
            String conditionJson = (String) rule.get("condition_json");
            String matchModeStr = (String) rule.get("match_mode");
            int score = ((Number) rule.get("score")).intValue();
            int threshold = ((Number) rule.get("threshold")).intValue();

            if (matchRule(conditionJson, matchModeStr, context)) {
                matchedCount++;
                totalScore += score;
            }
            if (totalScore >= threshold) {
                triggered = true;
            }
        }
        return new EvaluationResult(matchedCount, totalScore, triggered, null);
    }

    /**
     * 匹配单条规则。
     */
    private boolean matchRule(String conditionJson, String matchModeStr, Map<String, Object> context) {
        try {
            List<Map<String, Object>> conditions = mapper.readValue(
                    conditionJson, new TypeReference<List<Map<String, Object>>>() {});
            MatchMode mode = MatchMode.valueOf(matchModeStr.toUpperCase());

            if (mode == MatchMode.ALL) {
                for (Map<String, Object> c : conditions) {
                    if (!ConditionEvaluator.evaluate(c, context)) return false;
                }
                return true;
            } else {
                for (Map<String, Object> c : conditions) {
                    if (ConditionEvaluator.evaluate(c, context)) return true;
                }
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void writeExecutionLog(long ruleId, String ruleCode, long userId,
                                   boolean matched, int score, int totalScore,
                                   boolean triggered, boolean aiFlag) {
        long logId = System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
        jdbc.update(
                "INSERT INTO risk_rule_execution_log(log_id, rule_id, rule_code, user_id, "
                        + "matched, score, total_score, triggered, ai_flag, executed_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?)",
                logId, ruleId, ruleCode, userId,
                matched ? 1 : 0, score, totalScore, triggered ? 1 : 0, aiFlag ? 1 : 0,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private ProposedAction parseAction(String action) {
        try {
            return ProposedAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 仅阻塞类动作需要立案（ALERT 仅提示不立案）。 */
    private boolean isBlockingAction(ProposedAction action) {
        return action != ProposedAction.ALERT;
    }

    /** 评估结果。 */
    public record EvaluationResult(int matchedCount, int totalScore, boolean triggered, Long caseId) {
        public List<String> toSummary() {
            List<String> s = new ArrayList<>();
            s.add("命中规则数: " + matchedCount);
            s.add("累计风险分: " + totalScore);
            s.add("是否触发: " + triggered);
            if (caseId != null) s.add("立案ID: " + caseId);
            return s;
        }
    }
}
