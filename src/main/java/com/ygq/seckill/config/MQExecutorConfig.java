package com.ygq.seckill.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class MQExecutorConfig {
    @Bean("mqSenderExecutor")
    public Executor mqSenderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);          // 核×2
        executor.setMaxPoolSize(32);           // 弹性扩容
        executor.setQueueCapacity(200);        // 队列容量
        executor.setThreadNamePrefix("MQ-Sender-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
