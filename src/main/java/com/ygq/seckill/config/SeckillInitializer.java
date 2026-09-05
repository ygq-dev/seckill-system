package com.ygq.seckill.config;

import com.ygq.seckill.service.GoodsCacheService;
import com.ygq.seckill.service.GoodsService;
import com.ygq.seckill.service.StockCacheService;
import com.ygq.seckill.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
@Slf4j
@Component
public class SeckillInitializer implements InitializingBean {

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private GoodsCacheService goodsCacheService;

    /**
     * 使用 AtomicBoolean 结合 CAS 操作，保证内存标记的原子性
     */
    private Map<Long, AtomicBoolean> localOverMap = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() {
        List<GoodsVo> list = goodsService.listGoodsVo();
        if (list == null || list.isEmpty()) {
            log.warn("没有商品数据需要预热");
            return;
        }

        for (GoodsVo goods : list) {
            Long goodsId = goods.getId();

            // 1. 预热库存到 Redis
            redisTemplate.opsForValue().set("stock:" + goodsId, goods.getStockCount().longValue());

            // 2. 内存售罄标记
            localOverMap.put(goodsId, new AtomicBoolean(false));

            // 3. 【核心】预热商品详情（双写）
            // 写 Redis：供其他实例或重启后使用
            // 写本地缓存：秒杀接口直接读内存，纳秒级
            goodsCacheService.putRedisCache(goodsId, goods);
            goodsCacheService.putLocalCache(goodsId, goods);

            // 4. 设置限流器（目前没有调用）
            RRateLimiter limiter = redissonClient.getRateLimiter("seckill:limiter:" + goodsId);
            limiter.setRate(RateType.OVERALL, 2000, 1, RateIntervalUnit.SECONDS);
        }

        log.info("商品预热完成，数量: {}", list.size());
    }


//    @Override
//    public void afterPropertiesSet() {
//        List<GoodsVo> list = goodsService.listGoodsVo();
//        for (GoodsVo goods : list) {
//            // 预热库存到 Redis
//            redisTemplate.opsForValue().set("stock:" + goods.getId(), goods.getStockCount().longValue());
//            localOverMap.put(goods.getId(), new AtomicBoolean(false));
//
//            // 设置限流器
//            RRateLimiter limiter = redissonClient.getRateLimiter("seckill:limiter:" + goods.getId());
//            limiter.setRate(RateType.OVERALL, 2000, 1, RateIntervalUnit.SECONDS);
//        }
//    }


    /**
     * 检查内存标记是否售罄
     */
    public boolean isOver(Long goodsId) {
        AtomicBoolean flag = localOverMap.get(goodsId);
        return flag != null && flag.get();
    }

    /**
     * 原子性地将内存标记设置为 true（售罄）
     * 使用 CAS 保证只有一个线程能成功将 false -> true
     * @return true 表示设置成功（本次由该线程触发售罄标记），false 表示已被其他线程抢先设置
     */
    public boolean setOverIfAbsent(Long goodsId) {
        AtomicBoolean flag = localOverMap.get(goodsId);
        return flag != null && flag.compareAndSet(false, true);
    }

    /**
     * 重置内存标记（用于取消订单后恢复抢购资格）
     */
    public void resetOver(Long goodsId) {
        AtomicBoolean flag = localOverMap.get(goodsId);
        if (flag != null) {
            flag.set(false);
        }
    }
}