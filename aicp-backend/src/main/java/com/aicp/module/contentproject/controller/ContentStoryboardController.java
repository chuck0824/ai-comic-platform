package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.StoryboardMaster;
import com.aicp.module.contentproject.entity.StoryboardScene;
import com.aicp.module.contentproject.entity.StoryboardShot;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.contentproject.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/storyboard")
@RequiredArgsConstructor
public class ContentStoryboardController {

    private final StoryboardService storyboardService;
    private final ProjectAccessService projectAccessService;

    @PostMapping("/generate")
    public ApiResponse<StoryboardMaster> generate(@PathVariable Long projectId,
                                                   @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        Long unitId = ((Number) body.get("content_unit_id")).longValue();
        return ApiResponse.success(storyboardService.generateATier(userId, projectId, unitId));
    }

    @GetMapping
    public ApiResponse<java.util.List<StoryboardMaster>> listMasters(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(storyboardService.listMasters(projectId));
    }

    @GetMapping("/{masterId}")
    public ApiResponse<StoryboardMaster> getMaster(@PathVariable Long projectId,
                                                    @PathVariable Long masterId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(storyboardService.getMaster(masterId));
    }

    @GetMapping("/{masterId}/scenes")
    public ApiResponse<java.util.List<StoryboardScene>> listScenes(@PathVariable Long projectId,
                                                                    @PathVariable Long masterId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(storyboardService.listScenes(masterId));
    }

    @GetMapping("/{masterId}/shots")
    public ApiResponse<java.util.List<StoryboardShot>> listShots(@PathVariable Long projectId,
                                                                  @PathVariable Long masterId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(storyboardService.listShots(masterId));
    }

    @PostMapping("/{masterId}/lock")
    public ApiResponse<Void> lockMaster(@PathVariable Long projectId,
                                         @PathVariable Long masterId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        storyboardService.lockMaster(masterId, userId);
        return ApiResponse.success();
    }

    // ===== M5: B/C-tier upgrade =====

    @PostMapping("/{masterId}/upgrade-b")
    public ApiResponse<Map<String, Object>> upgradeToB(@PathVariable Long projectId,
                                                        @PathVariable Long masterId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        return ApiResponse.success(storyboardService.upgradeToBTier(masterId, userId));
    }

    @PostMapping("/{masterId}/upgrade-c")
    public ApiResponse<Map<String, Object>> upgradeToC(@PathVariable Long projectId,
                                                        @PathVariable Long masterId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        return ApiResponse.success(storyboardService.upgradeToCTier(masterId, userId));
    }
}
