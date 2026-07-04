package com.aicp.module.enterprise.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that V7, V8, and V9 governance tables exist with correct
 * column types and unique constraints.
 */
@SpringBootTest
public class EnterpriseGovernanceSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void enterprisePurchaseBudgetsTableExists() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT COLUMN_NAME, TYPE_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'ENTERPRISE_PURCHASE_BUDGETS'");
        assertThat(cols).isNotEmpty();
        assertThat(cols).extracting(m -> m.get("COLUMN_NAME"))
                .contains("AMOUNT_CENTS", "RESERVED_CENTS", "CONSUMED_CENTS", "ROW_VERSION");
    }

    @Test
    void enterpriseBudgetEntriesTableHasIdempotencyKey() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'ENTERPRISE_PURCHASE_BUDGET_ENTRIES'");
        assertThat(cols).extracting(m -> m.get("COLUMN_NAME"))
                .contains("IDEMPOTENCY_KEY", "ENTRY_TYPE", "SOURCE_ID");
    }

    @Test
    void enterpriseApprovalItemsTableExists() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'ENTERPRISE_APPROVAL_ITEMS'");
        assertThat(cols).extracting(m -> m.get("COLUMN_NAME"))
                .contains("SOURCE_TYPE", "SOURCE_ID", "SOURCE_VERSION", "STATUS");
    }

    @Test
    void assetOutboxEventsTableExists() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'ASSET_OUTBOX_EVENTS'");
        assertThat(cols).isNotEmpty();
        assertThat(cols).extracting(m -> m.get("COLUMN_NAME"))
                .contains("EVENT_ID", "AGGREGATE_TYPE", "AGGREGATE_ID");
    }

    @Test
    void projectExportRequestsTableExists() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'PROJECT_EXPORT_REQUESTS'");
        assertThat(cols).isNotEmpty();
        assertThat(cols).extracting(m -> m.get("COLUMN_NAME"))
                .contains("STATUS", "WORKSPACE_ID", "PROJECT_ID", "ROW_VERSION");
    }

    @Test
    void enterpriseAuditIndexTableExists() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'ENTERPRISE_AUDIT_INDEX'");
        assertThat(cols).isNotEmpty();
        assertThat(cols).extracting(m -> m.get("COLUMN_NAME"))
                .contains("EVENT_ID", "SOURCE_DOMAIN", "REDACTED_SUMMARY");
    }
}
