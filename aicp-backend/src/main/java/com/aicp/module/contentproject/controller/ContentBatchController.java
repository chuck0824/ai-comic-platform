package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
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

    /** Generate content for multiple episodes at once */
    @PostMapping("/batch-generate")
    public ApiResponse<Map<String, Object>> batchGenerate(@PathVariable Long projectId,
                                                           @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        @SuppressWarnings("unchecked")
        List<Long> unitIds = ((List<Number>) body.get("unit_ids")).stream()
                .map(Number::longValue).toList();
        String jobType = (String) body.getOrDefault("job_type", "content_generate");
        String idempotencyKey = "batch-" + projectId + "-" + System.currentTimeMillis();

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
        int count = hookService.generateAllHooks(projectId);
        return ApiResponse.success(Map.of("generated", count));
    }

    /** Get hook summary for a project */
    @GetMapping("/hook-summary")
    public ApiResponse<Map<String, Object>> hookSummary(@PathVariable Long projectId) {
        return ApiResponse.success(hookService.getHookSummary(projectId));
    }

    /** Get hooks for a specific unit */
    @GetMapping("/units/{unitId}/hooks")
    public ApiResponse<Object> getUnitHooks(@PathVariable Long projectId,
                                             @PathVariable Long unitId) {
        var hook = hookService.getHooks(unitId);
        return ApiResponse.success(hook != null ? hook : Map.of());
    }

    /** Capture continuity snapshots for all episodes */
    @PostMapping("/capture-snapshots")
    public ApiResponse<Map<String, Object>> captureSnapshots(@PathVariable Long projectId) {
        int count = continuityService.captureAllSnapshots(projectId);
        return ApiResponse.success(Map.of("captured", count));
    }

    /** Check continuity conflicts */
    @GetMapping("/continuity-conflicts")
    public ApiResponse<List<Map<String, Object>>> checkConflicts(@PathVariable Long projectId) {
        return ApiResponse.success(continuityService.checkConflicts(projectId));
    }
}
