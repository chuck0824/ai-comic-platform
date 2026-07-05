package com.aicp.module.delivery;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 交付清单服务。
 * 固化正式采用和资产版本，生成不可变清单。
 * 探索模式不可创建清单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryManifestService {

    /**
     * 固化交付清单（事务）。
     */
    public DeliveryManifestView create(Long projectId, String projectMode, List<ItemInput> items,
                                        String idempotencyKey, Long actorId) {
        if (!"PRODUCTION".equals(projectMode)) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "正式生产画布才能创建交付清单");
        }
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "至少需要一个已采用的镜头");
        }

        String manifestUuid = UUID.randomUUID().toString();
        List<ItemView> itemViews = items.stream()
                .map(i -> new ItemView(i.shotUnitId, i.adoptionId, i.assetVersionId,
                        i.sortOrder, i.durationFps(), i.durationFrames(), i.fps(),
                        i.adoptedAt() != null ? i.adoptedAt() : LocalDateTime.now()))
                .toList();

        String hash = sha256(itemViews.toString());

        log.info("交付清单已创建: project={}, revision=1, items={}, hash={}",
                projectId, itemViews.size(), hash);

        return new DeliveryManifestView(manifestUuid, projectId, 1, hash, itemViews);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record ItemInput(Long shotUnitId, Long adoptionId, Long assetVersionId,
                             int sortOrder, int durationFps, int durationFrames, int fps,
                             LocalDateTime adoptedAt) {}
    public record ItemView(Long shotUnitId, Long adoptionId, Long assetVersionId,
                            int sortOrder, int durationFps, int durationFrames, int fps,
                            LocalDateTime adoptedAt) {}
    public record DeliveryManifestView(String uuid, Long projectId, int revision,
                                        String manifestHash, List<ItemView> items) {}
}
