package com.voltix.analytics.forecasting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DirtiesContext
class LoadForecastSchedulerIntegrationTest {

    @Autowired
    private LoadForecastScheduler forecastScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Dedicated fixture IDs for this test only -- chosen well outside the
    // range used by real dev/demo seed data (tenants 1-2, zones 1-2, meters
    // SM-0..SM-4, users operator/inspector/admin) so this test can never
    // collide with or delete them. This test used to unconditionally
    // DELETE FROM smart_meters/grid_zones/users/tenants (ALL rows), which
    // silently destroyed the shared dev database's seed data on every
    // "mvn test" run.
    private static final long TEST_TENANT_ID = 999_003L;
    private static final long TEST_ZONE_ID = 999_003L;
    private static final String TEST_METER_ID = "METER-FORECAST-TEST-999003";

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS metrics_history_bootstrap");
        // Only ever touches this test's own dedicated fixture rows -- never
        // a blanket DELETE affecting other tenants/zones/meters/users.
        jdbcTemplate.update("DELETE FROM zone_hourly_aggregates WHERE zone_id = ?", TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM metrics_history WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM telemetry_staging WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id = ?", TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?", TEST_TENANT_ID);

        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "Forecast Test Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_ID, TEST_TENANT_ID, "Forecast Test Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);

        // Insert historical metrics in the previous hour to trigger aggregation
        ZonedDateTime previousHour = ZonedDateTime.now().minusHours(1).truncatedTo(ChronoUnit.HOURS).plusMinutes(30);

        // Dynamically create partition for the historical record's date
        java.time.LocalDate date = previousHour.toLocalDate();
        String partitionName = "metrics_history_" + date.toString().replace("-", "_");
        jdbcTemplate.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s
                PARTITION OF metrics_history
                FOR VALUES FROM ('%s') TO ('%s')
                """, partitionName, date, date.plusDays(1)));

        jdbcTemplate.update("""
            INSERT INTO metrics_history (record_id, tenant_id, meter_id, zone_id, voltage, current_amp, kw_consumed, recorded_at)
            VALUES (999003, ?, ?, ?, 230.0, 10.0, 2.30, ?)
            """, TEST_TENANT_ID, TEST_METER_ID, TEST_ZONE_ID, java.sql.Timestamp.from(previousHour.toInstant()));
    }

    @Test
    void testHourlyAggregationAndForecasting() {
        forecastScheduler.runHourlyAggregationAndForecasting();

        // Verify zone_hourly_aggregates was populated (scoped to this test's own zone)
        List<Map<String, Object>> aggregates = jdbcTemplate.queryForList(
                "SELECT * FROM zone_hourly_aggregates WHERE zone_id = ?", TEST_ZONE_ID);
        assertEquals(1, aggregates.size());

        Map<String, Object> aggregate = aggregates.get(0);
        assertEquals(2.30, ((Number) aggregate.get("total_kw_consumed")).doubleValue(), 0.001);
        assertEquals(230.0, ((Number) aggregate.get("avg_voltage")).doubleValue(), 0.001);
        assertEquals(10.0, ((Number) aggregate.get("avg_current_amp")).doubleValue(), 0.001);

        // Verify the forecast was calculated and saved
        assertNotNull(aggregate.get("forecast_kw_2h"));
        assertNotNull(aggregate.get("forecast_generated_at"));
    }
}
