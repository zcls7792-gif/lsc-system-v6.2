package com.lianshengtong.release.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认告警通道实现：仅记录 ERROR 日志
 * <p>
 * 当容器中不存在其他 {@link AlertChannel} 实现时启用，作为兜底方案。
 * 生产环境注入具体的告警通道（如 DingtalkAlertChannel / FeishuAlertChannel）后会自动覆盖。
 * </p>
 *
 * @author lsc
 */
@Slf4j
@Component
@ConditionalOnMissingBean(AlertChannel.class)
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
