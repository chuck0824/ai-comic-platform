package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.entity.OutboxEvent;
import com.aicp.module.contentproject.mapper.OutboxEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String type, Long aggregateId, int revision, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType("content_project");
        event.setAggregateId(aggregateId);
        event.setAggregateRevision(revision);
        event.setEventType(type);
        event.setPayloadJson(toJson(payload));
        event.setStatus("pending");
        event.setOccurredAt(LocalDateTime.now());
        outboxMapper.insert(event);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Outbox 序列化失败", e);
            return "{}";
        }
    }
}
