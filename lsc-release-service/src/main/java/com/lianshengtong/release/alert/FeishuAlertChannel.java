package com.lianshengtong.release.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 飞书自定义机器人告警通道。
 * <p>
 * 启用条件：application.yml 中配置 {@code alert.channel=feishu}，
 * 同时设置 {@code alert.feishu.webhook-url}（含 secret 的完整 webhook）。
 * </p>
 *
 * <h3>示例</h3>
 * <pre>
 * alert:
 *   channel: feishu
 *   feishu:
 *     webhook-url: https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
 * </pre>
 *
 * <h3>安全性</h3>
 * 生产环境 webhook-url 应通过 Nacos Secret 或环境变量注入，禁止明文提交 Git。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "alert.channel", havingValue = "feishu")
@RequiredArgsConstructor
public class FeishuAlertChannel implements AlertChannel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${alert.feishu.webhook-url:}")
    private String webhookUrl;

    @Override
    public void send(String receivers, String title, String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[Alert][Feishu] webhook-url empty, fall back to logging. receivers={} title={}", receivers, title);
            return;
        }

        // Feishu 富文本 message format (interactive card 可选; 这里用 text 最简兼容)
        String atBlock = (receivers != null && !receivers.isBlank())
                ? String.format("\n<at id=%s></at>", receivers.replace(",", "</at> <at id="))
                : "";
        Map<String, Object> payload = Map.of(
                "msg_type", "text",
                "content", Map.of(
                        "text", "【LSC灰度告警】" + title + "\n" + content + atBlock
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);

        try {
            String resp = restTemplate.postForObject(webhookUrl, req, String.class);
            log.info("[Alert][Feishu] sent. receivers={} title={} resp={}", receivers, title, resp);
        } catch (RestClientException ex) {
            log.error("[Alert][Feishu] send failed: title={} receivers={} ex={}", title, receivers, ex.getMessage());
        }
    }

    @Override
    public String name() { return "FEISHU"; }
}
