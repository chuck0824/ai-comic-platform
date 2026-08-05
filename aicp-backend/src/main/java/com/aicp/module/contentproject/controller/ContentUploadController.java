package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.storage.ObjectStorageService;
import com.aicp.common.storage.SignedUrl;
import com.aicp.common.storage.StorageObjectRef;
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
    private final ObjectStorageService objectStorageService;

    @PostMapping("/upload")
    public ApiResponse<UploadFile> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(uploadService.upload(SecurityUtil.requireCurrentUserId(), file));
    }

    @GetMapping("/upload/{uploadId}")
    public ApiResponse<UploadFile> getUpload(@PathVariable Long uploadId) {
        return ApiResponse.success(uploadService.getUpload(uploadId));
    }

    @GetMapping("/upload/{uploadId}/download-url")
    public ApiResponse<Map<String, String>> downloadUrl(@PathVariable Long uploadId) {
        UploadFile uf = uploadService.getUpload(uploadId);
        if (uf.getUserId() != null && !uf.getUserId().equals(SecurityUtil.requireCurrentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权下载该上传文件");
        }
        if (uf.getStorageProvider() == null || uf.getStorageBucket() == null || uf.getStorageKey() == null) {
            throw new BizException(ErrorCode.ASSET_FILE_MISSING, "原件未入库对象存储");
        }
        SignedUrl signed = objectStorageService.signDownloadUrl(StorageObjectRef.of(
                uf.getStorageProvider(), uf.getStorageBucket(), uf.getStorageKey()));
        return ApiResponse.success(Map.of(
                "url", signed.url(),
                "expiresAt", signed.expiresAt().toString()));
    }

    @PostMapping("/upload/{uploadId}/ai-extract")
    public ApiResponse<Map<String, Object>> aiExtract(@PathVariable Long uploadId) {
        return ApiResponse.success(uploadService.aiExtract(uploadId));
    }

    @PostMapping("/upload/{uploadId}/confirm-import")
    public ApiResponse<Map<String, Object>> confirmImport(
            @PathVariable Long uploadId,
            @RequestBody Map<String, Object> body) {
        Long projectId = ((Number) body.get("project_id")).longValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) body.get("chapters");
        int created = uploadService.confirmImport(
                SecurityUtil.requireCurrentUserId(), projectId, uploadId, chapters);
        return ApiResponse.success(Map.of("created_units", created));
    }
}
