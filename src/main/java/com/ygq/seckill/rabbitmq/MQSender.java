package com.ygq.seckill.rabbitmq;


import com.ygq.seckill.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Component
public class MQSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 负责把秒杀请求“扔”进消息队列。
    // 调用 rabbitTemplate.convertAndSend，根据 Config 中定义的交换机和路由键，将 SeckillMessage 发送出去。
    public void sendSeckillMessage(SeckillMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message
        );
    }

//    public void sendSeckillMessage(SeckillMessage message) {
//        log.info("发送秒杀消息: userId={}, goodsId={}", message.getUserId(), message.getGoodsId());
//        rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_EXCHANGE, RabbitMQConfig.SECKILL_ROUTING_KEY, message);
//    }

//    public void send(Object message){
//        String msg = RedisService.beanToString(message);
//        log.info("send message:"+msg);
//        amqpTemplate.convertAndSend(MQConfig.QUEUE, message);
//    }

//    	public void sendTopic(Object message) {
//		String msg = RedisService.beanToString(message);
//		log.info("send topic message:"+msg);
//		amqpTemplate.convertAndSend(MQConfig.TOPIC_EXCHANGE, "topic.key1", msg+"1");
//		amqpTemplate.convertAndSend(MQConfig.TOPIC_EXCHANGE, "topic.key2", msg+"2");
//	}
//
//	public void sendSeckillMessage(SeckillMessage message){
//        String msg = RedisService.beanToString(message);
//        log.info("send message:"+msg);
//        amqpTemplate.convertAndSend(MQConfig.QUEUE, msg);
//
//    }
}
