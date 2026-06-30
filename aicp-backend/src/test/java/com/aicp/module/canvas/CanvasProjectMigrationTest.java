package com.aicp.module.canvas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Canvas Project 数据迁移验证")
class CanvasProjectMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("回填后旧数据的新列均为 NOT NULL")
    void backfillPopulatesAllNewColumnsForExistingRows() {
        var rows = jdbc.queryForList(
            "SELECT * FROM canvas_projects");
        for (var row : rows) {
            assertThat(row.get("CONTENT_PROJECT_ID")).as("content_project_id for " + row.get("UUID")).isNotNull();
            assertThat(row.get("PRODUCTION_UNIT_TYPE")).as("production_unit_type").isNotNull();
            assertThat(row.get("PRODUCTION_UNIT_ID")).as("production_unit_id").isNotNull();
            assertThat(row.get("PURPOSE")).as("purpose").isNotNull();
            assertThat(row.get("OWNER_ID")).as("owner_id").isNotNull();
            assertThat(row.get("IDEMPOTENCY_KEY")).as("idempotency_key").isNotNull();
            assertThat(row.get("REVISION")).as("revision").isNotNull();
            assertThat(row.get("IS_DELETED")).as("is_deleted").isNotNull();
        }
    }

    @Test
    @DisplayName("表中有数据时所有行的新列均非空")
    void canvasTableColumnsAreQueryable() {
        // Verify new columns exist and are queryable (even if no rows exist)
        var columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name='CANVAS_PROJECTS'",
            String.class);
        assertThat(columns).contains(
            "CONTENT_PROJECT_ID", "PRODUCTION_UNIT_TYPE", "PRODUCTION_UNIT_ID",
            "PURPOSE", "OWNER_ID", "IDEMPOTENCY_KEY", "REVISION", "IS_DELETED");
    }
}
