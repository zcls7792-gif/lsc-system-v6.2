#!/usr/bin/env python3
import os, re

def ensure_dir(p):
    d = os.path.dirname(p)
    os.makedirs(d, exist_ok=True)

# 1) LscLedgerController WebMvcTest
p = "lsc-ledger-service/src/test/java/com/lianshengtong/ledger/controller/LscLedgerControllerWebMvcTest.java"
ensure_dir(p)
with open(p, "w") as f: f.write("""package com.lianshengtong.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.service.LscLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试(G2 — ledger controller slice)：
 * 验证 HTTP 契约 (路径/方法/Content-Type/幂等)、Jackson 序列化、MockMvc 完整 web 层 stack。
 */
@WebMvcTest(controllers = LscLedgerController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class LscLedgerControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean LscLedgerService svc;

    private static LscAccount account(Long userId, Long locked, Long avail, int version) {
        LscAccount a = new LscAccount();
        a.setUserId(userId); a.setTotalLocked(locked); a.setTotalAvailable(avail);
        a.setVersion(version); a.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 10, version * 5, 0));
        return a;
    }

    @BeforeEach void reset() { /* MockBean auto reset */ }

    @Nested
    @DisplayName("POST /api/ledger/issue — LSC 发行 (integrated web stack)")
    class IssueApi {
        @Test @DisplayName("issue success → HTTP 200 with updated account JSON")
        void success() throws Exception {
            when(svc.issueLsc(10001L, 5000L, "idem-issue-001")).thenReturn(account(10001L, 5000L, 0L, 1));
            LscLedgerOpDTO dto = new LscLedgerOpDTO();
            dto.setUserId(10001L); dto.setLockedDelta(5000L); dto.setOrderNo("ORD-1");
            dto.setTransactionType(LscTransactionTypeEnum.CONSUME_ISSUE.getCode());
            dto.setIdempotentKey("idem-issue-001");
            mvc.perform(post("/api/ledger/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(10001))
                    .andExpect(jsonPath("$.data.totalLocked").value(5000));
            verify(svc).issueLsc(10001L, 5000L, "idem-issue-001");
        }

        @Test @DisplayName("issue 无 userId → 不调用 service（body 参数校验层 / 空值处理）")
        void missingUserId() throws Exception {
            LscLedgerOpDTO dto = new LscLedgerOpDTO();
            dto.setLockedDelta(5000L);
            mvc.perform(post("/api/ledger/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(dto)))
                    .andExpect(status().is4xxClientError());
            verify(svc, never()).issueLsc(anyLong(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("POST /api/ledger/refund — 退款")
    class RefundApi {
        @Test @DisplayName("refund success → 返回可用余额增量值")
        void success() throws Exception {
            when(svc.refundLsc(10001L, 1500L, "idem-refund-001")).thenReturn(account(10001L, 3500L, 3500L, 3));
            LscLedgerOpDTO dto = new LscLedgerOpDTO();
            dto.setUserId(10001L); dto.setAvailableDelta(1500L);
            dto.setTransactionType(5); dto.setIdempotentKey("idem-refund-001");
            mvc.perform(post("/api/ledger/refund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalAvailable").value(3500))
                    .andExpect(jsonPath("$.data.version").value(3));
            verify(svc).refundLsc(10001L, 1500L, "idem-refund-001");
        }
    }

    @Nested
    @DisplayName("GET /api/ledger/account/{userId}")
    class AccountApi {
        @Test @DisplayName("查用户账户：正常 200 + account JSON")
        void success() throws Exception {
            when(svc.getAccount(10001L)).thenReturn(account(10001L, 0L, 5000L, 2));
            mvc.perform(get("/api/ledger/account/10001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(10001))
                    .andExpect(jsonPath("$.data.totalAvailable").value(5000));
        }

        @Test @DisplayName("路径变量非法字符串 → 400 或 404（Spring MVC 参数转换失败处理）")
        void nonNumericPath() throws Exception {
            mvc.perform(get("/api/ledger/account/abc"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("GET /api/ledger/daily-summary & /api/ledger/locked-summary")
    class SummaryApi {
        @Test @DisplayName("daily summary 返回 200 + 统计 map")
        void daily() throws Exception {
            when(svc.dailySummary(any())).thenReturn(Map.of(
                    "totalIssue", 5000L, "totalPay", 3000L, "count", 3
            ));
            mvc.perform(get("/api/ledger/daily-summary")
                            .param("start", "2026-09-01")
                            .param("end", "2026-09-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalIssue").value(5000));
        }

        @Test @DisplayName("locked summary 返回 200")
        void locked() throws Exception {
            when(svc.lockedSummary()).thenReturn(Map.of(
                    "totalLocked", 100_000L, "users", 25
            ));
            mvc.perform(get("/api/ledger/locked-summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalLocked").value(100000));
        }
    }
}
""")
print(f"wrote {p}")
