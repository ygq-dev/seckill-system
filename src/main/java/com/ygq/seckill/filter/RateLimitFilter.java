package com.ygq.seckill.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.result.Result;
import com.ygq.seckill.util.LocalRateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private LocalRateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 只拦截秒杀接口
        if (!request.getRequestURI().contains("/seckill/do")) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = auth == null ? null : (Long) auth.getPrincipal();
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }

        String goodsIdStr = request.getParameter("goodsId");
        if (StringUtils.isBlank(goodsIdStr)) {
            chain.doFilter(request, response);
            return;
        }
        Long goodsId = Long.parseLong(goodsIdStr);

        // 纯内存计算
        if (!rateLimiterService.tryAcquireUser(userId) ||
                !rateLimiterService.tryAcquireGoods(goodsId)) {
            // 生产环境使用 debug 级别，避免日志刷盘
            if (log.isDebugEnabled()) {
                log.debug("限流拦截: userId={}, goodsId={}", userId, goodsId);
            }
            writeError(response, CodeMsg.ACCESS_LIMIT_REACHED);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, CodeMsg codeMsg) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(codeMsg)));
    }
}