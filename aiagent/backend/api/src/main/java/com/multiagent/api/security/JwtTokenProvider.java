package com.multiagent.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 生成、验证与刷新。
 * 读取 application.yml 中 jwt.secret / jwt.expiration-ms / jwt.refresh-threshold-ms 配置。
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshThresholdMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.refresh-threshold-ms}") long refreshThresholdMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshThresholdMs = refreshThresholdMs;
    }

    /** 为指定 userId 生成 JWT Token */
    public String generateToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** 从 Token 中提取 userId (subject) */
    public String getUserId(String token) {
        return parseClaims(token).getSubject();
    }

    /** 校验 Token 是否有效（签名 + 未过期） */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 若 Token 剩余有效期 < refreshThresholdMs，自动签发新 Token；
     * 否则返回 null 表示无需刷新。
     */
    public String refreshIfNeeded(String token) {
        try {
            Claims claims = parseClaims(token);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining > 0 && remaining < refreshThresholdMs) {
                return generateToken(claims.getSubject());
            }
        } catch (Exception e) {
            log.debug("Cannot refresh token: {}", e.getMessage());
        }
        return null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
