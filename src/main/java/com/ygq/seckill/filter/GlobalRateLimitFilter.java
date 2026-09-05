package com.ygq.seckill.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.result.Result;
import com.ygq.seckill.util.DynamicRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Component
@Order(Integer.MIN_VALUE)
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DynamicRateLimiter dynamicRateLimiter;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().contains("/api/seckill/do")) {
            chain.doFilter(request, response);
            return;
        }

        // 尝试获取令牌，立即返回，不等待
        if (!dynamicRateLimiter.tryAcquire()) {
//            long count = rejectedCount.increment();
//            if (count % 1000 == 0) {
//                log.warn("全局限流拦截累计次数: {}", count);
//            }
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.error(CodeMsg.ACCESS_LIMIT_REACHED)));
            return;
        }

        chain.doFilter(request, response);
    }
}