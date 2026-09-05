package com.ygq.seckill.rabbitmq;

import com.rabbitmq.client.Channel;
import com.ygq.seckill.config.RabbitMQConfig;
import com.ygq.seckill.config.SeckillInitializer;
import com.ygq.seckill.service.RedisService;
import com.ygq.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OrderReceiver {

    @Autowired
    private SeckillService seckillService;
    @Autowired
    private MessageConverter messageConverter;
    @Autowired
    private SeckillInitializer seckillInitializer;
    @Autowired
    private RedisService redisService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE,
            containerFactory = "batchRabbitListenerContainerFactory")
    public void handle(List<Message> messages, Channel channel) throws IOException {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // 用于存放最终需要 ACK 的消息（成功处理）
        List<Long> successTags = new ArrayList<>();
        // 用于存放需要重试的消息（异常）
        List<Long> retryTags = new ArrayList<>();
        // 用于存放 Redis 扣减成功但后续可能回滚的消息（保存它们的消息对象以便回滚）
        List<SeckillMessage> successMessages = new ArrayList<>();

        // 第一步：逐条执行 Redis Lua，并分类
        for (Message msg : messages) {
            long deliveryTag = msg.getMessageProperties().getDeliveryTag();
            SeckillMessage seckillMsg = null;
            try {
                Object obj = messageConverter.fromMessage(msg);
                if (!(obj instanceof SeckillMessage)) {
                    log.warn("非预期消息类型: {}", obj.getClass());
                    // 无法处理，直接确认并丢弃
                    channel.basicAck(deliveryTag, false);
                    continue;
                }
                seckillMsg = (SeckillMessage) obj;
            } catch (Exception e) {
                log.error("消息反序列化失败, deliveryTag={}", deliveryTag, e);
                // 反序列化失败的消息无法处理，直接丢弃
                channel.basicAck(deliveryTag, false);
                continue;
            }

            try {
                // 执行 Redis Lua 脚本（原子扣库存 + 防重）
                List luaResult = redisService.executeSeckillLua(seckillMsg.getGoodsId(), seckillMsg.getUserId());
                Long status = Long.parseLong(luaResult.get(0).toString());

                if (status == 1) {
                    // 成功，暂存
                    successMessages.add(seckillMsg);
                    successTags.add(deliveryTag);
                } else {
                    // 业务失败（库存不足或重复）
                    String reason = luaResult.get(1).toString();
                    if ("库存不足".equals(reason)) {
                        // 设置内存标记，减少后续无效请求
                        seckillInitializer.setOverIfAbsent(seckillMsg.getGoodsId());
                    }
                    // 重复秒杀或库存不足都不需要重试，直接确认
                    channel.basicAck(deliveryTag, false);
                }
            } catch (Exception e) {
                // Redis 连接超时、网络异常等，需要重试
                log.error("Redis Lua 执行异常, goodsId={}, userId={}", seckillMsg.getGoodsId(), seckillMsg.getUserId(), e);
                retryTags.add(deliveryTag);
            }
        }

        // 第二步：批量插入数据库（如果成功消息不为空）
        if (!successMessages.isEmpty()) {
            try {
                seckillService.batchHandleSeckill(successMessages);
                // 插入成功，确认所有成功消息（此时已在 successTags 中）
                for (Long tag : successTags) {
                    channel.basicAck(tag, false);
                }
            } catch (Exception e) {
                // 批量插入失败（如 DB 连接中断），必须回滚 Redis 库存并重试
                log.error("批量插入订单失败，开始回滚 Redis 库存", e);
                // 回滚 Redis 库存（每个成功消息都曾扣减过库存）
                for (SeckillMessage msg : successMessages) {
                    redisTemplate.opsForValue().increment("stock:" + msg.getGoodsId(), 1);
                    // 同时移除用户标记（因为订单未创建，应该让用户能重试）
                    redisTemplate.opsForSet().remove("seckill:users:" + msg.getGoodsId(), msg.getUserId());
                    seckillInitializer.resetOver(msg.getGoodsId());
                }
                // 将所有成功消息全部 NACK 以重试
                for (Long tag : successTags) {
                    channel.basicNack(tag, false, true);
                }
            }
        }

        // 第三步：处理需要重试的消息（Redis 异常）
        if (!retryTags.isEmpty()) {
            for (Long tag : retryTags) {
                // 重试，requeue=true
                channel.basicNack(tag, false, true);
            }
        }

        // 凡是已经 basicAck 的消息，不会再被处理；basicNack 的消息会重新入队。
    }
}