package com.lianshengtong.admin.config;

/**
 * 配置中心访问器抽象接口
 * <p>
 * 从 Nacos / Apollo / 本地 release_config 表读取配置原值。
 * 默认实现 {@link StubConfigCenterAccessor} 返回空字符串，生产环境注入
 * {@code NacosConfigCenterAccessor} 等具体实现后自动覆盖。
 * </p>
 *
 * @author lsc
 */
public interface ConfigCenterAccessor {

    /**
     * 按 key 读取当前生效的配置原值
     *
     * @param configKey 配置 key（如 rate_max / k_min / alpha）
     * @return 当前值；不存在时返回空字符串（而非 null，便于历史值留空）
     */
    String getOriginalValue(String configKey);
}
