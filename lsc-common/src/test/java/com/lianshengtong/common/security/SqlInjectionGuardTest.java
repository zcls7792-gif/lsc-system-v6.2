package com.lianshengtong.common.security;

import com.lianshengtong.common.exception.SecurityOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SQL 注入防护测试")
class SqlInjectionGuardTest {

    @Nested
    @DisplayName("注入检测 - SQL关键字")
    class SqlKeywordDetection {

        @Test
        @DisplayName("检测 SELECT 关键字")
        void detectSelectKeyword() {
            assertTrue(SqlInjectionGuard.containsInjection("1' OR '1'='1' UNION SELECT * FROM users--"));
        }

        @Test
        @DisplayName("检测 DROP TABLE")
        void detectDropTable() {
            assertTrue(SqlInjectionGuard.containsInjection("Robert'); DROP TABLE students;--"));
        }

        @Test
        @DisplayName("检测 INSERT 注入")
        void detectInsertInjection() {
            assertTrue(SqlInjectionGuard.containsInjection("'); INSERT INTO admin VALUES(1,'hacked');--"));
        }

        @Test
        @DisplayName("检测 UPDATE 注入")
        void detectUpdateInjection() {
            assertTrue(SqlInjectionGuard.containsInjection("1; UPDATE users SET role='admin' WHERE 1=1--"));
        }

        @Test
        @DisplayName("检测 DELETE 注入")
        void detectDeleteInjection() {
            assertTrue(SqlInjectionGuard.containsInjection("1; DELETE FROM audit_log--"));
        }
    }

    @Nested
    @DisplayName("注入检测 - 注释与特殊符号")
    class CommentDetection {

        @Test
        @DisplayName("检测双横线注释")
        void detectDoubleDash() {
            assertTrue(SqlInjectionGuard.containsInjection("admin'--"));
        }

        @Test
        @DisplayName("检测分号")
        void detectSemicolon() {
            assertTrue(SqlInjectionGuard.containsInjection("1'; DROP TABLE users;"));
        }

        @Test
        @DisplayName("检测注释块")
        void detectCommentBlock() {
            assertTrue(SqlInjectionGuard.containsInjection("1' /* malicious */ OR 1=1 --"));
        }
    }

    @Nested
    @DisplayName("注入检测 - 时间盲注")
    class TimeBasedInjection {

        @Test
        @DisplayName("检测 SLEEP 函数")
        void detectSleep() {
            assertTrue(SqlInjectionGuard.containsInjection("1' AND SLEEP(5)--"));
        }

        @Test
        @DisplayName("检测 BENCHMARK 函数")
        void detectBenchmark() {
            assertTrue(SqlInjectionGuard.containsInjection("1' AND BENCHMARK(1000000, SHA1('test'))--"));
        }

        @Test
        @DisplayName("检测 WAITFOR DELAY")
        void detectWaitforDelay() {
            assertTrue(SqlInjectionGuard.containsInjection("1'; WAITFOR DELAY '0:0:5'--"));
        }
    }

    @Nested
    @DisplayName("注入检测 - 编码绕过")
    class EncodedInjection {

        @Test
        @DisplayName("检测十六进制编码")
        void detectHexEncoding() {
            assertTrue(SqlInjectionGuard.containsInjection("0x73656c656374"));
        }

        @Test
        @DisplayName("检测 CHAR 函数")
        void detectCharFunction() {
            assertTrue(SqlInjectionGuard.containsInjection("CHAR(39) OR 1=1"));
        }
    }

    @Nested
    @DisplayName("安全输入 - 合法输入不误报")
    class SafeInputs {

        @Test
        @DisplayName("普通字符串")
        void normalString() {
            assertFalse(SqlInjectionGuard.containsInjection("hello world"));
        }

        @Test
        @DisplayName("用户名")
        void normalUsername() {
            assertFalse(SqlInjectionGuard.containsInjection("user_123"));
        }

        @Test
        @DisplayName("邮箱地址")
        void normalEmail() {
            assertFalse(SqlInjectionGuard.containsInjection("user@example.com"));
        }

        @Test
        @DisplayName("电话号码")
        void normalPhone() {
            assertFalse(SqlInjectionGuard.containsInjection("13800138000"));
        }

        @Test
        @DisplayName("包含 SQL 单词但非注入")
        void sqlWordInNormalText() {
            assertFalse(SqlInjectionGuard.containsInjection("selected_item"));
        }

        @Test
        @DisplayName("空值处理")
        void nullAndEmpty() {
            assertFalse(SqlInjectionGuard.containsInjection(null));
            assertFalse(SqlInjectionGuard.containsInjection(""));
        }
    }

    @Nested
    @DisplayName("输入校验")
    class InputValidation {

        @Test
        @DisplayName("合法输入不抛异常")
        void validInputNoException() {
            assertDoesNotThrow(() -> SqlInjectionGuard.validateUserInput("normal_input", "field"));
        }

        @Test
        @DisplayName("注入输入抛异常")
        void maliciousInputThrowsException() {
            assertThrows(SecurityOperationException.class,
                    () -> SqlInjectionGuard.validateUserInput("1' OR 1=1--", "username"));
        }

        @Test
        @DisplayName("空输入不抛异常")
        void nullInputNoException() {
            assertDoesNotThrow(() -> SqlInjectionGuard.validateUserInput(null, "field"));
            assertDoesNotThrow(() -> SqlInjectionGuard.validateUserInput("", "field"));
        }
    }

    @Nested
    @DisplayName("标识符校验")
    class IdentifierValidation {

        @Test
        @DisplayName("合法表名")
        void validTableName() {
            assertDoesNotThrow(() -> SqlInjectionGuard.validateTableName("user_orders"));
        }

        @Test
        @DisplayName("非法表名 - SQL关键字")
        void invalidTableName_keyword() {
            assertThrows(SecurityOperationException.class,
                    () -> SqlInjectionGuard.validateTableName("SELECT"));
        }

        @Test
        @DisplayName("非法表名 - 含特殊字符")
        void invalidTableName_specialChars() {
            assertThrows(SecurityOperationException.class,
                    () -> SqlInjectionGuard.validateTableName("user; DROP TABLE"));
        }

        @Test
        @DisplayName("合法列名")
        void validColumnName() {
            assertDoesNotThrow(() -> SqlInjectionGuard.validateColumnName("created_at"));
        }

        @Test
        @DisplayName("非法列名")
        void invalidColumnName() {
            assertThrows(SecurityOperationException.class,
                    () -> SqlInjectionGuard.validateColumnName("col OR 1=1"));
        }

        @Test
        @DisplayName("合法排序字段")
        void validOrderBy() {
            assertDoesNotThrow(() -> SqlInjectionGuard.validateOrderBy("created_at DESC"));
        }

        @Test
        @DisplayName("非法排序字段")
        void invalidOrderBy() {
            assertThrows(SecurityOperationException.class,
                    () -> SqlInjectionGuard.validateOrderBy("1; DROP TABLE"));
        }
    }

    @Nested
    @DisplayName("输入清洗")
    class Sanitization {

        @Test
        @DisplayName("移除危险字符")
        void removeDangerousChars() {
            String result = SqlInjectionGuard.sanitizeForQuery("test;--'\"\\");
            assertFalse(result.contains(";"));
            assertFalse(result.contains("--"));
            assertFalse(result.contains("'"));
        }

        @Test
        @DisplayName("null 安全处理")
        void nullSafeHandling() {
            assertNull(SqlInjectionGuard.sanitizeForQuery(null));
        }

        @Test
        @DisplayName("超长截断")
        void truncateLongInput() {
            String result = SqlInjectionGuard.validateAndSanitize("normal_input", "field", 5);
            assertEquals(5, result.length());
        }
    }
}
