package com.aicp.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    @Value("${jwt.access-token-expire:7200}")
    private long accessTokenExpire;

    @Value("${jwt.refresh-token-expire:2592000}")
    private long refreshTokenExpire;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String uuid, String accountType,
                                       String role, List<String> permissions) {
        Date now = new Date();
        return Jwts.builder()
                .subject(uuid)
                .claim("uid", userId)
                .claim("type", accountType)
                .claim("role", role != null ? role : "free_user")
                .claim("permissions", permissions != null ? permissions : List.of())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpire * 1000))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("purpose", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpire * 1000))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.warn("Token无效: {}", e.getMessage());
            throw e;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("uid", Long.class);
    }

    public String getUserUuid(String token) {
        return parseToken(token).getSubject();
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        return parseToken(token).get("permissions", List.class);
    }

    public String getAccountType(String token) {
        return parseToken(token).get("type", String.class);
    }
}
