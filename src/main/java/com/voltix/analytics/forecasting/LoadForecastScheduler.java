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

    @Scheduled(cron = "0 0 * * * *") // Runs at the start of every hour
    @Transactional
    public void runHourlyAggregationAndForecasting() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime start = now.minusHours(1);

        log.info("Starting hourly aggregation and load forecasting for period: {} to {}", start, now);

        // 1. Aggregate metrics from metrics_history into zone_hourly_aggregates
        jdbcTemplate.update("""
                INSERT INTO zone_hourly_aggregates
                    (tenant_id, zone_id, aggregated_hour, total_kw_consumed, avg_voltage, avg_current_amp)
                SELECT tenant_id, zone_id, date_trunc('hour', recorded_at) as hour,
                       SUM(kw_consumed), AVG(voltage), AVG(current_amp)
                  FROM metrics_history
                 WHERE recorded_at >= ? AND recorded_at < ?
                 GROUP BY tenant_id, zone_id, date_trunc('hour', recorded_at)
                    ON CONFLICT (tenant_id, zone_id, aggregated_hour)
                    DO UPDATE SET total_kw_consumed = EXCLUDED.total_kw_consumed,
                                  avg_voltage = EXCLUDED.avg_voltage,
                                  avg_current_amp = EXCLUDED.avg_current_amp
                """, java.sql.Timestamp.from(start.toInstant()), java.sql.Timestamp.from(now.toInstant()));

        // 2. Query all active zone aggregates for the period to calculate forecast
        List<Map<String, Object>> activeZones = jdbcTemplate.queryForList("""
                SELECT tenant_id, zone_id, aggregated_hour
                  FROM zone_hourly_aggregates
                 WHERE aggregated_hour = ?
                """, java.sql.Timestamp.from(start.toInstant()));

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

            // 3. Predict load 2 hours out
            ZonedDateTime forecastTargetTime = hour.plusHours(2);
            BigDecimal forecastKw = loadForecaster.predictLoad2h(tenantId, zoneId, forecastTargetTime);

            // 4. Update the aggregates table with forecast results
            jdbcTemplate.update("""
                    UPDATE zone_hourly_aggregates
                       SET forecast_kw_2h = ?, forecast_generated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND zone_id = ? AND aggregated_hour = ?
                    """, forecastKw, tenantId, zoneId, java.sql.Timestamp.from(hour.toInstant()));

            log.info("Load Forecast for ZoneId={} at target time {}: {} kW", zoneId, forecastTargetTime, forecastKw);
        }

        log.info("Hourly load forecasting job complete.");
    }
}
