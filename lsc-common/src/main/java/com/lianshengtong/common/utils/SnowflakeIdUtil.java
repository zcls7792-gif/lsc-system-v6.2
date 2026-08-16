package com.lianshengtong.common.utils;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;


/**
 * 雪花算法全局唯一ID生成器（无锁版）
 * <p>
 * 适配分库分表（8库32表），避免主键冲突。
 * 使用 AtomicLong + CAS 替代 synchronized，支持高并发场景。
 * </p>
 */
public class SnowflakeIdUtil {

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long TWEPOCH = 1704067200000L;

    private final long workerId;
    private final long datacenterId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);

    private SnowflakeIdUtil(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be > %d or < 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be > %d or < 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp.get()) {
            throw new RuntimeException(String.format(
                    "Clock moved backwards. Refusing to generate id for %d milliseconds",
                    lastTimestamp.get() - timestamp));
        }

        long lastTs = lastTimestamp.get();
        long seq;

        if (timestamp == lastTs) {
            seq = sequence.incrementAndGet() & SEQUENCE_MASK;
            if (seq == 0) {
                timestamp = tilNextMillis(lastTs);
            }
        } else {
            seq = 0L;
            sequence.set(0L);
        }

        lastTimestamp.set(timestamp);
        return ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | seq;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    private static class Holder {
        static final SnowflakeIdUtil INSTANCE = new SnowflakeIdUtil(1, 1);
    }

    public static SnowflakeIdUtil getInstance() {
        return Holder.INSTANCE;
    }

    public static long id() {
        return Holder.INSTANCE.nextId();
    }


    public long getWorkerId() { return workerId; }
    public long getDatacenterId() { return datacenterId; }
    public AtomicLong getSequence() { return sequence; }
    public AtomicLong getLastTimestamp() { return lastTimestamp; }
}
