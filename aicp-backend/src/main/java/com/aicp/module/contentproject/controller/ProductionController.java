package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.service.CanvasBridgeService;
import com.aicp.module.contentproject.service.ProductionAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * M5: Production-grade canvas integration.
 */
@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/production")
@RequiredArgsConstructor
public class ProductionController {

    private final CanvasBridgeService bridgeService;
    private final ProductionAgentService productionService;

    /** Import storyboard to canvas */
    @PostMapping("/import-to-canvas")
    public ApiResponse<Map<String,Object>> importToCanvas(@PathVariable Long projectId,
                                                           @RequestBody Map<String,Object> body) {
        Long masterId = ((Number) body.get("master_id")).longValue();
        return ApiResponse.success(bridgeService.importToCanvas(projectId, masterId,
                SecurityUtil.requireCurrentUserId()));
    }

    /** Batch generate images for canvas project */
    @PostMapping("/batch-generate-images")
    public ApiResponse<Map<String,Object>> batchGenerateImages(@PathVariable Long projectId,
                                                                @RequestBody Map<String,Object> body) {
        String canvasProjectId = (String) body.get("canvas_project_id");
        return ApiResponse.success(productionService.batchGenerateImages(canvasProjectId));
    }

    /** Quality check */
    @PostMapping("/quality-check")
    public ApiResponse<Map<String,Object>> qualityCheck(@PathVariable Long projectId,
                                                         @RequestBody Map<String,Object> body) {
        String canvasProjectId = (String) body.get("canvas_project_id");
        return ApiResponse.success(productionService.qualityCheck(canvasProjectId));
    }

    /** Adopt nodes */
    @PostMapping("/adopt-nodes")
    public ApiResponse<Map<String,Object>> adoptNodes(@PathVariable Long projectId,
                                                       @RequestBody Map<String,Object> body) {
        String canvasProjectId = (String) body.get("canvas_project_id");
        @SuppressWarnings("unchecked")
        List<String> nodeUuids = (List<String>) body.getOrDefault("node_uuids", List.of());
        int count = productionService.adoptNodes(canvasProjectId, nodeUuids);
        return ApiResponse.success(Map.of("adopted", count));
    }

    /** Sync diff */
    @PostMapping("/sync-diff")
    public ApiResponse<Map<String,Object>> syncDiff(@PathVariable Long projectId,
                                                     @RequestBody Map<String,Object> body) {
        Long masterId = ((Number) body.get("master_id")).longValue();
        String canvasProjectId = (String) body.get("canvas_project_id");
        return ApiResponse.success(productionService.syncDiff(masterId, canvasProjectId));
    }

    /** Export manifest */
    @PostMapping("/export-manifest")
    public ApiResponse<Map<String,Object>> exportManifest(@PathVariable Long projectId,
                                                           @RequestBody Map<String,Object> body) {
        String canvasProjectId = (String) body.get("canvas_project_id");
        return ApiResponse.success(productionService.exportManifest(canvasProjectId));
    }
}
