package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.ProjectSettingEntity;
import com.aicp.module.contentproject.entity.ProjectSettingVersion;
import com.aicp.module.contentproject.mapper.ProjectSettingEntityMapper;
import com.aicp.module.contentproject.mapper.ProjectSettingVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
public class ProjectSettingService {

    private final ProjectSettingEntityMapper entityMapper;
    private final ProjectSettingVersionMapper versionMapper;
    private final ProjectAccessService accessService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static final Set<String> VALID_TYPES = Set.of("character", "background", "faction", "location", "item");

    // ===== List =====

    public Page<Map<String, Object>> listSettings(Long userId, Long projectId,
                                                   String type, String status, String keyword,
                                                   int page, int pageSize) {
        accessService.require(projectId, userId, Action.VIEW);

        LambdaQueryWrapper<ProjectSettingEntity> query = new LambdaQueryWrapper<>();
        query.eq(ProjectSettingEntity::getProjectId, projectId);
        if (type != null && !type.isBlank()) query.eq(ProjectSettingEntity::getSettingType, type);
        if (status != null && !status.isBlank()) query.eq(ProjectSettingEntity::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(ProjectSettingEntity::getCanonicalName, keyword)
                    .or().like(ProjectSettingEntity::getSummary, keyword));
        }
        query.ne(ProjectSettingEntity::getStatus, "archived"); // 默认排除已归档
        query.orderByDesc(ProjectSettingEntity::getUpdatedAt);

        Page<ProjectSettingEntity> entityPage = entityMapper.selectPage(new Page<>(page, pageSize), query);
        Page<Map<String, Object>> result = new Page<>(page, pageSize, entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream().map(this::toMap).toList());
        return result;
    }

    // ===== Get =====

    public Map<String, Object> getSetting(Long userId, Long projectId, Long settingId) {
        accessService.require(projectId, userId, Action.VIEW);
        ProjectSettingEntity entity = getOrFail(settingId, projectId);
        return toMap(entity);
    }

    // ===== Create =====

    @Transactional
    public Map<String, Object> createSetting(Long userId, Long projectId, Map<String, Object> body) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        String settingType = (String) body.get("setting_type");
        if (settingType == null || !VALID_TYPES.contains(settingType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无效设定类型: " + settingType);
        }

        ProjectSettingEntity entity = new ProjectSettingEntity();
        entity.setProjectId(projectId);
        entity.setSettingType(settingType);
        entity.setCanonicalName((String) body.getOrDefault("canonical_name", "未命名"));
        entity.setAliasesJson(toJson(body.get("aliases")));
        entity.setSummary((String) body.get("summary"));
        entity.setDetailsJson(toJson(body.get("details")));
        entity.setRelationshipsJson(toJson(body.get("relationships")));
        entity.setStatus("draft");
        entity.setSourceType("manual");
        entity.setCurrentVersionNo(0);
        entity.setRevision(0);
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entityMapper.insert(entity);

        // 创建初始版本
        createSettingVersion(entity, "manual", userId, null);

        return toMap(entity);
    }

    // ===== Update =====

    @Transactional
    public Map<String, Object> updateSetting(Long userId, Long projectId, Long settingId, Map<String, Object> body) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        ProjectSettingEntity entity = getOrFail(settingId, projectId);

        // 乐观锁
        Integer reqRevision = body.containsKey("revision") ? ((Number) body.get("revision")).intValue() : null;
        if (reqRevision != null && !reqRevision.equals(entity.getRevision())) {
            throw new BizException(ErrorCode.EDIT_CONFLICT,
                    "设定已被他人修改，请刷新后重试。当前服务端 revision=" + entity.getRevision());
        }

        if (body.containsKey("canonical_name")) entity.setCanonicalName((String) body.get("canonical_name"));
        if (body.containsKey("aliases")) entity.setAliasesJson(toJson(body.get("aliases")));
        if (body.containsKey("summary")) entity.setSummary((String) body.get("summary"));
        if (body.containsKey("details")) entity.setDetailsJson(toJson(body.get("details")));
        if (body.containsKey("relationships")) entity.setRelationshipsJson(toJson(body.get("relationships")));
        if (body.containsKey("status")) entity.setStatus((String) body.get("status"));
        entity.setRevision(entity.getRevision() + 1);
        entity.setUpdatedBy(userId);
        entityMapper.updateById(entity);

        createSettingVersion(entity, "manual", userId, null);

        return toMap(entity);
    }

    // ===== Archive / Delete =====

    @Transactional
    public void archiveSetting(Long userId, Long projectId, Long settingId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        ProjectSettingEntity entity = getOrFail(settingId, projectId);
        entity.setStatus("archived");
        entity.setArchivedAt(LocalDateTime.now());
        entity.setArchivedBy(userId);
        entity.setRevision(entity.getRevision() + 1);
        entityMapper.updateById(entity);
    }

    // ===== Restore =====

    @Transactional
    public Map<String, Object> restoreSetting(Long userId, Long projectId, Long settingId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        ProjectSettingEntity entity = entityMapper.selectById(settingId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "设定不存在");
        }
        entity.setStatus("draft");
        entity.setArchivedAt(null);
        entity.setArchivedBy(null);
        entity.setRevision(entity.getRevision() + 1);
        entityMapper.updateById(entity);
        return toMap(entity);
    }

    // ===== Copy =====

    @Transactional
    public Map<String, Object> copySetting(Long userId, Long projectId, Long settingId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        ProjectSettingEntity original = getOrFail(settingId, projectId);

        ProjectSettingEntity copy = new ProjectSettingEntity();
        copy.setProjectId(projectId);
        copy.setSettingType(original.getSettingType());
        copy.setCanonicalName(original.getCanonicalName() + "（副本）");
        copy.setAliasesJson(original.getAliasesJson());
        copy.setSummary(original.getSummary());
        copy.setDetailsJson(original.getDetailsJson());
        copy.setRelationshipsJson(original.getRelationshipsJson());
        copy.setStatus("draft");
        copy.setSourceType("manual");
        copy.setCurrentVersionNo(0);
        copy.setRevision(0);
        copy.setCreatedBy(userId);
        copy.setUpdatedBy(userId);
        entityMapper.insert(copy);

        createSettingVersion(copy, "manual", userId, null);

        return toMap(copy);
    }

    // ===== Versions =====

    public List<Map<String, Object>> listVersions(Long userId, Long projectId, Long settingId) {
        accessService.require(projectId, userId, Action.VIEW);
        getOrFail(settingId, projectId); // ensure exists

        return versionMapper.selectList(
                new LambdaQueryWrapper<ProjectSettingVersion>()
                        .eq(ProjectSettingVersion::getEntityId, settingId)
                        .orderByDesc(ProjectSettingVersion::getVersionNo))
                .stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("entity_id", v.getEntityId());
                    m.put("version_no", v.getVersionNo());
                    m.put("source_type", v.getSourceType());
                    m.put("operated_by", v.getOperatedBy());
                    m.put("created_at", v.getCreatedAt());
                    return m;
                })
                .toList();
    }

    // ===== Internal =====

    private ProjectSettingEntity getOrFail(Long id, Long projectId) {
        ProjectSettingEntity entity = entityMapper.selectById(id);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "设定不存在: " + id);
        }
        return entity;
    }

    public void createSettingVersion(ProjectSettingEntity entity, String sourceType, Long operatedBy, String evidenceJson) {
        int nextVersion = entity.getCurrentVersionNo() + 1;
        ProjectSettingVersion version = new ProjectSettingVersion();
        version.setEntityId(entity.getId());
        version.setVersionNo(nextVersion);
        version.setSnapshotJson(toJson(toMap(entity)));
        version.setSourceType(sourceType);
        version.setOperatedBy(operatedBy);
        version.setEvidenceJson(evidenceJson);
        versionMapper.insert(version);

        entity.setCurrentVersionNo(nextVersion);
        entityMapper.updateById(entity);
    }

    private Map<String, Object> toMap(ProjectSettingEntity entity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", entity.getId());
        m.put("project_id", entity.getProjectId());
        m.put("setting_type", entity.getSettingType());
        m.put("canonical_name", entity.getCanonicalName());
        m.put("aliases", parseJson(entity.getAliasesJson()));
        m.put("summary", entity.getSummary());
        m.put("details", parseJson(entity.getDetailsJson()));
        m.put("relationships", parseJson(entity.getRelationshipsJson()));
        m.put("status", entity.getStatus());
        m.put("source_type", entity.getSourceType());
        m.put("current_version_no", entity.getCurrentVersionNo());
        m.put("revision", entity.getRevision());
        m.put("created_at", entity.getCreatedAt());
        m.put("updated_at", entity.getUpdatedAt());
        return m;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
