package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreationSettingsGateEvaluator implements StageGateEvaluator {

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.CREATION_SETTINGS;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        StageGateSupport.Builder b = support.builder()
                .evidence("currentParameterVersionId", project.getCurrentParameterVersionId());
        b.require(project.getCurrentParameterVersionId() != null,
                "PARAMETER_VERSION_MISSING", "创作设置参数版本缺失");
        if (project.getCurrentParameterVersionId() == null) {
            return b.build();
        }

        Map<String, Object> payload = support.loadParameterPayload(project.getCurrentParameterVersionId());
        b.evidence("parameterKeys", payload.keySet());

        Object contentType = support.firstNonBlank(payload, "contentType", "creationType", "content_type", "creation_type");
        Object genre = support.firstNonBlank(payload, "genre", "题材分类");
        Object episodeCount = support.firstNonBlank(payload, "episodeCount", "episode_count");
        Object duration = support.firstNonBlank(payload, "duration", "episodeDuration", "episode_duration");
        Object aspectRatio = support.firstNonBlank(payload, "aspectRatio", "aspect_ratio", "outputFormat", "output_format");
        Object styleId = support.firstNonBlank(payload, "styleId", "style_id", "tone");

        b.require(!support.blank(contentType), "CONTENT_TYPE_REQUIRED", "创作类型/内容类型不能为空");
        b.require(!support.blank(genre), "GENRE_REQUIRED", "题材分类不能为空");
        b.require(positiveNumber(episodeCount), "EPISODE_COUNT_REQUIRED", "集数必须大于 0");
        b.require(positiveNumber(duration), "DURATION_REQUIRED", "单集时长必须大于 0");
        b.require(!support.blank(aspectRatio), "ASPECT_RATIO_REQUIRED", "输出格式/画幅不能为空");
        b.require(!support.blank(styleId), "STYLE_REQUIRED", "风格/基调不能为空");

        Object model = payload.get("model");
        Object modelId = support.firstNonBlank(payload, "availableModelId", "available_model_id", "modelId", "model_id");
        if (modelId == null && model instanceof Map<?, ?> modelMap) {
            Object id = modelMap.get("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                modelId = id;
            }
        }
        b.require(modelId != null, "MODEL_REQUIRED", "可用模型不能为空");
        b.evidence("availableModelId", modelId);

        Object budget = support.firstNonBlank(payload, "budgetEstimate", "budget_estimate", "estimatedPoints", "estimated_points");
        boolean demoModel = model instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("demo"));
        if (!demoModel) {
            b.require(positiveNumber(budget), "BUDGET_ESTIMATE_REQUIRED", "预算/积分预估不可执行");
        }
        b.evidence("budgetEstimate", budget);
        return b.build();
    }

    private boolean positiveNumber(Object value) {
        if (value == null) {
            return false;
        }
        try {
            return Double.parseDouble(String.valueOf(value)) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
