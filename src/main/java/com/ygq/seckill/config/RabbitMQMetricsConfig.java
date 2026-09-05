package com.ygq.seckill.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
public class RabbitMQMetricsConfig {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private MeterRegistry meterRegistry;

    @PostConstruct
    public void registerRabbitMQMetrics() {
        CachingConnectionFactory factory = (CachingConnectionFactory) rabbitTemplate.getConnectionFactory();
        // Channel缓存大小（生产者+消费者，旧版本可用）
        Gauge.builder("rabbitmq.channels", factory, f -> f.getChannelCacheSize())
                .register(meterRegistry);
        System.out.println("✅ RabbitMQ指标注册成功");
    }
}
