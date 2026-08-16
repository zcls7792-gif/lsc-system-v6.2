package com.lianshengtong.aigateway.invoker;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AiModelInvoker 接口单元测试")
class AiModelInvokerTest {

    @Test
    @DisplayName("AiModelInvoker: 默认providerName返回类简名")
    void aiModelInvoker_defaultProviderName_returnsSimpleName() {
        AiModelInvoker invoker = new TestAiModelInvoker();
        assertEquals("TestAiModelInvoker", invoker.providerName());
    }

    @Test
    @DisplayName("AiModelInvoker: 自定义providerName覆盖默认实现")
    void aiModelInvoker_customProviderName_overridesDefault() {
        AiModelInvoker invoker = new AiModelInvoker() {
            @Override
            public String invoke(String capability, String input) throws Exception {
                return "ok";
            }

            @Override
            public String providerName() {
                return "DashScope-Max";
            }
        };
        assertEquals("DashScope-Max", invoker.providerName());
    }

    @Test
    @DisplayName("AiModelInvoker: invoke 正确传递capability和input")
    void aiModelInvoker_invoke_receivesCorrectParams() throws Exception {
        String[] captured = new String[2];
        AiModelInvoker invoker = new AiModelInvoker() {
            @Override
            public String invoke(String capability, String input) throws Exception {
                captured[0] = capability;
                captured[1] = input;
                return "{\"result\":\"ok\"}";
            }
        };

        String result = invoker.invoke("recommend", "{\"userId\":123}");
        assertEquals("recommend", captured[0]);
        assertEquals("{\"userId\":123}", captured[1]);
        assertEquals("{\"result\":\"ok\"}", result);
    }

    @Test
    @DisplayName("AiModelInvoker: invoke 支持所有能力标识")
    void aiModelInvoker_invoke_allCapabilities() throws Exception {
        String[] capabilities = {
                "recommend", "customerService", "profile",
                "risk", "simulation", "addressVerify",
                "productReview", "b2bVerify", "releasePredict"
        };

        for (String cap : capabilities) {
            final String currentCap = cap;
            AiModelInvoker invoker = new AiModelInvoker() {
                @Override
                public String invoke(String capability, String input) throws Exception {
                    if (!capability.equals(currentCap)) {
                        throw new AssertionError("期望能力: " + currentCap + " 实际: " + capability);
                    }
                    return "{\"cap\":\"" + capability + "\"}";
                }
            };
            String result = invoker.invoke(cap, "{}");
            assertTrue(result.contains(cap));
        }
    }

    @Test
    @DisplayName("AiModelInvoker: invoke 异常传播由调用方处理")
    void aiModelInvoker_invoke_exceptionPropagation() {
        AiModelInvoker invoker = new AiModelInvoker() {
            @Override
            public String invoke(String capability, String input) throws Exception {
                throw new Exception("模型调用失败: 超时");
            }
        };

        Exception ex = assertThrows(Exception.class,
                () -> invoker.invoke("risk", "{}"));
        assertTrue(ex.getMessage().contains("超时"));
    }

    @Test
    @DisplayName("AiModelInvoker: invoke 空input也能处理")
    void aiModelInvoker_invoke_emptyInput() throws Exception {
        AiModelInvoker invoker = new AiModelInvoker() {
            @Override
            public String invoke(String capability, String input) throws Exception {
                assertNotNull(capability);
                return "{}";
            }
        };
        assertDoesNotThrow(() -> invoker.invoke("recommend", ""));
    }

    @Test
    @DisplayName("AiModelInvoker: 匿名实现类providerName能正常返回")
    void aiModelInvoker_anonymousImpl_providerNameReturns() {
        AiModelInvoker invoker = new AiModelInvoker() {
            @Override
            public String invoke(String capability, String input) throws Exception {
                return "ok";
            }
        };
        String name = invoker.providerName();
        assertNotNull(name);
    }

    @Test
    @DisplayName("AiModelInvoker: 两个不同实现providerName不同")
    void aiModelInvoker_twoImpls_providerNamesDiffer() {
        AiModelInvoker invoker1 = new OpenAiModelInvoker();
        AiModelInvoker invoker2 = new StubAiModelInvoker();
        assertNotEquals(invoker1.providerName(), invoker2.providerName());
    }

    @Test
    @DisplayName("AiModelInvoker: invoke 大输入正确传递")
    void aiModelInvoker_invoke_largeInput() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"items\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(i).append(",\"name\":\"item").append(i).append("\"}");
        }
        sb.append("]}");
        String largeInput = sb.toString();

        AiModelInvoker invoker = new AiModelInvoker() {
            @Override
            public String invoke(String capability, String input) throws Exception {
                assertEquals(largeInput, input);
                return "{\"processed\":true}";
            }
        };
        String result = invoker.invoke("productReview", largeInput);
        assertEquals("{\"processed\":true}", result);
    }

    static class TestAiModelInvoker implements AiModelInvoker {
        @Override
        public String invoke(String capability, String input) throws Exception {
            return "{\"status\":\"ok\",\"capability\":\"" + capability + "\"}";
        }
    }

    static class OpenAiModelInvoker implements AiModelInvoker {
        @Override
        public String invoke(String capability, String input) throws Exception {
            return "{}";
        }
    }

    static class StubAiModelInvoker implements AiModelInvoker {
        @Override
        public String invoke(String capability, String input) throws Exception {
            return "{\"stub\":true}";
        }
    }
}