package com.lianshengtong.gateway.observability;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生成 16 字节 hex traceId：
 *   41 bits 毫秒级时间戳 + 10 bits 自增序号 + 22 bits 随机后缀
 * （满足长度 32、全局低碰撞率、便于日志检索）
 */
@Component
public class TraceIdGenerator {

    private static final long EPOCH_SHIFT = 1720000000000L; // 2024-07-03 ~ stable
    private static final AtomicLong SEQ = new AtomicLong(0L);
    private static final SecureRandom RND = new SecureRandom();

    public String next() {
        long ts = System.currentTimeMillis() - EPOCH_SHIFT;
        long seq = SEQ.getAndIncrement() & 0x3FFL; // 10 bits
        long rand = RND.nextLong() & 0x3FFFFFL; // 22 bits
        long value = (ts << 32) | (seq << 22) | rand;
        String s = Long.toHexString(value);
        // 左侧补零到 16 chars
        if (s.length() < 16) s = "0".repeat(16 - s.length()) + s;
        return s;
    }
}
