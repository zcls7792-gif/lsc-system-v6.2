package com.lianshengtong.mall.contract;

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
 * lsc-mall-service 作为 consumer — 消费 lsc-order-service 发布的 createMall 契约
 */
class MallConsumerOrderStubTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static HttpClient HTTP;

    @RegisterExtension
    static final StubRunnerExtension REG = new StubRunnerExtension()
            .downloadStub("com.lianshengtong:lsc-order-service:+:stubs")
            .stubsMode(StubRunnerProperties.StubsMode.LOCAL);

    @BeforeAll
    static void setupClient() {
        HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private String baseUrl() {
        java.net.URL url = REG.findStubUrl("com.lianshengtong", "lsc-order-service");
        if (url == null) throw new IllegalStateException("order-service stub not started");
        return url.toString();
    }

    @Test
    @DisplayName("[契约消费] createMallOrder — 与 mallOrderClient R<String> 返回一致")
    void createMallOrder_matchesFeignContract() throws IOException, InterruptedException {
        String qs = "productId=101&merchantId=20001&consumerId=10001&lscAmount=5000&rmbAmount=19.90&totalPrice=69.90";
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(baseUrl() + "/api/order/create-mall?" + qs))
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        R<String> r = MAPPER.readValue(resp.body(), new TypeReference<R<String>>() {});
        assertEquals(0, r.getCode());
        assertTrue(r.isSuccess());
        assertEquals("MALL-20260901-00001", r.getData());
    }
}
