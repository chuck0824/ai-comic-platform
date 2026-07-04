package com.aicp.module.enterprise.service;

import com.aicp.module.enterprise.entity.EnterpriseApprovalItem;
import com.aicp.module.enterprise.mapper.EnterpriseApprovalItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumes domain Outbox events and maintains the rebuildable
 * enterprise_approval_items projection. Only writes to the projection
 * table — never authorizes commands.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalProjector {

    private final EnterpriseApprovalItemMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Project a trade or asset event into the approval inbox.
     * Idempotent: duplicate events with the same source_type + source_id
     * are skipped; out-of-order events are ignored (source_version check).
     */
    @Transactional
    public void project(String sourceType, String sourceId, int sourceVersion,
                        String workspaceId, String departmentId,
                        Long requesterUserId, String summary, Long amountCents,
                        String status, String allowedActionsJson) {

        var existing = mapper.selectOne(new LambdaQueryWrapper<EnterpriseApprovalItem>()
                .eq(EnterpriseApprovalItem::getSourceType, sourceType)
                .eq(EnterpriseApprovalItem::getSourceId, sourceId));

        if (existing != null) {
            // Only update if source version is newer
            if (sourceVersion <= existing.getSourceVersion()) {
                log.debug("Skipping stale event: {}:{} v{} <= v{}",
                        sourceType, sourceId, sourceVersion, existing.getSourceVersion());
                return;
            }
            existing.setSourceVersion(sourceVersion);
            existing.setStatus(status);
            existing.setSummary(summary);
            existing.setAllowedActionsJson(allowedActionsJson);
            existing.setLastEventAt(LocalDateTime.now());
            if ("APPROVED".equals(status) || "REJECTED".equals(status)) {
                existing.setDecidedAt(LocalDateTime.now());
            }
            mapper.updateById(existing);
            return;
        }

        var item = new EnterpriseApprovalItem();
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setSourceVersion(sourceVersion);
        item.setWorkspaceId(workspaceId);
        item.setDepartmentId(departmentId != null ? departmentId : "");
        item.setRequesterUserId(requesterUserId);
        item.setSummary(summary);
        item.setAmountCents(amountCents);
        item.setCurrency("CNY");
        item.setStatus(status);
        item.setAllowedActionsJson(allowedActionsJson);
        item.setSubmittedAt(LocalDateTime.now());
        item.setLastEventAt(LocalDateTime.now());
        item.setRowVersion(0);
        mapper.insert(item);
    }

    /**
     * Parse a trade Outbox event payload and project it.
     */
    public void projectFromPayload(String sourceType, String aggregateId,
                                    int sourceVersion, String payload) {
        try {
            JsonNode p = objectMapper.readTree(payload);
            project(
                    sourceType,
                    p.path("source_id").asText(aggregateId),
                    sourceVersion,
                    p.path("workspace_id").asText(),
                    p.path("department_id").asText(""),
                    p.path("requester_user_id").asLong(),
                    p.path("summary").asText(""),
                    p.path("amount_cents").asLong(),
                    p.path("status").asText("PENDING"),
                    p.path("allowed_actions_json").asText("[]")
            );
        } catch (Exception e) {
            log.error("Failed to project Outbox event {}:{}: {}", sourceType, aggregateId, e.getMessage());
        }
    }
}
