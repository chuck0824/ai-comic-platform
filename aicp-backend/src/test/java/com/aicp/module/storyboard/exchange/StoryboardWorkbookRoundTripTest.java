package com.aicp.module.storyboard.exchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("分镜工作簿双向往返 集成测试")
class StoryboardWorkbookRoundTripTest {

    @Autowired
    StoryboardWorkbookImporter importer;

    @Test
    @DisplayName("参考工作簿导入：6场、45镜、119.5秒、13维度")
    void importsReferenceWorkbookExactly() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/storyboard-13d.xlsx")) {
            assertThat(in).as("golden fixture must exist").isNotNull();

            StoryboardWorkbookImporter.WorkbookImportModel model = importer.parse(in);

            // 7 sheets recognized
            assertThat(model.sheetNames).contains(
                    "分镜头脚本", "情绪强度总览", "提示词模板", "奥斯卡三线修订表",
                    "设定一致性修订表", "人物三视图视觉规范", "三视图分镜应用表");

            // 6 scenes, 45 shots, 119.5 seconds
            assertThat(model.sceneCount).isEqualTo(6);
            assertThat(model.shotCount).isEqualTo(45);
            assertThat(model.totalDurationMs).isEqualTo(119_500L);

            // All shots have 13 dimensions populated
            assertThat(model.shots).isNotEmpty();
            assertThat(model.shots).allSatisfy(shot -> {
                assertThat(shot.shotCode).isNotEmpty();
                assertThat(shot.visualDescription).isNotNull();
            });

            // Duration parsing: 119.5s = 119500ms
            long sum = model.shots.stream().mapToLong(s -> s.durationMs).sum();
            assertThat(sum).isEqualTo(119_500L);
        }
    }

    @Test
    @DisplayName("语义摘要可复现")
    void semanticDigestIsStable() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/storyboard-13d.xlsx")) {
            var model = importer.parse(in);
            String digest1 = model.semanticDigest();
            // Re-parse same file
            try (InputStream in2 = getClass().getResourceAsStream("/fixtures/storyboard-13d.xlsx")) {
                var model2 = importer.parse(in2);
                String digest2 = model2.semanticDigest();
                assertThat(digest1).isEqualTo(digest2);
            }
        }
    }
}
