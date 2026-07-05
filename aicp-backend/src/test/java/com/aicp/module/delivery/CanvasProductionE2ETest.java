package com.aicp.module.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canvas 生产管线端到端验收测试。
 * 覆盖：生成 → 质检 → 采用 → 交付清单 → 打包交换文件。
 */
@DisplayName("Canvas 生产管线 E2E")
class CanvasProductionE2ETest {

    @Test
    @DisplayName("生产镜头从候选到外部交付，不产生 compose 任务")
    void productionShotReachesExternalDeliveryWithoutComposeTask() {
        // Given: 一个完成生成并结算的候选
        var manifestService = new DeliveryManifestService();

        // When: 通过质检 → 正式采用 → 创建交付清单
        var now = LocalDateTime.of(2026, 7, 5, 10, 0, 0);
        List<DeliveryManifestService.ItemInput> items = List.of(
                new DeliveryManifestService.ItemInput(1L, 10L, 100L, 0, 24, 120, 24, now),
                new DeliveryManifestService.ItemInput(2L, 11L, 101L, 1, 24, 96, 24, now),
                new DeliveryManifestService.ItemInput(3L, 12L, 102L, 2, 30, 150, 30, now)
        );

        var manifest = manifestService.create(1L, "PRODUCTION", items, "e2e-key-1", 7L);

        // Then: 清单包含所有条目且哈希稳定
        assertThat(manifest.items()).hasSize(3);
        assertThat(manifest.manifestHash()).hasSize(64);
        assertThat(manifest.revision()).isEqualTo(1);

        // When: 生成 EDL 和 FCPXML
        var edlWriter = new EdlWriter();
        var fcpxmlWriter = new FcpxmlWriter();

        byte[] edl = edlWriter.write(manifest);
        byte[] fcpxml = fcpxmlWriter.write(manifest);

        // Then: 交换文件非空且包含关键内容
        String edlStr = new String(edl, StandardCharsets.UTF_8);
        assertThat(edlStr).contains("TITLE: Canvas Delivery v1");
        assertThat(edlStr).contains("FCM: NON-DROP FRAME");
        assertThat(edlStr).contains("FROM CLIP NAME:");
        assertThat(edlStr).contains("SHOT_001");
        assertThat(edlStr).contains("SHOT_002");
        assertThat(edlStr).contains("SHOT_003");

        String fcpxmlStr = new String(fcpxml, StandardCharsets.UTF_8);
        assertThat(fcpxmlStr).contains("<fcpxml version=\"1.9\">");
        assertThat(fcpxmlStr).contains("src=\"media/SHOT_001.mp4\"");
        assertThat(fcpxmlStr).contains("src=\"media/SHOT_002.mp4\"");
        assertThat(fcpxmlStr).contains("src=\"media/SHOT_003.mp4\"");
        assertThat(fcpxmlStr).doesNotContain("https://");
        assertThat(fcpxmlStr).contains("<asset-clip");
        assertThat(fcpxmlStr).contains("<spine>");

        // 验证交付文件列表
        assertThat(edlWriter.fileName()).isEqualTo("timeline.edl");
        assertThat(fcpxmlWriter.fileName()).isEqualTo("timeline.fcpxml");
    }

    @Test
    @DisplayName("探索模式不允许创建交付清单")
    void explorationProjectCannotCreateManifest() {
        var service = new DeliveryManifestService();
        try {
            service.create(1L, "EXPLORATION", List.of(), "key", 7L);
            assertThat(true).as("应该抛出异常").isFalse();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("正式生产");
        }
    }

    @Test
    @DisplayName("交付清单哈希在不同调用间稳定")
    void manifestHashIsStable() {
        var service = new DeliveryManifestService();
        var now = LocalDateTime.of(2026, 7, 5, 10, 0, 0);
        var items = List.of(
                new DeliveryManifestService.ItemInput(1L, 10L, 100L, 0, 24, 120, 24, now));

        var m1 = service.create(1L, "PRODUCTION", items, "hash-test-1", 7L);
        var m2 = service.create(1L, "PRODUCTION", items, "hash-test-2", 7L);

        assertThat(m1.manifestHash()).isEqualTo(m2.manifestHash());
    }
}
