package com.aicp.module.storyboard.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.canvas.entity.StoryboardShot;
import com.aicp.module.canvas.mapper.StoryboardShotMapper;
import com.aicp.module.generation.service.GenerationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/storyboards")
@RequiredArgsConstructor
public class StoryboardController {

    private final StoryboardShotMapper shotMapper;
    private final GenerationService generationService;

    // ===== AI 解析剧本为分镜 =====
    @PostMapping("/{storyboardId}/parse")
    public ApiResponse<Map<String, Object>> parseStoryboard(@PathVariable String storyboardId,
                                                            @RequestBody Map<String, Object> body) {
        // AI 拆分场次/镜头/台词/旁白/角色/场景/道具
        // 返回解析后的分镜行列表
        var task = generationService.createTask(
                toLong(body.get("project_id")), toLong(body.get("node_id")), null,
                "agent", "storyboard_parse", "deepseek-v3", body);
        return ApiResponse.success(Map.of(
                "task_id", task.getUuid(),
                "storyboard_id", storyboardId,
                "status", "pending",
                "message", "AI正在解析剧本，请稍后查询任务状态"
        ));
    }

    // ===== 更新分镜行 =====
    @PatchMapping("/{storyboardId}/shots/{shotId}")
    public ApiResponse<Void> updateShot(@PathVariable String storyboardId,
                                        @PathVariable String shotId,
                                        @RequestBody Map<String, Object> body) {
        StoryboardShot shot = shotMapper.selectOne(
                new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getUuid, shotId));
        if (shot == null) return ApiResponse.error(46004, "分镜不存在");
        body.forEach((key, value) -> {
            switch (key) {
                case "shot_no" -> shot.setShotNo(toInt(value, shot.getShotNo()));
                case "scene_no" -> shot.setSceneNo(toInt(value, shot.getSceneNo()));
                case "duration" -> shot.setDuration(toInt(value, shot.getDuration()));
                case "shot_size" -> shot.setShotSize((String) value);
                case "camera_motion" -> shot.setCameraMotion((String) value);
                case "visual_description" -> shot.setVisualDescription((String) value);
                case "image_prompt" -> shot.setImagePrompt((String) value);
                case "video_prompt" -> shot.setVideoPrompt((String) value);
            }
        });
        shotMapper.updateById(shot);
        return ApiResponse.success();
    }

    // ===== 批量分镜生图 =====
    @PostMapping("/{storyboardId}/generate-images")
    public ApiResponse<Map<String, Object>> generateImages(@PathVariable String storyboardId,
                                                            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> shotIds = (List<String>) body.getOrDefault("shot_ids", List.of());
        String modelId = (String) body.getOrDefault("model_id", "seedream-5.0");
        Long projectId = toLong(body.get("project_id"));
        Long nodeId = toLong(body.get("node_id"));

        List<Map<String, String>> tasks = new ArrayList<>();
        for (String shotId : shotIds) {
            StoryboardShot shot = shotMapper.selectOne(
                    new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getUuid, shotId));
            if (shot != null) {
                shot.setImageStatus("generating");
                shotMapper.updateById(shot);
                var task = generationService.createTask(projectId, nodeId, shot.getId(),
                        "image", null, modelId,
                        Map.of("prompt", shot.getImagePrompt(), "shot_id", shotId));
                tasks.add(Map.of("shot_id", shotId, "task_id", task.getUuid()));
            }
        }
        return ApiResponse.success(Map.of(
                "message", "批量生图任务已创建", "task_count", tasks.size(), "tasks", tasks));
    }

    // ===== 批量图生视频 =====
    @PostMapping("/{storyboardId}/generate-videos")
    public ApiResponse<Map<String, Object>> generateVideos(@PathVariable String storyboardId,
                                                            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> shotIds = (List<String>) body.getOrDefault("shot_ids", List.of());
        String modelId = (String) body.getOrDefault("model_id", "seedance-2.0");
        Long projectId = toLong(body.get("project_id"));
        Long nodeId = toLong(body.get("node_id"));

        List<Map<String, String>> tasks = new ArrayList<>();
        for (String shotId : shotIds) {
            StoryboardShot shot = shotMapper.selectOne(
                    new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getUuid, shotId));
            if (shot != null) {
                shot.setVideoStatus("generating");
                shotMapper.updateById(shot);
                var task = generationService.createTask(projectId, nodeId, shot.getId(),
                        "video", null, modelId,
                        Map.of("image_prompt", shot.getImagePrompt(), "video_prompt", shot.getVideoPrompt(), "shot_id", shotId));
                tasks.add(Map.of("shot_id", shotId, "task_id", task.getUuid()));
            }
        }
        return ApiResponse.success(Map.of(
                "message", "批量视频任务已创建", "task_count", tasks.size(), "tasks", tasks));
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
}
