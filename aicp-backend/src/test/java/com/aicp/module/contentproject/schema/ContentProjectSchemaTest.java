package com.aicp.module.contentproject.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
