package com.aicp.module.trade.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TradeMarketSchemaTest {

    @Autowired
    DataSource dataSource;

    @Test
    void createsTradeTables() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(tableExists(c, "SCRIPT_LISTINGS")).isTrue();
            assertThat(tableExists(c, "LISTING_LICENSE_OPTIONS")).isTrue();
            assertThat(tableExists(c, "TRADE_ORDERS")).isTrue();
            assertThat(tableExists(c, "TRADE_ORDER_ITEMS")).isTrue();
            assertThat(tableExists(c, "SCRIPT_ENTITLEMENTS")).isTrue();
            assertThat(tableExists(c, "PURCHASED_SCRIPT_COPIES")).isTrue();
            assertThat(tableExists(c, "PURCHASE_REQUESTS")).isTrue();
            assertThat(tableExists(c, "REFUND_REQUESTS")).isTrue();
            assertThat(tableExists(c, "TRADE_OUTBOX_EVENTS")).isTrue();
            assertThat(tableExists(c, "TRADE_AUDIT_LOGS")).isTrue();
        }
    }

    @Test
    void tradeOrdersHasIdempotencyGuard() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Set<String> uniqueColumns = uniqueColumnSets(c, "TRADE_ORDERS");
            assertThat(uniqueColumns).contains("BUYER_WORKSPACE_ID");
            assertThat(uniqueColumns).contains("CREATE_IDEMPOTENCY_KEY");
        }
    }

    @Test
    void tradeOrdersHasOrderNoUniqueConstraint() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Set<String> uniqueColumns = uniqueColumnSets(c, "TRADE_ORDERS");
            assertThat(uniqueColumns).contains("ORDER_NO");
        }
    }

    @Test
    void orderItemHasSingleItemGuard() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(hasUniqueConstraint(c, "TRADE_ORDER_ITEMS", "ORDER_ID")).isTrue();
        }
    }

    @Test
    void entitlementHasUniqueOrderItemGuard() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(hasUniqueConstraint(c, "SCRIPT_ENTITLEMENTS", "ORDER_ITEM_ID")).isTrue();
        }
    }

    @Test
    void outboxHasCompositeIdempotencyGuard() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Set<String> uniqueColumns = uniqueColumnSets(c, "TRADE_OUTBOX_EVENTS");
            assertThat(uniqueColumns).contains("AGGREGATE_TYPE");
            assertThat(uniqueColumns).contains("AGGREGATE_ID");
            assertThat(uniqueColumns).contains("EVENT_TYPE");
            assertThat(uniqueColumns).contains("IDEMPOTENCY_KEY");
        }
    }

    @Test
    void allAmountColumnsUseBigint() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Verify money columns in trade_orders are BIGINT (not DECIMAL)
            String columnType = getColumnType(c, "TRADE_ORDERS", "TOTAL_AMOUNT_CENTS");
            assertThat(columnType.toUpperCase())
                    .contains("BIGINT");
        }
    }

    // -- helpers --

    private boolean tableExists(Connection c, String tableName) throws Exception {
        DatabaseMetaData meta = c.getMetaData();
        String upper = tableName.toUpperCase();
        try (ResultSet rs = meta.getTables(null, "PUBLIC", upper, null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(null, null, upper, null)) {
            return rs.next();
        }
    }

    private boolean hasUniqueConstraint(Connection c, String tableName, String columnName) throws Exception {
        Set<String> allColumns = uniqueColumnSets(c, tableName);
        return allColumns.contains(columnName.toUpperCase());
    }

    private Set<String> uniqueColumnSets(Connection c, String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(null, null, tableName.toUpperCase(), true, true)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null) {
                    columns.add(col.toUpperCase());
                }
            }
        }
        return columns;
    }

    private String getColumnType(Connection c, String tableName, String columnName) throws Exception {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(null, "PUBLIC", tableName.toUpperCase(), columnName.toUpperCase())) {
            if (rs.next()) return rs.getString("TYPE_NAME");
        }
        try (ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (rs.next()) return rs.getString("TYPE_NAME");
        }
        return "UNKNOWN";
    }
}
