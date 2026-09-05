package com.ygq.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ygq.seckill.exception.GlobalException;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;
@Slf4j
@Service
public class GoodsCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private ObjectMapper objectMapper;

    // 本地缓存：秒杀期间商品信息只读，永不过期
    private Cache<Long, GoodsVo> localCache;

    @PostConstruct
    public void init() {
        localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .build();  // 永不过期，依赖应用重启刷新
        log.info("GoodsCacheService 本地缓存初始化完成");
    }

    /**
     * 获取商品详情（秒杀专用 - 快速失败版）
     * 核心原则：绝不阻塞等待，读不到就快速失败
     */
    public GoodsVo getGoodsVoById(Long goodsId) {
        // 1. 本地缓存（纳秒级，无锁竞争）
        GoodsVo goods = localCache.getIfPresent(goodsId);
        if (goods != null) {
            return goods;
        }

        // 2. Redis 缓存
        String key = "goods:detail:" + goodsId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            try {
                if (obj instanceof String) {
                    String json = (String) obj;
                    if (StringUtils.hasText(json)) {
                        goods = objectMapper.readValue(json, GoodsVo.class);
                    }
                } else if (obj instanceof GoodsVo) {
                    goods = (GoodsVo) obj;
                }
                if (goods != null) {
                    localCache.put(goodsId, goods);
                    return goods;
                }
            } catch (Exception e) {
                log.error("Redis 商品数据反序列化失败: goodsId={}", goodsId, e);
                redisTemplate.delete(key);
            }
        }

        // 3. 兜底：数据库查询（极端情况，不带锁）
        log.warn("缓存未命中，走数据库兜底: goodsId={}", goodsId);
        goods = goodsService.getGoodsVoFromDb(goodsId);
        if (goods == null) {
            throw new GlobalException(CodeMsg.GOODS_NOT_EXIST);
        }

        // 回填缓存
        try {
            String json = objectMapper.writeValueAsString(goods);
            redisTemplate.opsForValue().set(key, json, 1, TimeUnit.HOURS);
            localCache.put(goodsId, goods);
        } catch (Exception e) {
            log.error("缓存写入失败: goodsId={}", goodsId, e);
        }

        return goods;
    }

    /**
     * 预热本地缓存
     */
    public void putLocalCache(Long goodsId, GoodsVo goods) {
        if (goods != null) {
            localCache.put(goodsId, goods);
            log.debug("本地缓存预热: goodsId={}", goodsId);
        }
    }

    /**
     * 预热 Redis 缓存
     */
    public void putRedisCache(Long goodsId, GoodsVo goods) {
        try {
            String key = "goods:detail:" + goodsId;
            String json = objectMapper.writeValueAsString(goods);
            redisTemplate.opsForValue().set(key, json, 1, TimeUnit.HOURS);
            log.debug("Redis 缓存预热: goodsId={}", goodsId);
        } catch (Exception e) {
            log.error("Redis 预热失败: goodsId={}", goodsId, e);
        }
    }
}