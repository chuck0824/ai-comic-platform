package com.aicp.module.enterprise.service;

import com.aicp.module.enterprise.entity.EnterpriseAuditIndex;
import com.aicp.module.enterprise.mapper.EnterpriseAuditIndexMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseAuditProjector {

    private final EnterpriseAuditIndexMapper mapper;

    @Transactional
    public void index(String workspaceId, String departmentId, Long actorUserId,
                      String action, String objectType, String objectId, String result,
                      String sourceDomain, String sourceRecordId, String requestId,
                      String redactedSummary, String eventId) {
        // Idempotency by event_id
        var existing = mapper.selectOne(new LambdaQueryWrapper<EnterpriseAuditIndex>()
                .eq(EnterpriseAuditIndex::getEventId, eventId));
        if (existing != null) return;

        var entry = new EnterpriseAuditIndex();
        entry.setWorkspaceId(workspaceId);
        entry.setDepartmentId(departmentId != null ? departmentId : "");
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setObjectType(objectType);
        entry.setObjectId(objectId);
        entry.setResult(result != null ? result : "SUCCESS");
        entry.setSourceDomain(sourceDomain);
        entry.setSourceRecordId(sourceRecordId);
        entry.setRequestId(requestId);
        entry.setRedactedSummary(redactedSummary);
        entry.setEventId(eventId);
        entry.setCreatedAt(LocalDateTime.now());
        mapper.insert(entry);
    }
}
