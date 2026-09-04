package com.lianshengtong.release.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认告警通道实现：仅记录 ERROR 日志。
 * <p>
 *   启用条件：{@code alert.channel=logging} 或者 {@code alert.channel} 未配置（matchIfMissing=true）。
 *   生产环境配置 {@code alert.channel=feishu} 后，{@link FeishuAlertChannel} 启用，本实现自动不注入。
 * </p>
 *
 * @author lsc
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "alert.channel", havingValue = "logging", matchIfMissing = true)
public class LoggingAlertChannel implements AlertChannel {

    @Override
    public void send(String receivers, String title, String content) {
        log.error("[ReleaseAlert][Logging] 接收人={} 标题={} 内容={}", receivers, title, content);
    }

    @Override
    public String name() {
        return "LOGGING";
    }
}
