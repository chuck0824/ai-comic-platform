package com.aicp.common.storage;

import com.aicp.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageApiController {

    private final StorageUploadService storageUploadService;
    private final ObjectStorageService objectStorageService;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "register_asset", defaultValue = "true") boolean registerAsset,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "media_type", required = false) String mediaType,
            @RequestParam(value = "prefix", required = false) String prefix,
            HttpServletRequest request) {
        StorageUploadService.UploadedFile uploaded = registerAsset
                ? storageUploadService.uploadAndRegisterAsset(
                        request, file, name, mediaType, prefix == null ? "uploads" : prefix)
                : storageUploadService.upload(file, prefix == null ? "uploads" : prefix);
        Map<String, Object> body = uploaded.toMap();
        body.put("active_provider", objectStorageService.activeProvider().code());
        return ApiResponse.success(body);
    }
}
