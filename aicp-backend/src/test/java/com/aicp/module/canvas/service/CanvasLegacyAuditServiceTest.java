package com.aicp.module.canvas.service;

import com.aicp.module.canvas.dto.CanvasMigrationViews.MigrationAuditIssue;
import com.aicp.module.canvas.dto.CanvasMigrationViews.MigrationAuditReport;
import com.aicp.module.canvas.entity.CanvasEdge;
import com.aicp.module.canvas.entity.CanvasNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CanvasLegacyAuditService 单元测试")
class CanvasLegacyAuditServiceTest {

    private CanvasLegacyAuditService service;

    @BeforeEach
    void setUp() {
        service = TestableAuditService.create();
    }

    @Nested
    @DisplayName("节点分类")
    class NodeClassification {

        @Test
        @DisplayName("包含 director 键的 reference 节点自动分类为 director")
        void classifiesDirectorReferenceWithoutMutatingNode() {
            CanvasNode node = TestableAuditService.node("reference",
                    "{\"director\":{\"camera\":{}}}");

            MigrationAuditIssue issue = service.classifyNode(node);

            assertThat(issue.suggestedType()).isEqualTo("director");
            assertThat(issue.status()).isEqualTo("AUTO_CLASSIFIED");
            // 原始节点类型不变
            assertThat(node.getType()).isEqualTo("reference");
        }

        @Test
        @DisplayName("空的 reference 节点需要人工确认")
        void ambiguousReferenceRequiresConfirmation() {
            MigrationAuditIssue issue = service.classifyNode(
                    TestableAuditService.node("reference", "{}"));

            assertThat(issue.status()).isEqualTo("NEEDS_CONFIRMATION");
        }

        @Test
        @DisplayName("无 input_data 的 reference 节点需要人工确认")
        void referenceWithoutDataNeedsConfirmation() {
            MigrationAuditIssue issue = service.classifyNode(
                    TestableAuditService.node("reference", null));

            assertThat(issue.status()).isEqualTo("NEEDS_CONFIRMATION");
        }

        @Test
        @DisplayName("非 reference 节点直接跳过，不产生 issue")
        void nonReferenceNodesAreSkipped() {
            assertThat(service.classifyNode(TestableAuditService.node("text", null))).isNull();
            assertThat(service.classifyNode(TestableAuditService.node("image", null))).isNull();
            assertThat(service.classifyNode(TestableAuditService.node("video", null))).isNull();
            assertThat(service.classifyNode(TestableAuditService.node("audio", null))).isNull();
            assertThat(service.classifyNode(TestableAuditService.node("script", null))).isNull();
        }

        @Test
        @DisplayName("解析失败的 input_data 返回 NEEDS_CONFIRMATION")
        void parseFailureLeadsToConfirmation() {
            MigrationAuditIssue issue = service.classifyNode(
                    TestableAuditService.node("reference", "not valid json {{{"));

            assertThat(issue.status()).isEqualTo("NEEDS_CONFIRMATION");
            assertThat(issue.reason()).contains("解析失败");
        }
    }

    @Nested
    @DisplayName("连线分类")
    class EdgeClassification {

        @Test
        @DisplayName("旧连线审计保持 LEGACY_UNMODIFIED 状态，不做修改")
        void legacyEdgesAreNotModifiedDuringAudit() {
            CanvasEdge edge = new CanvasEdge();
            edge.setUuid("edge-1");
            edge.setEdgeType("data");

            MigrationAuditIssue issue = service.classifyEdge(edge);

            assertThat(issue.status()).isEqualTo("LEGACY_UNMODIFIED");
            assertThat(issue.objectType()).isEqualTo("EDGE");
        }
    }

    @Nested
    @DisplayName("审计报告")
    class AuditReport {

        @Test
        @DisplayName("hasAmbiguity 在存在 NEEDS_CONFIRMATION 项时返回 true")
        void hasAmbiguityDetectsConfirmationItems() {
            var report = new MigrationAuditReport("p1", 5, 3,
                    java.util.List.of(
                            new MigrationAuditIssue("n1", "NODE", "reference", "director", "AUTO_CLASSIFIED", "ok"),
                            new MigrationAuditIssue("n2", "NODE", "reference", null, "NEEDS_CONFIRMATION", "ambiguous")
                    ));

            assertThat(report.hasAmbiguity()).isTrue();
            assertThat(report.autoClassifiedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("无歧义项时 hasAmbiguity 返回 false")
        void noAmbiguityWhenAllClassified() {
            var report = new MigrationAuditReport("p1", 5, 3,
                    java.util.List.of(
                            new MigrationAuditIssue("n1", "NODE", "reference", "director", "AUTO_CLASSIFIED", "ok")
                    ));

            assertThat(report.hasAmbiguity()).isFalse();
        }
    }

    /**
     * 可测试的 AuditService 实例工厂。
     * 仅构造纯函数分类逻辑，不依赖数据库。
     */
    static class TestableAuditService extends CanvasLegacyAuditService {

        TestableAuditService() {
            super(null, null, null, new com.fasterxml.jackson.databind.ObjectMapper());
        }

        static CanvasLegacyAuditService create() {
            return new TestableAuditService();
        }

        static CanvasNode node(String type, String inputData) {
            CanvasNode node = new CanvasNode();
            node.setUuid("node-" + System.nanoTime());
            node.setType(type);
            node.setInputData(inputData);
            node.setX(0);
            node.setY(0);
            return node;
        }
    }
}
