package com.ygq.seckill.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RedisRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        // 初始化Lua脚本
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(
                "local key = KEYS[1] " +
                        "local limit = tonumber(ARGV[1]) " +
                        "local current = tonumber(redis.call('get', key) or '0') " +
                        "if current + 1 > limit then " +
                        "    return 0 " +
                        "else " +
                        "    redis.call('incr', key) " +
                        "    redis.call('expire', key, 1) " + // 每秒过期
                        "    return 1 " +
                        "end"
        );
        this.rateLimitScript.setResultType(Long.class);
    }

    public boolean tryAcquire(String key, int limit) {
        Long result = stringRedisTemplate.execute(
                rateLimitScript,
                Arrays.asList(key),
                String.valueOf(limit)
        );
        return result != null && result == 1L;
    }
}