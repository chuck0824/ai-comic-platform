package com.aicp.module.storyboard.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.storyboard.dto.StoryboardRequests.CreateStoryboardRequest;
import com.aicp.module.storyboard.dto.StoryboardViews.StoryboardDetail;
import com.aicp.module.storyboard.dto.StoryboardViews.StoryboardSummary;
import com.aicp.module.storyboard.service.StoryboardQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/storyboards")
@RequiredArgsConstructor
public class StoryboardV2Controller {

    private final StoryboardQueryService queryService;

    @PostMapping
    public ApiResponse<StoryboardDetail> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateStoryboardRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        StoryboardDetail detail = queryService.createStoryboard(
                projectId, userId, request.contentUnitId(),
                request.sourceContentVersionId(), request.title(), request.purpose());
        return ApiResponse.success(detail);
    }

    @GetMapping
    public ApiResponse<List<StoryboardSummary>> list(@PathVariable Long projectId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(queryService.listStoryboards(projectId, userId));
    }

    @GetMapping("/{storyboardId}")
    public ApiResponse<StoryboardDetail> get(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(queryService.getStoryboardDetail(projectId, storyboardId, userId));
    }
}
