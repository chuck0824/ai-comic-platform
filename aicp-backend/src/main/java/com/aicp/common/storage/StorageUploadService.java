package com.aicp.common.storage;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.entity.AssetActivityLog;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.AssetActivityLogMapper;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageUploadService {

    private final ObjectStorageService objectStorageService;
    private final WorkspaceAssetMapper workspaceAssetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final AssetActivityLogMapper activityLogMapper;

    public record UploadedFile(
            StorageObjectRef ref,
            SignedUrl downloadUrl,
            String originalFilename,
            String contentType,
            long size,
            String assetUuid,
            Long assetId,
            Long versionId
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("storage_provider", ref.provider().code());
            map.put("storage_bucket", ref.bucket());
            map.put("storage_key", ref.key());
            map.put("storage_uri", StorageRefCodec.encode(ref));
            map.put("url", downloadUrl.url());
            map.put("expires_at", downloadUrl.expiresAt().toString());
            map.put("file_name", originalFilename);
            map.put("content_type", contentType);
            map.put("size", size);
            if (assetUuid != null) {
                map.put("asset_uuid", assetUuid);
                map.put("asset_id", assetId);
                map.put("version_id", versionId);
            }
            return map;
        }
    }

    public UploadedFile upload(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传文件不能为空");
        }
        try {
            String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
            String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            String key = buildKey(keyPrefix, original);
            StorageObjectRef ref = objectStorageService.upload(new StorageUploadRequest(
                    key,
                    file.getInputStream(),
                    file.getSize(),
                    contentType));
            SignedUrl signed = objectStorageService.signDownloadUrl(ref);
            return new UploadedFile(ref, signed, original, contentType, file.getSize(), null, null, null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.ASSET_DOWNLOAD_SIGN_FAILED, "上传失败: " + e.getMessage());
        }
    }

    public UploadedFile uploadBytes(byte[] bytes, String filename, String contentType, String keyPrefix) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传内容不能为空");
        }
        String safeName = filename == null || filename.isBlank() ? "upload.bin" : filename;
        String mime = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        String key = buildKey(keyPrefix, safeName);
        StorageObjectRef ref = objectStorageService.upload(new StorageUploadRequest(
                key,
                new ByteArrayInputStream(bytes),
                bytes.length,
                mime));
        SignedUrl signed = objectStorageService.signDownloadUrl(ref);
        return new UploadedFile(ref, signed, safeName, mime, bytes.length, null, null, null);
    }

    public UploadedFile uploadDataUrl(String dataUrl, String filename, String keyPrefix) {
        ParsedDataUrl parsed = parseDataUrl(dataUrl);
        String name = filename;
        if (name == null || name.isBlank()) {
            name = "capture." + extensionForMime(parsed.contentType());
        }
        return uploadBytes(parsed.bytes(), name, parsed.contentType(), keyPrefix);
    }

    @Transactional
    public UploadedFile uploadAndRegisterAsset(HttpServletRequest request,
                                               MultipartFile file,
                                               String assetName,
                                               String mediaType,
                                               String keyPrefix) {
        UploadedFile uploaded = upload(file, keyPrefix);
        return registerAsset(request, uploaded, assetName, mediaType);
    }

    @Transactional
    public UploadedFile registerAsset(HttpServletRequest request,
                                      UploadedFile uploaded,
                                      String assetName,
                                      String mediaType) {
        Long userId = SecurityUtil.requireCurrentUserId();
        String workspaceId = resolveWorkspaceId(request, userId);
        String workspaceType = workspaceId.startsWith("personal_") ? "personal" : "enterprise";

        WorkspaceAsset asset = new WorkspaceAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setWorkspaceId(workspaceId);
        asset.setWorkspaceType(workspaceType);
        asset.setCreatorUserId(userId);
        asset.setAssetType(mediaType == null ? "OTHER" : mediaType.toUpperCase(Locale.ROOT));
        asset.setName(assetName == null || assetName.isBlank() ? uploaded.originalFilename() : assetName);
        asset.setSourceType("IMPORTED");
        asset.setMediaType(normalizeMediaType(mediaType, uploaded.contentType()));
        asset.setStatus("ACTIVE");
        asset.setRowVersion(0);
        asset.setTags("[]");
        asset.setAccessScope("PRIVATE");
        asset.setCreatedBy(userId);
        asset.setUpdatedBy(userId);
        workspaceAssetMapper.insert(asset);

        AssetVersion version = new AssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNumber(1);
        version.setStorageProvider(uploaded.ref().provider().code());
        version.setStorageBucket(uploaded.ref().bucket());
        version.setStorageKey(uploaded.ref().key());
        version.setMimeType(uploaded.contentType());
        version.setFileSize(uploaded.size());
        version.setPreviewUrl(uploaded.downloadUrl().url());
        version.setCreatedBy(userId);
        assetVersionMapper.insert(version);

        asset.setCurrentVersionId(version.getId());
        workspaceAssetMapper.updateById(asset);

        AssetActivityLog log = new AssetActivityLog();
        log.setWorkspaceId(workspaceId);
        log.setAssetId(asset.getId());
        log.setActorUserId(userId);
        log.setAction("UPLOADED");
        activityLogMapper.insert(log);

        return new UploadedFile(
                uploaded.ref(),
                uploaded.downloadUrl(),
                uploaded.originalFilename(),
                uploaded.contentType(),
                uploaded.size(),
                asset.getUuid(),
                asset.getId(),
                version.getId());
    }

    private static String resolveWorkspaceId(HttpServletRequest request, Long userId) {
        if (request != null) {
            WorkspaceContext ctx = WorkspaceContext.get(request);
            if (ctx != null && ctx.workspaceId() != null && !ctx.workspaceId().isBlank()) {
                return ctx.workspaceId();
            }
        }
        return "personal_" + userId;
    }

    private static String buildKey(String prefix, String originalFilename) {
        String safe = originalFilename.replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        if (slash >= 0) {
            safe = safe.substring(slash + 1);
        }
        safe = safe.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.isBlank()) {
            safe = "file.bin";
        }
        String p = (prefix == null || prefix.isBlank()) ? "uploads" : prefix.replaceAll("^/+|/+$", "");
        return p + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + safe;
    }

    private static String normalizeMediaType(String mediaType, String contentType) {
        if (mediaType != null && !mediaType.isBlank()) {
            return mediaType.toUpperCase(Locale.ROOT);
        }
        if (contentType == null) {
            return "OTHER";
        }
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("video/")) return "VIDEO";
        if (contentType.startsWith("audio/")) return "AUDIO";
        return "OTHER";
    }

    private record ParsedDataUrl(String contentType, byte[] bytes) {
    }

    private static ParsedDataUrl parseDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无效的 data URL");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无效的 data URL");
        }
        String meta = dataUrl.substring(5, comma);
        String data = dataUrl.substring(comma + 1);
        String contentType = "application/octet-stream";
        boolean base64 = meta.contains(";base64");
        int semi = meta.indexOf(';');
        if (semi > 0) {
            contentType = meta.substring(0, semi);
        } else if (!meta.isBlank() && !meta.equals("base64")) {
            contentType = meta;
        }
        byte[] bytes = base64
                ? Base64.getDecoder().decode(data)
                : java.net.URLDecoder.decode(data, java.nio.charset.StandardCharsets.UTF_8)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new ParsedDataUrl(contentType, bytes);
    }

    private static String extensionForMime(String mime) {
        if (mime == null) return "bin";
        return switch (mime) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            case "video/mp4" -> "mp4";
            case "audio/mpeg" -> "mp3";
            case "model/gltf-binary" -> "glb";
            default -> "bin";
        };
    }
}
