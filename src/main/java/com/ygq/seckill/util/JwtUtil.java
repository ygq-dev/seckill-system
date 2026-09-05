//package com.ygq.seckill.util;
//
//import io.jsonwebtoken.*;
//import io.jsonwebtoken.security.Keys;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import javax.crypto.SecretKey;
//import java.nio.charset.StandardCharsets;
//import java.util.Date;
//
//@Component
//public class JwtUtil {
//    @Value("${jwt.secret}")
//    private String secret;
//
//    @Value("${jwt.expiration}")
//    private Long expiration;
//
//    private SecretKey getSigningKey() {
//        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
//    }
//
//    public String generateToken(Long userId) {
//        Date now = new Date();
//        Date expiryDate = new Date(now.getTime() + expiration);
//        return Jwts.builder()
//                .subject(userId.toString())    //主题：存放用户ID
//                .issuedAt(now)                 //签发时间
//                .expiration(expiryDate)        //过期时间
//                .signWith(getSigningKey())     //签名：防止篡改
//                .compact();                    //压缩成字符串
//    }
//
//    public Long getUserIdFromToken(String token) {
//        Claims claims = Jwts.parser()
//                .verifyWith(getSigningKey())
//                .build()
//                .parseSignedClaims(token)
//                .getPayload();
//        return Long.parseLong(claims.getSubject());
//    }
//
//    public boolean validateToken(String token) {
//        try {
//            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
//            return true;
//        } catch (JwtException | IllegalArgumentException e) {
//            return false;
//        }
//    }
//}
package com.ygq.seckill.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey signingKey;
    private JwtParser jwtParser;

    @PostConstruct
    public void init() {
        // 1. 初始化密钥（只执行一次）
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // 2. 初始化解析器（只执行一次）
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .build();
    }

    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey) // 复用密钥
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        // 复用 jwtParser
        Claims claims = jwtParser
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            // 复用 jwtParser
            jwtParser.parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
