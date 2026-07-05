package com.aicp.module.generation.adapter;

import com.aicp.module.generation.capability.CapabilityRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Seedance 2.0 适配器 v1。
 * 将 CapabilityRequest 映射为 Seedance API 输入。
 * 生产启用前必须通过供应商 Gate（G0）。
 */
@Component
public class SeedanceAdapter implements ModelAdapter {

    private static final String ADAPTER_VERSION = "seedance-v1";
    private static final int MAX_IMAGES = 9;
    private static final int MAX_VIDEOS = 3;
    private static final int MAX_AUDIO = 3;

    @Override
    public AdapterPreview preview(CapabilityRequest request, ModelCapabilityProfile profile) {
        List<ReferenceSlot> images = new ArrayList<>();
        List<ReferenceSlot> videos = new ArrayList<>();
        List<ReferenceSlot> audios = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Set<String> seenRoles = new HashSet<>();
        for (var ref : request.references()) {
            String role = ref.role();
            if (!seenRoles.add(role)) {
                warnings.add("duplicate reference role removed: " + role);
                continue;
            }

            switch (role) {
                case "identity", "scene", "composition", "style_ref" -> {
                    if (images.size() < MAX_IMAGES) images.add(slot(ref, images.size()));
                    else warnings.add("图片槽位已满，跳过: " + role);
                }
                case "motion_reference", "camera_motion" -> {
                    if (videos.size() < MAX_VIDEOS) videos.add(slot(ref, videos.size()));
                    else warnings.add("视频槽位已满，跳过: " + role);
                }
                case "audio_timing", "audio_reference" -> {
                    if (audios.size() < MAX_AUDIO) audios.add(slot(ref, audios.size()));
                    else warnings.add("音频槽位已满，跳过: " + role);
                }
                default -> warnings.add("未知角色类型: " + role);
            }
        }

        String fingerprint = computeFingerprint(request, profile);
        int estimatedCredits = profile.productionVerified() ? 50 : 0; // mock: 0 if not verified

        return new AdapterPreview(
                profile.profileId(), null, ADAPTER_VERSION,
                images, videos, audios,
                request.intent(), warnings, estimatedCredits, fingerprint
        );
    }

    @Override
    public ProviderRequest compile(AdapterPreview confirmedPreview) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("images", confirmedPreview.images().size());
        params.put("videos", confirmedPreview.videos().size());
        params.put("audios", confirmedPreview.audios().size());
        params.put("prompt", confirmedPreview.prompt());

        return new ProviderRequest(
                confirmedPreview.modelId(), "default",
                new ModelAdapter.MapWrapper(params),
                UUID.randomUUID().toString(), null
        );
    }

    private ReferenceSlot slot(CapabilityRequest.SemanticReference ref, int index) {
        return new ReferenceSlot(ref.role(), ref.assetId(), ref.assetVersionId(), index);
    }

    private String computeFingerprint(CapabilityRequest request, ModelCapabilityProfile profile) {
        try {
            String input = request.intent() + "|" + request.mode() + "|" + profile.profileId() + "|" + ADAPTER_VERSION;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return "fp-" + System.currentTimeMillis();
        }
    }

    public static String adapterVersion() { return ADAPTER_VERSION; }
}
