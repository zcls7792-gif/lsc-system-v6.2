package com.lianshengtong.ledger.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V7.3 账户/流水实体冻结字段存在性测试。
 */
class LscAccountFrozenTest {

    @Test
    void hasTotalFrozenField() {
        // V7.3 spec 8.2: total_frozen 为 bigint 默认 0(冻结余额)
        Field field = assertDoesNotThrow(() -> LscAccount.class.getDeclaredField("totalFrozen"));
        assertNotNull(field);
    }

    @Test
    void builderSetsTotalFrozen() {
        LscAccount acc = LscAccount.builder()
                .userId(1L)
                .totalLocked(100L)
                .totalAvailable(50L)
                .totalFrozen(20L)
                .version(1)
                .build();
        assertEquals(20L, acc.getTotalFrozen());
    }

    @Test
    void lscTransactionHasBeforeAndAfterFrozen() {
        // V7.3 流水快照：before_frozen / after_frozen
        assertDoesNotThrow(() -> LscTransaction.class.getDeclaredField("beforeFrozen"));
        assertDoesNotThrow(() -> LscTransaction.class.getDeclaredField("afterFrozen"));
    }
}
