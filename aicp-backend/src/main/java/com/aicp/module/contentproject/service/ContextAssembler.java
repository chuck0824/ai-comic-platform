package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.dto.CreativeBibleViews.ResolvedWritingGuideView;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAssembler {

    private final ContentProjectMapper projectMapper;
    private final ProjectParameterVersionMapper parameterVersionMapper;
    private final ContentVersionMapper versionMapper;
    private final CreativeBibleVersionMapper bibleMapper;
    private final WritingGuideResolver guideResolver;
    private final ObjectMapper objectMapper;

    public ContextSnapshot assemble(Long projectId, GenerationJobRequest request) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }

        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Long> selected = new LinkedHashMap<>();

        // load selected parameter version
        Long paramVersionId = request.selectedVersions() != null
                ? request.selectedVersions().get("parameter")
                : project.getCurrentParameterVersionId();
        if (paramVersionId != null) {
            ProjectParameterVersion pv = parameterVersionMapper.selectById(paramVersionId);
            if (pv == null || !pv.getProjectId().equals(projectId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "参数版本不属于当前项目");
            }
            try {
                Map<String, Object> payload = objectMapper.readValue(pv.getPayloadJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                context.put("parameter", payload);
            } catch (JsonProcessingException e) {
                context.put("parameter", Map.of());
            }
            selected.put("parameter", paramVersionId);
        }

        // load selected content versions
        if (request.selectedVersions() != null) {
            for (Map.Entry<String, Long> entry : request.selectedVersions().entrySet()) {
                if ("parameter".equals(entry.getKey())) continue;
                Long versionId = entry.getValue();
                ContentVersion cv = versionMapper.selectById(versionId);
                if (cv == null || !cv.getProjectId().equals(projectId)) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "内容版本 " + entry.getKey() + " 不属于当前项目");
                }
                try {
                    Map<String, Object> content = objectMapper.readValue(cv.getContentJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    context.put(entry.getKey(), content);
                } catch (JsonProcessingException e) {
                    context.put(entry.getKey(), cv.getPlainText());
                }
                selected.put(entry.getKey(), versionId);
            }
        }

        // add strategy
        Map<String, Object> strategyMap = null;
        if (request.strategy() != null) {
            strategyMap = parseStrategyJson(request.strategy());
            context.put("strategy", strategyMap);
        }

        // ── Creative Bible ──
        Long bibleVersionId = null;
        Long projectGuideId = null;
        List<Long> characterGuideIds = List.of();
        Long unitGuideId = null;
        String resolvedGuideJson = null;

        boolean allowUnconfirmed = strategyMap != null
                && Boolean.TRUE.equals(strategyMap.get("allow_unconfirmed_bible"));

        CreativeBibleVersion confirmedBible = bibleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CreativeBibleVersion>()
                        .eq(CreativeBibleVersion::getProjectId, projectId)
                        .eq(CreativeBibleVersion::getStatus, "confirmed")
                        .orderByDesc(CreativeBibleVersion::getVersionNo)
                        .last("LIMIT 1"));

        if (confirmedBible == null) {
            if (!allowUnconfirmed) {
                throw new BizException(ErrorCode.PARAM_INVALID, "创作圣经尚未确认，请先确认生态或实体设定");
            }
            log.warn("创作圣经未确认但允许继续: projectId={}, bypass=allow_unconfirmed_bible", projectId);
        } else {
            bibleVersionId = confirmedBible.getId();
            context.put("creative_bible", parseSnapshotJson(confirmedBible.getSnapshotJson()));
            selected.put("creative_bible", bibleVersionId);

            // Resolve writing guides
            Long contentUnitId = request.targetId() != null
                    && "content_unit".equals(request.targetType()) ? request.targetId() : null;
            List<Long> charIds = extractCharacterIds(strategyMap);
            ResolvedWritingGuideView resolvedGuide = guideResolver.resolve(
                    projectId, bibleVersionId, contentUnitId, charIds);
            projectGuideId = resolvedGuide.projectGuideId();
            characterGuideIds = resolvedGuide.characterGuideIds();
            unitGuideId = resolvedGuide.unitGuideId();
            try {
                resolvedGuideJson = objectMapper.writeValueAsString(resolvedGuide.resolved());
            } catch (JsonProcessingException e) {
                resolvedGuideJson = "{}";
            }
            context.put("resolved_writing_guide", resolvedGuide.resolved());
        }

        String payloadJson;
        try {
            TreeMap<String, Object> sorted = new TreeMap<>(context);
            payloadJson = objectMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }
        String hash = sha256(payloadJson);

        return new ContextSnapshot(selected, bibleVersionId, projectGuideId,
                characterGuideIds, unitGuideId, resolvedGuideJson, payloadJson, hash);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStrategyJson(String strategy) {
        if (strategy == null || strategy.isBlank()) return null;
        try {
            return objectMapper.readValue(strategy, Map.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object parseSnapshotJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> extractCharacterIds(Map<String, Object> strategy) {
        if (strategy == null) return List.of();
        Object raw = strategy.get("character_ids");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof Number)
                    .map(o -> ((Number) o).longValue())
                    .filter(id -> id > 0)
                    .toList();
        }
        return List.of();
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
}
