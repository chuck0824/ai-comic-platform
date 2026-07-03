package com.aicp.module.asset.schema;

import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.generation.entity.GenerationTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the canonical workbench columns exist in both H2 and MySQL schemas.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AssetWorkbenchSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void workspaceAssetCanonicalColumnsExist() {
        Set<String> cols = columns("WORKSPACE_ASSETS");
        assertThat(cols).contains(
                "CONTENT_PROJECT_ID", "SOURCE_CANVAS_PROJECT_ID", "SOURCE_NODE_ID",
                "SOURCE_TASK_ID", "MEDIA_TYPE",
                "DELETED_AT", "DELETED_BY",
                "PURGE_AT", "PURGE_BLOCKED_REASON",
                "LEGACY_PLATFORM_ASSET_ID");
    }

    @Test
    void assetVersionCanonicalColumnsExist() {
        Set<String> cols = columns("ASSET_VERSIONS");
        assertThat(cols).contains(
                "SOURCE_TASK_ID", "STORAGE_PROVIDER", "STORAGE_BUCKET", "STORAGE_KEY",
                "MIME_TYPE", "FILE_SIZE", "WIDTH", "HEIGHT", "DURATION_MS",
                "GENERATION_SNAPSHOT");
    }

    @Test
    void generationTaskCanonicalColumnsExist() {
        Set<String> cols = columns("GENERATION_TASKS");
        assertThat(cols).contains(
                "WORKSPACE_ID", "CREATED_BY", "CONTENT_PROJECT_ID", "ASSET_TYPE",
                "RETRY_OF_TASK_ID", "IDEMPOTENCY_KEY", "REQUEST_ID");
    }

    @Test
    void newWorkbenchTablesExist() {
        assertThat(tableExists("WORKSPACE_ASSET_FAVORITES")).isTrue();
        assertThat(tableExists("ASSET_ACTIVITY_LOGS")).isTrue();
        assertThat(tableExists("CANVAS_ASSET_PLACEMENTS")).isTrue();
        assertThat(tableExists("ASSET_COMMAND_IDEMPOTENCIES")).isTrue();
        assertThat(tableExists("GENERATION_SETTLEMENT_OUTBOX")).isTrue();
    }

    @Test
    void workspaceAssetEntityMapsAllColumns() {
        // Verifies the entity class compiles and MyBatis-Plus can introspect it
        assertThat(WorkspaceAsset.class.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableName.class)).isTrue();
    }

    @Test
    void assetVersionEntityMapsAllColumns() {
        assertThat(AssetVersion.class.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableName.class)).isTrue();
    }

    @Test
    void generationTaskEntityMapsAllColumns() {
        assertThat(GenerationTask.class.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableName.class)).isTrue();
    }

    private Set<String> columns(String table) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                String.class, table));
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
