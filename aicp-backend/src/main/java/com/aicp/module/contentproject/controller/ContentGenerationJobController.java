package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.service.ContentGenerationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/generation-jobs")
@RequiredArgsConstructor
public class ContentGenerationJobController {

    private final ContentGenerationJobService jobService;

    @PostMapping
    public ResponseEntity<ApiResponse<GenerationJobView>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody GenerationJobRequest request) {
        String key = idempotencyKey != null ? idempotencyKey
                : java.util.UUID.randomUUID().toString();
        Long userId = SecurityUtil.requireCurrentUserId();
        // projectId comes from target_id when targetType is "project"
        Long projectId = request.targetId();
        GenerationJobView result = jobService.createJob(userId, projectId, request, key);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<GenerationJobView> get(@PathVariable Long id) {
        return ApiResponse.success(jobService.getJob(SecurityUtil.requireCurrentUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        jobService.cancelJob(SecurityUtil.requireCurrentUserId(), id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<GenerationJobView> accept(@PathVariable Long id) {
        return ApiResponse.success(jobService.acceptJob(SecurityUtil.requireCurrentUserId(), id));
    }

    @PostMapping("/{id}/discard")
    public ApiResponse<GenerationJobView> discard(@PathVariable Long id) {
        return ApiResponse.success(jobService.discardJob(SecurityUtil.requireCurrentUserId(), id));
    }
}
