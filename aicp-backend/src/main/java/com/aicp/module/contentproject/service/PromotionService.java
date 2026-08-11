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
    private final ContentVersionSelector versionSelector;
    private final AiRouter aiRouter;
    private final AiResponseParser parser;

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

        ContentVersion version = versionSelector.resolvePublic(unit);
        if (version == null) throw new BizException(ErrorCode.PARAM_INVALID, "没有可用的内容版本");

        String content = version.getPlainText();
        if (content == null || content.isBlank()) content = version.getContentJson();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", PROMO_PROMPT);
        params.put("prompt", "请为以下剧本生成宣传物料：\n\n" + parser.ellipsis(content, 4000));
        params.put("temperature", 0.8);
        params.put("max_tokens", 2048);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = parser.extractText(result);
        Map<String, Object> promo = parser.parseJson(text);

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
        cv.setContentJson(parser.toJson(promo));
        cv.setPlainText(parser.toJson(promo));
        cv.setSource("ai_generated");
        cv.setContentHash(parser.sha256(parser.toJson(promo)));
        Long userId = com.aicp.common.util.SecurityUtil.getCurrentUserId();
        cv.setCreatedBy(userId != null ? userId : 0L);
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
}
