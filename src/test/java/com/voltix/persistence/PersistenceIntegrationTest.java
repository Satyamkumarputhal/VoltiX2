package com.voltix.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MetricsPartitionMaintenanceService partitionService;

    @Autowired
    private MetricsBatchWriter batchWriter;

    // Dedicated fixture IDs for this test only -- chosen well outside the
    // range used by real dev/demo seed data (tenants 1-2, zones 1-2, meters
    // SM-0..SM-4, users operator/inspector/admin) so this test can never
    // collide with or delete them. This test used to unconditionally
    // DELETE FROM smart_meters/grid_zones/users/tenants (ALL rows), which
    // silently destroyed the shared dev database's seed data on every
    // "mvn test" run.
    private static final long TEST_TENANT_ID = 999_002L;
    private static final long TEST_ZONE_ID = 999_002L;
    private static final String TEST_METER_ID = "METER-PERSIST-TEST-999002";

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS metrics_history_bootstrap");
        // Only ever touches this test's own dedicated fixture rows -- never
        // a blanket DELETE affecting other tenants/zones/meters/users.
        jdbcTemplate.update("DELETE FROM system_alerts WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM metrics_history WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM telemetry_staging WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id = ?", TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?", TEST_TENANT_ID);

        // Seed references
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "Persistence Test Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.25)",
                TEST_ZONE_ID, TEST_TENANT_ID, "Persistence Test Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);

        // Pre-create partitions for the test environment
        partitionService.ensureForwardPartitions();
    }

    @Test
    void testPartitionCreation() {
        partitionService.ensureForwardPartitions();

        // Check if partitions were created in PostgreSQL
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE tablename LIKE 'metrics_history_%'",
                String.class
        );

        assertFalse(tables.isEmpty(), "At least one daily partition should be created");
        
        // Check if a partition for today is created
        String todayPartition = "metrics_history_" + java.time.LocalDate.now(java.time.Clock.systemUTC()).toString().replace("-", "_");
        assertTrue(tables.contains(todayPartition), "Should contain partition: " + todayPartition);
    }

    @Test
    void testBatchWriting() {
        // Insert a staging record first since MetricsBatchWriter updates staging table.
        // Use a staging_id well outside any real range to avoid collisions.
        long stagingId = 999_999_002L;
        jdbcTemplate.update("""
            INSERT INTO telemetry_staging (staging_id, tenant_id, meter_id, transaction_id, zone_id, voltage, current_amp, kw_consumed, client_timestamp, processed)
            VALUES (?, ?, ?, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', ?, 230.0, 10.0, 2.3, CURRENT_TIMESTAMP, FALSE)
        """, stagingId, TEST_TENANT_ID, TEST_METER_ID, TEST_ZONE_ID);

        MetricsHistoryRow row = new MetricsHistoryRow(
                stagingId,
                TEST_TENANT_ID,
                TEST_ZONE_ID,
                TEST_METER_ID,
                230.0,
                10.0,
                2.3,
                0.05,
                ZonedDateTime.now()
        );

        batchWriter.enqueue(row);
        batchWriter.flush(true); // flush all immediately

        // Verify row was written to metrics_history (scoped to this test's own meter)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM metrics_history WHERE meter_id = ?", Integer.class, TEST_METER_ID);
        assertTrue(count != null && count == 1, "Should have 1 metrics history record");

        // Verify staging record was marked processed
        Boolean processed = jdbcTemplate.queryForObject(
                "SELECT processed FROM telemetry_staging WHERE staging_id = ?", Boolean.class, stagingId);
        assertTrue(processed != null && processed, "Telemetry staging record should be marked processed");
    }
}
