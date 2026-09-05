package com.ygq.seckill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.seckill.entity.SeckillGoods;
import com.ygq.seckill.exception.OptimisticLockException;
import com.ygq.seckill.mapper.GoodsMapper;
import com.ygq.seckill.mapper.SeckillGoodsMapper;
import com.ygq.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GoodsService {

    @Autowired
    private GoodsMapper goodsMapper;
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    // 查询商品列表（带秒杀信息）
    public List<GoodsVo> listGoodsVo() {
        String key = "goods:list";
        Object cached = redisTemplate.opsForValue().get(key);
        List<GoodsVo> list = null;

        if (cached != null) {
            if (cached instanceof List) {
                List<?> rawList = (List<?>) cached;
                if (!rawList.isEmpty()) {
                    Object first = rawList.get(0);
                    if (first instanceof GoodsVo) {
                        list = (List<GoodsVo>) rawList;
                    } else if (first instanceof LinkedHashMap) {
                        try {
                            list = objectMapper.convertValue(rawList, new TypeReference<List<GoodsVo>>() {});
                            // 修复缓存，用正确类型覆盖
                            redisTemplate.opsForValue().set(key, list, 60, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            // 转换失败，忽略缓存，走 DB
                            list = null;
                        }
                    }
                } else {
                    // 空列表
                    list = new ArrayList<>();
                }
            }
        }

        if (list == null) {
            list = goodsMapper.selectGoodsVoList();
            redisTemplate.opsForValue().set(key, list, 60, TimeUnit.SECONDS);
        }

        return list;
    }

    public GoodsVo getGoodsVoById(Long goodsId) {
        String key = "goods:detail:" + goodsId;
        // 1. 从Redis获取
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached instanceof String) {
                String json = (String) cached;
                if (json.isEmpty()) {
                    return null;
                }
                try {
                    return objectMapper.readValue(json, GoodsVo.class);
                } catch (Exception e) {
                    // 反序列化失败，忽略并继续查DB
                }
            } else if (cached instanceof GoodsVo) {
                return (GoodsVo) cached;
            }
        }
        // 2. 缓存未命中，加互斥锁
        String lockKey = "lock:goods:" + goodsId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        try {
            if (Boolean.TRUE.equals(locked)) {
                GoodsVo vo = goodsMapper.selectGoodsVoById(goodsId);
                if (vo == null) {
                    redisTemplate.opsForValue().set(key, "", 30, TimeUnit.SECONDS);
                    return null;
                }
                // 写入Redis，无TTL（逻辑永不过期，由后台定时刷新）
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo));
                return vo;
            } else {
                // 未获得锁，休眠后重试
                Thread.sleep(50);
                return getGoodsVoById(goodsId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return goodsMapper.selectGoodsVoById(goodsId);
        } catch (Exception e) {
            return goodsMapper.selectGoodsVoById(goodsId);
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                redisTemplate.delete(lockKey);
            }
        }
    }


    public void reduceStockWithRetry(SeckillGoods seckillGoods) throws OptimisticLockException {
        int retryTimes = 3;
        for (int i = 0; i < retryTimes; i++) {
            int version = seckillGoodsMapper.getVersionByGoodsId(seckillGoods.getGoodsId());
            seckillGoods.setVersion(version);
            int update = seckillGoodsMapper.reduceStockByVersion(seckillGoods.getId(), seckillGoods.getVersion());
            if (update > 0) {
                return; // 成功
            }
            // 短暂休眠避免死循环
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        }
        throw new OptimisticLockException("乐观锁更新失败，重试后仍失败");
    }

    public void increaseStock(Long goodsId, int count) {
        // 数据库回增（可选，因为乐观锁重试会麻烦，简单起见直接 update）
        seckillGoodsMapper.increaseStock(goodsId, count);
        // Redis 回增
        redisTemplate.opsForValue().increment("stock:" + goodsId, count);
    }

    /**
     * 直接扣减数据库库存（无乐观锁，无条件版本号）
     * 由于 Redis 已预扣，此方法只做最终一致性保证
     */
    public void reduceStockDirect(Long seckillId) throws OptimisticLockException {
        int update = seckillGoodsMapper.reduceStockDirect(seckillId);
        if (update == 0) {
            // 库存为0或记录不存在，抛出异常
            throw new OptimisticLockException("库存不足或记录不存在");
        }
    }

    public GoodsVo getGoodsVoFromDb(Long goodsId) {
        return goodsMapper.selectGoodsVoById(goodsId);
    }
}
