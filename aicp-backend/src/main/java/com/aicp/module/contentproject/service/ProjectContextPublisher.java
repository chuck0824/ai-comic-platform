package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.entity.ContentProjectProfile;
import com.aicp.module.contentproject.entity.ProjectSettingEntity;
import com.aicp.module.contentproject.mapper.ContentProjectProfileMapper;
import com.aicp.module.contentproject.mapper.ProjectSettingEntityMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectContextPublisher {

    private final ProjectWorkflowService workflowService;
    private final ContentProjectProfileMapper profileMapper;
    private final ProjectSettingEntityMapper settingEntityMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 基于当前已确认的资料和设定，生成新参数快照并发布上下文刷新事件。
     */
    public void publish(Long projectId, Long userId) {
        // 1. 组装当前已确认数据
        Map<String, Object> params = new LinkedHashMap<>();

        // 资料
        ContentProjectProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<ContentProjectProfile>()
                        .eq(ContentProjectProfile::getProjectId, projectId));
        if (profile != null) {
            params.put("genre_tag", profile.getGenreTag());
            params.put("plot_tags", parseJson(profile.getPlotTags()));
            params.put("tone_tags", parseJson(profile.getToneTags()));
            params.put("setting_tag", profile.getSettingTag());
            params.put("synopsis", profile.getSynopsis());
            params.put("outline", profile.getOutline());
        }

        // 已确认设定
        List<ProjectSettingEntity> confirmedSettings = settingEntityMapper.selectList(
                new LambdaQueryWrapper<ProjectSettingEntity>()
                        .eq(ProjectSettingEntity::getProjectId, projectId)
                        .eq(ProjectSettingEntity::getStatus, "confirmed"));
        List<Map<String, Object>> settingsList = new ArrayList<>();
        for (ProjectSettingEntity s : confirmedSettings) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("type", s.getSettingType());
            sm.put("name", s.getCanonicalName());
            sm.put("summary", s.getSummary());
            sm.put("details", parseJson(s.getDetailsJson()));
            sm.put("relationships", parseJson(s.getRelationshipsJson()));
            settingsList.add(sm);
        }
        params.put("settings", settingsList);

        // 2. 创建新参数版本
        var request = new com.aicp.module.contentproject.dto.ContentProjectRequests.AppendParameterRequest(
                params, null);
        var version = workflowService.appendParameters(userId, projectId, request);

        // 3. 发布上下文刷新事件
        outboxService.append("CONTEXT_REFRESH", projectId, 0,
                Map.of("project_id", projectId,
                        "parameter_version_id", version.id(),
                        "published_by", userId));

        log.info("上下文已刷新 projectId={} parameterVersionId={} settingsCount={}",
                projectId, version.id(), confirmedSettings.size());
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return String.valueOf(value); }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }
}
