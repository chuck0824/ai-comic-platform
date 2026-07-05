package com.aicp.module.canvas.kernel;

import com.aicp.module.canvas.service.CanvasPortRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CanvasPortRegistry 端口校验测试")
class CanvasPortRegistryTest {

    private CanvasPortRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CanvasPortRegistry();
    }

    @Test
    @DisplayName("image_ref OUTPUT → image_ref INPUT 可以连接")
    void imageRefOutputToInput() {
        assertThat(registry.canConnect("image", "image_ref", "video", "image_ref", "scene")).isTrue();
    }

    @Test
    @DisplayName("不同类型端口不可连接")
    void differentPayloadTypesCannotConnect() {
        assertThat(registry.canConnect("audio", "audio_ref", "image", "image_ref", null)).isFalse();
    }

    @Test
    @DisplayName("源端口方向不匹配时拒绝（video_candidate 只有 OUTPUT）")
    void outputOnlyPortCannotBeInputSource() {
        // video_candidate 只有 OUTPUT 定义 → 作为 INPUT 使用应被拒绝
        assertThat(registry.canConnect("video", "video_candidate", "text", "text_out", null)).isFalse();
    }

    @Test
    @DisplayName("director_package OUTPUT → INPUT 可以连接")
    void directorOutputToInput() {
        assertThat(registry.canConnect("director", "director_package", "video", "director_package", "director_input")).isTrue();
    }

    @Test
    @DisplayName("不允许的角色被拒绝")
    void invalidRoleRejected() {
        var decision = registry.validate("image", "image_ref", "video", "image_ref", "invalid_role");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("角色");
    }

    @Test
    @DisplayName("未知端口被拒绝")
    void unknownPortRejected() {
        assertThat(registry.canConnect("image", "nonexistent", "video", "image_ref", null)).isFalse();
    }

    @Test
    @DisplayName("合约版本为 canvas-ports-v1")
    void contractVersionIsCorrect() {
        assertThat(CanvasPortRegistry.CONTRACT_VERSION).isEqualTo("canvas-ports-v1");
    }

    @Test
    @DisplayName("14 种端口定义注册成功")
    void allPortsRegistered() {
        assertThat(registry.allPortKeys()).hasSize(9); // 9 unique keys
        assertThat(registry.allPortKeys()).contains(
                "text_out", "shot", "image_ref", "motion_ref", "camera_ref",
                "audio_ref", "director_package", "video_candidate", "quality_report");
    }
}
