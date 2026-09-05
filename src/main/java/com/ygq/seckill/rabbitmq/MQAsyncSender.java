package com.ygq.seckill.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.seckill.config.RabbitMQConfig;
import com.ygq.seckill.entity.MqOutbox;
import com.ygq.seckill.mapper.MqOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class MQAsyncSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private MqOutboxMapper outboxMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Async("mqSenderExecutor")
    public void sendStockDecrease(Long goodsId, Integer quantity) {
        StockDecreaseMessage msg = new StockDecreaseMessage(goodsId, quantity);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.STOCK_DECREASE_ROUTING_KEY,
                    msg
            );
            log.info("异步发送扣减库存MQ成功: goodsId={}", goodsId);
        } catch (Exception e) {
            // 落库兜底
            MqOutbox outbox = new MqOutbox();
            outbox.setExchange(RabbitMQConfig.SECKILL_EXCHANGE);
            outbox.setRoutingKey(RabbitMQConfig.STOCK_DECREASE_ROUTING_KEY);

            try {
                // 使用 Jackson 将对象序列化为 JSON 字符串
                outbox.setPayload(objectMapper.writeValueAsString(msg));
            } catch (JsonProcessingException ex) {
                log.error("StockDecreaseMessage 序列化 JSON 失败", ex);
                // 如果序列化都失败了，说明是严重的代码问题，直接抛出或记录即可
                throw new RuntimeException("序列化消息失败", ex);
            }

            outbox.setStatus(0);
            outbox.setRetryCount(0);
            outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(10)); // 10秒后重试
            outboxMapper.insert(outbox);
            log.error("MQ 发送失败，已落 outbox: goodsId={}", goodsId, e);
        }
    }

    @Async("mqSenderExecutor")
    public void sendSeckillOrder(SeckillMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,   // 订单路由键
                    message
            );
        } catch (Exception e) {
            // 落库兜底
            MqOutbox outbox = new MqOutbox();
            outbox.setExchange(RabbitMQConfig.SECKILL_EXCHANGE);
            outbox.setRoutingKey(RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY);
            try {
                outbox.setPayload(objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException ex) {
                log.error("序列化失败", ex);
                return;
            }
            outbox.setStatus(0);
            outbox.setRetryCount(0);
            outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(10));
            outboxMapper.insert(outbox);
            log.error("秒杀消息发送失败，已落 Outbox 待重试", e);
        }
    }
}
