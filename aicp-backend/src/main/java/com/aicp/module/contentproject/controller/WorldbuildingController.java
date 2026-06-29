package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.service.WorldbuildingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/world")
@RequiredArgsConstructor
public class WorldbuildingController {

    private final WorldbuildingService wb;

    // Characters
    @PostMapping("/characters")
    public ApiResponse<CharacterProfile> createCharacter(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return ApiResponse.success(wb.createCharacter(projectId, (String)body.get("name"), (String)body.get("role")));
    }
    @PostMapping("/characters/ai-generate")
    public ApiResponse<Map<String,Object>> aiGenerateCharacter(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return ApiResponse.success(wb.aiGenerateCharacter(projectId, (String)body.get("name"), (String)body.getOrDefault("context","")));
    }
    @GetMapping("/characters")
    public ApiResponse<List<CharacterProfile>> listCharacters(@PathVariable Long projectId) { return ApiResponse.success(wb.listCharacters(projectId)); }

    // Tasks
    @PostMapping("/tasks")
    public ApiResponse<PlotTask> createTask(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return ApiResponse.success(wb.createTask(projectId, (String)body.get("type"), (String)body.get("title"), (String)body.get("description")));
    }
    @PostMapping("/tasks/ai-generate")
    public ApiResponse<Map<String,Object>> aiGenerateTasks(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        int count = wb.aiGeneratePlotTasks(projectId, (String)body.getOrDefault("context",""));
        return ApiResponse.success(Map.of("generated", count));
    }
    @GetMapping("/tasks")
    public ApiResponse<List<PlotTask>> listTasks(@PathVariable Long projectId) { return ApiResponse.success(wb.listTasks(projectId)); }

    // Volumes
    @PostMapping("/volumes")
    public ApiResponse<VolumeOutline> createVolume(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return ApiResponse.success(wb.createVolume(projectId, ((Number)body.get("volume_no")).intValue(), (String)body.get("title")));
    }
    @PostMapping("/volumes/ai-generate")
    public ApiResponse<Map<String,Object>> aiGenerateVolumes(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        int count = wb.aiGenerateVolumes(projectId, ((Number)body.getOrDefault("count",5)).intValue(), (String)body.getOrDefault("context",""));
        return ApiResponse.success(Map.of("generated", count));
    }
    @GetMapping("/volumes")
    public ApiResponse<List<VolumeOutline>> listVolumes(@PathVariable Long projectId) { return ApiResponse.success(wb.listVolumes(projectId)); }

    // Locations
    @PostMapping("/locations")
    public ApiResponse<WorldLocation> createLocation(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return ApiResponse.success(wb.createLocation(projectId, (String)body.get("name"), (String)body.get("tier"), body.containsKey("parent_id") ? ((Number)body.get("parent_id")).longValue() : null));
    }
    @PostMapping("/locations/ai-extract")
    public ApiResponse<Map<String,Object>> aiExtractLocations(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        int count = wb.aiExtractLocations(projectId, (String)body.getOrDefault("content",""));
        return ApiResponse.success(Map.of("extracted", count));
    }
    @GetMapping("/locations")
    public ApiResponse<List<WorldLocation>> listLocations(@PathVariable Long projectId) { return ApiResponse.success(wb.listLocations(projectId)); }

    // Timeline + Foreshadowing
    @PostMapping("/timeline/ai-generate")
    public ApiResponse<Map<String,Object>> aiGenerateTimeline(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return ApiResponse.success(wb.aiGenerateTimeline(projectId, (String)body.getOrDefault("context","")));
    }

    // Summary
    @GetMapping("/summary")
    public ApiResponse<Map<String,Object>> worldSummary(@PathVariable Long projectId) { return ApiResponse.success(wb.getWorldSummary(projectId)); }
}
