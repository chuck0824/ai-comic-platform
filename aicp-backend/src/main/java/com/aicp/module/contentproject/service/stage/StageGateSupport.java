package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.ProjectParameterVersion;
import com.aicp.module.contentproject.entity.StoryboardHandoffSnapshot;
import com.aicp.module.contentproject.entity.UploadFile;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.aicp.module.contentproject.mapper.StoryboardHandoffSnapshotMapper;
import com.aicp.module.contentproject.mapper.UploadFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门禁评估共享读模型：从参数版本 / ContentUnit 草稿读取可计算证据。
 */
@Component
@RequiredArgsConstructor
public class StageGateSupport {

    private final ProjectParameterVersionMapper parameterVersionMapper;
    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final UploadFileMapper uploadFileMapper;
    private final StoryboardHandoffSnapshotMapper handoffSnapshotMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> loadParameterPayload(Long parameterVersionId) {
        if (parameterVersionId == null) {
            return Map.of();
        }
        ProjectParameterVersion version = parameterVersionMapper.selectById(parameterVersionId);
        if (version == null || version.getPayloadJson() == null || version.getPayloadJson().isBlank()) {
            return Map.of();
        }
        return parseObject(version.getPayloadJson());
    }

    public ContentUnit findUnit(Long projectId, String unitType) {
        if (projectId == null || unitType == null) {
            return null;
        }
        return unitMapper.selectOne(new LambdaQueryWrapper<ContentUnit>()
                .eq(ContentUnit::getProjectId, projectId)
                .eq(ContentUnit::getUnitType, unitType)
                .eq(ContentUnit::getIsDeleted, 0)
                .orderByAsc(ContentUnit::getDisplayNo)
                .last("limit 1"));
    }

    public Map<String, Object> loadUnitPayload(Long projectId, String unitType) {
        ContentUnit unit = findUnit(projectId, unitType);
        if (unit == null) {
            return Map.of();
        }
        ContentVersion draft = versionMapper.selectOne(new LambdaQueryWrapper<ContentVersion>()
                .eq(ContentVersion::getContentUnitId, unit.getId())
                .eq(ContentVersion::getStatus, "draft")
                .last("limit 1"));
        if (draft != null && draft.getContentJson() != null && !draft.getContentJson().isBlank()) {
            return parseObject(draft.getContentJson());
        }
        if (unit.getCurrentVersionId() != null) {
            ContentVersion current = versionMapper.selectById(unit.getCurrentVersionId());
            if (current != null && current.getContentJson() != null) {
                return parseObject(current.getContentJson());
            }
        }
        return Map.of();
    }

    public ContentVersion loadCurrentVersion(ContentUnit unit) {
        if (unit == null || unit.getCurrentVersionId() == null) {
            return null;
        }
        return versionMapper.selectById(unit.getCurrentVersionId());
    }

    public UploadFile loadUpload(Long uploadId) {
        if (uploadId == null) {
            return null;
        }
        return uploadFileMapper.selectById(uploadId);
    }

    public StoryboardHandoffSnapshot latestHandoff(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return handoffSnapshotMapper.selectOne(new LambdaQueryWrapper<StoryboardHandoffSnapshot>()
                .eq(StoryboardHandoffSnapshot::getProjectId, projectId)
                .orderByDesc(StoryboardHandoffSnapshot::getId)
                .last("limit 1"));
    }

    public Object firstNonBlank(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String s) {
                if (!s.isBlank()) {
                    return s.trim();
                }
            } else {
                return value;
            }
        }
        return null;
    }

    public boolean blank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> asObjectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    public Map<String, Object> parseObject(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("_raw", json == null ? "" : json);
        }
    }

    public static final class Builder {
        private final List<Map<String, Object>> blockers = new ArrayList<>();
        private final List<Map<String, Object>> warnings = new ArrayList<>();
        private final Map<String, Object> evidence = new LinkedHashMap<>();

        public Builder evidence(String key, Object value) {
            evidence.put(key, value);
            return this;
        }

        public Builder require(boolean ok, String code, String message) {
            if (!ok) {
                blockers.add(StageGateResult.issue(code, message));
            }
            return this;
        }

        public Builder warn(boolean condition, String code, String message) {
            if (condition) {
                warnings.add(StageGateResult.issue(code, message));
            }
            return this;
        }

        public StageGateResult build() {
            return StageGateResult.of(blockers, warnings, evidence);
        }
    }

    public Builder builder() {
        return new Builder();
    }
}
