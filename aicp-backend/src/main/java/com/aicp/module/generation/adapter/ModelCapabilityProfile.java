package com.aicp.module.generation.adapter;

import java.util.List;
import java.util.Map;

/**
 * 模型能力配置（版本化）。
 * 从 model-capabilities/seedance-2.0.json 加载。
 */
public record ModelCapabilityProfile(
        String profileId,           // seedance-2.0
        String adapterVersion,      // seedance-v1
        boolean productionVerified, // 是否通过供应商 Gate
        Limits limits,
        List<String> supportedFormats,
        RateLimits rateLimits
) {
    public record Limits(int maxImages, int maxVideos, int maxAudio,
                          int maxDurationSeconds, List<String> allowedAspectRatios) {}
    public record RateLimits(int maxConcurrent, int maxPerMinute, int maxPerHour) {}
}
