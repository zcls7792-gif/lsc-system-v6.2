package com.lianshengtong.common.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LscTransactionTypeEnumTest {

    @Test
    void v73_enumValues_matchSpec() {
        // V7.3 spec 8.3 流水类型：1消费赠送入锁定 2订单抵扣 3退款退回 4退款回扣
        // 5到期作废 6推荐奖励入锁定 7每日释放 8风控冻结 9风控解冻
        assertEquals(1, LscTransactionTypeEnum.GRANT_LOCKED.getCode());
        assertEquals("消费赠送入锁定", LscTransactionTypeEnum.GRANT_LOCKED.getDesc());
        assertEquals(2, LscTransactionTypeEnum.ORDER_DEDUCT.getCode());
        assertEquals("订单抵扣", LscTransactionTypeEnum.ORDER_DEDUCT.getDesc());
        assertEquals(3, LscTransactionTypeEnum.REFUND_RETURN.getCode());
        assertEquals("退款退回", LscTransactionTypeEnum.REFUND_RETURN.getDesc());
        assertEquals(4, LscTransactionTypeEnum.REFUND_DEDUCT.getCode());
        assertEquals("退款回扣", LscTransactionTypeEnum.REFUND_DEDUCT.getDesc());
        assertEquals(5, LscTransactionTypeEnum.EXPIRE_WRITEOFF.getCode());
        assertEquals("到期作废", LscTransactionTypeEnum.EXPIRE_WRITEOFF.getDesc());
        assertEquals(6, LscTransactionTypeEnum.PROMOTION_REWARD_LOCKED.getCode());
        assertEquals("推荐奖励入锁定", LscTransactionTypeEnum.PROMOTION_REWARD_LOCKED.getDesc());
        assertEquals(7, LscTransactionTypeEnum.DAILY_RELEASE.getCode());
        assertEquals("每日释放", LscTransactionTypeEnum.DAILY_RELEASE.getDesc());
        assertEquals(8, LscTransactionTypeEnum.RISK_FREEZE.getCode());
        assertEquals("风控冻结", LscTransactionTypeEnum.RISK_FREEZE.getDesc());
        assertEquals(9, LscTransactionTypeEnum.RISK_UNFREEZE.getCode());
        assertEquals("风控解冻", LscTransactionTypeEnum.RISK_UNFREEZE.getDesc());
    }

    @Test
    void v73_doesNotContain_b2bOrWriteoff() {
        // V7.3 禁止 B2B 流转和商家核销，枚举不应包含这些值
        for (LscTransactionTypeEnum e : LscTransactionTypeEnum.values()) {
            assertNotEquals("B2B流转支付", e.getDesc());
            assertNotEquals("商家核销", e.getDesc());
            assertNotEquals("线下消费", e.getDesc());
        }
    }

    @Test
    void of_returnsCorrectEnum_forV73Code() {
        assertEquals(LscTransactionTypeEnum.GRANT_LOCKED, LscTransactionTypeEnum.of(1));
        assertEquals(LscTransactionTypeEnum.RISK_UNFREEZE, LscTransactionTypeEnum.of(9));
    }
}
