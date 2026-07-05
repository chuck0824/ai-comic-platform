package com.aicp.module.quality.canvas;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Canvas 质量报告服务。
 * 四来源：模型元数据、Blender 差异、自动规则引擎、人工录入。
 * 质量报告不得自动采用或创建导演台。
 */
@Slf4j
@Service
public class CanvasQualityService {

    public enum QualityDimension { IDENTITY, COMPOSITION, ACTION, CAMERA, PHYSICS, AUDIO_TIMING, CONTINUITY }
    public enum QualityStatus { PASS, WARN, BLOCK }

    public record QualityIssue(QualityDimension dimension, String severity, int startMs, int endMs,
                                Long sourceNodeId, String sourceTrackId, String suggestedAction,
                                String expected, String observed) {}

    public record QualityReport(String uuid, Long candidateId, QualityStatus overallStatus,
                                 List<QualityIssue> issues) {}

    /**
     * 候选生成后自动触发质量检测。
     * 返回报告；不修改候选或导演状态。
     */
    public QualityReport evaluate(CandidateContext ctx) {
        List<QualityIssue> issues = new ArrayList<>();

        // 自动规则引擎
        autoDurMismatch(ctx, issues);
        autoAspectMismatch(ctx, issues);

        QualityStatus status = issues.stream().anyMatch(i -> "ERROR".equals(i.severity()))
                ? QualityStatus.BLOCK
                : issues.stream().anyMatch(i -> "WARN".equals(i.severity()))
                ? QualityStatus.WARN : QualityStatus.PASS;

        log.info("质量评估完成: candidate={}, status={}, issues={}", ctx.candidateId(), status, issues.size());
        return new QualityReport(UUID.randomUUID().toString(), ctx.candidateId(), status,
                Collections.unmodifiableList(issues));
    }

    private void autoDurMismatch(CandidateContext ctx, List<QualityIssue> issues) {
        if (ctx.actualDurationMs() > 0 && Math.abs(ctx.actualDurationMs() - ctx.targetDurationMs()) > 500) {
            issues.add(new QualityIssue(QualityDimension.CONTINUITY, "WARN",
                    0, ctx.actualDurationMs(), null, null, null,
                    ctx.targetDurationMs() + "ms", ctx.actualDurationMs() + "ms"));
        }
    }

    private void autoAspectMismatch(CandidateContext ctx, List<QualityIssue> issues) {
        if (ctx.actualAspectRatio() != null && ctx.targetAspectRatio() != null
                && !ctx.actualAspectRatio().equals(ctx.targetAspectRatio())) {
            issues.add(new QualityIssue(QualityDimension.COMPOSITION, "WARN",
                    0, ctx.actualDurationMs(), null, null, null,
                    ctx.targetAspectRatio(), ctx.actualAspectRatio()));
        }
    }

    public record CandidateContext(Long candidateId, int targetDurationMs, int actualDurationMs,
                                    String targetAspectRatio, String actualAspectRatio) {}
}
