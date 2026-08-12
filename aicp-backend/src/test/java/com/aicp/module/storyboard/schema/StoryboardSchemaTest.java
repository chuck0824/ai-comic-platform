package com.aicp.module.storyboard.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("分镜专业领域 Schema 验证")
class StoryboardSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("13张分镜领域表均存在")
    void createsAllStoryboardDomainTables() {
        var expected = List.of(
            "storyboards", "storyboard_versions", "storyboard_version_scenes",
            "storyboard_version_shots", "storyboard_emotion_segments",
            "storyboard_prompt_templates", "storyboard_creative_rules",
            "storyboard_character_visuals", "storyboard_shot_visual_bindings",
            "storyboard_review_issues", "storyboard_jobs", "storyboard_audit_logs",
            "storyboard_canvas_snapshots"
        );
        for (String table : expected) {
            Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Integer.class, table.toUpperCase());
            assertThat(count).as("表 %s 应存在", table).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("storyboards 表包含所有核心列")
    void storyboardsHasCoreColumns() {
        var columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name = 'STORYBOARDS'");
        var names = columns.stream().map(c -> c.get("COLUMN_NAME").toString()).toList();
        assertThat(names).contains(
            "ID", "UUID", "PROJECT_ID", "CONTENT_UNIT_ID",
            "SOURCE_CONTENT_VERSION_ID", "TITLE", "PURPOSE",
            "CURRENT_DRAFT_VERSION_ID", "CURRENT_LOCKED_VERSION_ID",
            "PRODUCTION_STATUS", "CREATED_BY", "IS_DELETED");
    }

    @Test
    @DisplayName("storyboard_versions 表包含版本控制列")
    void versionsHasLifecycleColumns() {
        var columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name = 'STORYBOARD_VERSIONS'");
        var names = columns.stream().map(c -> c.get("COLUMN_NAME").toString()).toList();
        assertThat(names).contains(
            "ID", "UUID", "STORYBOARD_ID", "PARENT_VERSION_ID",
            "TIER", "VERSION_NO", "STATUS", "REVISION",
            "CREATED_FROM", "LOCKED_BY");
    }

    @Test
    @DisplayName("storyboard_version_shots 表包含13维镜头字段")
    void shotsHasThirteenDimensions() {
        var columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name = 'STORYBOARD_VERSION_SHOTS'");
        var names = columns.stream().map(c -> c.get("COLUMN_NAME").toString()).toList();
        assertThat(names).contains(
            "SHOT_CODE", "DURATION_MS", "SHOT_SIZE",
            "VISUAL_DESCRIPTION", "LIGHTING_ATMOSPHERE", "CHARACTER_ACTION",
            "EMOTION_DESCRIPTION", "DIALOGUE_TEXT", "SCENE_TAGS_JSON",
            "SOUND_EFFECT", "REFERENCE_TEXT", "IMAGE_PROMPT", "VIDEO_MOTION_PROMPT");
    }

    @Test
    @DisplayName("通用 schema 可在空库独立创建 V2 分镜链路")
    void genericSchemaCreatesV2StoryboardChainInFreshDatabase() throws Exception {
        String genericSchema = new ClassPathResource("db/schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int sectionStart = genericSchema.indexOf("-- V2 storyboard scene-asset snapshot binding");
        assertThat(sectionStart).as("通用 schema 应包含 V2 storyboard baseline 区块").isGreaterThanOrEqualTo(0);
        String storyboardSection = genericSchema.substring(sectionStart);
        assertThat(storyboardSection).doesNotContain("ALTER TABLE storyboard_version_shots");

        String url = "jdbc:h2:mem:generic_schema_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(
                    storyboardSection.getBytes(StandardCharsets.UTF_8)));
            for (String table : List.of("storyboards", "storyboard_versions",
                    "storyboard_version_scenes", "storyboard_version_shots")) {
                try (var statement = connection.prepareStatement(
                        "select count(*) from information_schema.tables where lower(table_name) = ?")) {
                    statement.setString(1, table);
                    try (var result = statement.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getInt(1)).as("通用 schema 应创建表 %s", table).isEqualTo(1);
                    }
                }
            }
            try (var statement = connection.prepareStatement(
                    "select lower(column_name) from information_schema.columns "
                            + "where lower(table_name) = 'storyboard_version_shots'")) {
                try (var result = statement.executeQuery()) {
                    var columns = new java.util.ArrayList<String>();
                    while (result.next()) columns.add(result.getString(1));
                    assertThat(columns).contains("scene_asset_id", "scene_asset_version_id",
                            "scene_variant_id", "scene_variant_version", "scene_asset_snapshot");
                }
            }
            try (var statement = connection.prepareStatement(
                    "select distinct lower(index_name) from information_schema.index_columns "
                            + "where lower(table_name) = 'storyboard_version_shots'")) {
                try (var result = statement.executeQuery()) {
                    var indexes = new java.util.ArrayList<String>();
                    while (result.next()) indexes.add(result.getString(1));
                    assertThat(indexes).contains("idx_sbshot_version", "idx_sbshot_scene_asset",
                            "idx_sbshot_scene_asset_version");
                }
            }
        }
    }
}
