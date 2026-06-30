package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.storyboard.domain.ProductionGate;
import com.aicp.module.storyboard.domain.StoryboardEnums.IssueStatus;
import com.aicp.module.storyboard.domain.StoryboardEnums.IssueSeverity;
import com.aicp.module.storyboard.dto.StoryboardViews.*;
import com.aicp.module.storyboard.entity.*;
import com.aicp.module.storyboard.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardReviewService {

    private final StoryboardReviewIssueMapper issueMapper;
    private final StoryboardVersionShotMapper shotMapper;
    private final StoryboardCharacterVisualMapper characterVisualMapper;
    private final StoryboardShotVisualBindingMapper visualBindingMapper;
    private final StoryboardAccessService accessService;

    // ===== Issue CRUD =====

    public List<ReviewIssueView> listIssues(Long projectId, Long versionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        return issueMapper.selectList(
                new LambdaQueryWrapper<StoryboardReviewIssue>()
                        .eq(StoryboardReviewIssue::getVersionId, versionId)
                        .orderByAsc(StoryboardReviewIssue::getSeverity)
                        .orderByAsc(StoryboardReviewIssue::getCreatedAt))
                .stream().map(this::toIssueView).toList();
    }

    @Transactional
    public ReviewIssueView resolveIssue(Long projectId, Long versionId, Long issueId, Long userId,
                                         String newStatus, String resolutionNote) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.REVIEW);
        StoryboardReviewIssue issue = issueMapper.selectById(issueId);
        if (issue == null || !versionId.equals(issue.getVersionId())) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND, "审核问题不存在");
        }

        IssueStatus status = IssueStatus.valueOf(newStatus.toUpperCase());
        if (status == IssueStatus.OPEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能将问题状态改回open");
        }

        issue.setStatus(status.value());
        issue.setResolutionNote(resolutionNote);
        if (status == IssueStatus.RESOLVED) {
            issue.setResolvedBy(userId);
            issue.setResolvedAt(LocalDateTime.now());
        }
        issueMapper.updateById(issue);

        return toIssueView(issue);
    }

    // ===== Deterministic Checks =====

    @Transactional
    public List<ReviewIssueView> runChecks(Long projectId, Long versionId, Long userId) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.REVIEW);
        List<StoryboardShot> shots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, versionId));

        List<ReviewIssueView> newIssues = new ArrayList<>();

        for (StoryboardShot shot : shots) {
            String code = shot.getShotCode() != null ? shot.getShotCode() : "S??-C??";

            // Duration checks
            if (shot.getDurationMs() == null || shot.getDurationMs() <= 0) {
                newIssues.add(upsertIssue(versionId, "duration_zero",
                        "error", shot.getId(), code + " 镜头时长缺失"));
            } else if (shot.getDurationMs() > 30_000) {
                newIssues.add(upsertIssue(versionId, "duration_long",
                        "warning", shot.getId(), code + " 镜头时长超过30秒"));
            }
        }

        // Consecutive same shot size check
        for (int i = 0; i < shots.size() - 5; i++) {
            String size = shots.get(i).getShotSize();
            if (size != null && !size.isEmpty()) {
                boolean allSame = true;
                for (int j = 1; j < 6; j++) {
                    if (!size.equals(shots.get(i + j).getShotSize())) {
                        allSame = false;
                        break;
                    }
                }
                if (allSame) {
                    String code = shots.get(i).getShotCode();
                    newIssues.add(upsertIssue(versionId, "consecutive_shot_size",
                            "warning", shots.get(i).getId(),
                            code + " 起连续6镜景别相同(" + size + ")，可能造成视觉疲劳"));
                }
            }
        }

        // Missing prompts
        for (StoryboardShot shot : shots) {
            String code = shot.getShotCode() != null ? shot.getShotCode() : "S??-C??";
            if (isBlank(shot.getImagePrompt())) {
                newIssues.add(upsertIssue(versionId, "missing_image_prompt",
                        "warning", shot.getId(), code + " 缺少图片提示词"));
            }
            if ("C".equals(version.getTier()) && isBlank(shot.getVideoMotionPrompt())) {
                newIssues.add(upsertIssue(versionId, "missing_video_prompt",
                        "error", shot.getId(), code + " 缺少视频动作提示词 (C档必需)"));
            }
            if ("C".equals(version.getTier()) && isBlank(shot.getFailureStrategy())) {
                newIssues.add(upsertIssue(versionId, "missing_failure_strategy",
                        "error", shot.getId(), code + " 缺少失败策略 (C档必需)"));
            }
        }

        return newIssues;
    }

    public ProductionGate.GateResult evaluateGate(Long projectId, Long versionId, Long userId) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        List<StoryboardShot> shots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, versionId));
        List<String> boundCharacters = visualBindingMapper.selectList(
                new LambdaQueryWrapper<StoryboardShotVisualBinding>()
                        .eq(StoryboardShotVisualBinding::getVersionId, versionId))
                .stream().map(b -> "binding-" + b.getCharacterVisualId()).distinct().toList();
        List<String> openIssues = issueMapper.selectList(
                new LambdaQueryWrapper<StoryboardReviewIssue>()
                        .eq(StoryboardReviewIssue::getVersionId, versionId)
                        .eq(StoryboardReviewIssue::getStatus, "open")
                        .eq(StoryboardReviewIssue::getSeverity, "error"))
                .stream().map(StoryboardReviewIssue::getFingerprint).toList();

        return ProductionGate.evaluate(version, shots, boundCharacters, openIssues);
    }

    public boolean hasBlockingIssues(Long versionId) {
        return issueMapper.selectCount(
                new LambdaQueryWrapper<StoryboardReviewIssue>()
                        .eq(StoryboardReviewIssue::getVersionId, versionId)
                        .eq(StoryboardReviewIssue::getStatus, "open")
                        .eq(StoryboardReviewIssue::getSeverity, "error")) > 0;
    }

    // ===== Helpers =====

    private ReviewIssueView upsertIssue(Long versionId, String type, String severity,
                                         Long shotId, String message) {
        String fingerprint = sha256(versionId + "|" + type + "|" + (shotId != null ? shotId : "0") + "|" + message);

        StoryboardReviewIssue existing = issueMapper.selectOne(
                new LambdaQueryWrapper<StoryboardReviewIssue>()
                        .eq(StoryboardReviewIssue::getVersionId, versionId)
                        .eq(StoryboardReviewIssue::getFingerprint, fingerprint));
        if (existing != null) {
            return toIssueView(existing);
        }

        StoryboardReviewIssue issue = new StoryboardReviewIssue();
        issue.setVersionId(versionId);
        issue.setFingerprint(fingerprint);
        issue.setIssueType(type);
        issue.setSeverity(severity);
        issue.setShotId(shotId);
        issue.setMessage(message);
        issue.setStatus(IssueStatus.OPEN.value());
        issueMapper.insert(issue);
        return toIssueView(issue);
    }

    static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private ReviewIssueView toIssueView(StoryboardReviewIssue i) {
        return new ReviewIssueView(i.getId(), i.getFingerprint(), i.getIssueType(),
                i.getSeverity(), i.getShotId(), i.getMessage(), i.getEvidence(),
                i.getSuggestion(), i.getStatus(), i.getResolutionNote(),
                i.getResolvedBy(), i.getResolvedAt());
    }
}
