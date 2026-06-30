package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.WorkEditorRequests.*;
import com.aicp.module.contentproject.dto.WorkEditorViews.*;
import com.aicp.module.contentproject.service.WorkEditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/content-projects")
@RequiredArgsConstructor
public class WorkEditorController {

    private final WorkEditorService workEditorService;

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
}
