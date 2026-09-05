package com.ygq.seckill.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.seckill.config.RabbitMQConfig;
import com.ygq.seckill.entity.MqOutbox;
import com.ygq.seckill.mapper.MqOutboxMapper;
import com.ygq.seckill.rabbitmq.SeckillMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class LocalMessageQueue {

    private final BlockingQueue<SeckillMessage> queue;
    private final RabbitTemplate rabbitTemplate;
    private final MqOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private ExecutorService workerPool;

    @Value("${seckill.local-queue.batch-size:50}")
    private int batchSize;

    @Value("${seckill.local-queue.poll-timeout-ms:50}")
    private long pollTimeoutMs;

    @Value("${seckill.local-queue.consumer-threads:4}")
    private int consumerThreads;

    public LocalMessageQueue(
            @Value("${seckill.local-queue.capacity:5000}") int capacity,
            RabbitTemplate rabbitTemplate,
            MqOutboxMapper outboxMapper,
            ObjectMapper objectMapper) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.rabbitTemplate = rabbitTemplate;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(1000, 2, 5000)
                .retryOn(Exception.class)
                .build();
    }

    @PostConstruct
    public void start() {
        workerPool = Executors.newFixedThreadPool(consumerThreads);
        for (int i = 0; i < consumerThreads; i++) {
            workerPool.submit(this::consume);
        }
        log.info("LocalMessageQueue 启动，消费者线程数: {}, 容量: {}, 批量大小: {}",
                consumerThreads, queue.remainingCapacity() + queue.size(), batchSize);
    }

    /**
     * 写入队列（非阻塞）
     */
    public boolean offer(SeckillMessage message) {
        return queue.offer(message);
    }

    public int size() {
        return queue.size();
    }

    /**
     * 核心消费逻辑（由线程池中的多个线程并发执行）
     */
    private void consume() {
        List<SeckillMessage> batch = new ArrayList<>(batchSize);
        while (running.get()) {
            try {
                SeckillMessage first = queue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, batchSize - batch.size());

                if (!batch.isEmpty()) {
                    sendBatchWithRetry(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("批量发送循环异常", e);
            } finally {
                batch.clear();
            }
        }

        // 线程退出前，尽力处理当前线程持有的剩余消息
        if (!batch.isEmpty()) {
            sendBatchWithRetry(batch);
        }
        log.info("消费者线程 {} 已停止", Thread.currentThread().getName());
    }

    private void sendBatchWithRetry(List<SeckillMessage> batch) {
        try {
            retryTemplate.execute(context -> {
                for (SeckillMessage msg : batch) {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.SECKILL_EXCHANGE,
                            RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                            msg
                    );
                }
                if (log.isDebugEnabled()) {
                    log.debug("批量发送成功，数量: {}", batch.size());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("批量发送失败，转入 Outbox，数量: {}", batch.size(), e);
            for (SeckillMessage msg : batch) {
                saveToOutbox(msg);
            }
        }
    }

    private void saveToOutbox(SeckillMessage msg) {
        try {
            MqOutbox outbox = new MqOutbox();
            outbox.setExchange(RabbitMQConfig.SECKILL_EXCHANGE);
            outbox.setRoutingKey(RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY);
            outbox.setPayload(objectMapper.writeValueAsString(msg));
            outbox.setStatus(0);
            outbox.setRetryCount(0);
            outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(5));
            outboxMapper.insert(outbox);
        } catch (JsonProcessingException e) {
            log.error("序列化消息到 Outbox 失败，消息将丢失！userId={}, goodsId={}",
                    msg.getUserId(), msg.getGoodsId(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("应用关闭，开始优雅停机...");
        running.set(false);

        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 强制处理队列中剩余的所有消息
        if (!queue.isEmpty()) {
            log.warn("等待超时，强制处理剩余消息，数量: {}", queue.size());
            List<SeckillMessage> remaining = new ArrayList<>();
            queue.drainTo(remaining);
            if (!remaining.isEmpty()) {
                sendBatchWithRetry(remaining);
            }
        }
        log.info("优雅停机完成");
    }
}