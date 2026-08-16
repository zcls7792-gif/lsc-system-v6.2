package com.lianshengtong.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认配置中心访问器实现：返回空字符串
 * <p>
 * 当容器中不存在其他 {@link ConfigCenterAccessor} 实现时启用，作为兜底方案。
 * 生产环境注入具体实现（如 NacosConfigCenterAccessor / JdbcConfigCenterAccessor）后会自动覆盖。
 * </p>
 *
 * @author lsc
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ConfigCenterAccessor.class)
public class StubConfigCenterAccessor implements ConfigCenterAccessor {

    @Override
    public String getOriginalValue(String configKey) {
        log.debug("[ConfigCenter][Stub] 读取配置原值 key={} (返回空占位)", configKey);
        return "";
    }
}
