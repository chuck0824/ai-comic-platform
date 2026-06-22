package com.aicp.module.script.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.module.script.entity.EpisodeHook;
import com.aicp.module.script.mapper.EpisodeHookMapper;
import com.aicp.module.script.service.HookService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/script/repo")
@RequiredArgsConstructor
public class HookController {

    private final HookService hookService;
    private final EpisodeHookMapper hookMapper;

    /** 获取剧本的所有钩子 */
    @GetMapping("/scripts/{scriptId}/hooks")
    public ApiResponse<Map<String, Object>> getHooks(@PathVariable Long scriptId) {
        return ApiResponse.success(hookService.getHookSummary(scriptId));
    }

    /** 为单集生成钩子 */
    @PostMapping("/scripts/{scriptId}/episodes/{episodeId}/hooks/generate")
    public ApiResponse<Map<String, Object>> generateEpisodeHooks(
            @PathVariable Long scriptId, @PathVariable Long episodeId) {
        hookService.generateHooksForEpisode(episodeId);
        return ApiResponse.success(Map.of(
                "message", "钩子生成任务已启动",
                "episode_id", episodeId,
                "script_id", scriptId
        ));
    }

    /** 批量为所有集生成钩子 */
    @PostMapping("/scripts/{scriptId}/hooks/generate-all")
    public ApiResponse<Map<String, Object>> generateAllHooks(@PathVariable Long scriptId) {
        hookService.generateAllHooks(scriptId);
        return ApiResponse.success(Map.of(
                "message", "批量钩子生成任务已启动",
                "script_id", scriptId
        ));
    }

    /** 更新单个钩子 */
    @PutMapping("/hooks/{hookId}")
    public ApiResponse<Map<String, Object>> updateHook(@PathVariable Long hookId,
                                                       @RequestBody Map<String, Object> body) {
        EpisodeHook hook = hookMapper.selectById(hookId);
        if (hook == null) {
            return ApiResponse.error(47010, "钩子不存在");
        }
        if (body.containsKey("content")) hook.setContent((String) body.get("content"));
        if (body.containsKey("hook_type")) hook.setHookType((String) body.get("hook_type"));
        if (body.containsKey("strength_score")) {
            Object score = body.get("strength_score");
            hook.setStrengthScore(score instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(score)));
        }
        if (body.containsKey("status")) hook.setStatus((String) body.get("status"));
        hookMapper.updateById(hook);
        return ApiResponse.success(Map.of("id", hook.getId(), "status", hook.getStatus()));
    }
}
