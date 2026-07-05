package com.aicp.module.sop.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.sop.domain.SopEnums;
import com.aicp.module.sop.entity.*;
import com.aicp.module.sop.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SopWorkOrderService {

    private final SopWorkOrderMapper workOrderMapper;
    private final SopWorkOrderEventMapper eventMapper;
    private final SopCheckResultMapper checkResultMapper;
    private final SopCheckRunMapper checkRunMapper;
    private final ProjectAccessService accessService;

    @Transactional
    public SopWorkOrder create(Long projectId, Long resultId, String responsibleRole, Long assigneeId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.PRODUCE);

        // Verify result exists and belongs to project
        SopCheckResult result = checkResultMapper.selectById(resultId);
        if (result == null) {
            throw new BizException(ErrorCode.SOP_RUN_NOT_FOUND);
        }
        SopCheckRun run = checkRunMapper.selectById(result.getRunId());
        if (run == null || !run.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.SOP_RUN_NOT_FOUND);
        }

        // Skip if result is PASS — nothing to fix
        if (SopEnums.SopResult.PASS.value().equals(result.getResult())) {
            throw new BizException(ErrorCode.SOP_INVALID_TRANSITION);
        }

        String fingerprint = result.getIssueFingerprint();
        if (fingerprint == null) {
            fingerprint = result.getRuleCode() + ":" + result.getTargetType() + ":" + result.getTargetId();
        }

        // Dedup: check for existing active work order
        Long existingCount = workOrderMapper.selectCount(new LambdaQueryWrapper<SopWorkOrder>()
                .eq(SopWorkOrder::getProjectId, projectId)
                .eq(SopWorkOrder::getIssueFingerprint, fingerprint)
                .eq(SopWorkOrder::getActiveMarker, 1));
        if (existingCount > 0) {
            throw new BizException(ErrorCode.SOP_WORK_ORDER_CONFLICT);
        }

        // Create work order
        SopWorkOrder order = new SopWorkOrder();
        order.setProjectId(projectId);
        order.setRunId(result.getRunId());
        order.setResultId(resultId);
        order.setRuleCode(result.getRuleCode());
        order.setIssueFingerprint(fingerprint);
        order.setStatus(SopEnums.WorkOrderStatus.OPEN.value());
        order.setSeverity(result.getSeverity());
        order.setResponsibleRole(responsibleRole);
        order.setAssigneeId(assigneeId);
        order.setActiveMarker(1);
        workOrderMapper.insert(order);

        // Record creation event
        appendEvent(order.getId(), null, SopEnums.WorkOrderStatus.OPEN, userId, "工单创建");

        log.info("Work order {} created for fingerprint {}", order.getId(), fingerprint);
        return order;
    }

    @Transactional
    public SopWorkOrder transition(Long projectId, Long orderId, SopEnums.WorkOrderStatus targetStatus, String note) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.PRODUCE);

        SopWorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null || !order.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.SOP_RUN_NOT_FOUND);
        }

        SopEnums.WorkOrderStatus current = SopEnums.WorkOrderStatus.valueOf(order.getStatus().toUpperCase());
        if (!current.canTransitionTo(targetStatus)) {
            throw new BizException(ErrorCode.SOP_INVALID_TRANSITION);
        }

        // Handle special transitions
        if (targetStatus == SopEnums.WorkOrderStatus.PASSED) {
            order.setActiveMarker(null); // remove from active index
        }
        if (targetStatus == SopEnums.WorkOrderStatus.ASSIGNED && current == SopEnums.WorkOrderStatus.OPEN) {
            // Assignment happens
        }

        order.setStatus(targetStatus.value());
        workOrderMapper.updateById(order);

        appendEvent(order.getId(), current, targetStatus, userId, note);

        log.info("Work order {} transitioned: {} -> {}", orderId, current.value(), targetStatus.value());
        return order;
    }

    @Transactional
    public SopWorkOrder review(Long projectId, Long orderId, boolean approved, String note) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.REVIEW);

        SopWorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null || !order.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.SOP_RUN_NOT_FOUND);
        }

        SopEnums.WorkOrderStatus current = SopEnums.WorkOrderStatus.valueOf(order.getStatus().toUpperCase());
        SopEnums.WorkOrderStatus target = approved
                ? SopEnums.WorkOrderStatus.PASSED
                : SopEnums.WorkOrderStatus.REOPENED;

        if (!current.canTransitionTo(target)) {
            throw new BizException(ErrorCode.SOP_INVALID_TRANSITION);
        }

        if (target == SopEnums.WorkOrderStatus.PASSED) {
            order.setActiveMarker(null);
        }
        order.setStatus(target.value());
        workOrderMapper.updateById(order);

        appendEvent(order.getId(), current, target, userId,
                note != null ? note : (approved ? "审核通过" : "审核驳回，需重新修复"));

        log.info("Work order {} reviewed: {} -> {}", orderId, current.value(), target.value());
        return order;
    }

    public SopWorkOrder get(Long projectId, Long orderId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.VIEW);

        SopWorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null || !order.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.SOP_RUN_NOT_FOUND);
        }
        return order;
    }

    public List<SopWorkOrderEvent> getEvents(Long orderId) {
        return eventMapper.selectList(new LambdaQueryWrapper<SopWorkOrderEvent>()
                .eq(SopWorkOrderEvent::getWorkOrderId, orderId)
                .orderByAsc(SopWorkOrderEvent::getCreatedAt));
    }

    private void appendEvent(Long orderId, SopEnums.WorkOrderStatus from, SopEnums.WorkOrderStatus to,
                              Long operatorId, String note) {
        SopWorkOrderEvent event = new SopWorkOrderEvent();
        event.setWorkOrderId(orderId);
        event.setFromStatus(from != null ? from.value() : null);
        event.setToStatus(to.value());
        event.setOperatorId(operatorId);
        event.setNote(note);
        eventMapper.insert(event);
    }
}
