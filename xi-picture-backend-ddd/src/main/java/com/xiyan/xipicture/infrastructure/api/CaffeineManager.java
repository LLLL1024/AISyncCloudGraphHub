package com.xiyan.xipicture.infrastructure.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CaffeineManager {

    /**
     * 本地缓存
     * 单独封装为一个类
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000L) // 最大 10000 条
            // 缓存 5 分钟后移除
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * 获取本地缓存操作对象
     */
    public Cache<String, String> getLocalCache() {
        return LOCAL_CACHE;
    }
}
