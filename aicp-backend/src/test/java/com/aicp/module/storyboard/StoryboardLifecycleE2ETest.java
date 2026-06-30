package com.aicp.module.storyboard;

import com.aicp.module.storyboard.domain.ProductionGate;
import com.aicp.module.storyboard.domain.StoryboardEnums.*;
import com.aicp.module.storyboard.domain.StoryboardStateMachine;
import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("分镜生命周期 E2E 测试")
class StoryboardLifecycleE2ETest {

    @Nested
    @DisplayName("状态机")
    class StateMachine {

        @Test
        @DisplayName("DRAFT → REVIEWING → LOCKED")
        void draftReviewLock() {
            // No exception = valid
            StoryboardStateMachine.requireTransition(VersionStatus.DRAFT, VersionStatus.REVIEWING);
            StoryboardStateMachine.requireTransition(VersionStatus.REVIEWING, VersionStatus.LOCKED);
        }

        @Test
        @DisplayName("LOCKED → SUPERSEDED")
        void lockedToSuperseded() {
            StoryboardStateMachine.requireTransition(VersionStatus.LOCKED, VersionStatus.SUPERSEDED);
        }

        @Test
        @DisplayName("REVIEWING → DRAFT (退回)")
        void reviewingBackToDraft() {
            StoryboardStateMachine.requireTransition(VersionStatus.REVIEWING, VersionStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("档位准入")
    class TierGating {

        @Test
        @DisplayName("A → B 合法，B → C 合法，A → C 非法")
        void tierUpgradeRules() {
            StoryboardStateMachine.requireTierUpgrade(Tier.A, Tier.B);
            StoryboardStateMachine.requireTierUpgrade(Tier.B, Tier.C);
            // A → C should throw
            try {
                StoryboardStateMachine.requireTierUpgrade(Tier.A, Tier.C);
                throw new AssertionError("Expected exception for A→C");
            } catch (RuntimeException e) {
                assertThat(e.getMessage()).contains("升到 C 档");
            }
        }
    }

    @Nested
    @DisplayName("生产准入")
    class ProductionGating {

        @Test
        @DisplayName("C档完整镜头通过准入")
        void cTierCompletePassesGate() {
            StoryboardVersion version = new StoryboardVersion();
            version.setTier("C");
            version.setStatus("locked");

            StoryboardShot shot = new StoryboardShot();
            shot.setShotCode("S01-C01");
            shot.setDurationMs(3000L);
            shot.setImagePrompt("cinematic, dramatic lighting");
            shot.setVideoMotionPrompt("slow push-in");
            shot.setDubText("你好");
            shot.setFailureStrategy("retry");

            var result = ProductionGate.evaluate(version, List.of(shot), List.of("char1"), List.of());
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("C档缺少图片提示词 → 不通过")
        void cTierMissingImagePromptFails() {
            StoryboardVersion version = new StoryboardVersion();
            version.setTier("C");

            StoryboardShot shot = new StoryboardShot();
            shot.setShotCode("S01-C01");
            shot.setDurationMs(3000L);
            shot.setImagePrompt(null);
            shot.setVideoMotionPrompt("yes");
            shot.setFailureStrategy("retry");

            var result = ProductionGate.evaluate(version, List.of(shot), List.of("char1"), List.of());
            assertThat(result.allowed()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("图片提示词"));
        }

        @Test
        @DisplayName("有未解决阻断问题 → 不通过")
        void openBlockingIssueFails() {
            StoryboardVersion version = new StoryboardVersion();
            version.setTier("A");

            StoryboardShot shot = new StoryboardShot();
            shot.setShotCode("S01-C01");
            shot.setDurationMs(3000L);

            var result = ProductionGate.evaluate(version, List.of(shot), List.of(),
                    List.of("issue-fingerprint-1"));
            assertThat(result.allowed()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("未解决"));
        }
    }
}
