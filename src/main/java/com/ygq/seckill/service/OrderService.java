package com.ygq.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ygq.seckill.config.RabbitMQConfig;
import com.ygq.seckill.config.SeckillInitializer;
import com.ygq.seckill.entity.Order;
import com.ygq.seckill.entity.OrderInfo;
import com.ygq.seckill.entity.SeckillOrder;
import com.ygq.seckill.mapper.OrderInfoMapper;
import com.ygq.seckill.mapper.OrderMapper;
import com.ygq.seckill.mapper.SeckillOrderMapper;
import com.ygq.seckill.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
@Slf4j
@Service
public class OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private SeckillOrderMapper seckillOrderMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private RetryQueueService retryQueueService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private SeckillInitializer seckillInitializer;
    @Autowired
    private OrderMapper orderMapper;

    @Transactional
    public OrderInfo createOrder(Long userId, GoodsVo goods) {
        // 1. 插入订单详情表 sk_order_info
        OrderInfo order = new OrderInfo();
        order.setUserId(userId);
        order.setGoodsId(goods.getId());
        order.setGoodsName(goods.getGoodsName());
        order.setGoodsCount(1);
        order.setGoodsPrice(goods.getSeckillPrice());
        order.setOrderChannel(1);
        order.setStatus(0);
        order.setCreateDate(LocalDateTime.now());
        orderInfoMapper.insert(order);   // 生成 order.id

        // 2. 插入订单关联表 sk_order（用于快速关联 userId+goodsId -> orderId）
        Order orderRel = new Order();
        orderRel.setUserId(userId);
        orderRel.setGoodsId(goods.getId());
        orderRel.setOrderId(order.getId());
        orderMapper.insert(orderRel);

        // 3. 插入防重表 sk_seckill_order（仅 userId+goodsId）
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setUserId(userId);
        seckillOrder.setGoodsId(goods.getId());
        // orderId 字段标记为 exist=false，不插入
        seckillOrderMapper.insert(seckillOrder);

        // 4. 发送延迟消息（保持不变）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DELAYED_EXCHANGE,
                RabbitMQConfig.DELAYED_ROUTING_KEY,
                order.getId(),
                message -> {
                    message.getMessageProperties().setHeader("x-delay", 15 * 60 * 1000); // 15分钟
                    return message;
                }
        );

        return order;
    }

    public Long getSeckillResult(Long userId, Long goodsId) {
        // 1. 先查 Redis 缓存
        String orderKey = "order:user:" + userId + ":goods:" + goodsId;
        Object orderIdObj = redisTemplate.opsForValue().get(orderKey);
        if (orderIdObj != null) {
            return ((Number) orderIdObj).longValue();
        }

        // 2. 查防重表 sk_seckill_order 判断是否已下单
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getUserId, userId)
                .eq(SeckillOrder::getGoodsId, goodsId);
        SeckillOrder seckillOrder = seckillOrderMapper.selectOne(wrapper);
        if (seckillOrder != null) {
            // 已下单，从 sk_order 表获取 orderId
            LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.eq(Order::getUserId, userId)
                    .eq(Order::getGoodsId, goodsId);
            Order orderRel = orderMapper.selectOne(orderWrapper);
            if (orderRel != null) {
                Long orderId = orderRel.getOrderId();
                // 回填 Redis 缓存
                redisTemplate.opsForValue().set(orderKey, orderId, 1, TimeUnit.HOURS);
                return orderId;
            }
            // 极端情况：防重表有记录但 sk_order 没有，返回 -1 表示异常（或重试）
            return -1L;
        }

        // 3. 检查重试队列
        if (retryQueueService.isInRetryQueue(userId, goodsId)) {
            return -2L;
        }

        // 4. 检查库存是否耗尽
        String stockKey = "stock:" + goodsId;
        Object stockObj = redisTemplate.opsForValue().get(stockKey);
        if (stockObj != null && ((Number) stockObj).longValue() == 0) {
            return -1L;   // 售罄
        }

        // 5. 未下单且库存还有，返回 0 表示未处理完
        return 0L;
    }


    @Transactional
    public void cancelOrder(Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null || order.getStatus() != 0) return;

        // 更新订单状态为已关闭
        order.setStatus(2);
        orderInfoMapper.updateById(order);

        // 回滚库存
        goodsService.increaseStock(order.getGoodsId(), order.getGoodsCount());

        // 删除防重表记录（允许用户重新秒杀）
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getUserId, order.getUserId())
                .eq(SeckillOrder::getGoodsId, order.getGoodsId());
        seckillOrderMapper.delete(wrapper);

        // 删除订单关联表 sk_order 记录（若存在）
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getUserId, order.getUserId())
                .eq(Order::getGoodsId, order.getGoodsId());
        orderMapper.delete(orderWrapper);

        // 清理 Redis 用户已购标记和订单缓存
        String userSetKey = "seckill:users:" + order.getGoodsId();
        redisTemplate.opsForSet().remove(userSetKey, order.getUserId());
        String orderKey = "order:user:" + order.getUserId() + ":goods:" + order.getGoodsId();
        redisTemplate.delete(orderKey);

        // 重置内存售罄标记
        seckillInitializer.resetOver(order.getGoodsId());

        log.info("订单 {} 已取消，库存回滚{}件，防重记录已清除", orderId, order.getGoodsCount());
    }

//    @Transactional
//    public void cancelOrder(Long orderId) {
//        OrderInfo order = orderInfoMapper.selectById(orderId);
//        if (order == null || order.getStatus() != 0) return;
//
//        // 更新订单状态为已关闭
//        order.setStatus(2); // 2-已关闭
//        orderInfoMapper.updateById(order);
//
//        // 释放库存（回写数据库 + Redis）
//        goodsService.increaseStock(order.getGoodsId(), order.getGoodsCount());
//
//        // 【新增】重置该商品的限流器，避免库存释放后限流器依然卡死
//        resetRateLimiter(order.getGoodsId());
//
//        // 【新增】重置内存售罄标记，允许用户重新抢购
//        // 如果不想在 OrderService 里依赖 SeckillInitializer，也可以直接删除 Redis 的 stock 键让系统重建，但重置标记更优雅
//        // 这里通过 Redis 删除 stock 键来触发重建（但重建依赖预热逻辑，建议直接重置标记）
//        // 推荐做法：在 OrderService 中注入 SeckillInitializer 并调用 resetOver
//        // 重置内存售罄标记
//        seckillInitializer.resetOver(order.getGoodsId());
//    }

    private Long convertToLong(Object orderId) {
        return (Long) orderId;
    }

    public OrderInfo getOrderById(Long orderId) {
        return orderInfoMapper.selectById(orderId);
    }

    /**
     * 重置该商品的限流器（当库存回退后，允许限流器重新放行）
     * 删除旧限流器并重新设置速率
     */
    private void resetRateLimiter(Long goodsId) {
        String key = "seckill:limiter:" + goodsId;
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // 直接强制覆盖速率
        limiter.setRate(RateType.OVERALL, 1500, 1, RateIntervalUnit.SECONDS);
        log.info("限流器已重置: goodsId={}", goodsId);
    }

}
