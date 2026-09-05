package com.ygq.seckill.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
@Slf4j
@Service
public class RedisService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<List> SECKILL_SCRIPT = new DefaultRedisScript<>();

    static {
        // Lua 脚本：原子扣减库存 + 防重
        String script =
                "local stockKey = KEYS[1]\n" +
                        "local userSetKey = KEYS[2]\n" +
                        "local userId = ARGV[1]\n" +
                        "local quantity = tonumber(ARGV[2])\n" +
                        "local stock = redis.call('get', stockKey)\n" +
                        "if not stock or tonumber(stock) < quantity then\n" +
                        "    return {0, '库存不足'}\n" +
                        "end\n" +
                        "local isMember = redis.call('sismember', userSetKey, userId)\n" +
                        "if isMember == 1 then\n" +
                        "    return {0, '重复秒杀'}\n" +
                        "end\n" +
                        "local newStock = redis.call('decrby', stockKey, quantity)\n" +
                        "if newStock < 0 then\n" +
                        "    redis.call('incrby', stockKey, quantity)\n" +
                        "    return {0, '库存不足'}\n" +
                        "end\n" +
                        "redis.call('sadd', userSetKey, userId)\n" +
                        "return {1, newStock}\n";
        SECKILL_SCRIPT.setScriptText(script);
        SECKILL_SCRIPT.setResultType(List.class);
    }

//    public List executeSeckillLua(Long goodsId, Long userId) {
//        long t1 = System.nanoTime();
//        // 获取连接（Lettuce 连接池）
//        String stockKey = "stock:" + goodsId;
//        String userSetKey = "seckill:users:" + goodsId;
//        long t2 = System.nanoTime();
//        List result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Arrays.asList(stockKey, userSetKey),
//                userId.toString(),
//                "1"
//        );
//        long t3 = System.nanoTime();
//        // 打印三个时间：连接获取耗时、执行耗时、总耗时
//        log.info("Redis 耗时: 连接获取={}μs, 执行={}μs, 总={}μs",
//                (t2-t1)/1000, (t3-t2)/1000, (t3-t1)/1000);
//        return result;
//    }

    public List executeSeckillLua(Long goodsId, Long userId) {
        String stockKey = "stock:" + goodsId;
        String userSetKey = "seckill:users:" + goodsId;
        return stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(stockKey, userSetKey),
                userId.toString(),
                "1"
        );
    }
}
