package com.lianshengtong.order.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.result.R;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.contract.stubrunner.junit.StubRunnerExtension;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * lsc-order-service 作为 consumer — 消费 lsc-ledger-service 发布的 issue/pay/refund 契约
 * <p>
 * 使用 StubRunnerExtension (JUnit5 原生扩展)，完全不依赖 Spring Boot 上下文，
 * 绕开 Redisson/Nacos/Seata/Druid 等类路径 autoconfig 冲突。
 */
class OrderConsumerLedgerStubTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static HttpClient HTTP;

    @BeforeAll
    static void setupClient() {
        HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @RegisterExtension
    static final StubRunnerExtension REG = new StubRunnerExtension()
            .downloadStub("com.lianshengtong:lsc-ledger-service:+:stubs")
            .stubsMode(StubRunnerProperties.StubsMode.LOCAL);

    private String stubBaseUrl() {
        java.net.URL url = REG.findStubUrl("com.lianshengtong", "lsc-ledger-service");
        if (url == null) throw new IllegalStateException("lsc-ledger-service stub not started");
        return url.toString();
    }

    private R<Void> postJson(String path, Object body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .uri(URI.create(stubBaseUrl() + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), path + " statusCode=200");
        return MAPPER.readValue(resp.body(), new TypeReference<R<Void>>() {});
    }

    @Test
    @DisplayName("[契约消费] issueLsc — 与 orderLedgerClient#issueLsc 声明一致")
    void issueLsc_matchesFeignContract() throws Exception {
        LscLedgerOpDTO dto = LscLedgerOpDTO.builder()
                .userId(10001L).lockedDelta(5000L)
                .orderNo("ORD-20260901-00001")
                .idempotentKey("idem-issue-001").transactionType(1)
                .remark("消费发行-订单锁定")
                .build();
        R<Void> r = postJson("/api/ledger/issue", dto);
        assertEquals(0, r.getCode(), "issueLsc code=0");
        assertTrue(r.isSuccess());
    }

    @Test
    @DisplayName("[契约消费] payLsc — 与 orderLedgerClient#payLsc 声明一致")
    void payLsc_matchesFeignContract() throws Exception {
        LscLedgerOpDTO dto = LscLedgerOpDTO.builder()
                .userId(10001L).counterpartyId(20001L).availableDelta(3000L)
                .orderNo("ORD-20260901-00002")
                .idempotentKey("idem-pay-001").transactionType(3)
                .build();
        R<Void> r = postJson("/api/ledger/pay", dto);
        assertEquals(0, r.getCode(), "payLsc code=0");
        assertTrue(r.isSuccess());
    }

    @Test
    @DisplayName("[契约消费] refundLsc — 与 orderLedgerClient#refundLsc 声明一致")
    void refundLsc_matchesFeignContract() throws Exception {
        LscLedgerOpDTO dto = LscLedgerOpDTO.builder()
                .userId(10001L).counterpartyId(20001L).availableDelta(1500L)
                .orderNo("ORD-20260901-00003")
                .idempotentKey("idem-refund-001").transactionType(5)
                .build();
        R<Void> r = postJson("/api/ledger/refund", dto);
        assertEquals(0, r.getCode(), "refundLsc code=0");
        assertTrue(r.isSuccess());
    }
}
