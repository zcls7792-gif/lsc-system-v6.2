package com.lianshengtong.api.util;

/**
 * 雪花算法全局ID生成器（V6.2 附录 P0-3）
 * 64位结构：1位符号位 + 41位时间戳 + 10位机器ID + 12位序列号
 * 单机每毫秒可生成 4096 个唯一ID
 */
public class SnowflakeIdGenerator {

    // 起始时间戳（2026-01-01 00:00:00 UTC）
    private static final long EPOCH = 1735689600000L;

    // 机器ID所占位数
    private static final long MACHINE_ID_BITS = 10L;
    // 序列号所占位数
    private static final long SEQUENCE_BITS = 12L;

    // 机器ID最大值
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    // 序列号最大值
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    // 机器ID左移位数
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    // 时间戳左移位数
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    private static volatile SnowflakeIdGenerator instance;

    private SnowflakeIdGenerator(long machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                    "machineId 超出范围 [0," + MAX_MACHINE_ID + "]");
        }
        this.machineId = machineId;
    }

    /**
     * 单例获取（默认机器ID=1）
     */
    public static SnowflakeIdGenerator getInstance() {
        return getInstance(1L);
    }

    public static SnowflakeIdGenerator getInstance(long machineId) {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    instance = new SnowflakeIdGenerator(machineId);
                }
            }
        }
        return instance;
    }

    /**
     * 生成下一个全局唯一ID（线程安全）
     */
    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            // 时钟回拨：等待到下一毫秒
            currentTimestamp = waitForNextMillis(lastTimestamp);
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成字符串形式的ID
     */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
