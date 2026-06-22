package com.aicp.module.script.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.script.entity.PromptTemplate;
import com.aicp.module.script.mapper.PromptTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/script/prompts")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateMapper mapper;

    /** 默认 Prompt 模板（12 槽位 × 5 大类） */
    private static final List<Map<String, String>> DEFAULTS = List.of(
        // 剧本类
        Map.of("category", "script_generate", "name", "剧本生成", "desc", "快速模式/精细模式的剧本生成 Prompt"),
        Map.of("category", "script_parse", "name", "剧本解析", "desc", "上传剧本 txt/docx 的解析 Prompt"),
        Map.of("category", "script_split", "name", "智能分集", "desc", "AI 自动将长文本拆分为集数"),
        // 角色类
        Map.of("category", "character_extract", "name", "角色提取", "desc", "从剧本中提取角色名称+描述"),
        Map.of("category", "character_image", "name", "角色生成图", "desc", "根据角色描述生成角色参考图"),
        // 分镜类
        Map.of("category", "shot_split", "name", "分镜拆解", "desc", "Scene→Shots 拆解+构图+运镜"),
        // 画面类
        Map.of("category", "frame_generate_first", "name", "首帧生成", "desc", "根据分镜描述生成关键首帧"),
        Map.of("category", "frame_generate_last", "name", "尾帧生成", "desc", "根据分镜描述+首帧生成尾帧"),
        Map.of("category", "scene_frame_generate", "name", "场景参考帧", "desc", "生成场景参考图(非首尾帧模式)"),
        // 视频类
        Map.of("category", "video_generate", "name", "视频生成", "desc", "首尾帧插值生成视频片段"),
        Map.of("category", "video_prompt_generate", "name", "视频提示词生成", "desc", "根据分镜描述生成视频 Prompt"),
        Map.of("category", "promotion_generate", "name", "投流素材生成", "desc", "标题/封面/钩子/切片脚本生成 Prompt")
    );

    @GetMapping
    public ApiResponse<Map<String, Object>> listPrompts(
            @RequestParam(required = false) String category) {
        Long userId = SecurityUtil.requireCurrentUserId();
        var query = new LambdaQueryWrapper<PromptTemplate>()
                .and(w -> w.eq(PromptTemplate::getOwnerId, userId)
                        .or().eq(PromptTemplate::getVisibility, "public"));
        if (category != null) query.eq(PromptTemplate::getCategory, category);
        query.orderByAsc(PromptTemplate::getCategory).orderByDesc(PromptTemplate::getVersion);

        List<PromptTemplate> templates = mapper.selectList(query);

        // 补充默认模板（如果用户还没有自定义）
        Set<String> existingCategories = new HashSet<>();
        for (PromptTemplate t : templates) existingCategories.add(t.getCategory());

        List<Map<String, Object>> fullList = new ArrayList<>();
        for (Map<String, String> def : DEFAULTS) {
            Map<String, Object> item = new LinkedHashMap<>(def);
            item.put("is_default", !existingCategories.contains(def.get("category")));
            // 找到用户的版本
            PromptTemplate userVersion = templates.stream()
                    .filter(t -> def.get("category").equals(t.getCategory()))
                    .findFirst().orElse(null);
            if (userVersion != null) {
                item.put("id", userVersion.getId());
                item.put("uuid", userVersion.getUuid());
                item.put("content", userVersion.getContent());
                item.put("version", userVersion.getVersion());
                item.put("is_default", false);
            } else {
                item.put("content", "【" + def.get("name") + "】\n\n请在此编辑 Prompt 模板。支持变量：{{genre}}, {{plot}}, {{tone}}, {{setting}}, {{idea}}, {{title}}, {{character}}");
                item.put("version", 1);
            }
            fullList.add(item);
        }

        return ApiResponse.success(Map.of("items", fullList, "categories", List.of("script", "character", "shot", "frame", "video")));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> savePrompt(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        String category = (String) body.get("category");

        // 查找已有版本
        PromptTemplate existing = mapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getOwnerId, userId)
                .eq(PromptTemplate::getCategory, category));

        if (existing != null) {
            existing.setContent((String) body.getOrDefault("content", existing.getContent()));
            existing.setName((String) body.getOrDefault("name", existing.getName()));
            existing.setVersion(existing.getVersion() + 1);
            mapper.updateById(existing);
        } else {
            existing = new PromptTemplate();
            existing.setUuid("prompt_" + UUID.randomUUID().toString().substring(0, 8));
            existing.setOwnerId(userId);
            existing.setCategory(category);
            existing.setName((String) body.getOrDefault("name", category));
            existing.setContent((String) body.getOrDefault("content", ""));
            existing.setVersion(1);
            existing.setVisibility("private");
            existing.setStatus("draft");
            mapper.insert(existing);
        }

        return ApiResponse.success(Map.of("id", existing.getId(), "uuid", existing.getUuid(),
                "version", existing.getVersion(), "message", "Prompt 已保存 (v" + existing.getVersion() + ")"));
    }
}
