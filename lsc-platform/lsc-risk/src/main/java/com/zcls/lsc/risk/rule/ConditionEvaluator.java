package com.zcls.lsc.risk.rule;

import com.zcls.lsc.risk.enums.RiskEnums.ConditionOp;

import java.util.List;
import java.util.Map;

/**
 * 条件评估器：单条条件 field op value 对 context 的判定。
 *
 * 判定口径（语义冻结）：
 *  - 数值型 value 按 double 比较（EQ/NE/GT/GTE/LT/LTE）
 *  - IN/NOT_IN 的 value 为数组，判断 context 值是否在集合内
 *  - CONTAINS 判断 context 值（字符串）是否包含 value 子串
 *  - context 中无对应 field 视为不满足
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    /**
     * 评估单条条件。
     */
    public static boolean evaluate(Map<String, Object> condition, Map<String, Object> context) {
        String field = (String) condition.get("field");
        String opStr = (String) condition.get("op");
        Object value = condition.get("value");

        if (field == null || opStr == null) return false;
        Object ctxValue = context.get(field);
        if (ctxValue == null) return false;

        ConditionOp op;
        try {
            op = ConditionOp.valueOf(opStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        switch (op) {
            case EQ:
                return compareAsDouble(ctxValue, value) == 0;
            case NE:
                return compareAsDouble(ctxValue, value) != 0;
            case GT:
                return compareAsDouble(ctxValue, value) > 0;
            case GTE:
                return compareAsDouble(ctxValue, value) >= 0;
            case LT:
                return compareAsDouble(ctxValue, value) < 0;
            case LTE:
                return compareAsDouble(ctxValue, value) <= 0;
            case IN:
                return value instanceof List && ((List<?>) value).contains(ctxValue);
            case NOT_IN:
                return !(value instanceof List) || !((List<?>) value).contains(ctxValue);
            case CONTAINS:
                return ctxValue.toString().contains(value.toString());
            default:
                return false;
        }
    }

    private static int compareAsDouble(Object a, Object b) {
        try {
            double da = Double.parseDouble(a.toString());
            double db = Double.parseDouble(b.toString());
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.toString().compareTo(b.toString());
        }
    }
}
