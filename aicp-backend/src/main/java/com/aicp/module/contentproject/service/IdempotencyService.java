package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.IdempotencyRecord;
import com.aicp.module.contentproject.mapper.IdempotencyRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * R2-A §6.6：幂等键落库。同键同 hash 重放响应；同键不同 hash → IDEMPOTENCY_PAYLOAD_CONFLICT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int TTL_DAYS = 30;

    private final IdempotencyRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> T execute(Long userId, String idempotencyKey, String scope, Object requestBody,
                         Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        String key = idempotencyKey.trim();
        String requestHash = sha256(canonicalJson(requestBody));

        IdempotencyRecord existing = findActive(userId, key);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BizException(ErrorCode.IDEMPOTENCY_PAYLOAD_CONFLICT);
            }
            try {
                return objectMapper.readValue(existing.getResponsePayload(), responseType);
            } catch (Exception e) {
                log.warn("幂等响应反序列化失败，重新执行 scope={} key={}", scope, key);
                return action.get();
            }
        }

        T result = action.get();
        persist(userId, key, scope, requestHash, result);
        return result;
    }

    private IdempotencyRecord findActive(Long userId, String key) {
        return recordMapper.selectOne(new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getUserId, userId)
                .eq(IdempotencyRecord::getIdempotencyKey, key)
                .gt(IdempotencyRecord::getExpiresAt, LocalDateTime.now())
                .last("limit 1"));
    }

    private void persist(Long userId, String key, String scope, String requestHash, Object result) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setIdempotencyKey(key);
        record.setScope(scope);
        record.setRequestHash(requestHash);
        record.setResponsePayload(canonicalJson(result));
        record.setExpiresAt(LocalDateTime.now().plusDays(TTL_DAYS));
        record.setCreatedAt(LocalDateTime.now());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            IdempotencyRecord raced = findActive(userId, key);
            if (raced != null && !requestHash.equals(raced.getRequestHash())) {
                throw new BizException(ErrorCode.IDEMPOTENCY_PAYLOAD_CONFLICT);
            }
            log.info("幂等并发写入已忽略 userId={} key={}", userId, key);
        } catch (Exception e) {
            // H2 / MySQL 可能包装为其他异常
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                IdempotencyRecord raced = findActive(userId, key);
                if (raced != null && !requestHash.equals(raced.getRequestHash())) {
                    throw new BizException(ErrorCode.IDEMPOTENCY_PAYLOAD_CONFLICT);
                }
                return;
            }
            throw e;
        }
    }

    private String canonicalJson(Object value) {
        try {
            if (value == null) {
                return "null";
            }
            // 先转树再写，降低字段顺序抖动
            return objectMapper.writeValueAsString(objectMapper.valueToTree(value));
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input == null ? 0 : input.hashCode());
        }
    }
}
