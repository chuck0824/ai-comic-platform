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

import java.util.*;

/**
 * M2: Promotional materials generation.
 * Titles, cover copy, 3-second hooks, clip scripts, comment guides, CTAs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    static final String PROMO_PROMPT = """
        你是一位资深的短剧营销专家。请根据剧本内容生成宣传物料。输出JSON：
        {
          "titles": ["备选标题1", "备选标题2", "备选标题3"],
          "cover_copy": "封面文案（50字以内）",
          "hook_3s": "3秒短视频钩子文案",
          "clip_scripts": [
            {"scene": "关键场景", "hook_text": "配文", "duration_sec": 15}
          ],
          "comment_guide": "评论区引导话术",
          "cta": "行动号召文案"
        }
        """;

    @Transactional
    public Map<String, Object> generatePromotion(Long projectId, Long sourceUnitId) {
        ContentUnit unit = unitMapper.selectById(sourceUnitId);
        if (unit == null) throw new BizException(ErrorCode.NOT_FOUND);

        ContentVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, sourceUnitId)
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        if (version == null) throw new BizException(ErrorCode.PARAM_INVALID, "没有可用的内容版本");

        String content = version.getPlainText();
        if (content == null || content.isBlank()) content = version.getContentJson();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", PROMO_PROMPT);
        params.put("prompt", "请为以下剧本生成宣传物料：\n\n" + ellipsis(content, 4000));
        params.put("temperature", 0.8);
        params.put("max_tokens", 2048);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = extractText(result);
        Map<String, Object> promo = parseJson(text);

        // Create a promotion unit to store results
        ContentUnit promoUnit = new ContentUnit();
        promoUnit.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
        promoUnit.setProjectId(projectId);
        promoUnit.setUnitType("promotion");
        promoUnit.setDisplayNo(1);
        promoUnit.setTitle((String) promo.getOrDefault("cover_copy", "宣传物料"));
        promoUnit.setStatus("draft");
        promoUnit.setRevision(0);
        promoUnit.setIsDeleted(0);
        unitMapper.insert(promoUnit);

        ContentVersion cv = new ContentVersion();
        cv.setProjectId(projectId);
        cv.setContentUnitId(promoUnit.getId());
        cv.setVersionNo(1);
        cv.setStatus("draft");
        cv.setContentJson(toJson(promo));
        cv.setPlainText(toJson(promo));
        cv.setSource("ai_generated");
        cv.setContentHash(sha256(toJson(promo)));
        cv.setCreatedBy(1L); // system-generated
        versionMapper.insert(cv);

        promoUnit.setCurrentVersionId(cv.getId());
        unitMapper.updateById(promoUnit);

        return promo;
    }

    /** Adapt from existing content: generate both adaptation and promotion */
    @Transactional
    public Map<String, Object> generateAdaptationAndPromotion(Long userId, Long projectId, Long sourceUnitId, String format) {
        // Already handled by AdaptationService, this is the promo-only entry
        return generatePromotion(projectId, sourceUnitId);
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
        } catch (Exception e) { return Map.of("raw", text); }
    }

    private String toJson(Object obj) { try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; } }
    private String ellipsis(String s, int max) { return s != null && s.length() > max ? s.substring(0, max) + "..." : s; }
    private String sha256(String input) {
        try { java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256"); byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte b : hash) hex.append(String.format("%02x", b)); return hex.toString(); }
        catch (Exception e) { return "" + input.hashCode(); }
    }
}
