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

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS metrics_history_bootstrap");
        jdbcTemplate.execute("DELETE FROM system_alerts");
        jdbcTemplate.execute("DELETE FROM public_complaints");
        jdbcTemplate.execute("DELETE FROM zone_hourly_aggregates");
        jdbcTemplate.execute("DELETE FROM metrics_history");
        jdbcTemplate.execute("DELETE FROM telemetry_staging");
        jdbcTemplate.execute("DELETE FROM smart_meters");
        jdbcTemplate.execute("DELETE FROM grid_zones");
        jdbcTemplate.execute("DELETE FROM tenants");

        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (1, 'Test Tenant', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (1, 1, 'Zone A', 1.0)");
        jdbcTemplate.execute("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES ('METER-001', 1, 1, 'SN-001', 'ACTIVE')");

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
            VALUES (1, 1, 'METER-001', 1, 230.0, 10.0, 2.30, ?)
            """, java.sql.Timestamp.from(previousHour.toInstant()));
    }

    @Test
    void testHourlyAggregationAndForecasting() {
        forecastScheduler.runHourlyAggregationAndForecasting();

        // Verify zone_hourly_aggregates was populated
        List<Map<String, Object>> aggregates = jdbcTemplate.queryForList("SELECT * FROM zone_hourly_aggregates");
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
