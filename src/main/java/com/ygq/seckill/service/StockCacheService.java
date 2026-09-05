package com.ygq.seckill.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class StockCacheService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 本地缓存（Caffeine），加速读取
    private final Cache<Long, Boolean> localCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(3, TimeUnit.SECONDS)  // 短过期，保证最终一致
            .build();

    public boolean isStockEmpty(Long goodsId) {
        // 先读本地缓存
        Boolean flag = localCache.getIfPresent(goodsId);
        if (flag != null) {
            return flag;
        }
        // 再读 Redis
        String key = "seckill:over:" + goodsId;
        Boolean exists = redisTemplate.hasKey(key);
        localCache.put(goodsId, exists);
        return exists;
    }

    public void markStockEmpty(Long goodsId) {
        String key = "seckill:over:" + goodsId;
        redisTemplate.opsForValue().setIfAbsent(key, "1", 1, TimeUnit.HOURS);
        localCache.put(goodsId, true);
    }

    public void clearStockEmpty(Long goodsId) {
        String key = "seckill:over:" + goodsId;
        redisTemplate.delete(key);
        localCache.invalidate(goodsId);
    }
}