package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.entity.StoryboardMaster;
import com.aicp.module.contentproject.entity.StoryboardScene;
import com.aicp.module.contentproject.entity.StoryboardShot;
import com.aicp.module.contentproject.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/storyboard")
@RequiredArgsConstructor
public class ContentStoryboardController {

    private final StoryboardService storyboardService;

    @PostMapping("/generate")
    public ApiResponse<StoryboardMaster> generate(@PathVariable Long projectId,
                                                   @RequestBody Map<String, Object> body) {
        Long unitId = ((Number) body.get("content_unit_id")).longValue();
        return ApiResponse.success(storyboardService.generateATier(
                SecurityUtil.requireCurrentUserId(), projectId, unitId));
    }

    @GetMapping
    public ApiResponse<java.util.List<StoryboardMaster>> listMasters(@PathVariable Long projectId) {
        return ApiResponse.success(storyboardService.listMasters(projectId));
    }

    @GetMapping("/{masterId}")
    public ApiResponse<StoryboardMaster> getMaster(@PathVariable Long projectId,
                                                    @PathVariable Long masterId) {
        return ApiResponse.success(storyboardService.getMaster(masterId));
    }

    @GetMapping("/{masterId}/scenes")
    public ApiResponse<java.util.List<StoryboardScene>> listScenes(@PathVariable Long projectId,
                                                                    @PathVariable Long masterId) {
        return ApiResponse.success(storyboardService.listScenes(masterId));
    }

    @GetMapping("/{masterId}/shots")
    public ApiResponse<java.util.List<StoryboardShot>> listShots(@PathVariable Long projectId,
                                                                  @PathVariable Long masterId) {
        return ApiResponse.success(storyboardService.listShots(masterId));
    }

    @PostMapping("/{masterId}/lock")
    public ApiResponse<Void> lockMaster(@PathVariable Long projectId,
                                         @PathVariable Long masterId) {
        storyboardService.lockMaster(masterId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.success();
    }

    // ===== M5: B/C-tier upgrade =====

    @PostMapping("/{masterId}/upgrade-b")
    public ApiResponse<Map<String, Object>> upgradeToB(@PathVariable Long projectId,
                                                        @PathVariable Long masterId) {
        return ApiResponse.success(storyboardService.upgradeToBTier(masterId,
                SecurityUtil.requireCurrentUserId()));
    }

    @PostMapping("/{masterId}/upgrade-c")
    public ApiResponse<Map<String, Object>> upgradeToC(@PathVariable Long projectId,
                                                        @PathVariable Long masterId) {
        return ApiResponse.success(storyboardService.upgradeToCTier(masterId,
                SecurityUtil.requireCurrentUserId()));
    }
}
