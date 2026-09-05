package com.ygq.seckill.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RetryQueueService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String RETRY_QUEUE_KEY = "seckill:retry:queue";

    public void addRetryTask(Long userId, Long goodsId) {
        String task = userId + ":" + goodsId;
        redisTemplate.opsForList().rightPush(RETRY_QUEUE_KEY, task);
    }

    public List<String> popRetryTasks(int batchSize) {
        List<String> tasks = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            Object taskObj = redisTemplate.opsForList().leftPop(RETRY_QUEUE_KEY);
            if (taskObj == null) break;
            tasks.add((String) taskObj);
        }
        return tasks;
    }

    public boolean isInRetryQueue(Long userId, Long goodsId) {
        String task = userId + ":" + goodsId;
        return redisTemplate.opsForList().range(RETRY_QUEUE_KEY, 0, -1).contains(task);
    }
}