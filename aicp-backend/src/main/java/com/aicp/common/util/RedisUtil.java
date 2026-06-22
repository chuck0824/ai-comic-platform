package com.aicp.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return clazz.cast(value);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    public long getExpire(String key, TimeUnit unit) {
        Long expire = redisTemplate.getExpire(key, unit);
        return expire != null ? expire : -1;
    }

    public void blacklistToken(String token, long timeoutSeconds) {
        set("blacklist:token:" + token, "1", timeoutSeconds, TimeUnit.SECONDS);
    }

    public boolean isTokenBlacklisted(String token) {
        return hasKey("blacklist:token:" + token);
    }

    public void setRefreshToken(Long userId, String refreshToken) {
        set("refresh:user:" + userId, refreshToken, 30, TimeUnit.DAYS);
    }

    public String getRefreshToken(Long userId) {
        return get("refresh:user:" + userId, String.class);
    }

    public void deleteRefreshToken(Long userId) {
        delete("refresh:user:" + userId);
    }
}
