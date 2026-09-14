package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.LocalRewriteAdoptRequest;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.LocalRewriteRequest;
import com.aicp.module.contentproject.service.ContentReviewService;
import com.aicp.module.contentproject.service.ContentUnitService;
import com.aicp.module.contentproject.service.IdempotencyService;
import com.aicp.module.contentproject.service.LocalRewriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/content-units")
@RequiredArgsConstructor
public class ContentUnitController {

    private final ContentUnitService unitService;
    private final ContentReviewService reviewService;
    private final LocalRewriteService localRewriteService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/{id}/draft")
    public ApiResponse<DraftView> getDraft(@PathVariable Long id) {
        return ApiResponse.success(unitService.getDraft(SecurityUtil.requireCurrentUserId(), id));
    }

    @PutMapping("/{id}/draft")
    public ApiResponse<DraftView> saveDraft(@PathVariable Long id, @RequestBody SaveDraftRequest request) {
        return ApiResponse.success(unitService.saveDraft(SecurityUtil.requireCurrentUserId(), id, request));
    }

    @GetMapping("/{id}/conflicts")
    public ApiResponse<Map<String, Object>> getConflicts(
            @PathVariable Long id,
            @RequestParam(value = "base_revision", required = false) Integer baseRevision) {
        return ApiResponse.success(unitService.getConflictComparison(
                SecurityUtil.requireCurrentUserId(), id, baseRevision));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<ContentVersionView>> listVersions(@PathVariable Long id) {
        return ApiResponse.success(unitService.listVersions(id));
    }

    @PostMapping("/{id}/versions")
    public ApiResponse<ContentVersionView> createVersion(@PathVariable Long id,
                                                          @RequestBody CreateVersionRequest request) {
        return ApiResponse.success(unitService.createVersion(
                SecurityUtil.requireCurrentUserId(), id, request));
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public ApiResponse<DraftView> restoreVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(unitService.restoreVersion(
                SecurityUtil.requireCurrentUserId(), id, versionId));
    }

    // ===== M1: Three-Agent Review =====

    @PostMapping("/{id}/review")
    public ApiResponse<Map<String, Object>> reviewUnit(@PathVariable Long id) {
        return ApiResponse.success(reviewService.reviewUnit(id));
    }

    // ===== R2-A.3 Local AI rewrite =====

    @PostMapping("/{id}/local-rewrite")
    public ApiResponse<Map<String, Object>> createLocalRewrite(
            @PathVariable Long id,
            @RequestBody LocalRewriteRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = SecurityUtil.requireCurrentUserId();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = idempotencyService.execute(
                userId,
                idempotencyKey,
                "local-rewrite:" + id,
                request,
                (Class<Map<String, Object>>) (Class<?>) Map.class,
                () -> localRewriteService.createRewrite(userId, id, request));
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/local-rewrite/adopt")
    public ApiResponse<DraftView> adoptLocalRewrite(
            @PathVariable Long id,
            @RequestBody LocalRewriteAdoptRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(idempotencyService.execute(
                userId,
                idempotencyKey,
                "local-rewrite-adopt:" + id,
                request,
                DraftView.class,
                () -> localRewriteService.adoptPatches(userId, id, request)));
    }
}
