package com.ygq.seckill.filter;

import jakarta.servlet.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求的调用链追踪
 * @author
 */
@Slf4j
public class LogTraceIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        MDC.clear();
        MDC.put("trace_id", UUID.randomUUID().toString());
        chain.doFilter(request, response);
    }
}