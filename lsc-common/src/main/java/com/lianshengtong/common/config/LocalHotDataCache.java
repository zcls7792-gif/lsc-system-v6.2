package com.lianshengtong.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;




public class LocalHotDataCache {

    private final Cache<String, Object> merchantCache;
    private final Cache<String, Object> productCache;
    private final Cache<String, Object> configCache;

    public LocalHotDataCache() {
        this.merchantCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
        this.productCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
        this.configCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    public Cache<String, Object> merchantCache() { return merchantCache; }
    public Cache<String, Object> productCache() { return productCache; }
    public Cache<String, Object> configCache() { return configCache; }

    public void clearAll() {
        merchantCache.invalidateAll();
        productCache.invalidateAll();
        configCache.invalidateAll();
    }

    public void clearMerchant(Long merchantId) {
        merchantCache.invalidate(String.valueOf(merchantId));
    }

    public void clearProduct(Long productId) {
        productCache.invalidate(String.valueOf(productId));
    }


    public LocalHotDataCache(Cache<String, Object> merchantCache, Cache<String, Object> productCache, Cache<String, Object> configCache) {
        this.merchantCache = merchantCache;
        this.productCache = productCache;
        this.configCache = configCache;
    }

    public Cache<String, Object> getMerchantCache() { return merchantCache; }
    public Cache<String, Object> getProductCache() { return productCache; }
    public Cache<String, Object> getConfigCache() { return configCache; }
}