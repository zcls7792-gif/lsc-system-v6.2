package com.lianshengtong.common.security;

/**
 * @deprecated 请使用 {@link com.lianshengtong.common.utils.LogSanitizer}
 * 此类为向后兼容的代理实现，已转发至完善的 LogSanitizer
 */
@Deprecated
public class LogSanitizer {

    /**
     * 清除日志注入危险字符
     * @param input 原始输入
     * @return 清洗后的字符串
     */
    public static String sanitize(String input) {
        return com.lianshengtong.common.utils.LogSanitizer.sanitize(input);
    }
}
