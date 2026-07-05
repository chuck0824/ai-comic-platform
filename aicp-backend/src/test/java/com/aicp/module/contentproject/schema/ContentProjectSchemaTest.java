package com.aicp.module.contentproject.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("M0 Schema 验证")
class ContentProjectSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("八张 M0 表均存在")
    void m0TablesExist() {
        for (String table : List.of(
                "content_projects", "project_members", "project_parameter_versions",
                "content_units", "content_versions", "artifact_dependencies",
                "content_generation_jobs", "outbox_events")) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?",
                    Integer.class, table.toUpperCase());
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("P0 创作圣经四张表均存在")
    void creativeBibleFoundationExists() {
        for (String table : List.of(
                "creative_bible_versions", "ecosystem_rules",
                "project_writing_guides", "generation_context_snapshots")) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?",
                    Integer.class, table.toUpperCase());
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("创作圣经版本表唯一约束阻止同一项目重复版本号")
    void creativeBibleVersionUniqueConstraintEnforced() {
        jdbc.update("insert into creative_bible_versions"
                + " (project_id, version_no, status, snapshot_json, created_by)"
                + " values (1, 1, 'draft', '{}', 1)");
        assertThatThrownBy(() -> jdbc.update("insert into creative_bible_versions"
                + " (project_id, version_no, status, snapshot_json, created_by)"
                + " values (1, 1, 'draft', '{}', 1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
