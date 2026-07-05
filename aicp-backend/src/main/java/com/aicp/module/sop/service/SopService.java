package com.aicp.module.sop.service;

import com.aicp.common.dto.PageResult;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.sop.domain.SopCheckContext;
import com.aicp.module.sop.domain.SopEnums;
import com.aicp.module.sop.domain.SopRuleDefinition;
import com.aicp.module.sop.domain.SopRuleEvaluation;
import com.aicp.module.sop.entity.*;
import com.aicp.module.sop.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SopService {

    private final SopCheckRunMapper checkRunMapper;
    private final SopCheckResultMapper checkResultMapper;
    private final SopWorkOrderMapper workOrderMapper;
    private final SopContextAssembler contextAssembler;
    private final SopRuleEngine ruleEngine;
    private final SopRuleCatalog ruleCatalog;
    private final ProjectAccessService accessService;
    private final ObjectMapper objectMapper;

    // ===== Check operations =====

    @Transactional
    public SopCheckRun runCheck(Long projectId, Long contentUnitId, Long canvasProjectId,
                                 SopEnums.TriggerType triggerType) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.PRODUCE);

        SopCheckContext context = contextAssembler.assemble(projectId, contentUnitId, canvasProjectId);
        String ruleSetVersion = ruleCatalog.getActiveRuleSetVersion();

        // Idempotency: reuse existing completed run
        SopCheckRun existing = checkRunMapper.selectOne(new LambdaQueryWrapper<SopCheckRun>()
                .eq(SopCheckRun::getProjectId, projectId)
                .eq(SopCheckRun::getScopeHash, context.scopeHash())
                .eq(SopCheckRun::getRuleSetVersion, ruleSetVersion)
                .eq(SopCheckRun::getSnapshotHash, context.snapshotHash())
                .eq(SopCheckRun::getStatus, SopEnums.RunStatus.COMPLETED.value()));
        if (existing != null) {
            log.info("Reusing existing check run {} for project {}", existing.getId(), projectId);
            return existing;
        }

        // Create run
        SopCheckRun run = new SopCheckRun();
        run.setProjectId(projectId);
        run.setContentUnitId(contentUnitId);
        run.setCanvasProjectId(canvasProjectId);
        run.setTriggerType(triggerType.value());
        run.setRuleSetVersion(ruleSetVersion);
        run.setScopeHash(context.scopeHash());
        run.setSnapshotHash(context.snapshotHash());
        run.setSourceRevisionsJson(toJson(context.sourceRevisions()));
        run.setStatus(SopEnums.RunStatus.RUNNING.value());
        run.setCreatedBy(userId);
        checkRunMapper.insert(run);

        // Evaluate rules
        List<SopRuleDefinition> activeRules = ruleCatalog.getActiveRules();
        List<SopRuleEvaluation> evaluations = ruleEngine.evaluateAll(context, activeRules);

        // Persist results
        List<SopCheckResult> results = new ArrayList<>();
        for (SopRuleEvaluation eval : evaluations) {
            SopCheckResult result = new SopCheckResult();
            result.setRunId(run.getId());
            result.setRuleCode(eval.ruleCode());
            result.setResult(eval.result().value());
            result.setSeverity(eval.severity().value());
            result.setCritical(eval.critical() ? 1 : 0);
            result.setTargetType(eval.targetType());
            result.setTargetId(eval.targetId());
            result.setIssueFingerprint(eval.issueFingerprint());
            result.setEvidenceJson(toJson(eval.evidence()));
            result.setSuggestion(eval.suggestion());
            result.setFixPolicy(eval.fixPolicy().value());
            results.add(result);
        }
        if (!results.isEmpty()) {
            for (SopCheckResult result : results) {
                checkResultMapper.insert(result);
            }
        }

        // Aggregate counts
        int passed = 0, warnings = 0, blocked = 0, notReady = 0, errors = 0;
        for (SopRuleEvaluation eval : evaluations) {
            switch (eval.result()) {
                case PASS -> passed++;
                case WARNING -> warnings++;
                case BLOCKED -> blocked++;
                case NOT_READY -> notReady++;
                case ERROR -> errors++;
            }
        }

        // Compute overall status
        if (blocked > 0 || errors > 0) {
            run.setOverallStatus(SopEnums.OverallStatus.RED.value());
        } else if (warnings > 0 || notReady > 0) {
            run.setOverallStatus(SopEnums.OverallStatus.YELLOW.value());
        } else {
            run.setOverallStatus(SopEnums.OverallStatus.GREEN.value());
        }

        run.setPassedCount(passed);
        run.setWarningCount(warnings);
        run.setBlockedCount(blocked);
        run.setNotReadyCount(notReady);
        run.setErrorCount(errors);
        run.setStatus(SopEnums.RunStatus.COMPLETED.value());
        run.setCompletedAt(LocalDateTime.now());
        checkRunMapper.updateById(run);

        log.info("Check run {} completed: overall={}, P={} W={} B={} N={} E={}",
                run.getId(), run.getOverallStatus(), passed, warnings, blocked, notReady, errors);
        return run;
    }

    // ===== Report queries =====

    public SopCheckRun getRun(Long projectId, Long runId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.VIEW);

        SopCheckRun run = checkRunMapper.selectById(runId);
        if (run == null || !run.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.SOP_RUN_NOT_FOUND);
        }

        // Check staleness
        SopCheckContext current = contextAssembler.assemble(projectId, run.getContentUnitId(), run.getCanvasProjectId());
        if (!current.snapshotHash().equals(run.getSnapshotHash())) {
            run.setStatus(SopEnums.RunStatus.STALE.value());
            checkRunMapper.updateById(run);
        }

        return run;
    }

    public List<SopCheckResult> getResults(Long runId) {
        return checkResultMapper.selectList(new LambdaQueryWrapper<SopCheckResult>()
                .eq(SopCheckResult::getRunId, runId));
    }

    public PageResult<SopCheckRun> listChecks(Long projectId, int page, int size) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.VIEW);

        Page<SopCheckRun> pageResult = checkRunMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<SopCheckRun>()
                        .eq(SopCheckRun::getProjectId, projectId)
                        .orderByDesc(SopCheckRun::getCreatedAt));
        return PageResult.of(pageResult.getRecords(), page, size, pageResult.getTotal());
    }

    // ===== Work order queries =====

    public PageResult<SopWorkOrder> listWorkOrders(Long projectId, int page, int size, String statusFilter) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.VIEW);

        LambdaQueryWrapper<SopWorkOrder> query = new LambdaQueryWrapper<SopWorkOrder>()
                .eq(SopWorkOrder::getProjectId, projectId)
                .orderByDesc(SopWorkOrder::getCreatedAt);
        if (statusFilter != null && !statusFilter.isBlank()) {
            query.eq(SopWorkOrder::getStatus, statusFilter);
        }
        Page<SopWorkOrder> pageResult = workOrderMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(pageResult.getRecords(), page, size, pageResult.getTotal());
    }

    // ===== Utility =====

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            return "{}";
        }
    }
}
