package com.ygq.seckill.rabbitmq;

import com.rabbitmq.client.Channel;
import com.ygq.seckill.config.RabbitMQConfig;
import com.ygq.seckill.mapper.SeckillGoodsMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class StockDecreaseReceiver {
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MessageConverter messageConverter;

    @PostConstruct
    public void init() {
        log.info("✅ StockDecreaseReceiver 已初始化，监听队列: {}", RabbitMQConfig.STOCK_DECREASE_QUEUE);
    }


    @RabbitListener(
            queues = RabbitMQConfig.STOCK_DECREASE_QUEUE,
            containerFactory = "batchRabbitListenerContainerFactory"
    )
    public void handle(List<Message> messages, Channel channel) throws IOException {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        log.info("📦 收到库存扣减批量消息，数量: {}", messages.size());

        // 1. 提取所有 deliveryTag
        List<Long> deliveryTags = messages.stream()
                .map(msg -> msg.getMessageProperties().getDeliveryTag())
                .collect(Collectors.toList());
        long maxDeliveryTag = deliveryTags.stream().max(Long::compareTo).orElse(0L);

        // 2. 反序列化消息体
        List<StockDecreaseMessage> stockMessages = new ArrayList<>();
        for (Message msg : messages) {
            try {
                Object obj = messageConverter.fromMessage(msg);
                if (obj instanceof StockDecreaseMessage) {
                    stockMessages.add((StockDecreaseMessage) obj);
                } else {
                    log.warn("非预期的消息类型: {}", obj.getClass().getName());
                }
            } catch (Exception e) {
                log.error("反序列化失败", e);
            }
        }

        if (stockMessages.isEmpty()) {
            channel.basicAck(maxDeliveryTag, true);
            return;
        }

        try {
            // 3. 聚合统计（按 goodsId 汇总）
            Map<Long, Integer> reduceMap = stockMessages.stream()
                    .collect(Collectors.groupingBy(
                            StockDecreaseMessage::getGoodsId,
                            Collectors.summingInt(StockDecreaseMessage::getQuantity)
                    ));
            log.info("收到库存扣减消息批次: {} 条, 汇总 goodsId={}", stockMessages.size(), reduceMap); // 添加批次日志

            // 4. 事务执行批量扣减
            Boolean success = transactionTemplate.execute(status -> {
                try {
                    for (Map.Entry<Long, Integer> entry : reduceMap.entrySet()) {
                        int updated = seckillGoodsMapper.batchReduceStock(entry.getKey(), entry.getValue());
                        if (updated == 0) {
                            log.warn("库存不足，goodsId={}", entry.getKey());
                            throw new RuntimeException("StockInsufficient");
                        }
                    }
                    return true;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    return false;
                }
            });

            // 5. 确认或重试
            if (Boolean.TRUE.equals(success)) {
                channel.basicAck(maxDeliveryTag, true);
                log.info("✅ 批量扣减成功，确认 {} 条消息", stockMessages.size());
            } else {
                channel.basicNack(maxDeliveryTag, true, true);
                log.warn("⚠️ 批量扣减失败，事务回滚，消息重试");
            }

        } catch (Exception e) {
            log.error("❌ 批量处理异常", e);
            channel.basicNack(maxDeliveryTag, true, true);
        }
    }
}

