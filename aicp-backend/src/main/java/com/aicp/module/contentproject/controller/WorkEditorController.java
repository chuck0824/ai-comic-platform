package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.WorkEditorRequests.*;
import com.aicp.module.contentproject.dto.WorkEditorViews.*;
import com.aicp.module.contentproject.service.ProjectSettingService;
import com.aicp.module.contentproject.service.WorkEditorService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/content-projects")
@RequiredArgsConstructor
public class WorkEditorController {

    private final WorkEditorService workEditorService;
    private final ProjectSettingService settingService;

    // ===== Legacy entry =====

    @GetMapping("/legacy-scripts/{scriptId}/editor")
    public ApiResponse<EditorView> getLegacyEditor(@PathVariable Long scriptId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(workEditorService.getLegacyEditor(userId, scriptId));
    }

    // ===== Editor aggregation =====

    @GetMapping("/{id}/editor")
    public ApiResponse<EditorView> getEditor(@PathVariable Long id) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(workEditorService.getEditor(userId, id));
    }

    // ===== Tags =====

    @PutMapping("/{id}/tags")
    public ApiResponse<ProfileView> updateTags(@PathVariable Long id,
                                                @RequestBody UpdateTagsRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(workEditorService.updateTags(userId, id, request));
    }

    // ===== Profile =====

    @PatchMapping("/{id}/profile")
    public ApiResponse<ProfileView> updateProfile(@PathVariable Long id,
                                                   @RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(workEditorService.updateProfile(userId, id, request));
    }

    // ===== Settings =====

    @GetMapping("/{id}/settings")
    public ApiResponse<PageResult<Map<String, Object>>> listSettings(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtil.requireCurrentUserId();
        Page<Map<String, Object>> result = settingService.listSettings(userId, id, type, status, keyword, page, pageSize);
        return ApiResponse.success(PageResult.of(result.getRecords(), page, pageSize, result.getTotal()));
    }

    @PostMapping("/{id}/settings")
    public ApiResponse<Map<String, Object>> createSetting(@PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(settingService.createSetting(userId, id, body));
    }

    @GetMapping("/{id}/settings/{settingId}")
    public ApiResponse<Map<String, Object>> getSetting(@PathVariable Long id,
                                                        @PathVariable Long settingId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(settingService.getSetting(userId, id, settingId));
    }

    @PatchMapping("/{id}/settings/{settingId}")
    public ApiResponse<Map<String, Object>> updateSetting(@PathVariable Long id,
                                                           @PathVariable Long settingId,
                                                           @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(settingService.updateSetting(userId, id, settingId, body));
    }

    @DeleteMapping("/{id}/settings/{settingId}")
    public ApiResponse<Void> archiveSetting(@PathVariable Long id,
                                            @PathVariable Long settingId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        settingService.archiveSetting(userId, id, settingId);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/settings/{settingId}/restore")
    public ApiResponse<Map<String, Object>> restoreSetting(@PathVariable Long id,
                                                            @PathVariable Long settingId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(settingService.restoreSetting(userId, id, settingId));
    }

    @PostMapping("/{id}/settings/{settingId}/copy")
    public ApiResponse<Map<String, Object>> copySetting(@PathVariable Long id,
                                                         @PathVariable Long settingId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(settingService.copySetting(userId, id, settingId));
    }

    @GetMapping("/{id}/settings/{settingId}/versions")
    public ApiResponse<List<Map<String, Object>>> listSettingVersions(@PathVariable Long id,
                                                                       @PathVariable Long settingId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(settingService.listVersions(userId, id, settingId));
    }
}
