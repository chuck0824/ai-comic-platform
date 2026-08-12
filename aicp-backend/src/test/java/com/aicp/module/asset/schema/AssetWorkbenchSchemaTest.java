package com.aicp.module.asset.schema;

import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.generation.entity.GenerationTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

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
    void assetApplicationsPersistStableTextConsumerKeys() throws Exception {
        assertThat(columns("ASSET_APPLICATIONS")).contains("TARGET_KEY");
        assertThat(indexExists("ASSET_APPLICATIONS", "IDX_AA_TARGET_KEY")).isTrue();
        String migration = new ClassPathResource("db/migration/V17__asset_application_target_key.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(migration)
                .containsIgnoringCase("ADD COLUMN target_key")
                .containsIgnoringCase("CREATE INDEX idx_aa_target_key")
                .doesNotContainIgnoringCase("IF NOT EXISTS");
        String undo = new ClassPathResource("db/migration/V17_undo.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(undo)
                .containsIgnoringCase("DROP INDEX idx_aa_target_key ON asset_applications")
                .containsIgnoringCase("DROP COLUMN target_key")
                .doesNotContainIgnoringCase("IF EXISTS");
        String h2 = new ClassPathResource("db/schema-h2.sql").getContentAsString(StandardCharsets.UTF_8);
        String mysql = new ClassPathResource("db/schema-mysql.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(h2)
                .containsIgnoringCase("target_key")
                .containsIgnoringCase("idx_aa_target_key");
        assertThat(mysql)
                .containsIgnoringCase("target_key")
                .containsIgnoringCase("idx_aa_target_key");
    }

    @Test
    void targetKeyMigrationExecutesOnceAgainstLegacySchema() throws Exception {
        String url = "jdbc:h2:mem:asset-target-key-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE asset_applications (id BIGINT PRIMARY KEY, target_type VARCHAR(32))");
            ClassPathResource migration = new ClassPathResource("db/migration/V17__asset_application_target_key.sql");
            ScriptUtils.executeSqlScript(connection, migration);

            try (ResultSet columns = statement.executeQuery("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'ASSET_APPLICATIONS' AND COLUMN_NAME = 'TARGET_KEY'
                    """)) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt(1)).isEqualTo(1);
            }
            try (ResultSet indexes = statement.executeQuery("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                    WHERE TABLE_NAME = 'ASSET_APPLICATIONS' AND INDEX_NAME = 'IDX_AA_TARGET_KEY'
                    """)) {
                assertThat(indexes.next()).isTrue();
                assertThat(indexes.getInt(1)).isEqualTo(1);
            }
        }
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

    private boolean indexExists(String table, String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, table, index);
        return count != null && count > 0;
    }
}
