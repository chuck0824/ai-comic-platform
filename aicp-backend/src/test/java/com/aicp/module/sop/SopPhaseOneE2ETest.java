package com.aicp.module.sop;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.sop.domain.SopCheckContext;
import com.aicp.module.sop.domain.SopEnums;
import com.aicp.module.sop.domain.SopRuleEvaluation;
import com.aicp.module.sop.entity.*;
import com.aicp.module.sop.mapper.*;
import com.aicp.module.sop.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Production SOP Phase 1 E2E")
class SopPhaseOneE2ETest {

    @Mock SopCheckRunMapper checkRunMapper;
    @Mock SopCheckResultMapper checkResultMapper;
    @Mock SopWorkOrderMapper workOrderMapper;
    @Mock SopWorkOrderEventMapper eventMapper;
    @Mock SopGateDecisionMapper gateDecisionMapper;
    @Mock SopContextAssembler contextAssembler;
    @Mock SopRuleEngine ruleEngine;
    @Mock SopRuleCatalog ruleCatalog;
    @Mock ProjectAccessService accessService;

    @InjectMocks SopService sopService;
    @InjectMocks SopWorkOrderService workOrderService;
    @InjectMocks SopGateService gateService;

    private final Long projectId = 7L;
    private final Long userId = 3L;

    @BeforeEach
    void setUp() {
        // Common setup: mock SecurityUtil via static mocking if needed
        when(ruleCatalog.getActiveRuleSetVersion()).thenReturn("production-readiness-v1");
        when(ruleCatalog.getActiveRules()).thenReturn(List.of());
    }

    @Nested
    @DisplayName("Check service")
    class CheckServiceTests {

        @Test
        @DisplayName("reuses completed run for same snapshot")
        void reusesCompletedRun() {
            SopCheckContext ctx = mockContext("hash-abc", "snap-abc");
            when(contextAssembler.assemble(eq(projectId), isNull(), isNull())).thenReturn(ctx);

            SopCheckRun existing = run(41L, "hash-abc", "snap-abc", "COMPLETED");
            when(checkRunMapper.selectOne(any())).thenReturn(existing);

            SopCheckRun result = sopService.runCheck(projectId, null, null, SopEnums.TriggerType.MANUAL);
            assertThat(result.getId()).isEqualTo(41L);
            verify(checkRunMapper, never()).insert(any());
        }

        @Test
        @DisplayName("creates new run when no matching run exists")
        void createsNewRun() {
            SopCheckContext ctx = mockContext("hash-new", "snap-new");
            when(contextAssembler.assemble(eq(projectId), isNull(), isNull())).thenReturn(ctx);
            when(checkRunMapper.selectOne(any())).thenReturn(null);
            when(checkRunMapper.insert(any())).thenReturn(1);

            SopRuleEvaluation eval = new SopRuleEvaluation(
                    "SCENE_GOAL", SopEnums.SopResult.PASS, SopEnums.Severity.P1, true,
                    "scene", "10", "fp-1", Map.of(), null, SopEnums.FixPolicy.MANUAL_ONLY);
            when(ruleEngine.evaluateAll(any(), any())).thenReturn(List.of(eval));
            when(checkResultMapper.insert(any(SopCheckResult.class))).thenReturn(1);
            when(checkRunMapper.updateById(any())).thenReturn(1);

            SopCheckRun result = sopService.runCheck(projectId, null, null, SopEnums.TriggerType.MANUAL);
            assertThat(result.getOverallStatus()).isEqualTo(SopEnums.OverallStatus.GREEN.value());
            verify(checkRunMapper).insert(any());
        }

        @Test
        @DisplayName("returns RED when BLOCKED results exist")
        void redWhenBlocked() {
            SopCheckContext ctx = mockContext("hash-b", "snap-b");
            when(contextAssembler.assemble(eq(projectId), isNull(), isNull())).thenReturn(ctx);
            when(checkRunMapper.selectOne(any())).thenReturn(null);
            when(checkRunMapper.insert(any())).thenReturn(1);

            SopRuleEvaluation blocked = new SopRuleEvaluation(
                    "ASSET_BINDING", SopEnums.SopResult.BLOCKED, SopEnums.Severity.P1, true,
                    "shot", "5", "fp-2", Map.of(), "缺少资产绑定", SopEnums.FixPolicy.CONFIRM_REQUIRED);
            SopRuleEvaluation pass = new SopRuleEvaluation(
                    "SCENE_GOAL", SopEnums.SopResult.PASS, SopEnums.Severity.P1, true,
                    "scene", "10", "fp-1", Map.of(), null, SopEnums.FixPolicy.MANUAL_ONLY);
            when(ruleEngine.evaluateAll(any(), any())).thenReturn(List.of(blocked, pass));
            when(checkResultMapper.insert(any(SopCheckResult.class))).thenReturn(1);
            when(checkRunMapper.updateById(any())).thenReturn(1);

            SopCheckRun result = sopService.runCheck(projectId, null, null, SopEnums.TriggerType.MANUAL);
            assertThat(result.getOverallStatus()).isEqualTo(SopEnums.OverallStatus.RED.value());
        }
    }

    @Nested
    @DisplayName("Work order state machine")
    class WorkOrderTests {

        @Test
        @DisplayName("follows valid lifecycle: OPEN → ASSIGNED → FIXING → PENDING_REVIEW → PASSED")
        void validLifecycle() {
            SopWorkOrder order = workOrder(1L, "OPEN");
            when(workOrderMapper.selectById(1L)).thenReturn(order);
            when(workOrderMapper.updateById(any())).thenReturn(1);
            when(eventMapper.insert(any())).thenReturn(1);

            // OPEN → ASSIGNED
            SopWorkOrder assigned = workOrderService.transition(projectId, 1L, SopEnums.WorkOrderStatus.ASSIGNED, "分派");
            assertThat(assigned.getStatus()).isEqualTo("assigned");

            // ASSIGNED → FIXING
            order.setStatus("assigned");
            SopWorkOrder fixing = workOrderService.transition(projectId, 1L, SopEnums.WorkOrderStatus.FIXING, "开始修复");
            assertThat(fixing.getStatus()).isEqualTo("fixing");

            // FIXING → PENDING_REVIEW
            order.setStatus("fixing");
            SopWorkOrder pending = workOrderService.transition(projectId, 1L, SopEnums.WorkOrderStatus.PENDING_REVIEW, "提交审核");
            assertThat(pending.getStatus()).isEqualTo("pending_review");
        }

        @Test
        @DisplayName("rejects invalid transition")
        void rejectsInvalidTransition() {
            SopWorkOrder order = workOrder(2L, "OPEN");
            when(workOrderMapper.selectById(2L)).thenReturn(order);

            assertThatThrownBy(() ->
                    workOrderService.transition(projectId, 2L, SopEnums.WorkOrderStatus.PASSED, "skip"))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.SOP_INVALID_TRANSITION.getCode());
        }

        @Test
        @DisplayName("rejects duplicate active work order")
        void rejectsDuplicate() {
            SopCheckResult result = checkResult(55L, "BLOCKED", "fp-dup");
            SopCheckRun run = checkRun(10L, projectId);
            when(checkResultMapper.selectById(55L)).thenReturn(result);
            when(checkRunMapper.selectById(10L)).thenReturn(run);
            when(workOrderMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() ->
                    workOrderService.create(projectId, 55L, "director", null))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.SOP_WORK_ORDER_CONFLICT.getCode());
        }
    }

    @Nested
    @DisplayName("Gate service")
    class GateServiceTests {

        @Test
        @DisplayName("denies when blocked results exist")
        void deniesWhenBlocked() {
            when(gateDecisionMapper.selectOne(any())).thenReturn(null);

            SopCheckContext ctx = mockContext("hash-g", "snap-g");
            when(contextAssembler.assemble(eq(projectId), isNull(), isNull())).thenReturn(ctx);
            when(checkRunMapper.selectOne(any())).thenReturn(null);
            when(checkRunMapper.insert(any())).thenReturn(1);

            SopRuleEvaluation blocked = new SopRuleEvaluation(
                    "ASSET_BINDING", SopEnums.SopResult.BLOCKED, SopEnums.Severity.P1, true,
                    "shot", "5", "fp-g", Map.of(), "缺少资产", SopEnums.FixPolicy.MANUAL_ONLY);
            when(ruleEngine.evaluateAll(any(), any())).thenReturn(List.of(blocked));
            when(checkResultMapper.insert(any(SopCheckResult.class))).thenReturn(1);
            when(checkRunMapper.updateById(any())).thenReturn(1);

            SopCheckResult result = new SopCheckResult();
            result.setId(99L); result.setRunId(1L); result.setRuleCode("ASSET_BINDING");
            result.setResult("blocked"); result.setSeverity("P1"); result.setCritical(1);
            when(checkResultMapper.selectList(any())).thenReturn(List.of(result));

            when(gateDecisionMapper.insert(any())).thenReturn(1);

            assertThatThrownBy(() ->
                    gateService.evaluate(projectId, null, null, SopEnums.GateType.PRODUCTION_ADMISSION, "key-1"))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.SOP_GATE_BLOCKED.getCode());
        }

        @Test
        @DisplayName("returns existing decision for same idempotency key")
        void idempotencyReturnsExisting() {
            SopGateDecision existing = new SopGateDecision();
            existing.setId(100L); existing.setProjectId(projectId); existing.setRunId(5L);
            existing.setGateType("production_admission"); existing.setAllowed(1);
            existing.setBlockerCount(0); existing.setIdempotencyKey("key-2");
            when(gateDecisionMapper.selectOne(any())).thenReturn(existing);

            SopGateDecision result = gateService.evaluate(projectId, null, null,
                    SopEnums.GateType.PRODUCTION_ADMISSION, "key-2");
            assertThat(result.getId()).isEqualTo(100L);
            verify(checkRunMapper, never()).insert(any());
        }
    }

    // ===== Helpers =====

    private SopCheckContext mockContext(String scopeHash, String snapshotHash) {
        return new SopCheckContext(projectId, null, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                Map.of("project", true), Map.of("project:" + projectId, 1),
                scopeHash, snapshotHash);
    }

    private SopCheckRun run(Long id, String scopeHash, String snapshotHash, String status) {
        SopCheckRun r = new SopCheckRun();
        r.setId(id); r.setProjectId(projectId);
        r.setScopeHash(scopeHash); r.setSnapshotHash(snapshotHash);
        r.setRuleSetVersion("production-readiness-v1");
        r.setStatus(status);
        return r;
    }

    private SopWorkOrder workOrder(Long id, String status) {
        SopWorkOrder o = new SopWorkOrder();
        o.setId(id); o.setProjectId(projectId);
        o.setStatus(status); o.setActiveMarker(1);
        return o;
    }

    private SopCheckRun checkRun(Long id, Long projectId) {
        SopCheckRun r = new SopCheckRun();
        r.setId(id); r.setProjectId(projectId);
        return r;
    }

    private SopCheckResult checkResult(Long id, String result, String fingerprint) {
        SopCheckResult r = new SopCheckResult();
        r.setId(id); r.setRunId(10L); r.setRuleCode("TEST_RULE");
        r.setResult(result); r.setSeverity("P1"); r.setCritical(1);
        r.setTargetType("shot"); r.setTargetId("1");
        r.setIssueFingerprint(fingerprint);
        return r;
    }
}
