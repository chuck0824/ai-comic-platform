package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * M2: Batch operations — multi-episode generation, hooks, continuity.
 */
@RestController
@RequestMapping("/api/v1/content-projects/{projectId}")
@RequiredArgsConstructor
public class ContentBatchController {

    private final ContentGenerationJobService jobService;
    private final ContentHookService hookService;
    private final ContinuityService continuityService;
    private final AdaptationService adaptationService;
    private final PromotionService promotionService;
    private final ProjectAccessService projectAccessService;

    /** Generate content for multiple episodes at once */
    @PostMapping("/batch-generate")
    public ApiResponse<Map<String, Object>> batchGenerate(@PathVariable Long projectId,
                                                           @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        @SuppressWarnings("unchecked")
        List<Long> unitIds = ((List<Number>) body.get("unit_ids")).stream()
                .map(Number::longValue).toList();
        String jobType = (String) body.getOrDefault("job_type", "content_generate");
        String idempotencyKey = "batch-" + projectId + "-" + jobType + "-"
                + unitIds.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining("-"));

        List<GenerationJobView> jobs = new ArrayList<>();
        for (Long unitId : unitIds) {
            var request = new GenerationJobRequest(jobType, "content_unit", unitId,
                    Map.of(), "", "v1");
            GenerationJobView job = jobService.createJob(userId, projectId, request,
                    idempotencyKey + "-" + unitId);
            jobs.add(job);
        }
        return ApiResponse.success(Map.of("total", unitIds.size(), "jobs", jobs));
    }

    /** Generate hooks for all episodes */
    @PostMapping("/generate-hooks")
    public ApiResponse<Map<String, Object>> generateHooks(@PathVariable Long projectId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        int count = hookService.generateAllHooks(projectId);
        return ApiResponse.success(Map.of("generated", count));
    }

    /** Get hook summary for a project */
    @GetMapping("/hook-summary")
    public ApiResponse<Map<String, Object>> hookSummary(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(hookService.getHookSummary(projectId));
    }

    /** Get hooks for a specific unit */
    @GetMapping("/units/{unitId}/hooks")
    public ApiResponse<Object> getUnitHooks(@PathVariable Long projectId,
                                             @PathVariable Long unitId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        var hook = hookService.getHooks(unitId);
        return ApiResponse.success(hook != null ? hook : Map.of());
    }

    /** Capture continuity snapshots for all episodes */
    @PostMapping("/capture-snapshots")
    public ApiResponse<Map<String, Object>> captureSnapshots(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.EDIT_CONTENT);
        int count = continuityService.captureAllSnapshots(projectId);
        return ApiResponse.success(Map.of("captured", count));
    }

    /** Check continuity conflicts */
    @GetMapping("/continuity-conflicts")
    public ApiResponse<List<Map<String, Object>>> checkConflicts(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(continuityService.checkConflicts(projectId));
    }

    // ===== M2: Adaptation + Promotion =====

    /** Create adaptation from source content */
    @PostMapping("/adapt")
    public ApiResponse<ContentUnit> createAdaptation(@PathVariable Long projectId,
                                                      @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        Long sourceUnitId = ((Number) body.get("source_unit_id")).longValue();
        String format = (String) body.getOrDefault("format", "short_drama");
        boolean multiEpisode = Boolean.TRUE.equals(body.get("multi_episode"));

        ContentUnit result;
        if (multiEpisode) {
            result = adaptationService.createAdaptationMultiEpisode(
                    userId, projectId, sourceUnitId, format);
        } else {
            result = adaptationService.createAdaptation(
                    userId, projectId, sourceUnitId, format);
        }
        return ApiResponse.success(result);
    }

    /** Generate promotional materials */
    @PostMapping("/promote")
    public ApiResponse<Map<String, Object>> generatePromotion(@PathVariable Long projectId,
                                                               @RequestBody Map<String, Object> body) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.EDIT_CONTENT);
        Long sourceUnitId = ((Number) body.get("source_unit_id")).longValue();
        return ApiResponse.success(promotionService.generatePromotion(projectId, sourceUnitId));
    }
}
