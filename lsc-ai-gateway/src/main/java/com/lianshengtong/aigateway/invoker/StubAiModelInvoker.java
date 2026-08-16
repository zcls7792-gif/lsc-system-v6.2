package com.lianshengtong.aigateway.invoker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认 AI 模型调用器实现：返回占位结果
 * <p>
 * 当容器中不存在其他 {@link AiModelInvoker} 实现时启用，作为兜底方案。
 * 生产环境注入具体的模型实现（如 OpenAiModelInvoker / DashScopeModelInvoker）后会自动覆盖。
 * </p>
 *
 * @author lsc
 */
@Slf4j
@Component
@ConditionalOnMissingBean(AiModelInvoker.class)
public class StubAiModelInvoker implements AiModelInvoker {

    @Override
    public String invoke(String capability, String input) throws Exception {
        log.info("[AiModelInvoker][Stub] capability={} inputLen={} 返回占位结果", capability,
                input == null ? 0 : input.length());
        // 返回最小合法 JSON，由各能力自行解析
        return "{\"stub\":true}";
    }

    @Override
    public String providerName() {
        return "STUB";
    }
}
