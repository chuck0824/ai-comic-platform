package com.aicp.module.asset.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.service.MarketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Legacy asset-market endpoints. Methods delegate to {@link MarketQueryService}
 * and are preserved as deprecated wrappers during frontend migration.
 *
 * @deprecated Use {@link AssetMarketController} for new /api/v1/asset/market/listings endpoints.
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/asset")
@RequiredArgsConstructor
public class AssetController {

    private final MarketQueryService marketQueryService;

    // ---- Deprecated wrappers (map old paths to new MarketQueryService) ----

    /** @deprecated Use GET /api/v1/asset/market/listings?type=CHECKPOINT */
    @Deprecated
    @GetMapping("/market/models")
    public ApiResponse<?> getModels(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(marketQueryService.search(null, "CHECKPOINT", "latest", page, size, null));
    }

    /** @deprecated Use GET /api/v1/asset/market/listings/{id} */
    @Deprecated
    @GetMapping("/market/models/{id}")
    public ApiResponse<AssetViews.ListingDetail> getModelDetail(@PathVariable Long id) {
        AssetViews.ListingDetail detail = marketQueryService.getDetail(id, null);
        return detail != null ? ApiResponse.success(detail)
                : ApiResponse.error(48001, "资产不存在");
    }

    /** @deprecated Use GET /api/v1/asset/market/listings?type=CHARACTER */
    @Deprecated
    @GetMapping("/market/characters")
    public ApiResponse<?> getCharacters(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(marketQueryService.search(null, "CHARACTER", "latest", page, size, null));
    }

    /** @deprecated Use GET /api/v1/asset/market/listings?type=SCENE */
    @Deprecated
    @GetMapping("/market/scenes")
    public ApiResponse<?> getScenes(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(marketQueryService.search(null, "SCENE", "latest", page, size, null));
    }

    /** @deprecated Use GET /api/v1/asset/market/listings?type=PROMPT */
    @Deprecated
    @GetMapping("/market/prompts")
    public ApiResponse<?> getPrompts(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(marketQueryService.search(null, "PROMPT", "latest", page, size, null));
    }

    /** Voice/BGM: not implemented in this release — returns empty. */
    @GetMapping("/market/voices")
    public ApiResponse<List<Map<String, Object>>> getVoices() {
        return ApiResponse.success(List.of(Map.of("notice", "即将开放")));
    }

    /** Voice/BGM: not implemented in this release — returns empty. */
    @GetMapping("/market/sounds")
    public ApiResponse<List<Map<String, Object>>> getSounds() {
        return ApiResponse.success(List.of(Map.of("notice", "即将开放")));
    }

    // ---- Remaining legacy stubs (return empty/success during migration) ----

    /** @deprecated Use GET /api/v1/asset/market/listings */
    @Deprecated
    @GetMapping("/market/search")
    public ApiResponse<?> search(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) String keyword) {
        return ApiResponse.success(marketQueryService.search(keyword, type, "latest", 1, 20, null));
    }

    @Deprecated
    @PostMapping("/market/models/{id}/apply")
    public ApiResponse<Map<String, String>> applyModel(@PathVariable Long id) {
        return ApiResponse.success(Map.of("message", "请使用 POST /api/v1/asset/library/{id}/applications"));
    }

    @Deprecated
    @PostMapping("/market/assets/{id}/download")
    public ApiResponse<Map<String, String>> download(@PathVariable Long id) {
        return ApiResponse.success(Map.of("message", "请使用 POST /api/v1/asset/market/listings/{id}/claim"));
    }

    @Deprecated
    @PostMapping("/market/assets/{id}/favorite")
    public ApiResponse<Void> favorite(@PathVariable Long id) {
        return ApiResponse.success();
    }

    @Deprecated
    @PostMapping("/market/publish")
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("message", "请使用 POST /api/v1/asset/library/{id}/publish"));
    }

    @Deprecated
    @PutMapping("/market/assets/{id}")
    public ApiResponse<Void> updateAsset(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success();
    }

    @Deprecated
    @GetMapping("/market/my/assets")
    public ApiResponse<?> getMyAssets() {
        return ApiResponse.success(List.of(Map.of("notice", "请使用 GET /api/v1/asset/library")));
    }

    @Deprecated
    @GetMapping("/market/my/favorites")
    public ApiResponse<?> getMyFavorites() {
        return ApiResponse.success(List.of(Map.of("notice", "请使用 GET /api/v1/asset/market/listings?claimed=true")));
    }

    @Deprecated
    @GetMapping("/market/my/downloads")
    public ApiResponse<?> getMyDownloads() {
        return ApiResponse.success(List.of(Map.of("notice", "请使用 GET /api/v1/asset/library?sourceType=MARKET_CLAIMED")));
    }
}
