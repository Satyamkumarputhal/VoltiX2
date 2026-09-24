package com.voltix.analytics.forecasting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class LoadForecastScheduler {
    private static final Logger log = LoggerFactory.getLogger(LoadForecastScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final ZoneLoadForecaster loadForecaster;

    public LoadForecastScheduler(JdbcTemplate jdbcTemplate, ZoneLoadForecaster loadForecaster) {
        this.jdbcTemplate = jdbcTemplate;
        this.loadForecaster = loadForecaster;
    }

    /**
     * Scheduled hourly job. Aggregates the PREVIOUS clock hour of telemetry and
     * forecasts each active zone. Behaviour is unchanged: sourceHour = now - 1h.
     *
     * NOTE: this only ever runs at the top of the hour AND only while the
     * application process is alive at that instant. For on-demand generation
     * (e.g. after injecting demo telemetry), operators use
     * {@code POST /api/v1/forecasts/run}, which calls
     * {@link #aggregateAndForecast(ZonedDateTime)} for the current hour.
     */
    @Scheduled(cron = "0 0 * * * *") // Runs at the start of every hour
    @Transactional
    public void runHourlyAggregationAndForecasting() {
        // Compute the source hour in UTC (whole UTC hour), consistent with the
        // UTC-aligned aggregation bucket and the on-demand /forecasts/run path.
        // Using the JVM-default zone here would, at a +05:30 offset, produce a
        // :30-past-UTC instant that never matches the whole-UTC-hour aggregate
        // bucket, so no forecast would be written.
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime sourceHour = now.minusHours(1);
        aggregateAndForecast(sourceHour);
    }

    /**
     * Aggregates the single clock hour {@code [sourceHour, sourceHour+1h)} of
     * telemetry into zone_hourly_aggregates, then generates and persists a
     * +2h forecast for every zone that has an aggregate at {@code sourceHour}.
     *
     * This is the shared body used by both the hourly cron and the on-demand
     * REST trigger. It does not change the ML model, exact-lag semantics,
     * Model B behaviour, or the persistence contract — it only makes the
     * source hour a parameter instead of hard-coding {@code now - 1h}.
     *
     * @param sourceHour the hour whose actuals are aggregated (truncated to the hour)
     * @return the number of zone forecasts generated/updated
     */
    @Transactional
    public int aggregateAndForecast(ZonedDateTime sourceHour) {
        ZonedDateTime start = sourceHour.truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime end = start.plusHours(1);

        log.info("Starting aggregation and load forecasting for source hour: {} to {}", start, end);

        // 1. Aggregate metrics from metrics_history into zone_hourly_aggregates.
        //    UTC ALIGNMENT: recorded_at is TIMESTAMP WITH TIME ZONE. A bare
        //    date_trunc('hour', recorded_at) truncates in the DB SESSION timezone
        //    (which follows the JDBC connection's JVM default, e.g. Asia/Kolkata
        //    +05:30). That would bucket UTC telemetry onto :30 UTC boundaries and
        //    never match the scheduler's whole-UTC-hour sourceHour or the
        //    forecaster's exact T-1h/T-2h lookups. We force UTC truncation with:
        //        date_trunc('hour', recorded_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'
        //    - "recorded_at AT TIME ZONE 'UTC'" -> the UTC wall-clock as a
        //      timestamp WITHOUT time zone,
        //    - date_trunc('hour', ...) -> truncated to the whole UTC hour,
        //    - "... AT TIME ZONE 'UTC'" -> reinterpret that UTC wall-clock as a
        //      timestamptz, yielding the exact whole-UTC-hour instant.
        //    This is independent of the session/JVM timezone. The same expression
        //    is used in SELECT and GROUP BY so buckets are consistent.
        jdbcTemplate.update("""
                INSERT INTO zone_hourly_aggregates
                    (tenant_id, zone_id, aggregated_hour, total_kw_consumed, avg_voltage, avg_current_amp)
                SELECT tenant_id, zone_id,
                       date_trunc('hour', recorded_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' as hour,
                       SUM(kw_consumed), AVG(voltage), AVG(current_amp)
                  FROM metrics_history
                 WHERE recorded_at >= ? AND recorded_at < ?
                 GROUP BY tenant_id, zone_id,
                          date_trunc('hour', recorded_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'
                    ON CONFLICT (tenant_id, zone_id, aggregated_hour)
                    DO UPDATE SET total_kw_consumed = EXCLUDED.total_kw_consumed,
                                  avg_voltage = EXCLUDED.avg_voltage,
                                  avg_current_amp = EXCLUDED.avg_current_amp
                """, java.sql.Timestamp.from(start.toInstant()), java.sql.Timestamp.from(end.toInstant()));

        // 2. Query all active zone aggregates for the period to calculate forecast
        List<Map<String, Object>> activeZones = jdbcTemplate.queryForList("""
                SELECT tenant_id, zone_id, aggregated_hour
                  FROM zone_hourly_aggregates
                 WHERE aggregated_hour = ?
                """, java.sql.Timestamp.from(start.toInstant()));

        int generated = 0;
        for (Map<String, Object> zoneMap : activeZones) {
            Long tenantId = ((Number) zoneMap.get("tenant_id")).longValue();
            Long zoneId = ((Number) zoneMap.get("zone_id")).longValue();

            // Safe parsing of aggregated_hour returned as Timestamp or OffsetDateTime
            Object hourObj = zoneMap.get("aggregated_hour");
            java.time.Instant instant;
            if (hourObj instanceof java.sql.Timestamp) {
                instant = ((java.sql.Timestamp) hourObj).toInstant();
            } else if (hourObj instanceof java.time.OffsetDateTime) {
                instant = ((java.time.OffsetDateTime) hourObj).toInstant();
            } else if (hourObj instanceof java.time.ZonedDateTime) {
                instant = ((java.time.ZonedDateTime) hourObj).toInstant();
            } else {
                instant = java.time.Instant.parse(hourObj.toString());
            }
            ZonedDateTime hour = ZonedDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);

            // 3. Predict load 2 hours out.
            //    hour                = aggregated_hour = SOURCE hour of the actuals just aggregated.
            //    forecastTargetTime  = hour + 2h        = the future hour this forecast predicts.
            ZonedDateTime forecastTargetTime = hour.plusHours(2);
            BigDecimal forecastKw = loadForecaster.predictLoad2h(tenantId, zoneId, forecastTargetTime);

            // 4. Persist the forecast with EXPLICIT target-hour semantics:
            //      forecast_kw_2h        = predicted load FOR forecast_target_hour
            //      forecast_target_hour  = hour + 2h (the hour being predicted)
            //      forecast_generated_at = wall-clock time this forecast was written
            //    The forecast is stored on the SOURCE-hour row (aggregated_hour = hour);
            //    forecast_target_hour removes the previous ambiguity where the +2h
            //    offset was implicit in Java code only.
            jdbcTemplate.update("""
                    UPDATE zone_hourly_aggregates
                       SET forecast_kw_2h = ?,
                           forecast_target_hour = ?,
                           forecast_generated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND zone_id = ? AND aggregated_hour = ?
                    """,
                    forecastKw,
                    java.sql.Timestamp.from(forecastTargetTime.toInstant()),
                    tenantId, zoneId,
                    java.sql.Timestamp.from(hour.toInstant()));

            log.info("Load Forecast for ZoneId={} sourceHour={} targetHour={}: {} kW",
                    zoneId, hour, forecastTargetTime, forecastKw);
            generated++;
        }

        log.info("Load forecasting job complete for source hour {}. {} zone forecast(s) generated.",
                start, generated);
        return generated;
    }
}
