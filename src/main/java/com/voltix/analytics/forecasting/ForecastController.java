package com.voltix.analytics.forecasting;

import com.voltix.analytics.forecasting.dto.ZoneForecastResponse;
import com.voltix.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Read-only REST API for Track 2 load forecasts.
 *
 * Exposes the latest available 2-hour load forecast for each zone the
 * authenticated caller's tenant is authorized to access.
 *
 * Authorization / tenant isolation:
 *   - Restricted to OPERATOR / ADMIN roles (same convention as other operator
 *     endpoints), enforced by @PreAuthorize + @EnableMethodSecurity.
 *   - The tenant is taken from the authenticated caller's JWT via
 *     TenantContext (populated by JwtAuthenticationFilter). The client CANNOT
 *     supply a tenantId; the query is always scoped to the caller's own tenant,
 *     so one tenant can never read another tenant's forecasts.
 */
@RestController
@RequestMapping("/api/v1/forecasts")
public class ForecastController {

    private static final Logger log = LoggerFactory.getLogger(ForecastController.class);

    private final JdbcTemplate jdbcTemplate;
    private final LoadForecastScheduler loadForecastScheduler;

    public ForecastController(JdbcTemplate jdbcTemplate, LoadForecastScheduler loadForecastScheduler) {
        this.jdbcTemplate = jdbcTemplate;
        this.loadForecastScheduler = loadForecastScheduler;
    }

    /**
     * Returns the latest available forecast for each accessible zone.
     *
     * "Latest" = the forecast row with the greatest forecast_generated_at per
     * (tenant_id, zone_id). Rows without a forecast (forecast_kw_2h IS NULL) are
     * excluded. forecast_target_hour is read directly from the persisted column,
     * never recomputed from aggregated_hour.
     *
     * @return HTTP 200 with a (possibly empty) list of per-zone forecasts.
     */
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<ZoneForecastResponse>> getLatestForecasts() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            // No tenant context means we cannot safely scope the query; return an
            // empty list rather than risk exposing cross-tenant data.
            log.warn("getLatestForecasts called with no tenant context; returning empty list.");
            return ResponseEntity.ok(Collections.emptyList());
        }

        // One efficient query: pick the single latest forecast per zone for this
        // tenant using a window function. Ties on forecast_generated_at are broken
        // by the most recent aggregated_hour for determinism.
        List<ZoneForecastResponse> forecasts = jdbcTemplate.query("""
                SELECT zone_id, aggregated_hour, forecast_target_hour,
                       forecast_kw_2h, forecast_generated_at
                  FROM (
                        SELECT zone_id, aggregated_hour, forecast_target_hour,
                               forecast_kw_2h, forecast_generated_at,
                               ROW_NUMBER() OVER (
                                   PARTITION BY zone_id
                                   ORDER BY forecast_generated_at DESC, aggregated_hour DESC
                               ) AS rn
                          FROM zone_hourly_aggregates
                         WHERE tenant_id = ?
                           AND forecast_kw_2h IS NOT NULL
                       ) ranked
                 WHERE rn = 1
                 ORDER BY zone_id
                """,
                (rs, rowNum) -> new ZoneForecastResponse(
                        rs.getLong("zone_id"),
                        toOffsetDateTime(rs.getTimestamp("aggregated_hour")),
                        toOffsetDateTime(rs.getTimestamp("forecast_target_hour")),
                        rs.getBigDecimal("forecast_kw_2h"),
                        toOffsetDateTime(rs.getTimestamp("forecast_generated_at"))
                ),
                tenantId);

        log.info("Returning {} zone forecast(s) for tenantId={}", forecasts.size(), tenantId);
        return ResponseEntity.ok(forecasts);
    }

    /**
     * On-demand forecast generation.
     *
     * The hourly {@link LoadForecastScheduler} cron only fires at the top of
     * each clock hour AND only while the application process is alive at that
     * instant, aggregating strictly the PREVIOUS hour. In dev/demo — where the
     * process runs intermittently and telemetry is injected ad hoc — that cron
     * rarely coincides with populated data, leaving forecasts stale.
     *
     * This endpoint lets an authenticated OPERATOR/ADMIN run the SAME
     * aggregation + forecast logic now, for the CURRENT clock hour, so freshly
     * injected telemetry is turned into a forecast immediately. It does not
     * change the ML model, exact-lag semantics, Model B, or the persistence
     * contract — it simply invokes the shared scheduler body for the current
     * hour instead of waiting for the cron.
     *
     * The run is global (aggregation groups by tenant/zone across all telemetry
     * in the hour); the subsequent GET remains tenant-scoped by JWT.
     *
     * @return HTTP 200 with the source hour processed and the number of zone
     *         forecasts generated.
     */
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runForecastNow() {
        ZonedDateTime currentHour = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        log.info("On-demand forecast run requested for current hour {}", currentHour);

        int generated = loadForecastScheduler.aggregateAndForecast(currentHour);

        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "sourceHour", currentHour.toOffsetDateTime().toString(),
                "forecastsGenerated", generated,
                "message", generated == 0
                        ? "No telemetry available for the current hour; no forecast generated."
                        : String.format("%d zone forecast(s) generated for the current hour.", generated)
        ));
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
