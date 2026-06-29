package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.service.ContentReviewService;
import com.aicp.module.contentproject.service.ContentUnitService;
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

    @GetMapping("/{id}/draft")
    public ApiResponse<DraftView> getDraft(@PathVariable Long id) {
        return ApiResponse.success(unitService.getDraft(SecurityUtil.requireCurrentUserId(), id));
    }

    @PutMapping("/{id}/draft")
    public ApiResponse<DraftView> saveDraft(@PathVariable Long id, @RequestBody SaveDraftRequest request) {
        return ApiResponse.success(unitService.saveDraft(SecurityUtil.requireCurrentUserId(), id, request));
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
}
