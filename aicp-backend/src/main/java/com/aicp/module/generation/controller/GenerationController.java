package com.aicp.module.generation.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.generation.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    // ===== Task endpoints =====
    @PostMapping("/api/v1/generation/tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        var task = generationService.createTask(
                toLong(body.get("project_id")), toLong(body.get("node_id")), toLong(body.get("shot_id")),
                (String) body.get("type"), (String) body.get("sub_type"),
                (String) body.get("model_id"), asMap(body.get("parameters")));
        return ApiResponse.success(toMap(task));
    }

    @GetMapping("/api/v1/generation/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String taskId) {
        var task = generationService.getTask(taskId);
        return task == null ? ApiResponse.error(46020, "任务不存在") : ApiResponse.success(toMap(task));
    }

    @PostMapping("/api/v1/generation/tasks/{taskId}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable String taskId) {
        generationService.cancelTask(taskId);
        return ApiResponse.success();
    }

    @PostMapping("/api/v1/generation/tasks/{taskId}/retry")
    public ApiResponse<Map<String, Object>> retryTask(@PathVariable String taskId) {
        var task = generationService.retryTask(taskId);
        return task == null ? ApiResponse.error(46020, "任务不存在") : ApiResponse.success(toMap(task));
    }

    // ===== Multi-copy variants =====
    @PostMapping("/api/v1/generation/variants")
    public ApiResponse<Map<String, Object>> createVariants(@RequestBody Map<String, Object> body) {
        Long parentTaskId = toLong(body.get("parent_task_id"));
        int count = toInt(body.get("count"), 2);
        var variants = generationService.createVariants(parentTaskId, count, asMap(body.get("parameters")));
        return ApiResponse.success(Map.of("variants", variants.stream().map(this::toMapVariant).toList()));
    }

    @GetMapping("/api/v1/generation/variants/{parentTaskId}")
    public ApiResponse<List<Map<String, Object>>> getVariants(@PathVariable Long parentTaskId) {
        var variants = generationService.getVariants(parentTaskId);
        return ApiResponse.success(variants.stream().map(this::toMapVariant).toList());
    }

    @PostMapping("/api/v1/generation/variants/{variantId}/select")
    public ApiResponse<Void> selectVariant(@PathVariable Long variantId) {
        generationService.selectVariant(variantId);
        return ApiResponse.success();
    }

    // ===== Omni-reference video =====
    @PostMapping("/api/v1/generation/video/reference")
    public ApiResponse<Map<String, Object>> createOmniReferenceVideo(@RequestBody Map<String, Object> body) {
        var task = generationService.createTask(
                toLong(body.get("project_id")), toLong(body.get("node_id")), null,
                "video", "omni_reference", (String) body.getOrDefault("model_id", "seedance-2.0"),
                body);
        return ApiResponse.success(toMap(task));
    }

    // ===== Credit / Cost =====
    @PostMapping("/api/v1/credits/estimate")
    public ApiResponse<Map<String, Object>> estimateCredits(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        String modelId = (String) body.get("model_id");
        var estimate = generationService.estimateCost(type, modelId, asMap(body.get("parameters")));
        return ApiResponse.success(estimate);
    }

    // ===== Asset history =====
    @GetMapping("/api/v1/assets/history")
    public ApiResponse<List<Map<String, Object>>> getAssetHistory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword) {
        Long userId = SecurityUtil.requireCurrentUserId();
        var assets = generationService.getAssetHistory(userId, type, projectId, keyword);
        return ApiResponse.success(assets.stream().map(this::toMapAsset).toList());
    }

    @PostMapping("/api/v1/assets/{assetId}/send-to-canvas")
    public ApiResponse<Map<String, String>> sendAssetToCanvas(@PathVariable Long assetId) {
        return ApiResponse.success(Map.of("message", "资产已发送到画布", "asset_id", String.valueOf(assetId)));
    }

    // ===== Helpers =====
    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? null : Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }

    private int toInt(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? fallback : Integer.parseInt(String.valueOf(v)); }
        catch (NumberFormatException e) { return fallback; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        if (v instanceof Map) return (Map<String, Object>) v;
        return Map.of();
    }

    private Map<String, Object> toMap(Object entity) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.convertValue(entity, Map.class);
        } catch (Exception e) { return Map.of(); }
    }

    private Map<String, Object> toMapVariant(com.aicp.module.generation.entity.GenerationVariant v) {
        return toMap(v);
    }

    private Map<String, Object> toMapAsset(com.aicp.module.generation.entity.PlatformAsset a) {
        return toMap(a);
    }
}
