package com.ygq.seckill.controller;

import com.ygq.seckill.config.SeckillInitializer;
import com.ygq.seckill.rabbitmq.SeckillMessage;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.result.Result;
import com.ygq.seckill.service.GoodsCacheService;
import com.ygq.seckill.service.OrderService;
import com.ygq.seckill.util.LocalMessageQueue;
import com.ygq.seckill.vo.GoodsVo;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
@Slf4j
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillInitializer seckillInitializer;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private GoodsCacheService goodsCacheService;
    @Autowired
    private LocalMessageQueue localMessageQueue;

    @PostMapping("/do")
    public Result<Integer> doSeckill(@RequestParam Long goodsId,
                                     @AuthenticationPrincipal Long userId) {
        // 1. 内存标记快速拦截
        if (seckillInitializer.isOver(goodsId)) {
            return Result.error(CodeMsg.SECKILL_OVER);
        }

        // 2. 商品信息校验
        GoodsVo goods = goodsCacheService.getGoodsVoById(goodsId);
        if (goods == null) {
            return Result.error(CodeMsg.GOODS_NOT_EXIST);
        }

        // 3. 秒杀时间校验
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(goods.getStartDate())) {
            return Result.error(CodeMsg.SECKILL_NOT_START);
        }
        if (now.isAfter(goods.getEndDate())) {
            return Result.error(CodeMsg.SECKILL_END);
        }

        // 4. 写入本地队列（非阻塞）
        if (!localMessageQueue.offer(new SeckillMessage(userId, goodsId))) {
            // 队列满，走降级
            log.warn("本地队列已满，请求被拒绝: userId={}, goodsId={}", userId, goodsId);
            return Result.error(CodeMsg.ACCESS_LIMIT_REACHED);
        }

        // 5. 立即返回排队中
        return Result.success(0);
    }

    @GetMapping("/result")
    public Result<Long> result(@RequestParam Long goodsId, @AuthenticationPrincipal Long userId) {
        Long orderId = orderService.getSeckillResult(userId, goodsId);
        return Result.success(orderId);
    }


    @DeleteMapping("/reset/{goodsId}")
    public Result<String> resetUser(@PathVariable Long goodsId, @AuthenticationPrincipal Long userId) {
        String userSetKey = "seckill:users:" + goodsId;
        String orderKey = "order:user:" + userId + ":goods:" + goodsId;
        redisTemplate.opsForSet().remove(userSetKey, userId);
        redisTemplate.delete(orderKey);
        return Result.success("已清除用户秒杀标记");
    }
}