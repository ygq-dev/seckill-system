package com.ygq.seckill.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        // 从配置文件读取 Redis 地址
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        config.useSingleServer()
                .setAddress(address)
                .setConnectTimeout(1000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);
        config.setNettyThreads(16);
        return Redisson.create(config);
    }

//    @Bean(destroyMethod = "shutdown")
//    public RedissonClient redissonClient() {
//        Config config = new Config();
//        config.useSingleServer()
//                .setAddress("redis://127.0.0.1:6379");
//        config.setThreads(16);        // 业务线程池
//        config.setNettyThreads(16);   // Netty 事件循环线程
//        return Redisson.create(config);
//    }


    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setDefaultSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> {
            builder.clientOptions(ClientOptions.builder()
                    .autoReconnect(true)
                    .pingBeforeActivateConnection(false)
                    .socketOptions(SocketOptions.builder()
                            .connectTimeout(Duration.ofMillis(1000))
                            .keepAlive(true)
                            .tcpNoDelay(true)
                            .build())
                    .build());
            builder.commandTimeout(Duration.ofMillis(3000));
        };
    }
}