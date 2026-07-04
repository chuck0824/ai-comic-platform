package com.aicp.module.asset.service;

import com.aicp.module.asset.entity.AssetOutboxEvent;
import com.aicp.module.asset.mapper.AssetOutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Writes asset domain Outbox events in the same transaction as the source
 * mutation. These events drive the enterprise approval projection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetOutboxService {

    private final AssetOutboxEventMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Emit an asset publish event. Called within the source transaction
     * so the event and the state change are atomic.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void emit(String aggregateType, String aggregateId,
                      String eventType, Map<String, Object> payload) {
        try {
            var event = new AssetOutboxEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setStatus("PENDING");
            event.setAttempts(0);
            event.setCreatedAt(LocalDateTime.now());
            mapper.insert(event);
        } catch (Exception e) {
            log.error("Failed to emit asset Outbox {}:{}:{}: {}",
                    aggregateType, aggregateId, eventType, e.getMessage());
        }
    }
}
