package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.QualityReport;
import com.aicp.module.contentproject.mapper.QualityReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * M5: QualityAgent — independent quality review service.
 * Binds quality issues to specific canvas nodes and asset versions.
 * Evaluates across 5 dimensions: correctness, security, performance, cost, consistency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityAgentService {

    private final QualityReportMapper reportMapper;
    private final AiRouter aiRouter;
    private final AiResponseParser parser;

    static final String QUALITY_PROMPT = """
        你是资深质量审核专家。请从以下5个维度审查内容：
        1. correctness(正确性) — 内容是否符合需求、事实是否准确
        2. security(安全性) — 是否存在敏感内容、合规风险
        3. performance(性能) — 生成效率、资源消耗评估
        4. cost(成本) — 算力/时间成本是否合理
        5. consistency(一致性) — 风格、质量是否与前序内容一致

        输出JSON：{"scores":{"correctness":0,"security":0,"performance":0,"cost":0,"consistency":0},
        "issues":[{"dimension":"","severity":"low|medium|high","description":""}],
        "summary":"总体评价"}
        """;

    @Transactional
    public QualityReport review(String canvasProjectId, String nodeUuid, Long assetVersionId,
                                 String content, Long projectId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", QUALITY_PROMPT);
        params.put("prompt", "请审查以下内容：\n" + parser.ellipsis(content, 4000));
        params.put("temperature", 0.1);
        params.put("max_tokens", 2048);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        Map<String, Object> parsed = parser.parseJson(parser.extractText(result));

        @SuppressWarnings("unchecked")
        Map<String, Object> scores = (Map<String, Object>) parsed.getOrDefault("scores", Map.of());

        QualityReport report = new QualityReport();
        report.setUuid(UUID.randomUUID().toString());
        report.setProjectId(projectId);
        report.setCanvasProjectId(canvasProjectId);
        report.setNodeUuid(nodeUuid);
        report.setAssetVersionId(assetVersionId);
        report.setCorrectnessScore(parser.toInt(scores.get("correctness"), 0));
        report.setSecurityScore(parser.toInt(scores.get("security"), 0));
        report.setPerformanceScore(parser.toInt(scores.get("performance"), 0));
        report.setCostScore(parser.toInt(scores.get("cost"), 0));
        report.setConsistencyScore(parser.toInt(scores.get("consistency"), 0));
        report.setIssuesJson(parser.toJson(parsed.get("issues")));
        report.setSummary(parser.str(parsed.get("summary")));
        report.setStatus("open");
        reportMapper.insert(report);

        log.info("QualityAgent review completed: report={}, avgScore={}", report.getUuid(),
                (report.getCorrectnessScore() + report.getSecurityScore() + report.getPerformanceScore()
                 + report.getCostScore() + report.getConsistencyScore()) / 5.0);
        return report;
    }

    public List<QualityReport> listByProject(Long projectId) {
        return reportMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QualityReport>()
                        .eq(QualityReport::getProjectId, projectId)
                        .orderByDesc(QualityReport::getCreatedAt));
    }

    @Transactional
    public void resolveReport(Long reportId, String resolution) {
        if (!List.of("resolved", "wont_fix").contains(resolution)) {
            throw new IllegalArgumentException("Invalid resolution: " + resolution + ". Expected: resolved or wont_fix");
        }
        QualityReport report = reportMapper.selectById(reportId);
        if (report != null) {
            report.setStatus(resolution);
            reportMapper.updateById(report);
        }
    }
}
