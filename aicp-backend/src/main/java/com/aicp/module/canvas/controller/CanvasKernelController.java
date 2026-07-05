package com.aicp.module.canvas.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.canvas.dto.CanvasMigrationViews;
import com.aicp.module.canvas.entity.CanvasShotUnit;
import com.aicp.module.canvas.entity.ShotAdoption;
import com.aicp.module.canvas.service.*;
import com.aicp.module.generation.capability.CapabilityCompiler;
import com.aicp.module.generation.entity.GenerationCandidate;
import com.aicp.module.generation.service.ModelRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Canvas 生产内核 R1 API。
 * ShotWorkUnit、类型化端口、候选、正式采用、单画布升级。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/canvas")
@RequiredArgsConstructor
public class CanvasKernelController {

    private final CanvasKernelService kernelService;
    private final CanvasPortRegistry portRegistry;
    private final ShotAdoptionService adoptionService;
    private final CanvasUpgradeService upgradeService;
    private final CanvasLegacyAuditService auditService;
    private final ModelRequestService modelRequestService;

    // ===== ShotWorkUnit =====

    /** 获取项目的所有 ShotWorkUnit */
    @GetMapping("/projects/{projectId}/shot-units")
    public ApiResponse<List<CanvasShotUnit>> listShotUnits(@PathVariable Long projectId) {
        return ApiResponse.success(kernelService.listByProject(projectId));
    }

    /** 创建 ShotWorkUnit */
    @PostMapping("/projects/{projectId}/shot-units")
    public ApiResponse<CanvasShotUnit> createShotUnit(
            @PathVariable Long projectId,
            @RequestBody CanvasKernelService.CreateUnitRequest request) {
        return ApiResponse.success(kernelService.createUnit(
                projectId, request, SecurityUtil.requireCurrentUserId()));
    }

    /** 更新 ShotWorkUnit（乐观锁） */
    @PatchMapping("/projects/{projectId}/shot-units/{unitId}")
    public ApiResponse<CanvasShotUnit> updateShotUnit(
            @PathVariable Long projectId,
            @PathVariable Long unitId,
            @RequestHeader("If-Match") int expectedVersion,
            @RequestBody CanvasKernelService.UpdateUnitRequest request) {
        return ApiResponse.success(kernelService.updateUnit(
                projectId, unitId, request, expectedVersion, SecurityUtil.requireCurrentUserId()));
    }

    // ===== Port Validation =====

    /** 校验端口连接（前端拖拽即时反馈） */
    @PostMapping("/ports/validate")
    public ApiResponse<CanvasPortRegistry.ConnectionDecision> validatePort(
            @RequestBody PortValidateRequest request) {
        return ApiResponse.success(portRegistry.validate(
                request.sourceType(), request.sourcePort(),
                request.targetType(), request.targetPort(), request.role()));
    }

    public record PortValidateRequest(String sourceType, String sourcePort,
                                       String targetType, String targetPort, String role) {}

    // ===== Model Requests (R3) =====

    /** 模型请求预览 */
    @PostMapping("/nodes/{nodeId}/model-requests/preview")
    public ApiResponse<?> previewModelRequest(@PathVariable Long nodeId,
                                               @RequestBody CapabilityCompiler.CompileInput input) {
        return ApiResponse.success(modelRequestService.preview(nodeId, input));
    }

    /** 模型请求提交 */
    @PostMapping("/nodes/{nodeId}/model-requests")
    public ApiResponse<ModelRequestService.SubmitResult> submitModelRequest(
            @PathVariable Long nodeId,
            @RequestBody ModelSubmitRequest request) {
        return ApiResponse.success(modelRequestService.submit(
                nodeId, request.input(), request.confirmedFingerprint(),
                request.idempotencyKey(), SecurityUtil.requireCurrentUserId()));
    }

    public record ModelSubmitRequest(CapabilityCompiler.CompileInput input,
                                      String confirmedFingerprint, String idempotencyKey) {}

    // ===== Candidates & Adoption =====

    /** 节点候选列表 */
    @GetMapping("/nodes/{nodeId}/candidates")
    public ApiResponse<List<GenerationCandidate>> listCandidates(@PathVariable Long nodeId) {
        return ApiResponse.success(modelRequestService.listCandidates(nodeId));
    }

    /** 更新节点局部候选选择 */
    @PutMapping("/nodes/{nodeId}/candidate-selection")
    public ApiResponse<Void> selectCandidate(@PathVariable Long nodeId,
                                              @RequestBody SelectCandidateRequest request) {
        modelRequestService.selectCandidate(nodeId, request.candidateId());
        return ApiResponse.success();
    }

    public record SelectCandidateRequest(Long candidateId) {}

    /** 创建正式采用 */
    @PostMapping("/projects/{projectId}/shot-units/{unitId}/adoptions")
    public ApiResponse<ShotAdoption> createAdoption(
            @PathVariable Long projectId,
            @PathVariable Long unitId,
            @RequestBody AdoptionRequest request) {
        return ApiResponse.success(adoptionService.adopt(
                unitId, request.candidateId(), SecurityUtil.requireCurrentUserId(),
                request.reason(), request.overrideReason()));
    }

    public record AdoptionRequest(Long candidateId, String reason, String overrideReason) {}

    // ===== Migration & Upgrade =====

    /** 只读迁移审计报告 */
    @GetMapping("/projects/{projectId}/migration-report")
    public ApiResponse<CanvasMigrationViews.MigrationAuditReport> getMigrationReport(
            @PathVariable String projectId) {
        return ApiResponse.success(auditService.report(projectId));
    }

    /** 执行单画布升级（需先通过预检） */
    @PostMapping("/projects/{projectId}/upgrade")
    public ApiResponse<CanvasUpgradeService.UpgradeResult> upgradeProject(
            @PathVariable String projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(upgradeService.upgrade(
                projectId, idempotencyKey, SecurityUtil.requireCurrentUserId()));
    }
}
