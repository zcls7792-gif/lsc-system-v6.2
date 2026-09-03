package com.lianshengtong.promotion.contract;

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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * lsc-promotion-service 作为 consumer — 消费 lsc-user-service 发布的 /api/user/info 契约
 */
class PromotionConsumerUserStubTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static HttpClient HTTP;

    @RegisterExtension
    static final StubRunnerExtension REG = new StubRunnerExtension()
            .downloadStub("com.lianshengtong:lsc-user-service:+:stubs")
            .stubsMode(StubRunnerProperties.StubsMode.LOCAL);

    @BeforeAll
    static void setupClient() {
        HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private String baseUrl() {
        java.net.URL url = REG.findStubUrl("com.lianshengtong", "lsc-user-service");
        if (url == null) throw new IllegalStateException("user-service stub not started");
        return url.toString();
    }

    @Test
    @DisplayName("[契约消费] getUserInfo — 与 promotionUserClient R<Map<String,Object>> 声明一致")
    void getUserInfo_responseMatchesFeignContract() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(baseUrl() + "/api/user/info?userId=10001"))
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());

        R<Map<String, Object>> r = MAPPER.readValue(resp.body(),
                new TypeReference<R<Map<String, Object>>>() {});

        assertEquals(0, r.getCode());
        assertTrue(r.isSuccess());

        Map<String, Object> data = r.getData();
        assertNotNull(data);
        assertEquals(10001, ((Number) data.get("userId")).longValue());
        assertEquals(1, ((Number) data.get("isVerified")).intValue());
        assertEquals(99001L, ((Number) data.get("referrerId")).longValue());
        assertEquals("13800000001", data.get("mobile"));
        assertEquals(0, ((Number) data.get("userType")).intValue());
        assertEquals(1, ((Number) data.get("status")).intValue());
    }
}
