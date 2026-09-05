package com.ygq.seckill.filter;

import com.ygq.seckill.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = null;
        Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers.hasMoreElements()) {
            token = headers.nextElement();
        }
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
//        System.out.println("直接获取 Authorization: " + request.getHeader("Authorization"));
//        System.out.println("收到token: " + token);
//        Enumeration<String> headerNames = request.getHeaderNames();
//        while (headerNames.hasMoreElements()) {
//            String name = headerNames.nextElement();
//            System.out.println(name + "=" + request.getHeader(name));
//        }
//        String token = extractToken(request);
//        System.out.println("收到token: " + token);
//        try {
//            Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
//            System.out.println("解析成功: " + claims.getSubject());
//        } catch (JwtException e) {
//            System.out.println("JWT解析失败: " + e.getMessage());
//        }
        if (token != null && jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.getUserIdFromToken(token);
//            System.out.println("解析userId: " + userId);
            // 设置认证信息
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
//            System.out.println("token无效或缺失");
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers.hasMoreElements()) {
            String bearerToken = headers.nextElement();
            if (bearerToken != null) {
                bearerToken = bearerToken.trim();
                if (bearerToken.startsWith("Bearer ")) {
                    return bearerToken.substring(7);
                }
            }
        }
        return null;
    }
}