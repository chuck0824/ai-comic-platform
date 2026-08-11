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
                // uid 用字符串，避免 JS / Go 对 >2^53 雪花 ID 的 JSON number 精度丢失
                .claim("uid", String.valueOf(userId))
                .claim("uuid", uuid)
                .claim("type", accountType)
                .claim("role", role != null ? role : "free_user")
                .claim("permissions", permissions != null ? permissions : List.of())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpire * 1000))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Short-lived one-shot SSO ticket for bridging 8080 ↔ 3001 browser sessions.
     * Validated by the peer with the same {@code JWT_SECRET}/{@code AICP_JWT_SECRET}.
     */
    public String generateSsoTicket(Long userId, String uuid, String nickname) {
        Date now = new Date();
        String jti = UUID.randomUUID().toString().replace("-", "");
        return Jwts.builder()
                .id(jti)
                .subject(uuid)
                .claim("uid", String.valueOf(userId))
                .claim("uuid", uuid)
                .claim("nickname", nickname != null ? nickname : "")
                .claim("purpose", "sso")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
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
        return readUidClaim(claims.get("uid"));
    }

    /** Accept string or numeric uid claims (new tokens use string). */
    static Long readUidClaim(Object uid) {
        if (uid == null) {
            return null;
        }
        if (uid instanceof Number number) {
            return number.longValue();
        }
        if (uid instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalArgumentException("unsupported uid claim type: " + uid.getClass().getName());
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
