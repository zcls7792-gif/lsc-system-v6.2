package com.zcls.lsc.risk.rule;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 风控规则引擎拦截点服务（单点入口）。
 *
 * 遵循经验：所有业务操作的风控拦截统一走本服务，避免规则判定逻辑分散在各模块。
 *
 * 拦截场景：
 *  - checkOrderCreation : 下单前
 *  - checkRefund        : 退款申请前
 *  - checkGrant         : 权益发放前
 *
 * 触发动作语义：当规则引擎返回 triggered=true 时，本服务抛出 BENEFIT_BLOCKED 异常，
 * 业务事务回滚；同时 RuleEngineService 已自动立案。
 */
@Service
public class RiskInterceptionService {

    private final RuleEngineService ruleEngineService;

    public RiskInterceptionService(RuleEngineService ruleEngineService) {
        this.ruleEngineService = ruleEngineService;
    }

    /**
     * 下单前风控拦截。
     *
     * @param userId     买家ID
     * @param goodsCent  商品金额(分)
     * @param buyerType  买家类型 C/B
     */
    public void checkOrderCreation(long userId, long goodsCent, String buyerType) {
        Map<String, Object> context = new HashMap<>();
        context.put("scene", "ORDER_CREATE");
        context.put("goods_cent", goodsCent);
        context.put("buyer_type", buyerType);
        context.put("user_id", userId);

        RuleEngineService.EvaluationResult result =
                ruleEngineService.evaluateAndEnforce(userId, context, true);
        if (result.triggered()) {
            throw new BusinessException(ErrorCode.BENEFIT_BLOCKED,
                    "risk control blocked order creation: score=" + result.totalScore());
        }
    }

    /**
     * 退款申请前风控拦截。
     *
     * @param userId     申请用户ID
     * @param orderId    原订单ID
     * @param refundCent 退款金额(分)
     * @param refundKind 退款类型 QTY/PRICE_DIFF
     */
    public void checkRefund(long userId, long orderId, long refundCent, String refundKind) {
        Map<String, Object> context = new HashMap<>();
        context.put("scene", "REFUND");
        context.put("order_id", orderId);
        context.put("refund_cent", refundCent);
        context.put("refund_kind", refundKind);
        context.put("user_id", userId);

        RuleEngineService.EvaluationResult result =
                ruleEngineService.evaluateAndEnforce(userId, context, true);
        if (result.triggered()) {
            throw new BusinessException(ErrorCode.BENEFIT_BLOCKED,
                    "risk control blocked refund: score=" + result.totalScore());
        }
    }

    /**
     * 权益发放前风控拦截。
     *
     * @param userId  接收用户ID
     * @param orderId 来源订单ID
     */
    public void checkGrant(long userId, long orderId) {
        Map<String, Object> context = new HashMap<>();
        context.put("scene", "GRANT");
        context.put("order_id", orderId);
        context.put("user_id", userId);

        RuleEngineService.EvaluationResult result =
                ruleEngineService.evaluateAndEnforce(userId, context, true);
        if (result.triggered()) {
            throw new BusinessException(ErrorCode.BENEFIT_BLOCKED,
                    "risk control blocked grant: score=" + result.totalScore());
        }
    }
}
