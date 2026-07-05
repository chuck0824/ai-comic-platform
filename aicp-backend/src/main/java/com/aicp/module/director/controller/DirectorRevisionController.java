package com.aicp.module.director.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.director.domain.DirectorDocument;
import com.aicp.module.director.entity.DirectorDraft;
import com.aicp.module.director.entity.DirectorRevision;
import com.aicp.module.director.entity.DirectorScene;
import com.aicp.module.director.service.DirectorDocumentValidator;
import com.aicp.module.director.service.DirectorSceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/canvas/projects/{projectId}/shot-units/{unitId}/director-scene")
@RequiredArgsConstructor
public class DirectorRevisionController {

    private final DirectorSceneService service;

    /** 读取导演场景摘要（含当前草稿和最新 revision） */
    @GetMapping
    public ApiResponse<DirectorSceneView> getScene(@PathVariable Long unitId) {
        DirectorScene scene = service.getOrCreateScene(unitId);
        DirectorDraft draft = service.getDraft(scene.getId());
        return ApiResponse.success(new DirectorSceneView(
                scene.getUuid(), scene.getShotUnitId(),
                draft != null ? draft.getRowVersion() : 0,
                scene.getCurrentRevisionId()));
    }

    public record DirectorSceneView(String sceneUuid, Long shotUnitId, int draftVersion, Long currentRevisionId) {}

    /** 读取当前草稿 JSON */
    @GetMapping("/draft")
    public ApiResponse<String> getDraft(@PathVariable Long unitId) {
        DirectorScene scene = service.getOrCreateScene(unitId);
        DirectorDraft draft = service.getDraft(scene.getId());
        return ApiResponse.success(draft != null ? draft.getDocumentJson() : "{}");
    }

    /** 乐观锁保存草稿 */
    @PutMapping("/draft")
    public ResponseEntity<ApiResponse<DraftSaveResult>> saveDraft(
            @PathVariable Long unitId,
            @RequestHeader(value = "If-Match", defaultValue = "0") int expectedVersion,
            @RequestBody DirectorDocument document) {
        try {
            DirectorScene scene = service.getOrCreateScene(unitId);
            DirectorDraft draft = service.saveDraft(scene.getId(), expectedVersion, document,
                    SecurityUtil.requireCurrentUserId());
            return ResponseEntity.ok(ApiResponse.success(
                    new DraftSaveResult(draft.getUuid(), draft.getRowVersion())));
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            return ResponseEntity.status(409).body(ApiResponse.error(43003, "编辑冲突: " + e.getMessage()));
        }
    }

    public record DraftSaveResult(String draftUuid, int newVersion) {}

    /** 校验当前草稿 */
    @PostMapping("/validate")
    public ApiResponse<DirectorDocumentValidator.ValidationResult> validate(@PathVariable Long unitId) {
        DirectorScene scene = service.getOrCreateScene(unitId);
        return ApiResponse.success(service.validate(scene.getId()));
    }

    /** 冻结不可变 revision */
    @PostMapping("/revisions")
    public ApiResponse<DirectorRevision> freezeRevision(
            @PathVariable Long unitId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        DirectorScene scene = service.getOrCreateScene(unitId);
        return ApiResponse.success(service.freeze(scene.getId(), idempotencyKey,
                SecurityUtil.requireCurrentUserId()));
    }

    /** 读取指定 revision */
    @GetMapping("/revisions/{revisionId}")
    public ApiResponse<DirectorRevision> getRevision(@PathVariable Long revisionId) {
        // TODO: R2 — revision read endpoint
        return ApiResponse.success(null);
    }
}
