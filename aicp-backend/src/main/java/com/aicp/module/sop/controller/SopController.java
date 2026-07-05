package com.aicp.module.sop.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.sop.domain.SopEnums;
import com.aicp.module.sop.dto.SopRequests.*;
import com.aicp.module.sop.dto.SopViews.*;
import com.aicp.module.sop.entity.*;
import com.aicp.module.sop.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sop")
@RequiredArgsConstructor
public class SopController {

    private final SopService sopService;
    private final SopWorkOrderService workOrderService;
    private final SopGateService gateService;

    @Value("${sop.enabled:true}")
    private boolean sopEnabled;

    private void checkEnabled() {
        if (!sopEnabled) {
            throw new BizException(ErrorCode.SOP_MODULE_DISABLED);
        }
    }

    // ===== Project list =====

    @GetMapping("/projects")
    public ApiResponse<PageResult<SopCheckRun>> listProjects(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkEnabled();
        // For Phase 1, list recent checks across projects
        // In production, this would join with project_members
        return ApiResponse.success(null); // stub — requires project member join
    }

    // ===== Summary =====

    @GetMapping("/projects/{projectId}/summary")
    public ApiResponse<SopSummaryView> getSummary(@PathVariable Long projectId) {
        checkEnabled();
        Long userId = SecurityUtil.requireCurrentUserId();
        SopCheckRun latest = sopService.listChecks(projectId, 1, 1).getItems().stream().findFirst().orElse(null);
        if (latest == null) {
            return ApiResponse.success(new SopSummaryView(projectId, null, 0, 0, 0, 0, 0, null, null, false));
        }
        List<SopCheckResult> results = sopService.getResults(latest.getId());
        SopSummaryView view = new SopSummaryView(
                projectId,
                latest.getOverallStatus(),
                latest.getPassedCount(),
                latest.getWarningCount(),
                latest.getBlockedCount(),
                latest.getNotReadyCount(),
                latest.getErrorCount(),
                latest.getId(),
                latest.getCompletedAt(),
                SopEnums.RunStatus.STALE.value().equals(latest.getStatus())
        );
        return ApiResponse.success(view);
    }

    // ===== Checks =====

    @PostMapping("/projects/{projectId}/checks")
    public ApiResponse<RunCheckResponse> runCheck(@PathVariable Long projectId,
                                                   @Valid @RequestBody RunCheckRequest request) {
        checkEnabled();
        SopEnums.TriggerType triggerType = SopEnums.TriggerType.valueOf(request.triggerType().toUpperCase());
        SopCheckRun run = sopService.runCheck(projectId, request.contentUnitId(), request.canvasProjectId(), triggerType);
        return ApiResponse.success(new RunCheckResponse(run.getId(), run.getOverallStatus(),
                run.getBlockedCount(), run.getWarningCount()));
    }

    @GetMapping("/projects/{projectId}/checks")
    public ApiResponse<PageResult<SopCheckRun>> listChecks(@PathVariable Long projectId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        checkEnabled();
        return ApiResponse.success(sopService.listChecks(projectId, page, size));
    }

    @GetMapping("/projects/{projectId}/checks/{runId}")
    public ApiResponse<CheckReportView> getCheckReport(@PathVariable Long projectId,
                                                        @PathVariable Long runId) {
        checkEnabled();
        SopCheckRun run = sopService.getRun(projectId, runId);
        List<SopCheckResult> results = sopService.getResults(runId);
        List<CheckResultView> resultViews = results.stream().map(r -> new CheckResultView(
                r.getId(), r.getRuleCode(), r.getResult(), r.getSeverity(),
                r.getCritical() == 1, r.getTargetType(), r.getTargetId(),
                r.getIssueFingerprint(), r.getEvidenceJson(), r.getSuggestion(), r.getFixPolicy()
        )).toList();
        CheckReportView report = new CheckReportView(
                run.getId(), run.getProjectId(), run.getOverallStatus(), run.getStatus(),
                run.getRuleSetVersion(), run.getPassedCount(), run.getWarningCount(),
                run.getBlockedCount(), run.getNotReadyCount(), run.getErrorCount(),
                resultViews, run.getCreatedAt(), run.getCompletedAt()
        );
        return ApiResponse.success(report);
    }

    // ===== Work orders =====

    @GetMapping("/projects/{projectId}/work-orders")
    public ApiResponse<PageResult<SopWorkOrder>> listWorkOrders(@PathVariable Long projectId,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int size,
                                                                  @RequestParam(required = false) String status) {
        checkEnabled();
        return ApiResponse.success(sopService.listWorkOrders(projectId, page, size, status));
    }

    @PostMapping("/projects/{projectId}/work-orders")
    public ApiResponse<WorkOrderView> createWorkOrder(@PathVariable Long projectId,
                                                       @Valid @RequestBody CreateWorkOrderRequest request) {
        checkEnabled();
        SopWorkOrder order = workOrderService.create(projectId, request.resultId(),
                request.responsibleRole(), request.assigneeId());
        return ApiResponse.success(toView(order));
    }

    @PatchMapping("/projects/{projectId}/work-orders/{id}")
    public ApiResponse<WorkOrderView> transitionWorkOrder(@PathVariable Long projectId,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody TransitionWorkOrderRequest request) {
        checkEnabled();
        SopEnums.WorkOrderStatus target = SopEnums.WorkOrderStatus.valueOf(request.toStatus().toUpperCase());
        SopWorkOrder order = workOrderService.transition(projectId, id, target, request.note());
        return ApiResponse.success(toView(order));
    }

    @PostMapping("/projects/{projectId}/work-orders/{id}/review")
    public ApiResponse<WorkOrderView> reviewWorkOrder(@PathVariable Long projectId,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody ReviewWorkOrderRequest request) {
        checkEnabled();
        SopWorkOrder order = workOrderService.review(projectId, id, request.approved(), request.note());
        return ApiResponse.success(toView(order));
    }

    // ===== Gate =====

    @PostMapping("/projects/{projectId}/gates/{gateType}/evaluate")
    public ApiResponse<GateDecisionView> evaluateGate(@PathVariable Long projectId,
                                                       @PathVariable String gateType,
                                                       @Valid @RequestBody EvaluateGateRequest request) {
        checkEnabled();
        SopEnums.GateType type = SopEnums.GateType.valueOf(gateType.toUpperCase());
        SopGateDecision decision = gateService.evaluate(projectId, request.contentUnitId(),
                request.canvasProjectId(), type,
                request.idempotencyKey() != null ? request.idempotencyKey() : UUID.randomUUID().toString());
        GateDecisionView view = new GateDecisionView(
                decision.getId(), decision.getProjectId(), decision.getRunId(),
                decision.getGateType(), decision.getAllowed() == 1, decision.getBlockerCount(),
                decision.getIdempotencyKey(), decision.getCreatedAt()
        );
        return ApiResponse.success(view);
    }

    // ===== Fixes (Phase 1 stub) =====

    @PostMapping("/projects/{projectId}/fixes/{resultId}")
    public ApiResponse<String> executeFix(@PathVariable Long projectId,
                                          @PathVariable Long resultId,
                                          @Valid @RequestBody ExecuteFixRequest request) {
        checkEnabled();
        // Phase 1: stub — returns accepted
        return ApiResponse.success("fix_accepted");
    }

    // ===== Compatibility =====

    @PostMapping("/check/production-readiness")
    public ApiResponse<RunCheckResponse> compatibilityReadiness(
            @Valid @RequestBody CompatibilityReadinessRequest request) {
        checkEnabled();
        SopCheckRun run = sopService.runCheck(request.projectId(), request.contentUnitId(), null,
                SopEnums.TriggerType.MANUAL);
        return ApiResponse.success(new RunCheckResponse(run.getId(), run.getOverallStatus(),
                run.getBlockedCount(), run.getWarningCount()));
    }

    // ===== Utility =====

    private WorkOrderView toView(SopWorkOrder o) {
        return new WorkOrderView(o.getId(), o.getProjectId(), o.getRunId(), o.getResultId(),
                o.getRuleCode(), o.getIssueFingerprint(), o.getStatus(), o.getSeverity(),
                o.getResponsibleRole(), o.getAssigneeId(), o.getResolutionNote(), o.getDeadline(), o.getCreatedAt());
    }
}
