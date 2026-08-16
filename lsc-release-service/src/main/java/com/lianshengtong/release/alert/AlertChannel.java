package com.lianshengtong.release.alert;

/**
 * 告警通道抽象接口
 * <p>
 * 各告警通道（短信、钉钉、飞书、邮件等）实现该接口。
 * 通过 Spring {@code @ConditionalOnProperty} / Bean 注入机制支持可插拔。
 * 默认实现 {@link LoggingAlertChannel} 仅打印日志，生产环境可按需覆盖。
 * </p>
 *
 * @author lsc
 */
public interface AlertChannel {

    /**
     * 发送告警消息
     *
     * @param receivers 接收人列表（逗号分隔的 adminId 或手机号/钉钉userId/飞书userId）
     * @param title     告警标题
     * @param content   告警正文
     */
    void send(String receivers, String title, String content);

    /**
     * 通道名称（用于日志区分）
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
