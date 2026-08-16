package com.lianshengtong.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志安全工具类 - 防止日志注入攻击
 * <p>
 * 日志注入是指攻击者通过在输入中插入特殊字符（如换行符、ANSI转义序列）
 * 来污染日志文件的攻击方式。此类提供日志输入的清洗和校验功能。
 * </p>
 */
public final class LogSanitizer {

    private static final Logger log = LoggerFactory.getLogger(LogSanitizer.class);

    private LogSanitizer() {
    }

    private static final String[] DANGEROUS_PATTERNS = {
            "\r", "\n", "\r\n",
            "\u001b", "\u001c", "\u001d", "\u001e",
            "\u0000", "\u0001", "\u0002", "\u0003",
            "\u0004", "\u0005", "\u0006", "\u0007",
            "\u000b", "\u000c", "\u000e", "\u000f",
            "\u0010", "\u0011", "\u0012", "\u0013",
            "\u0014", "\u0015", "\u0016", "\u0017",
            "\u0018", "\u0019", "\u001a", "\u001b",
            "\u001c", "\u001d", "\u001e", "\u001f"
    };

    private static final java.util.regex.Pattern HTML_TAG_PATTERN =
            java.util.regex.Pattern.compile("<[^>]*>");

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String sanitized = input;
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = sanitized.replace("\r\n", "_");
        sanitized = sanitized.replace("\r", "_");
        sanitized = sanitized.replace("\n", "_");
        for (String pattern : DANGEROUS_PATTERNS) {
            if (!pattern.equals("\r") && !pattern.equals("\n") && !pattern.equals("\r\n")) {
                sanitized = sanitized.replace(pattern, "");
            }
        }
        sanitized = sanitized.replaceAll("_{2,}", "_");
        sanitized = sanitized.replaceAll("[ \\t]{2,}", " ");
        sanitized = sanitized.replaceAll(" _ |_ ", "_");
        return sanitized.trim();
    }

    public static String sanitizeForLog(String input, int maxLength) {
        String sanitized = sanitize(input);
        if (sanitized != null && sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength) + "...(truncated)";
        }
        return sanitized;
    }

    public static boolean containsInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (String pattern : DANGEROUS_PATTERNS) {
            if (input.contains(pattern)) {
                log.warn("[LogSanitizer] 检测到潜在日志注入内容: pattern={}",
                        pattern.replace("\r", "\\r").replace("\n", "\\n"));
                return true;
            }
        }
        return false;
    }

    public static String sanitizeUserInput(String input) {
        if (input == null) {
            return null;
        }
        if (containsInjection(input)) {
            log.warn("[LogSanitizer] 用户输入包含危险字符，已清洗");
        }
        return sanitize(input);
    }

    public static String maskSensitive(String value, String type) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        switch (type) {
            case "phone":
                return value.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
            case "email":
                int atIndex = value.indexOf('@');
                if (atIndex > 1) {
                    return value.charAt(0) + "***" + value.substring(atIndex);
                }
                return value;
            case "idcard":
                if (value.length() >= 10) {
                    return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
                }
                return value;
            case "token":
                if (value.length() > 8) {
                    return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
                }
                return "****";
            case "password":
            case "secret":
            case "key":
                return "****";
            default:
                if (value.length() > 4) {
                    return value.charAt(0) + "***" + value.charAt(value.length() - 1);
                }
                return "****";
        }
    }
}
