package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.entity.UploadFile;
import com.aicp.module.contentproject.service.ContentUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/content-projects")
@RequiredArgsConstructor
public class ContentUploadController {

    private final ContentUploadService uploadService;

    @PostMapping("/upload")
    public ApiResponse<UploadFile> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(uploadService.upload(SecurityUtil.requireCurrentUserId(), file));
    }

    @GetMapping("/upload/{uploadId}")
    public ApiResponse<UploadFile> getUpload(@PathVariable Long uploadId) {
        return ApiResponse.success(uploadService.getUpload(uploadId));
    }

    @PostMapping("/upload/{uploadId}/ai-extract")
    public ApiResponse<Map<String, Object>> aiExtract(@PathVariable Long uploadId) {
        return ApiResponse.success(uploadService.aiExtract(uploadId));
    }

    @PostMapping("/upload/{uploadId}/confirm-import")
    public ApiResponse<Map<String, Object>> confirmImport(@PathVariable Long uploadId,
                                                           @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) body.getOrDefault("chapters", List.of());
        Long projectId = ((Number) body.get("project_id")).longValue();
        int created = uploadService.confirmImport(SecurityUtil.requireCurrentUserId(), projectId, uploadId, chapters);
        return ApiResponse.success(Map.of("created_units", created));
    }
}
