package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.WorkEditorRequests.*;
import com.aicp.module.contentproject.dto.WorkEditorViews.*;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.script.entity.Script;
import com.aicp.module.script.mapper.ScriptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final TagDictionaryMapper tagDictionaryMapper;
    private final ProjectSettingEntityMapper settingEntityMapper;
    private final SettingExtractionBatchMapper extractionBatchMapper;
    private final ProjectAccessService accessService;
    private final CreativeBibleService creativeBibleService;
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

    // ===== Editor Aggregation =====

    /**
     * 旧入口：通过 scriptId 解析后返回编辑器聚合视图。
     */
    public EditorView getLegacyEditor(Long userId, Long scriptId) {
        ContentProject project = resolveLegacy(userId, scriptId);
        return buildEditorView(project.getId());
    }

    /**
     * 新入口：通过 projectId 返回编辑器聚合视图。
     */
    public EditorView getEditor(Long userId, Long projectId) {
        accessService.require(projectId, userId, Action.VIEW);
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return buildEditorView(projectId);
    }

    private EditorView buildEditorView(Long projectId) {
        ContentProject project = projectMapper.selectById(projectId);
        ContentProjectProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<ContentProjectProfile>()
                        .eq(ContentProjectProfile::getProjectId, projectId));

        // 权限取第一个匹配成员的角色
        ProjectMember member = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)).stream().findFirst().orElse(null);
        String permissions = member != null ? member.getRole() : "viewer";

        // 设定数量统计
        Map<String, Integer> settingCounts = new LinkedHashMap<>();
        for (String type : List.of("character", "background", "faction", "location", "item")) {
            Long count = settingEntityMapper.selectCount(
                    new LambdaQueryWrapper<ProjectSettingEntity>()
                            .eq(ProjectSettingEntity::getProjectId, projectId)
                            .eq(ProjectSettingEntity::getSettingType, type)
                            .ne(ProjectSettingEntity::getStatus, "archived"));
            settingCounts.put(type, count.intValue());
        }

        // 待处理提取候选数
        Long pendingExtraction = extractionBatchMapper.selectCount(
                new LambdaQueryWrapper<SettingExtractionBatch>()
                        .eq(SettingExtractionBatch::getProjectId, projectId)
                        .in(SettingExtractionBatch::getStatus, "review_pending", "conflicted"));

        // 创作圣经健康度
        Map<String, Object> bibleHealth = creativeBibleService.health(
                member != null ? member.getUserId() : 0L, projectId);

        ProfileView pv = profile != null ? toProfileView(profile) : null;

        return new EditorView(
                project.getId(),
                project.getName(),
                0, // wordCount 后续从 content_units 汇总
                permissions,
                pv,
                project.getRevision(),
                settingCounts,
                pendingExtraction.intValue(),
                bibleHealth,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    // ===== Tags =====

    @Transactional
    public ProfileView updateTags(Long userId, Long projectId, UpdateTagsRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        ContentProjectProfile profile = getProfileOrFail(projectId);

        // 乐观锁校验
        if (request.revision() != null && !request.revision().equals(profile.getRevision())) {
            throw new BizException(ErrorCode.EDIT_CONFLICT,
                    "资料已被他人修改，请刷新后重试。当前服务端 revision=" + profile.getRevision());
        }

        // 标签校验
        validateTags(request);

        // 更新标签
        profile.setGenreTag(request.genre());
        profile.setPlotTags(toJson(request.plot()));
        profile.setToneTags(toJson(request.tone()));
        profile.setSettingTag(request.setting());
        profile.setRevision(profile.getRevision() + 1);
        profile.setUpdatedBy(userId);
        profileMapper.updateById(profile);

        return toProfileView(profile);
    }

    void validateTags(UpdateTagsRequest request) {
        // 加载活跃字典
        List<TagDictionary> dict = tagDictionaryMapper.selectList(
                new LambdaQueryWrapper<TagDictionary>()
                        .eq(TagDictionary::getIsActive, 1));

        Set<String> genreSet = dictValues(dict, "genre");
        Set<String> plotSet = dictValues(dict, "plot");
        Set<String> toneSet = dictValues(dict, "tone");
        Set<String> settingSet = dictValues(dict, "setting");

        // 题材：最多 1 个，必须合法
        if (request.genre() != null && !request.genre().isEmpty()) {
            if (!genreSet.contains(request.genre())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "无效题材: " + request.genre());
            }
        }

        // 情节：最多 3 个，去重后校验
        if (request.plot() != null) {
            List<String> deduped = request.plot().stream().distinct().toList();
            if (deduped.size() > 3) {
                throw new BizException(ErrorCode.PARAM_INVALID, "情节标签最多 3 个");
            }
            for (String p : deduped) {
                if (!plotSet.contains(p)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "无效情节标签: " + p);
                }
            }
        }

        // 情绪：最多 3 个，去重后校验
        if (request.tone() != null) {
            List<String> deduped = request.tone().stream().distinct().toList();
            if (deduped.size() > 3) {
                throw new BizException(ErrorCode.PARAM_INVALID, "情绪标签最多 3 个");
            }
            for (String t : deduped) {
                if (!toneSet.contains(t)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "无效情绪标签: " + t);
                }
            }
        }

        // 时空：最多 1 个，必须合法
        if (request.setting() != null && !request.setting().isEmpty()) {
            if (!settingSet.contains(request.setting())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "无效时空标签: " + request.setting());
            }
        }
    }

    // ===== Profile (synopsis/outline) =====

    @Transactional
    public ProfileView updateProfile(Long userId, Long projectId, UpdateProfileRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        ContentProjectProfile profile = getProfileOrFail(projectId);

        // 乐观锁校验
        if (request.revision() != null && !request.revision().equals(profile.getRevision())) {
            throw new BizException(ErrorCode.EDIT_CONFLICT,
                    "资料已被他人修改，请刷新后重试。当前服务端 revision=" + profile.getRevision());
        }

        if (request.synopsis() != null) {
            profile.setSynopsis(request.synopsis());
        }
        if (request.outline() != null) {
            profile.setOutline(request.outline());
        }
        profile.setRevision(profile.getRevision() + 1);
        profile.setUpdatedBy(userId);
        profileMapper.updateById(profile);

        return toProfileView(profile);
    }

    // ===== Internal helpers =====

    private ContentProjectProfile getProfileOrFail(Long projectId) {
        ContentProjectProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<ContentProjectProfile>()
                        .eq(ContentProjectProfile::getProjectId, projectId));
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目资料不存在: " + projectId);
        }
        return profile;
    }

    private Set<String> dictValues(List<TagDictionary> dict, String axis) {
        Set<String> values = new HashSet<>();
        for (TagDictionary entry : dict) {
            if (axis.equals(entry.getAxis())) {
                values.add(entry.getTagValue());
            }
        }
        return values;
    }

    private ProfileView toProfileView(ContentProjectProfile profile) {
        return new ProfileView(
                profile.getGenreTag(),
                parseJsonList(profile.getPlotTags()),
                parseJsonList(profile.getToneTags()),
                profile.getSettingTag(),
                profile.getSynopsis(),
                profile.getOutline(),
                profile.getRevision(),
                profile.getUpdatedBy(),
                profile.getUpdatedAt()
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 对外暴露标签校验，供 ScriptService 双写时复用。 */
    public void validateTagsPublic(UpdateTagsRequest request) {
        validateTags(request);
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
