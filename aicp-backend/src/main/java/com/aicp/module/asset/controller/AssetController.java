package com.aicp.module.asset.controller;

import com.aicp.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/asset")
public class AssetController {

    @GetMapping("/market/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String type,
                                                    @RequestParam(required = false) String keyword) {
        return ApiResponse.success(Map.of("items", getMockModels(), "pagination",
                Map.of("page", 1, "page_size", 20, "total", 4, "total_pages", 1, "has_more", false)));
    }

    @GetMapping("/market/models")
    public ApiResponse<List<Map<String, Object>>> getModels() {
        return ApiResponse.success(getMockModels());
    }

    @GetMapping("/market/models/{id}")
    public ApiResponse<Map<String, Object>> getModelDetail(@PathVariable Long id) {
        return ApiResponse.success(getMockModels().get(0));
    }

    @GetMapping("/market/characters")
    public ApiResponse<List<Map<String, Object>>> getCharacters() { return ApiResponse.success(List.of()); }

    @GetMapping("/market/scenes")
    public ApiResponse<List<Map<String, Object>>> getScenes() { return ApiResponse.success(List.of()); }

    @GetMapping("/market/prompts")
    public ApiResponse<List<Map<String, Object>>> getPrompts() { return ApiResponse.success(List.of()); }

    @GetMapping("/market/voices")
    public ApiResponse<List<Map<String, Object>>> getVoices() { return ApiResponse.success(List.of()); }

    @GetMapping("/market/sounds")
    public ApiResponse<List<Map<String, Object>>> getSounds() { return ApiResponse.success(List.of()); }

    @PostMapping("/market/models/{id}/apply")
    public ApiResponse<Map<String, String>> applyModel(@PathVariable Long id) {
        return ApiResponse.success(Map.of("message", "模型已应用到画布"));
    }

    @PostMapping("/market/assets/{id}/download")
    public ApiResponse<Map<String, String>> download(@PathVariable Long id) {
        return ApiResponse.success(Map.of("download_url", "https://cdn.example.com/assets/asset_" + id + ".zip"));
    }

    @PostMapping("/market/assets/{id}/favorite")
    public ApiResponse<Void> favorite(@PathVariable Long id) { return ApiResponse.success(); }

    @PostMapping("/market/publish")
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("id", 100, "status", "listed"));
    }

    @PutMapping("/market/assets/{id}")
    public ApiResponse<Void> updateAsset(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success();
    }

    @GetMapping("/market/my/assets")
    public ApiResponse<List<Map<String, Object>>> getMyAssets() { return ApiResponse.success(List.of()); }

    @GetMapping("/market/my/favorites")
    public ApiResponse<List<Map<String, Object>>> getMyFavorites() { return ApiResponse.success(List.of()); }

    @GetMapping("/market/my/downloads")
    public ApiResponse<List<Map<String, Object>>> getMyDownloads() { return ApiResponse.success(List.of()); }

    private List<Map<String, Object>> getMockModels() {
        return List.of(
                Map.of("id", 1, "asset_type", "checkpoint", "name", "韩漫风格 - 都市言情",
                        "author", Map.of("nickname", "AI视觉师"), "rating", 4.9, "use_count", 2340,
                        "price", 9.90, "tags", List.of("韩漫", "都市", "甜宠"),
                        "recommended_params", Map.of("trigger_words", "korean manhwa style, soft shading")),
                Map.of("id", 2, "asset_type", "checkpoint", "name", "写实风格 - 现代都市",
                        "author", Map.of("nickname", "写实派"), "rating", 4.7, "use_count", 5100,
                        "price", 0, "tags", List.of("写实", "现代", "都市")),
                Map.of("id", 3, "asset_type", "lora", "name", "二次元 - 日系动漫",
                        "author", Map.of("nickname", "二次元画师"), "rating", 4.6, "use_count", 1800,
                        "price", 19.90, "tags", List.of("二次元", "日系", "动漫")),
                Map.of("id", 4, "asset_type", "style_pack", "name", "水墨国风 - 仙侠古装",
                        "author", Map.of("nickname", "国风画师"), "rating", 4.8, "use_count", 890,
                        "price", 0, "tags", List.of("水墨", "国风", "仙侠"))
        );
    }
}
