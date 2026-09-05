package com.ygq.seckill.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.seckill.config.SeckillInitializer;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.result.Result;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PreCheckFilter implements Filter {

    @Autowired
    private SeckillInitializer seckillInitializer;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (!req.getRequestURI().contains("/api/seckill/do")) {
            chain.doFilter(request, response);
            return;
        }

        // 提取 goodsId
        String goodsIdParam = req.getParameter("goodsId");
        if (goodsIdParam == null) {
            chain.doFilter(request, response);
            return;
        }
        Long goodsId = Long.parseLong(goodsIdParam);

        // 内存标记检查（售罄则直接返回，不解析 JWT）
        if (seckillInitializer.isOver(goodsId)) {
            resp.setContentType("application/json;charset=UTF-8");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(objectMapper.writeValueAsString(Result.error(CodeMsg.SECKILL_OVER)));
            return;
        }

        chain.doFilter(request, response);
    }
}