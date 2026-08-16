package com.lianshengtong.aigateway.invoker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StubAiModelInvoker 单元测试")
class StubAiModelInvokerTest {

    private StubAiModelInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new StubAiModelInvoker();
    }

    @Test
    @DisplayName("invoke: 任意能力和输入返回stub JSON")
    void invoke_anyCapabilityAndInput_returnsStubJson() throws Exception {
        String result = invoker.invoke("recommend", "{\"userId\":1}");
        assertEquals("{\"stub\":true}", result);
    }

    @Test
    @DisplayName("invoke: 客服能力返回stub JSON")
    void invoke_customerService_returnsStubJson() throws Exception {
        String result = invoker.invoke("customerService", "{\"query\":\"hello\"}");
        assertEquals("{\"stub\":true}", result);
    }

    @Test
    @DisplayName("invoke: 风控能力返回stub JSON")
    void invoke_riskControl_returnsStubJson() throws Exception {
        String result = invoker.invoke("risk", "{\"orderId\":123}");
        assertEquals("{\"stub\":true}", result);
    }

    @Test
    @DisplayName("invoke: null输入正常处理不抛异常")
    void invoke_nullInput_handledGracefully() throws Exception {
        assertDoesNotThrow(() -> invoker.invoke("recommend", null));
        String result = invoker.invoke("recommend", null);
        assertEquals("{\"stub\":true}", result);
    }

    @Test
    @DisplayName("invoke: 空字符串输入正常处理")
    void invoke_emptyStringInput_handled() throws Exception {
        assertDoesNotThrow(() -> invoker.invoke("recommend", ""));
        String result = invoker.invoke("recommend", "");
        assertEquals("{\"stub\":true}", result);
    }

    @Test
    @DisplayName("invoke: 所有9种能力均返回stub JSON")
    void invoke_allCapabilities_returnStubJson() throws Exception {
        String[] capabilities = {"recommend", "customerService", "profile",
                "risk", "simulation", "addressVerify",
                "productReview", "b2bVerify", "releasePredict"};
        for (String cap : capabilities) {
            String result = invoker.invoke(cap, "{\"test\":1}");
            assertEquals("{\"stub\":true}", result);
        }
    }

    @Test
    @DisplayName("providerName: 返回STUB")
    void providerName_returnsSTUB() {
        assertEquals("STUB", invoker.providerName());
    }

    @Test
    @DisplayName("AiModelInvoker接口: invoke方法符合契约")
    void aiModelInvoker_invoke_contractFulfilled() throws Exception {
        AiModelInvoker modelInvoker = invoker;
        String result = modelInvoker.invoke("recommend", "{\"data\":\"test\"}");
        assertNotNull(result);
        assertEquals("{\"stub\":true}", result);
    }

    @Test
    @DisplayName("AiModelInvoker接口: providerName默认实现使用类简单名")
    void aiModelInvoker_providerName_defaultUsesSimpleName() {
        AiModelInvoker modelInvoker = invoker;
        String name = modelInvoker.providerName();
        assertEquals("STUB", name);
    }

    @Test
    @DisplayName("invoke: 大输入内容也不影响返回")
    void invoke_largeInput_returnsStubJson() throws Exception {
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeInput.append('x');
        }
        String result = invoker.invoke("recommend", largeInput.toString());
        assertEquals("{\"stub\":true}", result);
    }

    @Test
    @DisplayName("invoke: 特殊字符输入正常处理")
    void invoke_specialCharsInput_handled() throws Exception {
        String result = invoker.invoke("recommend", "{\"key\":\"val'ue\\\"with\\nSpecial\"}");
        assertEquals("{\"stub\":true}", result);
    }
}