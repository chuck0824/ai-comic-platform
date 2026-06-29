package com.aicp.module.contentproject.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ContentProjectEnums 单元测试")
class ContentProjectEnumsTest {

    @Test
    @DisplayName("仅接受支持的创作模式")
    void acceptsOnlySupportedCreationModes() {
        assertThat(ContentProjectEnums.CreationMode.parse("short_drama"))
                .isEqualTo(ContentProjectEnums.CreationMode.SHORT_DRAMA);
        assertThat(ContentProjectEnums.CreationMode.parse("LONG_FORM"))
                .isEqualTo(ContentProjectEnums.CreationMode.LONG_FORM);
        assertThat(ContentProjectEnums.CreationMode.parse("TVC"))
                .isEqualTo(ContentProjectEnums.CreationMode.TVC);
        assertThatThrownBy(() -> ContentProjectEnums.CreationMode.parse("movie"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("来源模式解析正确")
    void parsesSourceModes() {
        assertThat(ContentProjectEnums.SourceMode.parse("ai_manual"))
                .isEqualTo(ContentProjectEnums.SourceMode.AI_MANUAL);
        assertThat(ContentProjectEnums.SourceMode.parse("uploaded"))
                .isEqualTo(ContentProjectEnums.SourceMode.UPLOADED);
    }

    @Test
    @DisplayName("OWNER 可执行所有操作，VIEWER 仅可查看")
    void ownerCanPerformEveryProjectAction() {
        assertThat(ContentProjectEnums.Role.OWNER.allows(ContentProjectEnums.Action.DELETE_PROJECT)).isTrue();
        assertThat(ContentProjectEnums.Role.OWNER.allows(ContentProjectEnums.Action.EDIT_CONTENT)).isTrue();
        assertThat(ContentProjectEnums.Role.OWNER.allows(ContentProjectEnums.Action.MANAGE_MEMBERS)).isTrue();
        assertThat(ContentProjectEnums.Role.OWNER.allows(ContentProjectEnums.Action.VIEW)).isTrue();

        assertThat(ContentProjectEnums.Role.VIEWER.allows(ContentProjectEnums.Action.EDIT_CONTENT)).isFalse();
        assertThat(ContentProjectEnums.Role.VIEWER.allows(ContentProjectEnums.Action.DELETE_PROJECT)).isFalse();
        assertThat(ContentProjectEnums.Role.VIEWER.allows(ContentProjectEnums.Action.VIEW)).isTrue();
    }

    @Test
    @DisplayName("EDITOR 可编辑内容和运行 AI，不可管理成员")
    void editorPermissions() {
        assertThat(ContentProjectEnums.Role.EDITOR.allows(ContentProjectEnums.Action.EDIT_CONTENT)).isTrue();
        assertThat(ContentProjectEnums.Role.EDITOR.allows(ContentProjectEnums.Action.RUN_CONTENT_AI)).isTrue();
        assertThat(ContentProjectEnums.Role.EDITOR.allows(ContentProjectEnums.Action.MANAGE_MEMBERS)).isFalse();
        assertThat(ContentProjectEnums.Role.EDITOR.allows(ContentProjectEnums.Action.DELETE_PROJECT)).isFalse();
    }

    @Test
    @DisplayName("REVIEWER 仅可查看和审核")
    void reviewerPermissions() {
        assertThat(ContentProjectEnums.Role.REVIEWER.allows(ContentProjectEnums.Action.REVIEW)).isTrue();
        assertThat(ContentProjectEnums.Role.REVIEWER.allows(ContentProjectEnums.Action.VIEW)).isTrue();
        assertThat(ContentProjectEnums.Role.REVIEWER.allows(ContentProjectEnums.Action.EDIT_CONTENT)).isFalse();
    }

    @Test
    @DisplayName("PRODUCER 可查看和生产")
    void producerPermissions() {
        assertThat(ContentProjectEnums.Role.PRODUCER.allows(ContentProjectEnums.Action.PRODUCE)).isTrue();
        assertThat(ContentProjectEnums.Role.PRODUCER.allows(ContentProjectEnums.Action.VIEW)).isTrue();
        assertThat(ContentProjectEnums.Role.PRODUCER.allows(ContentProjectEnums.Action.EDIT_CONTENT)).isFalse();
    }

    @Test
    @DisplayName("枚举 value() 方法返回小写蛇形命名")
    void valueMethodReturnsLowerSnakeCase() {
        assertThat(ContentProjectEnums.CreationMode.SHORT_DRAMA.value()).isEqualTo("short_drama");
        assertThat(ContentProjectEnums.ContentStatus.NEEDS_REVISION.value()).isEqualTo("needs_revision");
        assertThat(ContentProjectEnums.StoryboardIntent.NOT_DECIDED.value()).isEqualTo("not_decided");
    }
}
