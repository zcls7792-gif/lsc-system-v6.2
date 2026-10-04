package com.zcls.lsc.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 业务日期口径（1.2）：
 *  - 业务日期 Asia/Shanghai，business_date 表示北京时间自然日。
 *  - 数据库时间保存 UTC 毫秒时间戳或统一 UTC 的 datetime(3)。
 *  - 所有跨日边界均按此口径测试。
 */
public final class BusinessDate {

    /** Asia/Shanghai 时区（固定 +08:00）。 */
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final ZoneId UTC = ZoneOffset.UTC;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private BusinessDate() {}

    /** 取当前北京时间自然日。 */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(Objects.requireNonNull(clock, "clock").withZone(SHANGHAI));
    }

    public static LocalDate today() {
        return today(Clock.systemUTC());
    }

    /** 把任意 Instant 折算为北京时间自然日。 */
    public static LocalDate toBusinessDate(Instant instant) {
        return LocalDate.ofInstant(instant, SHANGHAI);
    }

    /** 次日（5.1：以 grant_business_date+1 为 first_release_date）。 */
    public static LocalDate next(LocalDate businessDate) {
        return businessDate.plusDays(1L);
    }

    public static String format(LocalDate date) {
        return DATE_FORMAT.format(date);
    }

    /** UTC datetime(3) 毫秒精度，落库口径。 */
    public static String formatUtcMillis(LocalDateTime utcDateTime) {
        return DATETIME_MILLIS.format(utcDateTime);
    }

    /** 当前 UTC 毫秒时间戳。 */
    public static long currentUtcMillis() {
        return Instant.now().toEpochMilli();
    }
}
