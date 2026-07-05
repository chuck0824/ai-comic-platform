package com.aicp.module.generation.capability;

import java.util.List;

/**
 * 模型无关的能力请求。
 * Canvas 侧编译创作意图，不包含供应商特定参数。
 */
public record CapabilityRequest(
        String intent,           // 创作意图描述
        String mode,             // text_to_video | image_to_video | director_to_video
        int durationMs,
        String aspectRatio,
        String qualityTier,      // draft | standard | high
        String costPreference,   // speed | balanced | quality
        List<SemanticReference> references,
        Long directorRevisionId  // 可选：导演台 revision
) {
    public record SemanticReference(
            String role,         // identity | scene | composition | audio_timing | style_ref | camera_motion
            Long assetId,
            Long assetVersionId,
            Integer startMs,
            Integer endMs
    ) {}
}
