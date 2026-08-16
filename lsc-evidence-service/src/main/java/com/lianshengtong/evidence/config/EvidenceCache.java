package com.lianshengtong.evidence.config;

/**
 * 存证服务本地缓存接口
 * <p>
 * 抽象本地缓存的核心操作，便于不同实现（自研/ Caffeine）替换。
 * </p>
 */
public interface EvidenceCache {

    void put(String key, Object value);

    void put(String key, Object value, long ttlMs);

    <T> T get(String key);

    boolean containsKey(String key);

    void remove(String key);

    int size();

    void clear();

    long getHitCount();

    long getMissCount();

    long getEvictCount();

    long getHitRate();
}
