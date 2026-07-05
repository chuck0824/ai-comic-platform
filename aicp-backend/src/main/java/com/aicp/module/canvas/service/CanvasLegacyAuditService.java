package com.aicp.module.canvas.service;

import com.aicp.module.canvas.dto.CanvasMigrationViews.MigrationAuditIssue;
import com.aicp.module.canvas.dto.CanvasMigrationViews.MigrationAuditReport;
import com.aicp.module.canvas.entity.CanvasEdge;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasEdgeMapper;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * R0 只读旧画布审计服务。
 * 扫描节点和连线，分类但不修改任何数据。
 * 旧连线在审计阶段保持未修改状态；R1 升级时批量设置 port_contract_version='legacy'。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasLegacyAuditService {

    private final CanvasProjectMapper projectMapper;
    private final CanvasNodeMapper nodeMapper;
    private final CanvasEdgeMapper edgeMapper;
    private final ObjectMapper objectMapper;

    private static final String DIRECTOR_JSON_KEY = "director";

    /**
     * 生成指定画布的只读迁移审计报告。
     *
     * @param projectUuid 画布项目 UUID
     * @return 审计报告（包含所有节点、连线的分类结果）
     */
    public MigrationAuditReport report(String projectUuid) {
        CanvasProject project = projectMapper.selectOne(
                new LambdaQueryWrapper<CanvasProject>().eq(CanvasProject::getUuid, projectUuid));
        if (project == null) {
            throw new IllegalArgumentException("画布项目不存在: " + projectUuid);
        }

        List<CanvasNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<CanvasNode>().eq(CanvasNode::getProjectId, project.getId()));
        List<CanvasEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<CanvasEdge>().eq(CanvasEdge::getProjectId, project.getId()));

        List<MigrationAuditIssue> issues = new ArrayList<>();

        for (CanvasNode node : nodes) {
            MigrationAuditIssue issue = classifyNode(node);
            if (issue != null) {
                issues.add(issue);
            }
        }

        for (CanvasEdge edge : edges) {
            issues.add(classifyEdge(edge));
        }

        log.info("审计完成: project={}, nodes={}, edges={}, issues={}, ambiguous={}",
                projectUuid, nodes.size(), edges.size(), issues.size(),
                issues.stream().filter(i -> "NEEDS_CONFIRMATION".equals(i.status())).count());

        return new MigrationAuditReport(
                projectUuid, nodes.size(), edges.size(), Collections.unmodifiableList(issues));
    }

    /**
     * 分类单个节点。不修改节点数据。
     *
     * @param node 旧画布节点
     * @return 分类结果，无问题返回 null
     */
    MigrationAuditIssue classifyNode(CanvasNode node) {
        String type = node.getType();
        if (!"reference".equals(type)) {
            return null; // 非 reference 节点不需要分类
        }

        String inputData = node.getInputData();
        if (inputData != null && !inputData.isEmpty()) {
            try {
                Map<String, Object> data = objectMapper.readValue(inputData, Map.class);
                if (data.containsKey(DIRECTOR_JSON_KEY)) {
                    return new MigrationAuditIssue(
                            node.getUuid(), "NODE", type, "director",
                            "AUTO_CLASSIFIED", "input_data 包含 director 键 → 映射为 director 节点");
                }
                // 有 JSON 数据但不包含 director 键 — 需要人工判断
                return new MigrationAuditIssue(
                        node.getUuid(), "NODE", type, null,
                        "NEEDS_CONFIRMATION", "reference 节点有数据但无法自动判断目标类型");
            } catch (Exception e) {
                log.debug("无法解析节点 {} 的 input_data: {}", node.getUuid(), e.getMessage());
                return new MigrationAuditIssue(
                        node.getUuid(), "NODE", type, null,
                        "NEEDS_CONFIRMATION", "input_data 解析失败: " + e.getMessage());
            }
        }

        // reference 节点但没有 input_data — 完全无法判断
        return new MigrationAuditIssue(
                node.getUuid(), "NODE", type, null,
                "NEEDS_CONFIRMATION", "reference 节点无 input_data，无法自动分类");
    }

    /**
     * 分类旧连线。审计阶段不做任何修改。
     * R1 升级时统一设置 port_contract_version='legacy', status='NEEDS_CONFIRMATION'。
     */
    MigrationAuditIssue classifyEdge(CanvasEdge edge) {
        // 所有旧连线按设计统一标记为 LEGACY_UNMODIFIED
        // 实际迁移在 R1 单画布升级事务中执行
        return new MigrationAuditIssue(
                edge.getUuid(), "EDGE", edge.getEdgeType(), null,
                "LEGACY_UNMODIFIED", "旧连线将在 R1 升级时批量标记为 legacy/NEEDS_CONFIRMATION");
    }
}
