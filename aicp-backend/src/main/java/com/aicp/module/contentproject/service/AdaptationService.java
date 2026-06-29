package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * M2: Adaptation scripts — create adapted versions from source content.
 * Binds source version, generates per-episode adaptation, versioned + reviewable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptationService {

    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    static final String ADAPT_PROMPT = """
        你是一位资深改编编剧。请根据原文内容改编为短剧剧本格式。
        要求：
        1. 保留核心情节和人物关系
        2. 增加对白和动作描述
        3. 适合1-3分钟/集的短剧节奏
        4. 每集必须有开场钩子和结尾悬念

        输出JSON：
        {"title": "改编标题", "content": "改编后的完整剧本内容", "episodes": [{"episode_no":1, "title":"","content":""}]}
        """;

    @Transactional
    public ContentUnit createAdaptation(Long userId, Long projectId, Long sourceUnitId, String targetFormat) {
        ContentUnit source = unitMapper.selectById(sourceUnitId);
        if (source == null || !source.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        ContentVersion sourceVersion = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, sourceUnitId)
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        if (sourceVersion == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源内容没有可用版本");
        }

        String sourceContent = sourceVersion.getPlainText();
        if (sourceContent == null || sourceContent.isBlank()) {
            sourceContent = sourceVersion.getContentJson();
        }

        // Call AI for adaptation
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", ADAPT_PROMPT);
        params.put("prompt", "目标格式：" + targetFormat + "\n\n原文：\n" + ellipsis(sourceContent, 6000));
        params.put("temperature", 0.7);
        params.put("max_tokens", 4096);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = extractText(result);
        Map<String, Object> parsed = parseJson(text);

        // Create adaptation unit
        ContentUnit unit = new ContentUnit();
        unit.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
        unit.setProjectId(projectId);
        unit.setUnitType("adaptation");
        unit.setDisplayNo(getNextDisplayNo(projectId, "adaptation"));
        unit.setTitle((String) parsed.getOrDefault("title", "改编版"));
        unit.setStatus("draft");
        unit.setRevision(0);
        unit.setIsDeleted(0);
        unitMapper.insert(unit);

        // Create version
        String adaptedContent = (String) parsed.getOrDefault("content", text);
        ContentVersion cv = new ContentVersion();
        cv.setProjectId(projectId);
        cv.setContentUnitId(unit.getId());
        cv.setVersionNo(1);
        cv.setStatus("draft");
        cv.setContentJson(toJson(Map.of("content", adaptedContent, "source_unit_id", sourceUnitId)));
        cv.setPlainText(adaptedContent);
        cv.setSource("adaptation");
        cv.setContentHash(sha256(adaptedContent));
        cv.setCreatedBy(userId);
        versionMapper.insert(cv);

        unit.setCurrentVersionId(cv.getId());
        unitMapper.updateById(unit);

        return unit;
    }

    @Transactional
    public ContentUnit createAdaptationMultiEpisode(Long userId, Long projectId, Long sourceUnitId, String targetFormat) {
        ContentUnit source = unitMapper.selectById(sourceUnitId);
        if (source == null) throw new BizException(ErrorCode.NOT_FOUND);

        ContentVersion sourceVersion = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, sourceUnitId)
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        if (sourceVersion == null) throw new BizException(ErrorCode.PARAM_INVALID, "源内容没有可用版本");

        // AI generates multi-episode adaptation
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", ADAPT_PROMPT);
        params.put("prompt", "目标格式：" + targetFormat + "\n请分为多集改编：\n" + ellipsis(sourceVersion.getPlainText(), 6000));
        params.put("temperature", 0.7);
        params.put("max_tokens", 4096);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = extractText(result);
        Map<String, Object> parsed = parseJson(text);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> episodes = (List<Map<String, Object>>) parsed.getOrDefault("episodes", List.of());
        if (episodes.isEmpty()) {
            return createAdaptation(userId, projectId, sourceUnitId, targetFormat);
        }

        // Create one unit per episode
        ContentUnit firstUnit = null;
        for (Map<String, Object> ep : episodes) {
            ContentUnit unit = new ContentUnit();
            unit.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
            unit.setProjectId(projectId);
            unit.setUnitType("adaptation");
            unit.setDisplayNo(getNextDisplayNo(projectId, "adaptation"));
            unit.setTitle((String) ep.getOrDefault("title", "改编第" + ep.get("episode_no") + "集"));
            unit.setStatus("draft");
            unit.setRevision(0);
            unit.setIsDeleted(0);
            unitMapper.insert(unit);

            String epContent = (String) ep.getOrDefault("content", "");
            ContentVersion cv = new ContentVersion();
            cv.setProjectId(projectId);
            cv.setContentUnitId(unit.getId());
            cv.setVersionNo(1);
            cv.setStatus("draft");
            cv.setContentJson(toJson(Map.of("content", epContent)));
            cv.setPlainText(epContent);
            cv.setSource("adaptation");
            cv.setContentHash(sha256(epContent));
            cv.setCreatedBy(userId);
            versionMapper.insert(cv);

            unit.setCurrentVersionId(cv.getId());
            unitMapper.updateById(unit);

            if (firstUnit == null) firstUnit = unit;
        }
        return firstUnit;
    }

    private int getNextDisplayNo(Long projectId, String unitType) {
        List<ContentUnit> existing = unitMapper.selectList(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, unitType)
                        .orderByDesc(ContentUnit::getDisplayNo)
                        .last("limit 1"));
        return existing.isEmpty() ? 1 : existing.get(0).getDisplayNo() + 1;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> result) {
        Object choices = result.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map) {
                Object message = ((Map<String, Object>) first).get("message");
                if (message instanceof Map) {
                    Object c = ((Map<String, Object>) message).get("content");
                    if (c != null) return String.valueOf(c);
                }
            }
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) {
        try {
            String json = text;
            if (text.contains("```json")) { int s = text.indexOf("```json") + 7; int e = text.indexOf("```", s); if (e > s) json = text.substring(s, e).trim(); }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) { return Map.of("content", text); }
    }

    private String toJson(Object obj) { try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; } }
    private String ellipsis(String s, int max) { return s != null && s.length() > max ? s.substring(0, max) + "..." : s; }
    private String sha256(String input) {
        try { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte b : hash) hex.append(String.format("%02x", b)); return hex.toString(); }
        catch (Exception e) { return "" + input.hashCode(); }
    }
}
