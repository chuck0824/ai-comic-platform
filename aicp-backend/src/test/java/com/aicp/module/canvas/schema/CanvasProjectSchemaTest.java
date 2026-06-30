package com.aicp.module.canvas.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Canvas Project Schema 验证")
class CanvasProjectSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("canvas_projects 表包含全部溯源归属列")
    void canvasProjectHasTraceableOwnershipColumns() {
        var columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name='CANVAS_PROJECTS'",
            String.class);
        assertThat(columns).contains(
            "CONTENT_PROJECT_ID", "PRODUCTION_UNIT_TYPE", "PRODUCTION_UNIT_ID",
            "SOURCE_CONTENT_VERSION_ID", "SOURCE_STORYBOARD_VERSION_ID",
            "PRODUCTION_SNAPSHOT", "PURPOSE", "OWNER_ID", "THUMBNAIL_URL",
            "IDEMPOTENCY_KEY", "ARCHIVED_AT", "REVISION", "IS_DELETED");
    }

    @Test
    @DisplayName("旧列 script_id 和 episode_index 保留为 legacy")
    void legacyColumnsStillExist() {
        var columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name='CANVAS_PROJECTS'",
            String.class);
        assertThat(columns).contains("SCRIPT_ID", "EPISODE_INDEX");
    }

    @Test
    @DisplayName("唯一索引 uk_canvas_idempotency 存在")
    void idempotencyUniqueKeyExists() {
        var indexes = jdbc.queryForList(
            "select index_name from information_schema.indexes where table_name='CANVAS_PROJECTS'",
            String.class);
        assertThat(indexes).contains("UK_CANVAS_IDEMPOTENCY");
    }
}
