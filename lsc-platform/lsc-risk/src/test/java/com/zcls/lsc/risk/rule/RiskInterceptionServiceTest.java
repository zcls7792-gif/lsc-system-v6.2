package com.zcls.lsc.risk.rule;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * RiskInterceptionService 单元测试。
 *
 * 验证口径：
 *  - ALERT 类规则命中 → triggered=false → 不阻断业务
 *  - REVIEW/FREEZE 类规则命中 → triggered=true → 抛 BENEFIT_BLOCKED 阻断
 */
class RiskInterceptionServiceTest {

    private RuleEngineService ruleEngine;
    private RiskInterceptionService interception;

    @BeforeEach
    void setUp() {
        ruleEngine = Mockito.mock(RuleEngineService.class);
        interception = new RiskInterceptionService(ruleEngine);
    }

    @Test
    void alertRuleDoesNotBlockOrderCreation() {
        // ALERT 命中，triggered=false（不阻断）
        when(ruleEngine.evaluateAndEnforce(anyLong(), anyMap(), anyBoolean()))
                .thenReturn(new RuleEngineService.EvaluationResult(1, 10, false, null));

        assertDoesNotThrow(() -> interception.checkOrderCreation(1001L, 1_500_000L, "C"));
    }

    @Test
    void reviewRuleBlocksOrderCreation() {
        // REVIEW 命中，triggered=true（阻断）
        when(ruleEngine.evaluateAndEnforce(anyLong(), anyMap(), anyBoolean()))
                .thenReturn(new RuleEngineService.EvaluationResult(1, 20, true, 9001L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interception.checkOrderCreation(1001L, 6_000_000L, "B"));
        assertEquals(ErrorCode.BENEFIT_BLOCKED, ex.errorCode());
    }

    @Test
    void reviewRuleBlocksRefund() {
        when(ruleEngine.evaluateAndEnforce(anyLong(), anyMap(), anyBoolean()))
                .thenReturn(new RuleEngineService.EvaluationResult(1, 15, true, 9003L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interception.checkRefund(1001L, 2001L, 600_000L, "QTY"));
        assertEquals(ErrorCode.BENEFIT_BLOCKED, ex.errorCode());
    }

    @Test
    void alertRuleDoesNotBlockGrant() {
        when(ruleEngine.evaluateAndEnforce(anyLong(), anyMap(), anyBoolean()))
                .thenReturn(new RuleEngineService.EvaluationResult(1, 5, false, null));

        assertDoesNotThrow(() -> interception.checkGrant(1001L, 2001L));
    }

    @Test
    void noRuleMatchedDoesNotBlock() {
        when(ruleEngine.evaluateAndEnforce(anyLong(), anyMap(), anyBoolean()))
                .thenReturn(new RuleEngineService.EvaluationResult(0, 0, false, null));

        assertDoesNotThrow(() -> {
            interception.checkOrderCreation(1001L, 100L, "C");
            interception.checkRefund(1001L, 2001L, 100L, "QTY");
            interception.checkGrant(1001L, 2001L);
        });
    }
}
