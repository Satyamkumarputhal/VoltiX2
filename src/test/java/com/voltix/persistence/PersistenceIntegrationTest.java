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

    @BeforeEach
    void setUp() {
        // Clear tables to start from a clean state
        jdbcTemplate.execute("DROP TABLE IF EXISTS metrics_history_bootstrap");
        jdbcTemplate.execute("DELETE FROM system_alerts");
        jdbcTemplate.execute("DELETE FROM public_complaints");
        jdbcTemplate.execute("DELETE FROM zone_hourly_aggregates");
        jdbcTemplate.execute("DELETE FROM metrics_history");
        jdbcTemplate.execute("DELETE FROM telemetry_staging");
        jdbcTemplate.execute("DELETE FROM smart_meters");
        jdbcTemplate.execute("DELETE FROM grid_zones");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenants");

        // Seed references
        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (1, 'Test Tenant', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (1, 1, 'Zone A', 1.25)");
        jdbcTemplate.execute("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES ('METER-001', 1, 1, 'SN-001', 'ACTIVE')");

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
        // Insert a staging record first since MetricsBatchWriter updates staging table
        jdbcTemplate.execute("""
            INSERT INTO telemetry_staging (staging_id, tenant_id, meter_id, transaction_id, zone_id, voltage, current_amp, kw_consumed, client_timestamp, processed)
            VALUES (100, 1, 'METER-001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 1, 230.0, 10.0, 2.3, CURRENT_TIMESTAMP, FALSE)
        """);

        MetricsHistoryRow row = new MetricsHistoryRow(
                100L,
                1L,
                1L,
                "METER-001",
                230.0,
                10.0,
                2.3,
                0.05,
                ZonedDateTime.now()
        );

        batchWriter.enqueue(row);
        batchWriter.flush(true); // flush all immediately

        // Verify row was written to metrics_history
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM metrics_history", Integer.class);
        assertTrue(count != null && count == 1, "Should have 1 metrics history record");

        // Verify staging record was marked processed
        Boolean processed = jdbcTemplate.queryForObject("SELECT processed FROM telemetry_staging WHERE staging_id = 100", Boolean.class);
        assertTrue(processed != null && processed, "Telemetry staging record should be marked processed");
    }
}
