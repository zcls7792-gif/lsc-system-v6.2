package com.lianshengtong.ledger.contract;

import com.lianshengtong.ledger.controller.LscLedgerController;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.service.LscLedgerService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Spring Cloud Contract 契约测试基底类 — lsc-ledger-service (Provider)
 * <p>
 * 契约插件会根据 Groovy DSL 自动生成子类 ContractVerifierTest，
 * 每个契约场景对应一个 test_* 方法，统一通过 RestAssured MockMvc 发起 HTTP，
 * 并自动匹配 request/response body/header 的断言。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class LedgerContractBase {

    @Mock
    private LscLedgerService ledgerService;

    @InjectMocks
    private LscLedgerController ledgerController;

    @BeforeEach
    public void setup() {
        // --- issue 契约 ---
        when(ledgerService.issueLsc(eq(10001L), eq(5000L), anyString()))
                .thenReturn(accountOf(10001L, 5000L, 0L, 1));

        // --- pay 契约 (消费者 10001 可用 3000 -> 商家 20001) ---
        when(ledgerService.payLsc(eq(10001L), eq(20001L), eq(3000L), anyString()))
                .thenReturn(accountOf(10001L, 0L, 2000L, 2));

        // --- refund 契约 (消费者 10001 可用 1500 入账) ---
        when(ledgerService.refundLsc(eq(10001L), eq(1500L), anyString()))
                .thenReturn(accountOf(10001L, 3500L, 3500L, 3));

        RestAssuredMockMvc.standaloneSetup(ledgerController);
    }

    private static LscAccount accountOf(Long userId, Long locked, Long available, Integer version) {
        return LscAccount.builder()
                .userId(userId)
                .totalLocked(locked)
                .totalAvailable(available)
                .version(version)
                .updatedAt(LocalDateTime.of(2026, 9, 1,
                        10, version * 5, 0))
                .build();
    }
}
