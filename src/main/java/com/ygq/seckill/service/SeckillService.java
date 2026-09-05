package com.ygq.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ygq.seckill.entity.Order;
import com.ygq.seckill.entity.OrderInfo;
import com.ygq.seckill.entity.SeckillGoods;
import com.ygq.seckill.entity.SeckillOrder;
import com.ygq.seckill.exception.GlobalException;
import com.ygq.seckill.mapper.OrderInfoMapper;
import com.ygq.seckill.mapper.OrderMapper;
import com.ygq.seckill.mapper.SeckillGoodsMapper;
import com.ygq.seckill.mapper.SeckillOrderMapper;
import com.ygq.seckill.rabbitmq.SeckillMessage;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.util.SnowflakeIdWorker;
import com.ygq.seckill.vo.GoodsVo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillService {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private SeckillOrderMapper seckillOrderMapper;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderMapper orderRelMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private GoodsCacheService goodsCacheService;
    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;

    private Counter successCounter;
    private Counter failCounter;
    private Counter duplicateCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("seckill.orders.success")
                .description("秒杀成功订单数").register(meterRegistry);
        failCounter = Counter.builder("seckill.orders.fail")
                .description("秒杀失败数").register(meterRegistry);
        duplicateCounter = Counter.builder("seckill.orders.duplicate")
                .description("重复秒杀拦截数").register(meterRegistry);
    }

    /**
     * 处理秒杀消息（由MQ消费者调用）
     * 只负责创建订单，库存已在Redis中预扣，不再操作数据库库存
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleSeckill(Long userId, Long goodsId) {
        // 单条处理逻辑（保留，但压测主要走批量）
        SeckillOrder so = new SeckillOrder();
        so.setUserId(userId);
        so.setGoodsId(goodsId);
        try {
            seckillOrderMapper.insert(so);
        } catch (DuplicateKeyException e) {
            rollbackRedis(goodsId, userId);
            duplicateCounter.increment();
            return;
        }
        GoodsVo goods = goodsCacheService.getGoodsVoById(goodsId);
        if (goods == null) {
            seckillOrderMapper.deleteById(so.getId());
            rollbackRedis(goodsId, userId);
            failCounter.increment();
            throw new GlobalException(CodeMsg.GOODS_NOT_EXIST);
        }
        OrderInfo order = new OrderInfo();
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setGoodsName(goods.getGoodsName());
        order.setGoodsCount(1);
        order.setGoodsPrice(goods.getSeckillPrice());
        order.setOrderChannel(1);
        order.setStatus(0);
        order.setCreateDate(LocalDateTime.now());
        orderInfoMapper.insert(order);

        Order orderRel = new Order();
        orderRel.setUserId(userId);
        orderRel.setGoodsId(goodsId);
        orderRel.setOrderId(order.getId());
        orderRelMapper.insert(orderRel);

        redisTemplate.opsForSet().remove("seckill:users:" + goodsId, userId);
        int updated = seckillGoodsMapper.reduceStockByGoodsId(goodsId);
        if (updated == 0) log.error("数据库库存扣减失败，goodsId={}", goodsId);
        successCounter.increment();
    }

    /**
     * 回滚Redis预扣（库存加回，移除用户标记）
     */
    private void rollbackRedis(Long goodsId, Long userId) {
        redisTemplate.opsForValue().increment("stock:" + goodsId, 1);
        redisTemplate.opsForSet().remove("seckill:users:" + goodsId, userId);
    }

    /**
     * 库存对账补偿机制
     * 场景：MySQL 库存 > Redis 库存
     * 原因：消息丢失导致 MySQL 未扣减
     * 动作：补扣 MySQL 库存，使其与 Redis 一致
     */
//    @Scheduled(cron = "0 0 * * * ?") // 每小时执行
//    @Scheduled(cron = "0 0 3 * * ?") // 凌晨3点执行，压测期间可关闭
    public void reconcileStock() {
        log.info("========== 开始执行库存对账任务 ==========");

        // 1. 查询所有秒杀商品 (MyBatis-Plus 的 BaseMapper 自带 selectList(null) 方法)
        List<SeckillGoods> goodsList = seckillGoodsMapper.selectList(null);

        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }

        for (SeckillGoods goods : goodsList) {
            Long goodsId = goods.getGoodsId(); // 注意：使用 goodsId 构建 Key
            String stockKey = "stock:" + goodsId;

            // 2. 获取 Redis 库存
            Object redisStockObj = redisTemplate.opsForValue().get(stockKey);
            if (redisStockObj == null) {
                log.warn("对账跳过: goodsId={}, Redis无库存缓存", goodsId);
                continue;
            }

            long redisStock = Long.parseLong(redisStockObj.toString());
            long mysqlStock = goods.getStockCount();

            // 3. 比对逻辑：若 MySQL > Redis，说明 MySQL 未正确扣减（可能消息丢失）
            if (mysqlStock > redisStock) {
                int diff = (int) (mysqlStock - redisStock);
                log.warn("库存不一致发现: goodsId={}, MySQL={}, Redis={}, 差值={}", goodsId, mysqlStock, redisStock, diff);

                // 4. 执行补扣 (修正 MySQL)
                int updated = seckillGoodsMapper.batchReduceStock(goodsId, diff);
                if (updated > 0) {
                    log.info("√ 库存对账补扣成功: goodsId={}, 补扣数量={}", goodsId, diff);
                } else {
                    log.error("× 库存对账补扣失败: goodsId={}, 补扣数量={}", goodsId, diff);
                }
            }
            // 5. 反向差异 (MySQL < Redis)：理论上不应发生（防止超卖机制已拦截），若发生则修正 Redis
            else if (mysqlStock < redisStock) {
                log.warn("库存反向不一致: goodsId={}, MySQL={}, Redis={}", goodsId, mysqlStock, redisStock);
                // 以数据库为准，回滚 Redis
                redisTemplate.opsForValue().set(stockKey, mysqlStock);
            }
        }
        log.info("========== 库存对账任务结束 ==========");
    }

    /**
     * 批量处理秒杀订单（由 OrderReceiver 批量消费调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchHandleSeckill(List<SeckillMessage> messages) {
        if (messages == null || messages.isEmpty()) return;

        // 去重（以防万一）
        Map<String, SeckillMessage> dedupMap = new LinkedHashMap<>();
        for (SeckillMessage msg : messages) {
            dedupMap.putIfAbsent(msg.getUserId() + "-" + msg.getGoodsId(), msg);
        }
        List<SeckillMessage> uniqueMessages = new ArrayList<>(dedupMap.values());

        // 1. 构造批量数据（使用雪花 ID）
        List<SeckillOrder> seckillOrders = new ArrayList<>();
        List<OrderInfo> orderInfoList = new ArrayList<>();
        List<Order> orderRelList = new ArrayList<>();
        Map<Long, Integer> stockReduceMap = new HashMap<>();
        // 【修复1】生成式映射：msg -> orderId（orderInfoId 即订单号），
        // 批量插入后无需再回查数据库建立轮询映射
        Map<String, Long> orderIdMap = new HashMap<>();

        for (SeckillMessage msg : uniqueMessages) {
            long seckillOrderId = snowflakeIdWorker.nextId();
            long orderInfoId = snowflakeIdWorker.nextId();
            long orderRelId = snowflakeIdWorker.nextId();
            // 【修复1】记录映射，key 与去重逻辑保持一致
            orderIdMap.put(msg.getUserId() + "-" + msg.getGoodsId(), orderInfoId);

            SeckillOrder so = new SeckillOrder();
            so.setId(seckillOrderId);
            so.setUserId(msg.getUserId());
            so.setGoodsId(msg.getGoodsId());
            so.setCreateTime(LocalDateTime.now());
            seckillOrders.add(so);

            GoodsVo goods = goodsCacheService.getGoodsVoById(msg.getGoodsId());
            if (goods == null) {
                throw new GlobalException(CodeMsg.GOODS_NOT_EXIST);
            }

            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setId(orderInfoId);
            orderInfo.setUserId(msg.getUserId());
            orderInfo.setGoodsId(msg.getGoodsId());
            orderInfo.setGoodsName(goods.getGoodsName());
            orderInfo.setGoodsCount(1);
            orderInfo.setGoodsPrice(goods.getSeckillPrice());
            orderInfo.setOrderChannel(1);
            orderInfo.setStatus(0);
            orderInfo.setCreateDate(LocalDateTime.now());
            orderInfoList.add(orderInfo);

            Order orderRel = new Order();
            orderRel.setId(orderRelId);
            orderRel.setUserId(msg.getUserId());
            orderRel.setGoodsId(msg.getGoodsId());
            orderRel.setOrderId(orderInfoId);
            orderRelList.add(orderRel);

            stockReduceMap.merge(msg.getGoodsId(), 1, Integer::sum);
        }

        // 2. 批量插入（使用 MyBatis-Plus 的批量方法，需要自定义 SQL）
        try {
            seckillOrderMapper.batchInsert(seckillOrders);
            orderInfoMapper.batchInsert(orderInfoList);
            orderRelMapper.batchInsert(orderRelList);

            // 批量扣减数据库库存（按商品汇总）
            for (Map.Entry<Long, Integer> entry : stockReduceMap.entrySet()) {
                int updated = seckillGoodsMapper.batchReduceStock(entry.getKey(), entry.getValue());
                if (updated == 0) {
                    throw new RuntimeException("库存扣减失败，goodsId=" + entry.getKey());
                }
            }
        } catch (Exception e) {
            log.error("批量插入失败", e);
            throw e; // 抛出事务回滚
        }

        // 3.【修复2】订单缓存改为事务提交后写入：
        //   - orderId 直接取自 orderIdMap（消除 N 次 selectOne 回查）
        //   - afterCommit 回调保证：事务回滚时缓存一个 key 都不会写，
        //     轮询接口不会再出现幽灵订单；写缓存失败也不影响已提交的订单
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (SeckillMessage msg : uniqueMessages) {
                        Long orderId = orderIdMap.get(msg.getUserId() + "-" + msg.getGoodsId());
                        if (orderId == null) continue;
                        String key = "order:user:" + msg.getUserId() + ":goods:" + msg.getGoodsId();
                        try {
                            redisTemplate.opsForValue().set(key, orderId, 1, TimeUnit.HOURS);
                        } catch (Exception e) {
                            // 轮询接口 getSeckillResult 有 DB 回查兜底，缓存写失败可容忍
                            log.warn("订单缓存写入失败: key={}", key, e);
                        }
                    }
                }
            });
        } else {
            // 防御分支：无事务上下文时保持原行为直接写
            for (SeckillMessage msg : uniqueMessages) {
                Long orderId = orderIdMap.get(msg.getUserId() + "-" + msg.getGoodsId());
                if (orderId == null) continue;
                redisTemplate.opsForValue().set(
                        "order:user:" + msg.getUserId() + ":goods:" + msg.getGoodsId(),
                        orderId, 1, TimeUnit.HOURS);
            }
        }

        log.info("批量订单处理完成，成功数: {}", uniqueMessages.size());
    }
}
