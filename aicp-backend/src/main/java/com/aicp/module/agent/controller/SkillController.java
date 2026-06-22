package com.aicp.module.agent.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.agent.entity.Skill;
import com.aicp.module.agent.mapper.SkillMapper;
import com.aicp.module.generation.service.GenerationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillMapper skillMapper;
    private final GenerationService generationService;

    @PostMapping
    public ApiResponse<Map<String, Object>> createSkill(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.requireCurrentUserId();
        Skill skill = new Skill();
        skill.setUuid("skill_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        skill.setName((String) body.getOrDefault("name", "未命名Skill"));
        skill.setDescription((String) body.get("description"));
        skill.setContent((String) body.getOrDefault("content", "# 新Skill\n\n请在此编辑Markdown内容"));
        skill.setType((String) body.getOrDefault("type", "script"));
        skill.setVersion("1.0.0");
        skill.setVisibility((String) body.getOrDefault("visibility", "private"));
        skill.setOwnerId(userId);
        skill.setStatus("draft");
        skillMapper.insert(skill);
        return ApiResponse.success(toMap(skill));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getSkills(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String visibility) {
        Long userId = SecurityUtil.requireCurrentUserId();
        LambdaQueryWrapper<Skill> query = new LambdaQueryWrapper<>();
        // 数据隔离：仅返回自己的 + 公开的 skill
        query.and(w -> w.eq(Skill::getOwnerId, userId)
                .or().eq(Skill::getVisibility, "public"));
        if (type != null) query.eq(Skill::getType, type);
        if (visibility != null) query.eq(Skill::getVisibility, visibility);
        query.orderByDesc(Skill::getUsageCount);
        Page<Skill> result = skillMapper.selectPage(new Page<>(page, pageSize), query);
        return ApiResponse.success(Map.of(
            "items", result.getRecords().stream().map(this::toMap).toList(),
            "pagination", Map.of("page", page, "page_size", pageSize,
                "total", result.getTotal(), "has_more", result.hasNext())));
    }

    @GetMapping("/{skillId}")
    public ApiResponse<Map<String, Object>> getSkill(@PathVariable String skillId) {
        Skill skill = findSkill(skillId);
        return skill == null ? ApiResponse.error(47010, "Skill不存在") : ApiResponse.success(toMap(skill));
    }

    @PutMapping("/{skillId}")
    public ApiResponse<Map<String, Object>> updateSkill(@PathVariable String skillId,
                                                         @RequestBody Map<String, Object> body) {
        Skill skill = findSkill(skillId);
        if (skill == null) return ApiResponse.error(47010, "Skill不存在");
        if (body.containsKey("name")) skill.setName((String) body.get("name"));
        if (body.containsKey("description")) skill.setDescription((String) body.get("description"));
        if (body.containsKey("content")) skill.setContent((String) body.get("content"));
        if (body.containsKey("type")) skill.setType((String) body.get("type"));
        if (body.containsKey("visibility")) skill.setVisibility((String) body.get("visibility"));
        if (body.containsKey("status")) skill.setStatus((String) body.get("status"));
        skillMapper.updateById(skill);
        return ApiResponse.success(toMap(skill));
    }

    @DeleteMapping("/{skillId}")
    public ApiResponse<Void> deleteSkill(@PathVariable String skillId) {
        Skill skill = findSkill(skillId);
        if (skill != null) skillMapper.deleteById(skill.getId());
        return ApiResponse.success();
    }

    @PostMapping("/{skillId}/execute")
    public ApiResponse<Map<String, Object>> executeSkill(@PathVariable String skillId,
                                                          @RequestBody Map<String, Object> body) {
        Skill skill = findSkill(skillId);
        if (skill == null) return ApiResponse.error(47010, "Skill不存在");

        // 变量注入：替换 Markdown 中的 {{variable}}
        String executedContent = skill.getContent();
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) body.getOrDefault("variables", Map.of());
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            executedContent = executedContent.replace("{{" + entry.getKey() + "}}",
                    String.valueOf(entry.getValue()));
        }

        // 创建执行任务
        var task = generationService.createTask(
            toLong(body.get("project_id")), toLong(body.get("node_id")), null,
            "skill", null, "deepseek-v3",
            Map.of("skill_id", skillId, "skill_content", executedContent,
                   "input", body.getOrDefault("input", ""), "variables", variables));

        skill.setUsageCount((skill.getUsageCount() != null ? skill.getUsageCount() : 0) + 1);
        skillMapper.updateById(skill);

        return ApiResponse.success(Map.of(
            "execution_id", task.getUuid(),
            "skill_id", skillId,
            "status", "pending",
            "message", "Skill执行任务已创建"));
    }

    // === Helpers ===
    private Skill findSkill(String id) {
        Skill byUuid = skillMapper.selectOne(
            new LambdaQueryWrapper<Skill>().eq(Skill::getUuid, id));
        if (byUuid != null) return byUuid;
        try { return skillMapper.selectById(Long.parseLong(id)); }
        catch (NumberFormatException e) { return null; }
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? null : Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }

    private Map<String, Object> toMap(Skill skill) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", skill.getId());
        map.put("uuid", skill.getUuid());
        map.put("name", skill.getName());
        map.put("description", skill.getDescription());
        map.put("content", skill.getContent());
        map.put("type", skill.getType());
        map.put("version", skill.getVersion());
        map.put("visibility", skill.getVisibility());
        map.put("owner_id", skill.getOwnerId());
        map.put("usage_count", skill.getUsageCount());
        map.put("rating", skill.getRating());
        map.put("status", skill.getStatus());
        map.put("created_at", skill.getCreatedAt());
        map.put("updated_at", skill.getUpdatedAt());
        return map;
    }
}
