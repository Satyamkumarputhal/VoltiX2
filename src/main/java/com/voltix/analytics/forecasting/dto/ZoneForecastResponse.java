package com.voltix.analytics.forecasting.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-only API representation of the latest available 2-hour load forecast
 * for a single zone.
 *
 * Timestamp semantics (mirrors the persisted contract in zone_hourly_aggregates,
 * established by migration V9):
 *   - aggregatedHour       = SOURCE hour whose actual telemetry was aggregated.
 *   - forecastKw2h         = predicted load FOR forecastTargetHour (NOT the
 *                            actual load at aggregatedHour).
 *   - forecastTargetHour   = the future hour the forecast predicts. Read
 *                            DIRECTLY from the persisted forecast_target_hour
 *                            column; never derived as aggregatedHour + 2h here.
 *   - forecastGeneratedAt  = wall-clock time the forecast row was written.
 *
 * tenantId is intentionally NOT exposed: the endpoint is already tenant-scoped
 * by the authenticated caller's token, so returning the tenant id would leak an
 * internal identifier without adding value.
 */
public record ZoneForecastResponse(
        Long zoneId,
        OffsetDateTime aggregatedHour,
        OffsetDateTime forecastTargetHour,
        BigDecimal forecastKw2h,
        OffsetDateTime forecastGeneratedAt
) {
}
