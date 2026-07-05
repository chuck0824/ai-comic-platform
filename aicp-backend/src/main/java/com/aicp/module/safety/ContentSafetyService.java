package com.aicp.module.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 内容安全审核服务（R3 基础实现）。
 * 候选资产写入后 → 异步审核 → PASS/FLAGGED/REJECTED。
 * 审核超时 60s → FLAGGED（用户可见但标注"审核中"）。
 */
@Slf4j
@Service
public class ContentSafetyService {

    /**
     * 提交候选资产进行安全审核。
     * 异步执行，审核完成后通过 onReviewComplete 回调。
     */
    public void submitReview(Long candidateId, Long assetVersionId) {
        log.info("提交安全审核: candidate={}, asset={}", candidateId, assetVersionId);
        // TODO: 对接内容安全基础设施（NSFW检测、暴力检测、真人肖像授权校验）
        // 当前基础实现：直接标记为 PASS
        onReviewComplete(candidateId, SafetyStatus.PASS, null);
    }

    public void onReviewComplete(Long candidateId, SafetyStatus status, String reason) {
        log.info("安全审核完成: candidate={}, status={}, reason={}", candidateId, status, reason);
        // TODO: 更新 generation_candidates.safety_status
    }

    public enum SafetyStatus { PENDING, PASS, FLAGGED, REJECTED }
}
