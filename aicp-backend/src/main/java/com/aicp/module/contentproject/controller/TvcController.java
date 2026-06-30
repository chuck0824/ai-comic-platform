package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.contentproject.service.TvcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/tvc")
@RequiredArgsConstructor
public class TvcController {

    private final TvcService tvc;
    private final ProjectAccessService projectAccessService;

    @PostMapping("/brief")
    public ApiResponse<TvcBrief> createBrief(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.EDIT_CONTENT);
        return ApiResponse.success(tvc.createBrief(projectId, body));
    }
    @GetMapping("/brief")
    public ApiResponse<TvcBrief> getBrief(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(tvc.getBrief(projectId));
    }

    @PostMapping("/facts/ai-extract")
    public ApiResponse<Map<String,Object>> aiExtractFacts(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.EDIT_CONTENT);
        return ApiResponse.success(Map.of("extracted", tvc.aiExtractBrandFacts(projectId)));
    }
    @GetMapping("/facts")
    public ApiResponse<List<BrandFact>> listFacts(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(tvc.listFacts(projectId));
    }

    @PostMapping("/strategies/ai-generate")
    public ApiResponse<Map<String,Object>> aiGenerateStrategies(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.EDIT_CONTENT);
        int count = ((Number) body.getOrDefault("count", 3)).intValue();
        return ApiResponse.success(Map.of("generated", tvc.aiGenerateStrategies(projectId, count)));
    }
    @GetMapping("/strategies")
    public ApiResponse<List<CreativeStrategy>> listStrategies(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(tvc.listStrategies(projectId));
    }

    @PostMapping("/scripts/generate")
    public ApiResponse<TvcScript> generateScript(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.EDIT_CONTENT);
        Long strategyId = ((Number) body.get("strategy_id")).longValue();
        int duration = ((Number) body.getOrDefault("duration_sec", 30)).intValue();
        Long sourceUnitId = body.containsKey("source_unit_id") ? ((Number) body.get("source_unit_id")).longValue() : null;
        return ApiResponse.success(tvc.generateScript(projectId, strategyId, duration, sourceUnitId));
    }
    @PostMapping("/scripts/multi-platform")
    public ApiResponse<List<TvcScript>> multiPlatform(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.EDIT_CONTENT);
        Long strategyId = ((Number) body.get("strategy_id")).longValue();
        @SuppressWarnings("unchecked")
        List<String> platforms = (List<String>) body.getOrDefault("platforms", List.of("抖音","微信"));
        @SuppressWarnings("unchecked")
        List<Integer> durations = (List<Integer>) body.getOrDefault("durations", List.of(15,30,60));
        return ApiResponse.success(tvc.generateMultiPlatform(projectId, strategyId, platforms, durations));
    }
    @GetMapping("/scripts")
    public ApiResponse<List<TvcScript>> listScripts(@PathVariable Long projectId) {
        projectAccessService.require(projectId, SecurityUtil.requireCurrentUserId(), Action.VIEW);
        return ApiResponse.success(tvc.listScripts(projectId));
    }
}
