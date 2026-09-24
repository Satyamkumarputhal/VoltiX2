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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        // Insert historical metrics in the previous UTC hour to trigger aggregation.
        // The scheduler computes sourceHour in UTC (whole UTC hour, now-1h) and the
        // aggregation buckets on whole UTC hours, so the seed must sit inside that
        // exact UTC hour: previous-UTC-hour + 30 min.
        ZonedDateTime previousHour = ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .minusHours(1).truncatedTo(ChronoUnit.HOURS).plusMinutes(30);

        // Dynamically create partition for the historical record's date (UTC).
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

    /**
     * Proves the explicit forecast timestamp semantics established in V9:
     *   - aggregated_hour       = SOURCE hour (hour whose actuals were aggregated)
     *   - forecast_target_hour  = aggregated_hour + 2h (the hour predicted)
     *   - forecast_kw_2h        = predicted load FOR forecast_target_hour
     *   - forecast_generated_at = wall-clock time the forecast was written
     *
     * In particular this proves forecast_kw_2h is NOT the source hour's actual
     * load: the stored target hour is two hours after the source hour, so a
     * consumer can never confuse the forecast with total_kw_consumed@sourceHour.
     */
    @Test
    void forecastTargetHour_isExactlySourceHourPlusTwoHours() {
        forecastScheduler.runHourlyAggregationAndForecasting();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT aggregated_hour, forecast_target_hour, forecast_kw_2h, "
                + "forecast_generated_at, total_kw_consumed "
                + "FROM zone_hourly_aggregates WHERE zone_id = ?", TEST_ZONE_ID);

        java.time.Instant sourceHour = toInstant(row.get("aggregated_hour"));
        java.time.Instant targetHour = toInstant(row.get("forecast_target_hour"));

        assertNotNull(sourceHour, "aggregated_hour (source hour) must be present.");
        assertNotNull(targetHour, "forecast_target_hour must be persisted, not implicit.");

        // Core semantic guarantee: target hour is EXACTLY source hour + 2 hours.
        assertEquals(sourceHour.plus(java.time.Duration.ofHours(2)), targetHour,
                "forecast_target_hour must equal aggregated_hour + 2h. "
                + "sourceHour=" + sourceHour + " targetHour=" + targetHour);

        // The forecast value is present and the target hour is strictly in the
        // future relative to the source hour -- it cannot be read as the source
        // hour's actual load.
        assertNotNull(row.get("forecast_kw_2h"), "forecast_kw_2h must be present.");
        assertNotNull(row.get("forecast_generated_at"), "forecast_generated_at must be present.");
        assertTrue(targetHour.isAfter(sourceHour),
                "forecast_target_hour must be strictly after the source aggregated_hour.");

        // The database can now unambiguously answer, from a single row:
        //   what was forecast (forecast_kw_2h),
        //   for which future hour (forecast_target_hour),
        //   generated when (forecast_generated_at),
        //   from which source hour (aggregated_hour).
    }

    /**
     * REGRESSION: hourly aggregation must bucket on WHOLE UTC hours, independent
     * of the JVM/DB-session timezone.
     *
     * We insert three telemetry rows at 14:10, 14:25 and 14:40 UTC. In the
     * project's typical dev timezone (Asia/Kolkata, +05:30) these are 19:40 /
     * 19:55 / 20:10 IST, which a session-timezone date_trunc('hour', ...) would
     * split into TWO different :30-UTC buckets (13:30 and 14:30). The correct
     * UTC-forced truncation must instead place all three into exactly ONE bucket:
     * aggregated_hour = 14:00:00 UTC. This aligns with the scheduler's whole-UTC
     * sourceHour and the forecaster's exact T-1h / T-2h lookups.
     */
    @Test
    void aggregationBucketIsUtcAligned_regardlessOfSessionTimezone() {
        // Use a fixed, unambiguous UTC hour well in the past (stable, no "now" flakiness).
        java.time.ZonedDateTime sourceHourUtc =
                java.time.ZonedDateTime.of(2026, 2, 3, 14, 0, 0, 0, java.time.ZoneOffset.UTC);

        // Ensure a partition exists for that date.
        java.time.LocalDate date = sourceHourUtc.toLocalDate();
        String partitionName = "metrics_history_" + date.toString().replace("-", "_");
        jdbcTemplate.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s
                PARTITION OF metrics_history
                FOR VALUES FROM ('%s') TO ('%s')
                """, partitionName, date, date.plusDays(1)));

        // Clean any residue for this specific hour/zone.
        jdbcTemplate.update("DELETE FROM zone_hourly_aggregates WHERE zone_id = ? AND aggregated_hour = ?",
                TEST_ZONE_ID, java.sql.Timestamp.from(sourceHourUtc.toInstant()));
        jdbcTemplate.update("DELETE FROM metrics_history WHERE meter_id = ? AND recorded_at >= ? AND recorded_at < ?",
                TEST_METER_ID,
                java.sql.Timestamp.from(sourceHourUtc.toInstant()),
                java.sql.Timestamp.from(sourceHourUtc.plusHours(1).toInstant()));

        // Three readings at 14:10, 14:25, 14:40 UTC — all inside the SAME UTC hour.
        long recId = 999_030L;
        for (int min : new int[] {10, 25, 40}) {
            jdbcTemplate.update("""
                    INSERT INTO metrics_history
                        (record_id, tenant_id, meter_id, zone_id, voltage, current_amp, kw_consumed, recorded_at)
                    VALUES (?, ?, ?, ?, 236.0, 6.0, 10.0, ?)
                    """,
                    recId++, TEST_TENANT_ID, TEST_METER_ID, TEST_ZONE_ID,
                    java.sql.Timestamp.from(sourceHourUtc.plusMinutes(min).toInstant()));
        }

        // Run aggregation + forecast for that exact UTC source hour.
        forecastScheduler.aggregateAndForecast(sourceHourUtc);

        // The three readings must collapse into exactly ONE aggregate at 14:00:00 UTC.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT aggregated_hour, total_kw_consumed FROM zone_hourly_aggregates "
                + "WHERE zone_id = ? AND aggregated_hour >= ? AND aggregated_hour < ?",
                TEST_ZONE_ID,
                java.sql.Timestamp.from(sourceHourUtc.minusHours(1).toInstant()),
                java.sql.Timestamp.from(sourceHourUtc.plusHours(1).toInstant()));

        assertEquals(1, rows.size(),
                "All three same-UTC-hour readings must aggregate into exactly ONE bucket "
                + "(a session-timezone truncation would wrongly split them into two :30 buckets).");

        java.time.Instant bucket = toInstant(rows.get(0).get("aggregated_hour"));
        assertEquals(sourceHourUtc.toInstant(), bucket,
                "aggregated_hour must be the whole UTC hour 2026-02-03T14:00:00Z, not a :30 "
                + "offset bucket. Got: " + bucket);

        // Sum of the three readings (10.0 * 3) must be present in that single bucket.
        assertEquals(30.0, ((Number) rows.get(0).get("total_kw_consumed")).doubleValue(), 0.001,
                "The single UTC bucket must contain the SUM of all three readings.");

        // Cleanup this test's extra rows.
        jdbcTemplate.update("DELETE FROM zone_hourly_aggregates WHERE zone_id = ? AND aggregated_hour = ?",
                TEST_ZONE_ID, java.sql.Timestamp.from(sourceHourUtc.toInstant()));
        jdbcTemplate.update("DELETE FROM metrics_history WHERE meter_id = ? AND recorded_at >= ? AND recorded_at < ?",
                TEST_METER_ID,
                java.sql.Timestamp.from(sourceHourUtc.toInstant()),
                java.sql.Timestamp.from(sourceHourUtc.plusHours(1).toInstant()));
    }

    private static java.time.Instant toInstant(Object ts) {
        if (ts == null) return null;
        if (ts instanceof java.sql.Timestamp t) return t.toInstant();
        if (ts instanceof java.time.OffsetDateTime o) return o.toInstant();
        if (ts instanceof java.time.ZonedDateTime z) return z.toInstant();
        return java.time.Instant.parse(ts.toString());
    }
}
