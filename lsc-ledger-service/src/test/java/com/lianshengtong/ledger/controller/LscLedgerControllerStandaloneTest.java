package com.lianshengtong.ledger.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscTransaction;
import com.lianshengtong.ledger.service.LscLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * G2 集成：账本 Controller 的 HTTP 契约 + Jackson + Controller 内部 resolveAmount() 逻辑。
 * <p>standalone MockMvc：无 Nacos/Seata/Redisson 依赖。</p>
 */
@ExtendWith(MockitoExtension.class)
class LscLedgerControllerStandaloneTest {

    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock LscLedgerService svc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LscLedgerController(svc)).build();
    }

    private LscAccount acc(Long userId, long locked, long avail, int version) {
        LscAccount a = new LscAccount();
        a.setUserId(userId); a.setTotalLocked(locked); a.setTotalAvailable(avail);
        a.setVersion(version); a.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        return a;
    }

    private LscLedgerOpDTO op(Long userId, long locked, long avail, String orderNo, Long cp) {
        LscLedgerOpDTO o = new LscLedgerOpDTO();
        o.setUserId(userId);
        if (locked != 0L) o.setLockedDelta(locked);
        if (avail != 0L) o.setAvailableDelta(avail);
        o.setOrderNo(orderNo); o.setCounterpartyId(cp);
        return o;
    }

    @Nested @DisplayName("POST /api/ledger/issue")
    class Issue {
        @Test @DisplayName("lockedDelta=5000 → issueLsc(userId, 5000, orderNo)")
        void useLockedDeltaAsAmount() throws Exception {
            when(svc.issueLsc(10001L, 5000L, "ORD-1")).thenReturn(acc(10001L, 5000L, 0L, 1));
            mvc.perform(post("/api/ledger/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(op(10001L, 5000L, 0L, "ORD-1", null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalLocked").value(5000));
            verify(svc).issueLsc(10001L, 5000L, "ORD-1");
        }
    }

    @Nested @DisplayName("POST /api/ledger/release")
    class Release {
        @Test @DisplayName("availableDelta=1000 → releaseLsc(userId, 1000, orderNo)")
        void useAvailableDelta() throws Exception {
            when(svc.releaseLsc(10001L, 1000L, "ORD-2")).thenReturn(acc(10001L, 4000L, 1000L, 2));
            mvc.perform(post("/api/ledger/release")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(op(10001L, 0L, 1000L, "ORD-2", null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalAvailable").value(1000));
            verify(svc).releaseLsc(10001L, 1000L, "ORD-2");
        }
    }

    @Nested @DisplayName("POST /api/ledger/pay")
    class Pay {
        @Test @DisplayName("需要 counterpartyId → 调 payLsc(consumer, merchant, amount, orderNo)")
        void twoParty() throws Exception {
            when(svc.payLsc(10001L, 20001L, 500L, "ORD-3")).thenReturn(acc(10001L, 0L, 500L, 3));
            mvc.perform(post("/api/ledger/pay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(op(10001L, 0L, 500L, "ORD-3", 20001L))))
                    .andExpect(status().isOk());
            verify(svc).payLsc(10001L, 20001L, 500L, "ORD-3");
        }
    }

    @Nested @DisplayName("POST /api/ledger/refund")
    class Refund {
        @Test @DisplayName("退款回滚路径：可用余额增量入账")
        void refund() throws Exception {
            when(svc.refundLsc(10001L, 500L, "ORD-3")).thenReturn(acc(10001L, 0L, 1000L, 4));
            mvc.perform(post("/api/ledger/refund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(op(10001L, 0L, 500L, "ORD-3", null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.version").value(4));
            verify(svc).refundLsc(10001L, 500L, "ORD-3");
        }
    }

    @Nested @DisplayName("GET /api/ledger/account/{userId}")
    class Account {
        @Test @DisplayName("返回 LscAccount，Jackson 能正确序列化 BigDecimal/date")
        void ok() throws Exception {
            when(svc.getBalance(10001L)).thenReturn(acc(10001L, 0L, 1234L, 3));
            mvc.perform(get("/api/ledger/account/10001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(10001))
                    .andExpect(jsonPath("$.data.totalAvailable").value(1234));
        }
    }

    @Nested @DisplayName("GET /api/ledger/daily-summary?date=...&types=...")
    class DailySummary {
        @Test @DisplayName("日期 + 类型参数传递；空 types → service 收到 null")
        void dateAndNullTypes() throws Exception {
            when(svc.dailySummary(eq(LocalDate.of(2026, 9, 1)), isNull()))
                    .thenReturn(Map.of("totalIssue", 5000L, "count", 3));
            mvc.perform(get("/api/ledger/daily-summary").param("date", "2026-09-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalIssue").value(5000));
        }

        @Test @DisplayName("types=1,3,5 → 转成 List<Integer> [1,3,5]")
        void dateAndTypes() throws Exception {
            when(svc.dailySummary(eq(LocalDate.of(2026, 9, 1)), eq(List.of(1, 3, 5))))
                    .thenReturn(Map.of("totalIssue", 0L));
            mvc.perform(get("/api/ledger/daily-summary")
                            .param("date", "2026-09-01").param("types", "1,3,5"))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("GET /api/ledger/transactions")
    class TxList {
        @Test @DisplayName("默认分页 page=1 size=20；返回 IPage 序列化")
        void ok() throws Exception {
            LscTransaction tx = new LscTransaction();
            tx.setId(1L); tx.setUserId(10001L); tx.setType(1);
            IPage<LscTransaction> p = new Page<LscTransaction>(1, 20)
                    .setRecords(List.of(tx)).setTotal(1L);
            when(svc.transactionList(null, 1, 20, null, null, null, null)).thenReturn(p);
            mvc.perform(get("/api/ledger/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }
}
