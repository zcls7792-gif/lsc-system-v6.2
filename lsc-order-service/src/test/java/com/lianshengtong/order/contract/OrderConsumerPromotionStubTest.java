package com.lianshengtong.order.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * lsc-order-service 作为 consumer — 消费 lsc-promotion-service 发布的 first-order-notify 契约
 */
class OrderConsumerPromotionStubTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static HttpClient HTTP;

    @RegisterExtension
    static final StubRunnerExtension REG = new StubRunnerExtension()
            .downloadStub("com.lianshengtong:lsc-promotion-service:+:stubs")
            .stubsMode(StubRunnerProperties.StubsMode.LOCAL);

    @BeforeAll
    static void setupClient() {
        HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private String baseUrl() {
        java.net.URL url = REG.findStubUrl("com.lianshengtong", "lsc-promotion-service");
        if (url == null) throw new IllegalStateException("promotion stub not started");
        return url.toString();
    }

    @Test
    @DisplayName("[契约消费] notifyFirstOrder — 与 orderPromotionClient 声明一致")
    void notifyFirstOrder_matchesFeignContract() throws IOException, InterruptedException {
        String qs = "consumerId=10001&orderNo=ORD-20260901-00100&orderAmount=168.00&orderStatus=2&refundAmount=0.00";
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(baseUrl() + "/api/promotion/first-order-notify?" + qs))
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        R<Void> r = MAPPER.readValue(resp.body(), new TypeReference<R<Void>>() {});
        assertEquals(0, r.getCode());
        assertTrue(r.isSuccess());
        assertEquals("success", r.getMessage());
    }
}
