package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentProjectProfile;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.entity.ProjectParameterVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentProjectProfileMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.aicp.module.script.entity.Script;
import com.aicp.module.script.mapper.ScriptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkEditorService {

    private final ScriptMapper scriptMapper;
    private final ContentProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ContentProjectProfileMapper profileMapper;
    private final ProjectParameterVersionMapper parameterVersionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将旧 scriptId 解析为内容项目。
     * 已有映射则直接返回；否则新建项目 + profile + 成员 + 参数版本。
     *
     * @param userId   当前用户ID
     * @param scriptId 旧剧本ID
     * @return 解析后的 ContentProject
     */
    @Transactional
    public ContentProject resolveLegacy(Long userId, Long scriptId) {
        // 1. 查找旧剧本
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "剧本不存在: " + scriptId);
        }

        // 2. 校验剧本归属
        verifyScriptOwnership(userId, script);

        // 3. 查找已有映射
        ContentProject existing = projectMapper.selectOne(
                new LambdaQueryWrapper<ContentProject>()
                        .eq(ContentProject::getLegacyScriptId, scriptId)
                        .eq(ContentProject::getIsDeleted, 0));
        if (existing != null) {
            // 确保当前用户是项目成员
            ensureMembership(existing.getId(), userId);
            return existing;
        }

        // 4. 新建内容项目
        ContentProject project = new ContentProject();
        project.setUuid("CP_" + UUID.randomUUID().toString().replace("-", ""));
        project.setTenantType("personal");
        project.setTenantId(userId);
        project.setOwnerUserId(userId);
        project.setName(script.getTitle() != null ? script.getTitle() : "未命名作品");
        project.setCreationMode("short_drama");
        project.setSourceMode("ai_manual");
        project.setStoryboardIntentStatus("not_decided");
        project.setContentStatus("draft");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLastStageKey("story_seed");
        project.setLegacyScriptId(scriptId);
        project.setRevision(0);
        project.setIsDeleted(0);
        projectMapper.insert(project);

        // 5. 添加 Owner 成员
        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(userId);
        member.setRole("owner");
        memberMapper.insert(member);

        // 6. 创建项目资料，迁移旧标签和简介
        ContentProjectProfile profile = new ContentProjectProfile();
        profile.setProjectId(project.getId());
        profile.setGenreTag(script.getGenreTag());
        profile.setPlotTags(script.getPlotTags());
        profile.setToneTags(script.getToneTags());
        profile.setSettingTag(script.getSettingTag());
        profile.setSynopsis(script.getSynopsis());
        profile.setOutline(null);
        profile.setRevision(0);
        profile.setUpdatedBy(userId);
        profileMapper.insert(profile);

        // 7. 创建初始参数版本
        Map<String, Object> initialParams = new LinkedHashMap<>();
        initialParams.put("start_content", script.getSynopsis() != null ? script.getSynopsis() : "");
        initialParams.put("content_goal", "追更");
        String payloadJson = toJson(initialParams);
        String hash = sha256(payloadJson);

        ProjectParameterVersion paramV1 = new ProjectParameterVersion();
        paramV1.setProjectId(project.getId());
        paramV1.setVersionNo(1);
        paramV1.setPayloadJson(payloadJson);
        paramV1.setContentHash(hash);
        paramV1.setCreatedBy(userId);
        parameterVersionMapper.insert(paramV1);

        project.setCurrentParameterVersionId(paramV1.getId());
        projectMapper.updateById(project);

        log.info("旧剧本 {} 解析为新内容项目 {} (profile={}, paramVersion={})",
                scriptId, project.getId(), profile.getId(), paramV1.getId());

        return project;
    }

    /**
     * 校验当前用户是否为剧本的作者或拥有者。
     */
    private void verifyScriptOwnership(Long userId, Script script) {
        boolean isAuthor = userId.equals(script.getAuthorUserId());
        boolean isOwner = userId.equals(script.getOwnerUserId());
        if (!isAuthor && !isOwner) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该剧本");
        }
    }

    /**
     * 确保用户是项目成员，如果不是则添加为 viewer。
     */
    private void ensureMembership(Long projectId, Long userId) {
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId));
        if (count == 0) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(userId);
            member.setRole("viewer");
            memberMapper.insert(member);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
