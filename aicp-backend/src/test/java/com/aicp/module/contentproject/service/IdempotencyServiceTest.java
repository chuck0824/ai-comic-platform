package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.IdempotencyRecord;
import com.aicp.module.contentproject.mapper.IdempotencyRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock IdempotencyRecordMapper recordMapper;
    IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(recordMapper, new ObjectMapper());
    }

    @Test
    void blankKeyExecutesWithoutPersist() {
        AtomicInteger calls = new AtomicInteger();
        String result = service.execute(1L, "  ", "scope", Map.of("a", 1), String.class, () -> {
            calls.incrementAndGet();
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void sameKeySamePayloadReplaysStoredResponse() {
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setRequestHash(null); // filled after first call compute — use spy path via select
        // First call: no existing → insert
        when(recordMapper.selectOne(any())).thenReturn(null);
        Map<String, Object> first = service.execute(9L, "k-1", "stage", Map.of("x", 1),
                (Class<Map<String, Object>>) (Class<?>) Map.class,
                () -> Map.of("project_id", 12));
        assertThat(first.get("project_id")).isEqualTo(12);

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(recordMapper).insert(captor.capture());
        IdempotencyRecord saved = captor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo("k-1");
        assertThat(saved.getUserId()).isEqualTo(9L);

        // Second call: same hash → replay
        when(recordMapper.selectOne(any())).thenReturn(saved);
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> second = service.execute(9L, "k-1", "stage", Map.of("x", 1),
                (Class<Map<String, Object>>) (Class<?>) Map.class,
                () -> {
                    calls.incrementAndGet();
                    return Map.of("project_id", 99);
                });
        assertThat(calls.get()).isZero();
        assertThat(second.get("project_id")).isEqualTo(12);
    }

    @Test
    void sameKeyDifferentPayloadConflicts() {
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setRequestHash("deadbeef");
        existing.setResponsePayload("{\"ok\":true}");
        when(recordMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.execute(1L, "k-2", "scope", Map.of("n", 2),
                Map.class, () -> Map.of()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_PAYLOAD_CONFLICT.getCode());
    }
}
