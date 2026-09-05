package com.ygq.seckill.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;



@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        String ip = getClientIP(request);
//        String key = "rate:ip:" + ip;
//        RRateLimiter limiter = redissonClient.getRateLimiter(key);
//        // 每秒最多 5 次请求
//        limiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.SECONDS);
//        if (!limiter.tryAcquire()) {
//            response.setStatus(429);
//            response.getWriter().write("{\"code\":429,\"msg\":\"请求过于频繁\"}");
//            return false;
//        }
        return true;
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        return ip;
    }
}