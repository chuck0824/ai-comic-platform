package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.domain.ContentProjectEnums.CreationMode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.SourceMode;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.script.entity.Script;
import com.aicp.module.script.entity.ScriptEpisode;
import com.aicp.module.script.mapper.ScriptEpisodeMapper;
import com.aicp.module.script.mapper.ScriptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyProjectProjectionService {

    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ContentProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectParameterVersionMapper parameterVersionMapper;
    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    /**
     * Idempotent backfill: create one content-project per legacy script
     * that doesn't already have a project.
     */
    @Transactional
    public BackfillResult backfill(Long ownerId) {
        List<Script> scripts = scriptMapper.selectList(
                new LambdaQueryWrapper<Script>()
                        .eq(Script::getOwnerUserId, ownerId));

        int createdProjects = 0;
        int createdUnits = 0;
        int createdVersions = 0;
        int skipped = 0;

        for (Script script : scripts) {
            // check if already backfilled
            Long count = projectMapper.selectCount(
                    new LambdaQueryWrapper<ContentProject>()
                            .eq(ContentProject::getLegacyScriptId, script.getId()));
            if (count > 0) {
                skipped++;
                continue;
            }

            // determine source mode
            String sourceMode = script.getSource() != null && script.getSource().contains("upload")
                    ? "uploaded" : "ai_manual";

            // create project
            ContentProject project = new ContentProject();
            project.setUuid("CP_" + UUID.randomUUID().toString().replace("-", ""));
            project.setTenantType("personal");
            project.setTenantId(ownerId);
            project.setOwnerUserId(ownerId);
            project.setName(script.getTitle() != null ? script.getTitle() : "未命名项目");
            project.setCreationMode("short_drama");
            project.setSourceMode(sourceMode);
            project.setStoryboardIntentStatus("not_decided");
            project.setContentStatus(script.getStatus() != null ? script.getStatus() : "draft");
            project.setProductionStatus("not_started");
            project.setMarketStatus("private");
            project.setLastStageKey("uploaded".equals(sourceMode) ? "import_review" : "story_seed");
            project.setLegacyScriptId(script.getId());
            project.setRevision(0);
            project.setIsDeleted(0);
            projectMapper.insert(project);
            createdProjects++;

            // owner membership
            ProjectMember owner = new ProjectMember();
            owner.setProjectId(project.getId());
            owner.setUserId(ownerId);
            owner.setRole("owner");
            memberMapper.insert(owner);

            // parameter v1 from script synopsis
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("start_content", script.getSynopsis() != null ? script.getSynopsis() : "");
            params.put("content_goal", "追更");
            String payloadJson = toJson(params);
            String hash = sha256(payloadJson);

            ProjectParameterVersion pv = new ProjectParameterVersion();
            pv.setProjectId(project.getId());
            pv.setVersionNo(1);
            pv.setPayloadJson(payloadJson);
            pv.setContentHash(hash);
            pv.setCreatedBy(ownerId);
            parameterVersionMapper.insert(pv);
            project.setCurrentParameterVersionId(pv.getId());
            projectMapper.updateById(project);

            // create content units from episodes
            List<ScriptEpisode> episodes = episodeMapper.selectList(
                    new LambdaQueryWrapper<ScriptEpisode>()
                            .eq(ScriptEpisode::getScriptId, script.getId())
                            .orderByAsc(ScriptEpisode::getEpisodeNumber));

            for (ScriptEpisode ep : episodes) {
                ContentUnit unit = new ContentUnit();
                unit.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
                unit.setProjectId(project.getId());
                unit.setUnitType("episode");
                unit.setDisplayNo(ep.getEpisodeNumber() != null ? ep.getEpisodeNumber() : 1);
                unit.setTitle(ep.getTitle() != null ? ep.getTitle() : "第" + ep.getEpisodeNumber() + "集");
                unit.setStatus(ep.getStatus() != null ? ep.getStatus() : "draft");
                unit.setRevision(0);
                unit.setIsDeleted(0);
                unitMapper.insert(unit);
                createdUnits++;

                // content version v1
                ContentVersion cv = new ContentVersion();
                cv.setProjectId(project.getId());
                cv.setContentUnitId(unit.getId());
                cv.setVersionNo(1);
                cv.setStatus("draft");
                cv.setContentJson(toJson(Map.of("content", ep.getContent() != null ? ep.getContent() : "")));
                cv.setPlainText(ep.getContent());
                cv.setSource("manual_edit");
                cv.setContentHash(sha256(ep.getContent() != null ? ep.getContent() : ""));
                cv.setCreatedBy(ownerId);
                versionMapper.insert(cv);
                createdVersions++;

                unit.setCurrentVersionId(cv.getId());
                unitMapper.updateById(unit);
            }
        }

        return new BackfillResult(createdProjects, createdUnits, createdVersions, skipped);
    }

    /**
     * Project a V7-only content-project as a read-only legacy script summary.
     */
    public Map<String, Object> projectAsLegacyScript(Long projectId) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("id", project.getId());
        script.put("uuid", project.getUuid());
        script.put("title", project.getName());
        script.put("status", project.getContentStatus());
        script.put("source", "v7_content_project");
        script.put("compat_read_only", true);
        script.put("created_at", project.getCreatedAt());
        script.put("updated_at", project.getUpdatedAt());

        // count episodes
        long episodeCount = unitMapper.selectCount(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, "episode")
                        .eq(ContentUnit::getIsDeleted, 0));
        script.put("episode_count", (int) episodeCount);

        return script;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ===== character_profiles → project_setting_entities migration =====

    private final CharacterProfileMapper characterProfileMapper;
    private final ProjectSettingEntityMapper settingEntityMapper;

    /**
     * 可重复执行的迁移：将 character_profiles 转为 setting_type='character' 的
     * project_setting_entities。已有映射则跳过。
     */
    @Transactional
    public Map<String, Integer> migrateCharacterProfiles() {
        List<CharacterProfile> profiles = characterProfileMapper.selectList(
                new LambdaQueryWrapper<CharacterProfile>());
        int migrated = 0;
        int skipped = 0;

        for (CharacterProfile cp : profiles) {
            // 按 project_id + canonical_name 检查是否已迁移
            Long count = settingEntityMapper.selectCount(
                    new LambdaQueryWrapper<ProjectSettingEntity>()
                            .eq(ProjectSettingEntity::getProjectId, cp.getProjectId())
                            .eq(ProjectSettingEntity::getSettingType, "character")
                            .eq(ProjectSettingEntity::getCanonicalName, cp.getName()));
            if (count > 0) {
                skipped++;
                continue;
            }

            // 组装 details_json
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("role", cp.getRole());
            details.put("archetype", cp.getArchetype());
            details.put("appearance", cp.getAppearance());
            details.put("personality", cp.getPersonality());
            details.put("motivation", cp.getMotivation());
            details.put("long_term_goal", cp.getLongTermGoal());
            details.put("knowledge_boundary", cp.getKnowledgeBoundary());
            details.put("dialogue_style", cp.getDialogueStyle());
            details.put("backstory", cp.getBackstory());

            ProjectSettingEntity entity = new ProjectSettingEntity();
            entity.setProjectId(cp.getProjectId());
            entity.setSettingType("character");
            entity.setCanonicalName(cp.getName());
            entity.setSummary(cp.getRole());
            entity.setDetailsJson(toJson(details));
            entity.setRelationshipsJson(cp.getRelationshipsJson());
            entity.setStatus("draft".equals(cp.getStatus()) ? "draft" : "confirmed");
            entity.setSourceType("manual");
            entity.setCurrentVersionNo(0);
            entity.setRevision(0);
            entity.setCreatedBy(0L);
            entity.setUpdatedBy(0L);
            settingEntityMapper.insert(entity);

            migrated++;
        }

        log.info("character_profiles 迁移完成: migrated={} skipped={}", migrated, skipped);
        return Map.of("migrated", migrated, "skipped", skipped);
    }

    public record BackfillResult(int projects, int units, int versions, int skipped) {}
}
