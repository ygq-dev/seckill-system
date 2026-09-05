package com.ygq.seckill.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@Configuration
public class RabbitMQConfig {

    //定义队列 seckill.queue（存放消息的地方）
    public static final String SECKILL_QUEUE = "seckill.queue";

    //定义交换机 seckill.exchange（消息的中转站）
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_ROUTING_KEY = "seckill.key";

    // 库存扣减队列
    public static final String STOCK_DECREASE_QUEUE = "stock.decrease.queue";
    public static final String STOCK_DECREASE_ROUTING_KEY = "stock.decrease";

    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    // 延迟交换机（用于订单超时）
    public static final String DELAYED_EXCHANGE = "delayed.exchange";
    public static final String DELAYED_QUEUE = "order.timeout.queue";
    public static final String DELAYED_ROUTING_KEY = "order.timeout";
    // 死信交换机（DLX）
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLX_ROUTING_KEY = "dlx";

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    // 默认工厂（用于单条消费，如 OrderReceiver）
    // 必须是 Primary，且不要开启 batchListener
    @Bean
    @Primary
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory consumerConnectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(consumerConnectionFactory);
        factory.setMessageConverter(messageConverter); // 必须设置转换器
        factory.setConcurrentConsumers(20);
        factory.setMaxConcurrentConsumers(30);
        factory.setPrefetchCount(50);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    // 批量工厂
    @Bean("batchRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory batchRabbitListenerContainerFactory(
            ConnectionFactory consumerConnectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(consumerConnectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setBatchListener(true);
        factory.setConsumerBatchEnabled(true);
        factory.setBatchSize(50);                 // 批量消费
        factory.setReceiveTimeout(500L);          // 提交频率
        factory.setConcurrentConsumers(16);       // 8核×2
        factory.setMaxConcurrentConsumers(30);    // 应对突发
        factory.setPrefetchCount(20);             // 预取适中
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    @Bean
    @Primary
    public ConnectionFactory consumerConnectionFactory(RabbitProperties rabbitProperties) {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitProperties.getHost());
        factory.setPort(rabbitProperties.getPort());
        factory.setUsername(rabbitProperties.getUsername());
        factory.setPassword(rabbitProperties.getPassword());
        if (rabbitProperties.getVirtualHost() != null) {
            factory.setVirtualHost(rabbitProperties.getVirtualHost());
        }
        factory.setChannelCacheSize(200);
        return factory;
    }

    @Bean(name = "publisherConnectionFactory")
    public ConnectionFactory publisherConnectionFactory(RabbitProperties rabbitProperties) {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitProperties.getHost());
        factory.setPort(rabbitProperties.getPort());
        factory.setUsername(rabbitProperties.getUsername());
        factory.setPassword(rabbitProperties.getPassword());
        if (rabbitProperties.getVirtualHost() != null) {
            factory.setVirtualHost(rabbitProperties.getVirtualHost());
        }
        factory.setChannelCacheSize(500);
        factory.setChannelCheckoutTimeout(5000);
        return factory;
    }


    // RabbitTemplate 使用生产者专用工厂
    @Bean
    public RabbitTemplate rabbitTemplate(@Qualifier("publisherConnectionFactory") ConnectionFactory publisherConnectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(publisherConnectionFactory);
        template.setMessageConverter(messageConverter);
        // 不再需要 usePublisherConnection
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送失败，原因: {}", cause);
                // 无需 correlationData，直接记录
            }
        });
        template.setReturnsCallback(returned -> {
            log.error("消息路由失败: {}", returned);
        });
        return template;
    }

    @Bean
    public TopicExchange seckillExchange() {
        return new TopicExchange(SECKILL_EXCHANGE);
    }

    //定义绑定关系：将队列绑定到交换机，并指定路由键 seckill.key
    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    //配置 JSON 转换器：让消息在传输时自动变成 JSON 格式，方便阅读和跨语言解析
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public CustomExchange delayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(DELAYED_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue delayedQueue() {
        return new Queue(DELAYED_QUEUE, true);
    }

    @Bean
    public Binding delayedBinding() {
        return BindingBuilder.bind(delayedQueue()).to(delayedExchange()).with(DELAYED_ROUTING_KEY).noargs();
    }


    @Bean
    public Queue stockDecreaseQueue() {
        return QueueBuilder.durable(STOCK_DECREASE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding stockDecreaseBinding() {
        return BindingBuilder.bind(stockDecreaseQueue())
                .to(seckillExchange())
                .with(STOCK_DECREASE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("dead.letter.queue")
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }

    // 订单队列 Bean
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    // 订单队列绑定
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }
}