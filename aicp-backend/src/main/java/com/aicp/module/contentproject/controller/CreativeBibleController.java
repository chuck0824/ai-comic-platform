package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.CreativeBibleRequests.*;
import com.aicp.module.contentproject.dto.CreativeBibleViews.*;
import com.aicp.module.contentproject.service.CreativeBibleService;
import com.aicp.module.contentproject.service.WritingGuideResolver;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects")
@RequiredArgsConstructor
public class CreativeBibleController {

    private final CreativeBibleService bibleService;
    private final WritingGuideResolver guideResolver;

    // ── Bible version ──

    @GetMapping("/{id}/creative-bible")
    public ApiResponse<BibleSummaryView> getCurrent(@PathVariable Long id) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(bibleService.getCurrent(userId, id));
    }

    @GetMapping("/{id}/creative-bible/health")
    public ApiResponse<?> health(@PathVariable Long id) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(bibleService.health(userId, id));
    }

    @PostMapping("/{id}/creative-bible/versions")
    public ApiResponse<BibleSummaryView> createDraft(@PathVariable Long id,
                                                      @Valid @RequestBody CreateBibleDraftRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(bibleService.createDraft(userId, id, request));
    }

    @PostMapping("/{id}/creative-bible/versions/{versionId}/confirm")
    public ApiResponse<BibleSummaryView> confirm(@PathVariable Long id,
                                                  @PathVariable Long versionId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(bibleService.confirm(userId, id, versionId));
    }

    // ── Ecosystem rules ──

    @GetMapping("/{id}/creative-bible/versions/{versionId}/ecosystem-rules")
    public ApiResponse<?> listEcosystem(@PathVariable Long id,
                                         @PathVariable Long versionId,
                                         @RequestParam(required = false) String ruleType,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.requireCurrentUserId();
        Page<EcosystemRuleView> result = bibleService.listEcosystem(
                userId, id, versionId, ruleType, page, pageSize);
        return ApiResponse.success(PageResult.of(
                result.getRecords(), page, pageSize, result.getTotal()));
    }

    @PostMapping("/{id}/creative-bible/versions/{versionId}/ecosystem-rules")
    public ApiResponse<EcosystemRuleView> createEcosystem(@PathVariable Long id,
                                                           @PathVariable Long versionId,
                                                           @Valid @RequestBody UpsertEcosystemRuleRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(
                bibleService.upsertEcosystem(userId, id, versionId, null, request));
    }

    @PatchMapping("/{id}/creative-bible/versions/{versionId}/ecosystem-rules/{ruleId}")
    public ApiResponse<EcosystemRuleView> updateEcosystem(@PathVariable Long id,
                                                           @PathVariable Long versionId,
                                                           @PathVariable Long ruleId,
                                                           @Valid @RequestBody UpsertEcosystemRuleRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(
                bibleService.upsertEcosystem(userId, id, versionId, ruleId, request));
    }

    // ── Writing guides ──

    @GetMapping("/{id}/creative-bible/versions/{versionId}/writing-guides")
    public ApiResponse<List<WritingGuideView>> listWritingGuides(@PathVariable Long id,
                                                                  @PathVariable Long versionId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(bibleService.listWritingGuides(userId, id, versionId));
    }

    @PostMapping("/{id}/creative-bible/versions/{versionId}/writing-guides")
    public ApiResponse<WritingGuideView> saveWritingGuide(@PathVariable Long id,
                                                           @PathVariable Long versionId,
                                                           @Valid @RequestBody UpsertWritingGuideRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(bibleService.saveWritingGuide(userId, id, versionId, request));
    }

    @PostMapping("/{id}/creative-bible/versions/{versionId}/writing-guides/resolve")
    public ApiResponse<ResolvedWritingGuideView> resolveWritingGuide(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @Valid @RequestBody ResolveWritingGuideRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(guideResolver.resolve(
                id, versionId, request.contentUnitId(), request.characterIds()));
    }

    // ── Lifecycle ──

    @PostMapping("/{id}/creative-bible/versions/{versionId}/submit-review")
    public ApiResponse<BibleSummaryView> submitReview(@PathVariable Long id,
                                                       @PathVariable Long versionId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(bibleService.submitReview(userId, id, versionId));
    }

    @PostMapping("/{id}/creative-bible/versions/{versionId}/archive")
    public ApiResponse<Void> archive(@PathVariable Long id,
                                     @PathVariable Long versionId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        bibleService.archive(userId, id, versionId);
        return ApiResponse.success();
    }
}
