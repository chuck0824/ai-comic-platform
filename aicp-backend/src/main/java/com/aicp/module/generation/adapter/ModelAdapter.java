package com.aicp.module.generation.adapter;

import com.aicp.module.generation.capability.CapabilityRequest;

import java.util.List;

/**
 * 模型适配器接口。
 * 将 CapabilityRequest 翻译为供应商特定请求。
 */
public interface ModelAdapter {

    /** 生成预览：推荐、费用、参考角色、裁剪信息 */
    AdapterPreview preview(CapabilityRequest request, ModelCapabilityProfile profile);

    /** 将确认后的预览编译为供应商请求 */
    ProviderRequest compile(AdapterPreview confirmedPreview);

    record AdapterPreview(
            String modelId, String modelVersion, String adapterVersion,
            List<ReferenceSlot> images, List<ReferenceSlot> videos, List<ReferenceSlot> audios,
            String prompt, List<String> warnings, int estimatedCredits, String previewFingerprint
    ) {}

    record ReferenceSlot(String role, Long assetId, Long assetVersionId, int slotIndex) {}

    record ProviderRequest(
            String providerModelId, String region,
            MapWrapper parameters, String idempotencyKey, String callbackUrl
    ) {}

    /** 用于序列化的通用 Map 包装 */
    record MapWrapper(java.util.Map<String, Object> data) {}
}
