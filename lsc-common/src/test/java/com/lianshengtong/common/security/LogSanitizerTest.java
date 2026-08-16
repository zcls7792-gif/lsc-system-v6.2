package com.lianshengtong.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogSanitizer日志注入防护测试
 * 覆盖率提升目标: 83.3% → 95%+
 */
class LogSanitizerTest {

    @Nested
    @DisplayName("sanitize 基础清洗")
    class SanitizeTests {

        @Test
        @DisplayName("null输入返回null")
        void nullInputReturnsNull() {
            assertNull(LogSanitizer.sanitize(null));
        }

        @Test
        @DisplayName("正常字符串不被修改")
        void normalStringUnchanged() {
            assertEquals("Hello World", LogSanitizer.sanitize("Hello World"));
        }

        @Test
        @DisplayName("移除HTML标签")
        void removesHtmlTags() {
            assertEquals("Hello World", LogSanitizer.sanitize("<script>alert(1)</script>Hello World"));
        }

        @Test
        @DisplayName("移除换行符")
        void removesNewlines() {
            assertEquals("line1_line2", LogSanitizer.sanitize("line1\nline2"));
        }

        @Test
        @DisplayName("移除回车符")
        void removesCarriageReturn() {
            assertEquals("line1_line2", LogSanitizer.sanitize("line1\rline2"));
        }

        @Test
        @DisplayName("移除CRLF组合")
        void removesCrlf() {
            assertEquals("line1_line2", LogSanitizer.sanitize("line1\r\nline2"));
        }

        @Test
        @DisplayName("移除多个HTML标签")
        void removesMultipleTags() {
            assertEquals("text", LogSanitizer.sanitize("<div><p>text</p></div>"));
        }

        @Test
        @DisplayName("组合攻击 - XSS+日志注入")
        void combinedXssAndLogInjection() {
            String malicious = "<script>alert('XSS')</script>\nUser input: 123\r";
            String result = LogSanitizer.sanitize(malicious);
            assertFalse(result.contains("<script>"));
            assertFalse(result.contains("\n"));
            assertFalse(result.contains("\r"));
        }

        @Test
        @DisplayName("前后空白处理")
        void trimsWhitespace() {
            assertEquals("content", LogSanitizer.sanitize("  content  "));
        }

        @Test
        @DisplayName("空字符串处理")
        void emptyStringHandled() {
            assertEquals("", LogSanitizer.sanitize(""));
        }
    }

    @Nested
    @DisplayName("安全场景测试")
    class SecurityScenarioTests {

        @Test
        @DisplayName("模拟用户输入含HTML脚本")
        void userInputWithHtmlScript() {
            String input = "用户输入: <img src=x onerror=alert(1)>";
            String result = LogSanitizer.sanitize(input);
            assertFalse(result.contains("<img"));
            assertFalse(result.contains("onerror"));
        }

        @Test
        @DisplayName("模拟登录日志注入")
        void loginLogInjection() {
            String input = "admin\n[2025-01-01] Login successful for root";
            String result = LogSanitizer.sanitize(input);
            assertFalse(result.contains("\n"));
        }

        @Test
        @DisplayName("SQL注入标签防护")
        void sqlInjectionInTag() {
            String input = "Robert'); DROP TABLE students;--";
            String result = LogSanitizer.sanitize("<b>" + input + "</b>");
            assertFalse(result.contains("<b>"));
            assertFalse(result.contains("</b>"));
            assertTrue(result.contains("DROP TABLE"));
        }

        @Test
        @DisplayName("Nginx日志注入防护")
        void nginxLogInjection() {
            String input = "GET /admin%0d%0aAccess: admin HTTP/1.1";
            String result = LogSanitizer.sanitize(input);
            assertFalse(result.contains("\r"));
            assertFalse(result.contains("\n"));
        }

        @Test
        @DisplayName("控制字符注入防护")
        void controlCharacterInjection() {
            String input = "normal\u0000data";
            String result = LogSanitizer.sanitize(input);
            assertFalse(result.contains("\u0000"));
        }

        @Test
        @DisplayName("Unicode绕过尝试")
        void unicodeBypassAttempt() {
            // 使用Java转义序列代替Unicode转义序列，避免编译器解析问题
            String input = "test\r\n<script>alert(1)</script>";
            String result = LogSanitizer.sanitize(input);
            assertFalse(result.contains("\r"));
            assertFalse(result.contains("<script>"));
        }
    }

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTests {

        @Test
        @DisplayName("大量数据清洗")
        void largeDataSanitization() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("<script>x</script>\n");
            }
            String result = LogSanitizer.sanitize(sb.toString());
            assertNotNull(result);
            assertFalse(result.contains("<script>"));
            assertFalse(result.contains("\n"));
        }

        @Test
        @DisplayName("仅含HTML标签的输入")
        void onlyHtmlTags() {
            String input = "<html><body><div><p><span></span></p></div></body></html>";
            String result = LogSanitizer.sanitize(input);
            assertEquals("", result);
        }

        @Test
        @DisplayName("混合内容")
        void mixedContent() {
            String input = "用户名<input name='user'>密码\r\n";
            String result = LogSanitizer.sanitize(input);
            assertFalse(result.contains("<input"));
            assertFalse(result.contains("\r"));
            assertFalse(result.contains("\n"));
        }
    }
}