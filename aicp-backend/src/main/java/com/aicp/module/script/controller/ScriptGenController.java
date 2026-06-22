package com.aicp.module.script.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.script.service.ScriptGenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/script/gen")
@RequiredArgsConstructor
public class ScriptGenController {

    private final ScriptGenService genService;

    @PostMapping("/quick")
    public ApiResponse<Map<String, Object>> quickGen(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(genService.createGenTask("quick", body));
    }

    @PostMapping("/topic")
    public ApiResponse<Map<String, Object>> genTopic(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(genService.createGenTask("topic", body));
    }

    @PostMapping("/synopsis")
    public ApiResponse<Map<String, Object>> genSynopsis(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(genService.createGenTask("synopsis", body));
    }

    @PostMapping("/outline")
    public ApiResponse<Map<String, Object>> genOutline(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(genService.createGenTask("outline", body));
    }

    @PostMapping("/episode")
    public ApiResponse<Map<String, Object>> genEpisode(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(genService.createGenTask("episode", body));
    }

    @PostMapping("/storyboard")
    public ApiResponse<Map<String, Object>> genStoryboard(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(genService.createGenTask("storyboard", body));
    }

    @PostMapping("/promotion")
    public ApiResponse<Map<String, Object>> genPromotion(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(genService.createGenTask("promotion", body));
    }

    @PostMapping("/storyboard/upgrade")
    public ApiResponse<Map<String, Object>> upgradeStoryboard(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of(
                "task_id", "gen_upgrade_" + System.currentTimeMillis(),
                "from_tier", body.get("from_tier"),
                "to_tier", body.get("to_tier"),
                "status", "pending"
        ));
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        return ApiResponse.success(genService.getTaskStatus(taskId));
    }

    @GetMapping("/tasks")
    public ApiResponse<?> getTaskHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(genService.getTaskHistory(page, pageSize));
    }
}
