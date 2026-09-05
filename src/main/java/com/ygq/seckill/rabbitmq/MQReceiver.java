package com.ygq.seckill.rabbitmq;

import com.rabbitmq.client.Channel;
import com.ygq.seckill.config.RabbitMQConfig;
import com.ygq.seckill.exception.OptimisticLockException;
import com.ygq.seckill.exception.StockEmptyException;
import com.ygq.seckill.service.SeckillService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class MQReceiver {

    @Autowired
    private SeckillService seckillService;
    @Autowired
    private RedisTemplate redisTemplate;

    @PostConstruct
    public void init() {
        log.debug("MQReceiver 初始化成功，监听队列: {}", RabbitMQConfig.SECKILL_QUEUE);
    }

//    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE, concurrency = "20-50")
    public void receive(SeckillMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag,
                        @Header(name = "x-redelivered", defaultValue = "false") boolean redelivered) {
        try {
            seckillService.handleSeckill(message.getUserId(), message.getGoodsId());
            channel.basicAck(tag, false);
        } catch (StockEmptyException e) {
            // 库存已空，直接ACK，不重试
            log.warn("库存已空，消息丢弃: userId={}, goodsId={}", message.getUserId(), message.getGoodsId());
            try {
                channel.basicAck(tag, false);
            } catch (IOException ex) {
                log.error("ACK失败", ex);
            }
        } catch (OptimisticLockException e) {
            // 乐观锁冲突，判断是否已重试过
            if (redelivered) {
                // 已重试过，最终回滚Redis并丢弃消息
                log.warn("乐观锁冲突重试仍失败，执行回滚并丢弃: userId={}, goodsId={}",
                        message.getUserId(), message.getGoodsId());
                // 回滚Redis（因为handleSeckill中未回滚）
                redisTemplate.opsForValue().increment("stock:" + message.getGoodsId(), 1);
                redisTemplate.opsForSet().remove("seckill:users:" + message.getGoodsId(), message.getUserId());
                try {
                    channel.basicAck(tag, false); // 确认消费，不再重试
                } catch (IOException ex) {
                    log.error("ACK失败", ex);
                }
            } else {
                // 首次失败，重新入队重试
                log.warn("乐观锁冲突，消息将重新入队: userId={}, goodsId={}",
                        message.getUserId(), message.getGoodsId());
                try {
                    channel.basicNack(tag, false, true);
                } catch (IOException ex) {
                    log.error("NACK失败", ex);
                }
            }
        } catch (Exception e) {
            log.error("处理秒杀消息异常", e);
            try {
                channel.basicNack(tag, false, false); // 其他异常不重试
            } catch (IOException ex) {
                log.error("NACK失败", ex);
            }
        }
    }

//    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE, concurrency = "5-10")
//    public void receive(SeckillMessage message, Channel channel,
//                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
//        log.info("MQ消息到达: userId={}, goodsId={}", message.getUserId(), message.getGoodsId());
//        try {
//            seckillService.handleSeckill(message.getUserId(), message.getGoodsId());
//            channel.basicAck(tag, false);
//        } catch (Exception e) {
//            log.error("处理消息异常", e);
//            try {
//                channel.basicNack(tag, false, true);
//            } catch (IOException ex) {
//                log.error("NACK失败", ex);
//            }
//        }
//    }


    // 负责从队列里“捞”消息，并真正执行秒杀业务逻辑（减库存、下单）。
    // 使用 @RabbitListener 监听队列。收到消息后调用 SeckillService 处理业务，处理成功发送 ACK（确认），失败则 NACK（拒绝并重试）
//    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE, concurrency = "10-20")
//    public void receive(SeckillMessage message, Channel channel,
//                        @Header(AmqpHeaders.DELIVERY_TAG) long tag,
//                        @Header(name = "x-redelivered", defaultValue = "false") boolean redelivered) {
//        try {
//            log.info("收到秒杀消息: userId={}, goodsId={}", message.getUserId(), message.getGoodsId());
//            seckillService.handleSeckill(message.getUserId(), message.getGoodsId());
//            channel.basicAck(tag, false);
//            log.info("消息处理成功，订单已创建");
//        } catch (OptimisticLockException e) {
//            // 乐观锁冲突，根据是否已重试决定是否重新入队
//            if (redelivered) {
//                try {
//                    channel.basicNack(tag, false, false);
//                    log.error("乐观锁冲突重试失败，消息丢弃: {}", message);
//                } catch (IOException ex) {
//                    log.error("拒绝消息失败", ex);
//                }
//            } else {
//                try {
//                    channel.basicNack(tag, false, true);
//                    log.warn("乐观锁冲突，消息重新入队: {}", message);
//                } catch (IOException ex) {
//                    log.error("重新入队失败", ex);
//                }
//            }
//        } catch (Exception e) {
//            log.error("其他异常，重新入队", e);
//            try {
//                channel.basicNack(tag, false, true);
//            } catch (IOException ex) {
//                log.error("重新入队失败", ex);
//            }
//        }
//    }
}
