package com.aicp.module.generation.capability;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 将节点、资产和导演语义编译为模型无关 CapabilityRequest。
 * 不包含供应商 slot 编号或模型特定参数。
 */
@Component
public class CapabilityCompiler {

    /**
     * 从节点和输入编译能力请求。
     */
    public CapabilityRequest compile(CompileInput input) {
        List<CapabilityRequest.SemanticReference> refs = new ArrayList<>();

        for (var asset : input.assets()) {
            refs.add(new CapabilityRequest.SemanticReference(
                    asset.role(), asset.assetId(), asset.assetVersionId(),
                    asset.startMs(), asset.endMs()));
        }

        return new CapabilityRequest(
                input.intent() != null ? input.intent() : "video_generation",
                input.mode() != null ? input.mode() : "text_to_video",
                input.durationMs() > 0 ? input.durationMs() : 5000,
                input.aspectRatio() != null ? input.aspectRatio() : "16:9",
                input.qualityTier() != null ? input.qualityTier() : "standard",
                input.costPreference() != null ? input.costPreference() : "balanced",
                Collections.unmodifiableList(refs),
                input.directorRevisionId()
        );
    }

    public record CompileInput(
            String intent, String mode, int durationMs, String aspectRatio,
            String qualityTier, String costPreference,
            List<AssetRef> assets, Long directorRevisionId
    ) {}

    public record AssetRef(String role, Long assetId, Long assetVersionId, Integer startMs, Integer endMs) {}
}
