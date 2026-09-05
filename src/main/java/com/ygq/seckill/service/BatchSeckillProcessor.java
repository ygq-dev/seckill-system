//package com.ygq.seckill.service;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.ygq.seckill.entity.OrderInfo;
//import com.ygq.seckill.entity.SeckillGoods;
//import com.ygq.seckill.entity.SeckillOrder;
//import com.ygq.seckill.mapper.OrderInfoMapper;
//import com.ygq.seckill.mapper.SeckillGoodsMapper;
//import com.ygq.seckill.mapper.SeckillOrderMapper;
//import com.ygq.seckill.vo.GoodsVo;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentLinkedQueue;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//public class BatchSeckillProcessor {
//
//    private static final int BATCH_INSERT_SIZE = 100;
//
//    @Autowired
//    private SeckillGoodsMapper seckillGoodsMapper;
//
//    @Autowired
//    private OrderInfoMapper orderInfoMapper;
//
//    @Autowired
//    private GoodsService goodsService;
//
//    @Autowired
//    private RedisTemplate<String, Object> redisTemplate;
//
//    @Autowired
//    private StockCacheService stockCacheService;
//
//    @Autowired
//    private SeckillOrderMapper seckillOrderMapper;
//
//    private final Map<Long, BatchEntry> buffer = new ConcurrentHashMap<>();
//    private final AtomicBoolean flushing = new AtomicBoolean(false);
//
//    public void addTask(Long userId, Long goodsId) {
//        buffer.computeIfAbsent(goodsId, k -> new BatchEntry()).addUser(userId);
//        log.debug("任务入队: userId={}, goodsId={}, bufferSize={}", userId, goodsId, buffer.size());
//    }
//
//    @Scheduled(fixedDelay = 50)
//    public void flush() {
//        if (buffer.isEmpty()) {
//            if (System.currentTimeMillis() % 10000 < 100) {
//                log.info("定时刷新触发，但 buffer 为空");
//            }
//            return;
//        }
//        if (!flushing.compareAndSet(false, true)) {
//            log.warn("上一次刷新尚未完成，本次跳过");
//            return;
//        }
//        long start = System.currentTimeMillis();
//        log.info("开始刷新缓冲，当前 buffer 中商品种类数: {}", buffer.size());
//        try {
//            Map<Long, BatchEntry> snapshot = new HashMap<>(buffer);
//            buffer.clear();
//            for (Map.Entry<Long, BatchEntry> entry : snapshot.entrySet()) {
//                Long goodsId = entry.getKey();
//                BatchEntry be = entry.getValue();
//                List<Long> userIds = be.drainUserIds();
//                int total = userIds.size();
//                if (total == 0) continue;
//
//                try {
//                    boolean success = batchDeductAndCreateOrders(goodsId, userIds, total);
//                    if (!success) {
//                        // 库存不足，回滚 Redis 库存和防重标记
//                        log.warn("商品{}库存不足，回滚Redis库存{}并移除用户标记", goodsId, total);
//                        redisTemplate.opsForValue().increment("stock:" + goodsId, total);
//                        for (Long uid : userIds) {
//                            redisTemplate.opsForSet().remove("seckill:users:" + goodsId, uid);
//                        }
//                    }
//                } catch (Exception e) {
//                    // 数据库异常（如插入失败），回滚 Redis 库存和防重标记
//                    log.error("批量处理异常，回滚Redis库存{}并移除用户标记", total, e);
//                    redisTemplate.opsForValue().increment("stock:" + goodsId, total);
//                    for (Long uid : userIds) {
//                        redisTemplate.opsForSet().remove("seckill:users:" + goodsId, uid);
//                    }
//                    // 可选：将任务重新入队（但此处直接丢弃，因为回滚后后续请求会重新发送 MQ）
//                }
//            }
//        } catch (Exception e) {
//            log.error("批量处理异常", e);
//        } finally {
//            flushing.set(false);
//            log.info("刷新完成，耗时 {}ms", System.currentTimeMillis() - start);
//        }
//    }
//
//    @Transactional(rollbackFor = Exception.class)
//    public boolean batchDeductAndCreateOrders(Long goodsId, List<Long> userIds, int total) {
//        // 过滤已下单用户（依然保留，但用 Redis 标记加速，依赖最终一致）
//        // 为避免防重表脏数据，以数据库为准
//        List<Long> newUserIds = filterExistingOrders(userIds, goodsId);
//        if (newUserIds.isEmpty()) {
//            log.info("商品{} 所有用户均已下单，跳过处理", goodsId);
//            return true;
//        }
//        int realTotal = newUserIds.size();
//
//        // 扣减数据库库存（原子操作）
//        int update = seckillGoodsMapper.batchReduceStock(goodsId, realTotal);
//        if (update == 0) {
//            log.warn("商品{} 库存不足，批量扣减失败，需扣{}件", goodsId, realTotal);
//            return false;
//        }
//
//        // 获取商品信息
//        GoodsVo goods = goodsService.getGoodsVoById(goodsId);
//        if (goods == null) {
//            throw new RuntimeException("商品不存在，无法创建订单");
//        }
//
//        // 批量生成订单
//        List<OrderInfo> orders = new ArrayList<>(realTotal);
//        List<SeckillOrder> seckillOrders = new ArrayList<>(realTotal);
//        LocalDateTime now = LocalDateTime.now();
//        for (Long userId : newUserIds) {
//            OrderInfo order = new OrderInfo();
//            order.setUserId(userId);
//            order.setGoodsId(goodsId);
//            order.setGoodsName(goods.getGoodsName());
//            order.setGoodsCount(1);
//            order.setGoodsPrice(goods.getSeckillPrice());
//            order.setOrderChannel(1);
//            order.setStatus(0);
//            order.setCreateDate(now);
//            orders.add(order);
//
//            SeckillOrder so = new SeckillOrder();
//            so.setUserId(userId);
//            so.setGoodsId(goodsId);
//            seckillOrders.add(so);
//        }
//
//        // 在插入订单和防重表之前，拆分批次
//        List<List<OrderInfo>> orderBatches = partition(orders, BATCH_INSERT_SIZE);
//        for (List<OrderInfo> batch : orderBatches) {
//            orderInfoMapper.batchInsert(batch);
//        }
//
//        List<List<SeckillOrder>> seckillBatches = partition(seckillOrders, BATCH_INSERT_SIZE);
//        for (List<SeckillOrder> batch : seckillBatches) {
//            seckillOrderMapper.batchInsertIgnore(batch);
//        }
//
//        // 更新内存标记和 Redis 库存（若库存为0则标记）
//        SeckillGoods after = seckillGoodsMapper.selectByGoodsId(goodsId);
//        if (after != null && after.getStockCount() <= 0) {
//            stockCacheService.markStockEmpty(goodsId);
//        }
//
//        log.info("批量处理成功：商品{}，实际扣减{}件，生成{}个订单", goodsId, realTotal, orders.size());
//        return true;
//    }
//
//    // 辅助过滤方法
//    private List<Long> filterExistingOrders(List<Long> userIds, Long goodsId) {
//        if (userIds.isEmpty()) return Collections.emptyList();
//        // 从数据库查询已存在防重记录的用户
//        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(SeckillOrder::getGoodsId, goodsId)
//                .in(SeckillOrder::getUserId, userIds);
//        List<SeckillOrder> existing = seckillOrderMapper.selectList(wrapper);
//        Set<Long> existingUserSet = existing.stream().map(SeckillOrder::getUserId).collect(Collectors.toSet());
//        return userIds.stream().filter(uid -> !existingUserSet.contains(uid)).collect(Collectors.toList());
//    }
//
//    private <T> List<List<T>> partition(List<T> list, int size) {
//        List<List<T>> result = new ArrayList<>();
//        for (int i = 0; i < list.size(); i += size) {
//            result.add(list.subList(i, Math.min(i + size, list.size())));
//        }
//        return result;
//    }
//
//    private static class BatchEntry {
//        private final Queue<Long> userIds = new ConcurrentLinkedQueue<>();
//        public void addUser(Long userId) { userIds.offer(userId); }
//        public List<Long> drainUserIds() {
//            List<Long> list = new ArrayList<>();
//            Long id;
//            while ((id = userIds.poll()) != null) {
//                list.add(id);
//            }
//            return list;
//        }
//    }
//}