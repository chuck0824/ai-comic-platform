package com.aicp.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pulls remote AI media URLs into the platform object storage and rewrites
 * generation output payloads with durable storage_* fields.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationOutputPersister {

    private final StorageUploadService storageUploadService;
    private final ObjectStorageService objectStorageService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Map<String, Object> persist(String taskType, Long taskId, Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(raw);
        List<Map<String, Object>> assets = new ArrayList<>();

        List<String> urls = extractUrls(raw);
        int index = 0;
        for (String url : urls) {
            try {
                PersistedMedia media = fetchAndStore(url, taskType, taskId, index++);
                assets.add(media.toMap());
                if (index == 1) {
                    // Flatten first asset onto root for settlement compatibility.
                    result.putAll(media.toMap());
                }
            } catch (Exception e) {
                log.warn("Failed to persist generation media url={} taskId={}: {}", url, taskId, e.getMessage());
            }
        }
        if (!assets.isEmpty()) {
            result.put("persisted_assets", assets);
            result.put("active_provider", objectStorageService.activeProvider().code());
        }
        return result;
    }

    private PersistedMedia fetchAndStore(String url, String taskType, Long taskId, int index) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "aicp-backend/storage-persister")
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("download status=" + response.statusCode());
        }
        byte[] body = response.body();
        String contentType = response.headers().firstValue("Content-Type")
                .orElse(guessMime(taskType));
        String filename = filenameFor(taskType, taskId, index, contentType);
        var uploaded = storageUploadService.uploadBytes(
                body, filename, contentType, "generation/" + (taskId == null ? "misc" : taskId));
        return new PersistedMedia(uploaded, url);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractUrls(Map<String, Object> raw) {
        List<String> urls = new ArrayList<>();
        Object data = raw.get("data");
        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object url = map.get("url");
                    if (url instanceof String s && looksLikeUrl(s)) {
                        urls.add(s);
                    }
                    Object b64 = map.get("b64_json");
                    // ignore base64 for now; URL path is the common case
                }
            }
        }
        Object url = raw.get("url");
        if (url instanceof String s && looksLikeUrl(s)) {
            urls.add(s);
        }
        Object outputUrl = raw.get("output_url");
        if (outputUrl instanceof String s && looksLikeUrl(s)) {
            urls.add(s);
        }
        return urls.stream().distinct().toList();
    }

    private static boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String guessMime(String taskType) {
        return switch (taskType == null ? "" : taskType) {
            case "image" -> "image/png";
            case "video", "compose", "export" -> "video/mp4";
            case "audio" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }

    private static String filenameFor(String taskType, Long taskId, int index, String contentType) {
        String ext = switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav" -> "wav";
            default -> "bin";
        };
        return (taskType == null ? "media" : taskType) + "_"
                + (taskId == null ? "x" : taskId) + "_" + index + "." + ext;
    }

    private record PersistedMedia(StorageUploadService.UploadedFile uploaded, String sourceUrl) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>(uploaded.toMap());
            map.put("source_url", sourceUrl);
            map.put("preview_url", uploaded.downloadUrl().url());
            return map;
        }
    }
}
