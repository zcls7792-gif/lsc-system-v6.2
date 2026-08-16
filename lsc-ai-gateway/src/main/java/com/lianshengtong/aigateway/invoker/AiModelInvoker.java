package com.lianshengtong.aigateway.invoker;

/**
 * AI 模型调用器抽象接口
 * <p>
 * 9 个 AI 能力（推荐/客服/画像/风控/仿真/地址比对/商品审核/B2B核验/释放预测）统一通过该接口接入真实模型。
 * 默认实现 {@link StubAiModelInvoker} 返回占位结果，生产环境通过注入
 * {@code OpenAiModelInvoker} / {@code DashScopeModelInvoker} 等具体实现替换。
 * </p>
 * <p>
 * 接入真实模型示例：
 * <pre>{@code
 * @Component
 * @ConditionalOnProperty(prefix = "ai.gateway.model", name = "provider", havingValue = "openai")
 * public class OpenAiModelInvoker implements AiModelInvoker {
 *     // 调用 OpenAI API
 * }
 * }</pre>
 * </p>
 *
 * @author lsc
 */
public interface AiModelInvoker {

    /**
     * 同步调用 AI 模型
     *
     * @param capability 能力标识（recommend/customerService/profile/risk/simulation/addressVerify/productReview/b2bVerify/releasePredict）
     * @param input      模型输入（结构化 JSON 字符串）
     * @return 模型原始输出（结构化 JSON 字符串）
     * @throws Exception 调用失败抛出异常，由调用方走熔断降级
     */
    String invoke(String capability, String input) throws Exception;

    /**
     * 模型提供方名称（用于日志区分）
     */
    default String providerName() {
        return getClass().getSimpleName();
    }
}
