package com.lianshengtong.common.security;

import com.lianshengtong.common.exception.SecurityOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public final class SqlInjectionGuard {

    private static final Logger log = LoggerFactory.getLogger(SqlInjectionGuard.class);

    private SqlInjectionGuard() {
    }

    private static final String[] SQL_INJECTION_PATTERNS = {
            "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|EXEC|EXECUTE|UNION|GRANT|REVOKE|TRUNCATE)\\b)",
            "(?i)(--|;--|;|/\\*|\\*/|xp_|sp_)",
            "(?i)(\\bxp_cmdshell\\b|\\bxp_services\\b|\\bxp_regread\\b)",
            "(?i)(0x[0-9a-fA-F]+)",
            "(?i)(\\bSLEEP\\s*\\(|\\bBENCHMARK\\s*\\(|\\bWAITFOR\\s+DELAY\\b)",
            "(?i)(\\bOR\\s+1\\s*=\\s*1\\b|\\bAND\\s+1\\s*=\\s*1\\b)",
            "(?i)(\\bxp_\\w+\\b|\\bxp_\\w+\\b)",
            "(\\bCHAR\\s*\\(|\\bCONCAT\\s*\\(|\\bGROUP_CONCAT\\s*\\()"
    };

    private static final Pattern[] COMPILED_PATTERNS;

    static {
        COMPILED_PATTERNS = new Pattern[SQL_INJECTION_PATTERNS.length];
        for (int i = 0; i < SQL_INJECTION_PATTERNS.length; i++) {
            COMPILED_PATTERNS[i] = Pattern.compile(SQL_INJECTION_PATTERNS[i]);
        }
    }

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private static final Pattern VALID_TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private static final Pattern VALID_COLUMN_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private static final Pattern VALID_ORDER_BY = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_,\\s]*$");

    public static boolean containsInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (Pattern pattern : COMPILED_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    public static void validateUserInput(String input, String fieldName) {
        if (input == null || input.isEmpty()) {
            return;
        }
        if (containsInjection(input)) {
            log.warn("[SqlInjectionGuard] SQL注入检测: field={}, input={}", fieldName, input);
            throw new SecurityOperationException("输入包含潜在的危险字符，请检查输入内容");
        }
    }

    public static void validateUserInput(String input) {
        validateUserInput(input, "unknown");
    }

    public static String sanitizeForQuery(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[;\"'\\\\\\-\\/\\*\\%\\_]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    public static void validateIdentifier(String identifier) {
        if (identifier == null || !VALID_IDENTIFIER.matcher(identifier).matches()) {
            log.warn("[SqlInjectionGuard] 非法标识符: {}", identifier);
            throw new SecurityOperationException("标识符格式不合法");
        }
    }

    private static final java.util.Set<String> SQL_KEYWORDS = java.util.Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
            "EXEC", "EXECUTE", "UNION", "GRANT", "REVOKE", "TRUNCATE",
            "WHERE", "FROM", "JOIN", "TABLE", "INDEX", "VIEW", "DATABASE",
            "ORDER", "GROUP", "HAVING", "LIMIT", "OFFSET", "SET", "INTO",
            "VALUES", "DEFAULT", "CHECK", "CONSTRAINT", "PRIMARY", "FOREIGN",
            "REFERENCES", "UNIQUE", "NOT", "NULL", "AND", "OR", "IN", "IS",
            "LIKE", "BETWEEN", "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END",
            "AS", "ON", "INNER", "OUTER", "LEFT", "RIGHT", "FULL", "CROSS",
            "ASC", "DESC", "BY", "DISTINCT", "ALL", "ANY", "SOME"
    );

    public static void validateTableName(String tableName) {
        if (tableName == null || !VALID_TABLE_NAME.matcher(tableName).matches()) {
            log.warn("[SqlInjectionGuard] 非法表名: {}", tableName);
            throw new SecurityOperationException("表名格式不合法");
        }
        if (SQL_KEYWORDS.contains(tableName.toUpperCase())) {
            log.warn("[SqlInjectionGuard] 表名使用了SQL关键字: {}", tableName);
            throw new SecurityOperationException("表名使用了SQL关键字");
        }
    }

    public static void validateColumnName(String columnName) {
        if (columnName == null || !VALID_COLUMN_NAME.matcher(columnName).matches()) {
            log.warn("[SqlInjectionGuard] 非法列名: {}", columnName);
            throw new SecurityOperationException("列名格式不合法");
        }
    }

    public static void validateOrderBy(String orderBy) {
        if (orderBy == null || !VALID_ORDER_BY.matcher(orderBy).matches()) {
            log.warn("[SqlInjectionGuard] 非法排序字段: {}", orderBy);
            throw new SecurityOperationException("排序字段格式不合法");
        }
    }

    public static String validateAndSanitize(String input, String fieldName, int maxLength) {
        if (input == null) {
            return null;
        }
        validateUserInput(input, fieldName);
        String sanitized = sanitizeForQuery(input);
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized;
    }
}
