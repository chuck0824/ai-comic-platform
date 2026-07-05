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
public class SopGateService {

    private final SopService sopService;
    private final SopGateDecisionMapper gateDecisionMapper;
    private final SopCheckResultMapper checkResultMapper;
    private final ProjectAccessService accessService;

    @Transactional
    public SopGateDecision evaluate(Long projectId, Long contentUnitId, Long canvasProjectId,
                                     SopEnums.GateType gateType, String idempotencyKey) {
        Long userId = SecurityUtil.requireCurrentUserId();
        accessService.require(projectId, userId, Action.PRODUCE);

        // Idempotency check
        SopGateDecision existing = gateDecisionMapper.selectOne(new LambdaQueryWrapper<SopGateDecision>()
                .eq(SopGateDecision::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            log.info("Returning existing gate decision {} for idempotency key {}", existing.getId(), idempotencyKey);
            return existing;
        }

        // Run or reuse a check
        SopCheckRun run = sopService.runCheck(projectId, contentUnitId, canvasProjectId, SopEnums.TriggerType.GATE);

        // Verify not stale
        if (SopEnums.RunStatus.STALE.value().equals(run.getStatus())) {
            throw new BizException(ErrorCode.SOP_RUN_STALE);
        }

        // Load results
        List<SopCheckResult> results = checkResultMapper.selectList(new LambdaQueryWrapper<SopCheckResult>()
                .eq(SopCheckResult::getRunId, run.getId()));

        // Evaluate: deny if any BLOCKED, critical NOT_READY, or critical ERROR
        List<SopCheckResult> blockers = results.stream()
                .filter(r -> {
                    SopEnums.SopResult result = SopEnums.SopResult.valueOf(r.getResult().toUpperCase());
                    boolean isCritical = r.getCritical() != null && r.getCritical() == 1;
                    return result == SopEnums.SopResult.BLOCKED
                            || (result == SopEnums.SopResult.NOT_READY && isCritical)
                            || (result == SopEnums.SopResult.ERROR && isCritical);
                })
                .toList();

        boolean allowed = blockers.isEmpty();

        // Persist decision
        SopGateDecision decision = new SopGateDecision();
        decision.setProjectId(projectId);
        decision.setRunId(run.getId());
        decision.setGateType(gateType.value());
        decision.setAllowed(allowed ? 1 : 0);
        decision.setBlockerCount(blockers.size());
        decision.setIdempotencyKey(idempotencyKey);
        gateDecisionMapper.insert(decision);

        if (!allowed) {
            log.warn("Gate {} BLOCKED for project {}: {} blocker(s)", gateType.value(), projectId, blockers.size());
            throw new BizException(ErrorCode.SOP_GATE_BLOCKED,
                    "生产准入 Gate 未通过：存在 " + blockers.size() + " 项阻断问题");
        }

        log.info("Gate {} ALLOWED for project {}", gateType.value(), projectId);
        return decision;
    }
}
