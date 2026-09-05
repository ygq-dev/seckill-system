package com.ygq.seckill.util;

import com.google.common.util.concurrent.RateLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Component
public class LocalRateLimiterService {

    // 用户限流：每个用户每秒 10 次（防止脚本刷接口）
    private Cache<Long, RateLimiter> userLimiters;
    // 商品限流：每个商品每秒 2000 次（集群总限流需靠 Nginx，这里是单机防护）
    private Cache<Long, RateLimiter> goodsLimiters;

    @Value("${seckill.user.qps}")
    private double USER_QPS;

    @Value("${seckill.goods.qps}")
    private double GOODS_QPS;

    @PostConstruct
    public void init() {
        // Caffeine 缓存：1分钟未访问则自动过期，避免内存泄漏
        userLimiters = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();

        goodsLimiters = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    /**
     * 尝试获取用户令牌（非阻塞，立即返回）
     */
    public boolean tryAcquireUser(Long userId) {
        RateLimiter limiter = userLimiters.get(userId, key -> RateLimiter.create(USER_QPS));
        return limiter.tryAcquire();
    }

    /**
     * 尝试获取商品令牌（非阻塞，立即返回）
     */
    public boolean tryAcquireGoods(Long goodsId) {
        RateLimiter limiter = goodsLimiters.get(goodsId, key -> RateLimiter.create(GOODS_QPS));
        return limiter.tryAcquire();
    }
}