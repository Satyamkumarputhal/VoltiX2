package com.voltix.analytics.forecasting;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.voltix.analytics.onnx.OnnxSessionPool;
import com.voltix.platform.config.VoltixProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.OptionalDouble;

@Service
public class ZoneLoadForecaster {
    private static final Logger log = LoggerFactory.getLogger(ZoneLoadForecaster.class);

    private final JdbcTemplate jdbcTemplate;
    private final VoltixProperties properties;
    private final ResourceLoader resourceLoader;
    private final OrtEnvironment environment;
    private final OnnxSessionPool loadForecasterSessionPool;

    public ZoneLoadForecaster(JdbcTemplate jdbcTemplate,
            VoltixProperties properties,
            ResourceLoader resourceLoader,
            OrtEnvironment environment,
            @Qualifier("loadForecasterSessionPool") OnnxSessionPool loadForecasterSessionPool) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.environment = environment;
        this.loadForecasterSessionPool = loadForecasterSessionPool;
    }

    public BigDecimal predictLoad2h(Long tenantId, Long zoneId, ZonedDateTime targetTime) {
        Resource modelResource = resourceLoader.getResource(properties.getAnalytics().getLoadForecasterModel());
        if (modelResource.exists() && loadForecasterSessionPool.available()) {
            log.info("Evaluating Track 2 Macro Load Monitor via ONNX for zoneId={}", zoneId);
            return evaluateWithOnnx(tenantId, zoneId, targetTime);
        } else {
            log.warn("ONNX load forecaster model session not available. Using aggregate heuristic fallback.");
            return evaluateHeuristic(tenantId, zoneId, targetTime);
        }
    }

    private BigDecimal evaluateWithOnnx(Long tenantId, Long zoneId, ZonedDateTime targetTime) {
        OrtSession session = null;
        try {
            session = loadForecasterSessionPool.borrowSession().orElse(null);
            if (session == null) {
                return evaluateHeuristic(tenantId, zoneId, targetTime);
            }

            // TEMPORAL ALIGNMENT (Option A fix):
            //   The forecast is generated at the SOURCE/ANCHOR hour T and predicts
            //   load at the TARGET hour T+2h. The scheduler passes targetTime = T+2h,
            //   so the anchor hour is recovered as:
            //       sourceHour (T) = targetTime - 2h
            //
            //   The trained Model B associates, for each training row anchored at t:
            //       hour_of_day/day_of_week = calendar(t)
            //       lag_1h = load(t - 1h)
            //       lag_2h = load(t - 2h)
            //       target = load(t + 2h)
            //   To stay consistent with training AND to only use data available at
            //   generation time T, ALL features are anchored to T (never to the
            //   target). Concretely, for target T+2h:
            //       hour_of_day/day_of_week = calendar(T)          (= targetTime - 2h)
            //       lag_1h = load(T - 1h)                          (= targetTime - 3h)
            //       lag_2h = load(T - 2h)                          (= targetTime - 4h)
            //   Every feature timestamp is <= T, so no future telemetry (data later
            //   than the generation hour) is ever read. The previous code anchored
            //   lags/calendar to targetTime, which required load at T+1h (future,
            //   unavailable at generation) and shifted every feature +2h vs training.
            //
            // Model B feature contract (4 features, index-ordered):
            //   [0] hour_of_day  — hour component of the ANCHOR hour T (0–23)
            //   [1] day_of_week  — ISO day-of-week of T minus 1, Mon=0 … Sun=6
            //   [2] lag_1h       — total_kw_consumed at EXACTLY T - 1h
            //   [3] lag_2h       — total_kw_consumed at EXACTLY T - 2h
            // Temperature has been removed from the feature vector.
            // avg_temperature is not queried here; no fabricated constant is substituted.
            //
            // TEMPORAL CORRECTNESS: lag lookups use EXACT timestamp equality
            // (aggregated_hour = ?), NOT a nearest-at-or-before scan
            // (aggregated_hour <= ? ORDER BY DESC LIMIT 1). A nearest-before scan
            // would silently substitute a stale row and feed the model data that is
            // older than it believes the lag to be. With exact equality, a missing
            // T-1h or T-2h aggregate yields no row, and we route to the existing
            // heuristic fallback rather than fabricating or substituting a value.
            //
            // Bind timestamps as java.sql.Timestamp. The PostgreSQL JDBC driver
            // cannot infer a SQL type for a raw java.time.ZonedDateTime bind
            // parameter, so we convert to Timestamp here (matching how
            // LoadForecastScheduler binds all of its timestamp parameters).
            ZonedDateTime sourceHour = targetTime.minusHours(2); // anchor hour T
            java.sql.Timestamp lag1Exact = java.sql.Timestamp.from(sourceHour.minusHours(1).toInstant()); // T-1h
            java.sql.Timestamp lag2Exact = java.sql.Timestamp.from(sourceHour.minusHours(2).toInstant()); // T-2h

            Double lag1h = queryExactLag(tenantId, zoneId, lag1Exact);
            Double lag2h = queryExactLag(tenantId, zoneId, lag2Exact);

            if (lag1h == null || lag2h == null) {
                log.warn("Exact lag aggregate missing for zoneId={} (lag_1h present={}, lag_2h present={}). "
                        + "Using heuristic.", zoneId, lag1h != null, lag2h != null);
                return evaluateHeuristic(tenantId, zoneId, targetTime);
            }

            float hourVal = (float) sourceHour.getHour();
            float dayVal  = (float) (sourceHour.getDayOfWeek().getValue() - 1);
            float lag1Val = lag1h.floatValue();
            float lag2Val = lag2h.floatValue();

            String inputName = session.getInputNames().iterator().next();
            float[][] features = new float[][] { { hourVal, dayVal, lag1Val, lag2Val } };

            try (OnnxTensor tensor = OnnxTensor.createTensor(environment, features);
                    OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
                OptionalDouble parsedVal = parseFirstNumber(result.get(0).getValue());
                if (parsedVal.isPresent()) {
                    return BigDecimal.valueOf(parsedVal.getAsDouble());
                }
            }
            return evaluateHeuristic(tenantId, zoneId, targetTime);
        } catch (Exception ex) {
            log.warn("ONNX load forecasting inference failed. Falling back to heuristic.", ex);
            return evaluateHeuristic(tenantId, zoneId, targetTime);
        } finally {
            loadForecasterSessionPool.returnSession(session);
        }
    }

    private BigDecimal evaluateHeuristic(Long tenantId, Long zoneId, ZonedDateTime targetTime) {
        try {
            // Bind as java.sql.Timestamp (the PG driver cannot infer a SQL type
            // from a raw ZonedDateTime). The fallback formula is unchanged.
            java.sql.Timestamp windowStart =
                    java.sql.Timestamp.from(ZonedDateTime.now().minusHours(24).toInstant());

            Double avgKw = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(AVG(total_kw_consumed), 1.5)
                      FROM zone_hourly_aggregates
                     WHERE tenant_id = ? AND zone_id = ?
                       AND aggregated_hour >= ?
                    """, Double.class, tenantId, zoneId, windowStart);

            double prediction = (avgKw != null ? avgKw : 1.5) * 1.10;
            return BigDecimal.valueOf(prediction);
        } catch (Exception e) {
            log.debug("Failed to calculate heuristic load prediction for zoneId={}. Defaulting to base load.", zoneId,
                    e);
            return BigDecimal.valueOf(2.50);
        }
    }

    /**
     * Returns total_kw_consumed for the aggregate at EXACTLY {@code exactHour},
     * or {@code null} if no aggregate exists at that precise hour.
     *
     * Uses aggregated_hour = ? (exact equality) — never a nearest-at-or-before
     * scan. A list query with {@code findFirst} is used instead of
     * {@code queryForObject} so that an absent row returns null rather than
     * throwing EmptyResultDataAccessException. The (tenant_id, zone_id,
     * aggregated_hour) primary key guarantees at most one matching row.
     */
    private Double queryExactLag(Long tenantId, Long zoneId, java.sql.Timestamp exactHour) {
        return jdbcTemplate.query("""
                SELECT total_kw_consumed
                  FROM zone_hourly_aggregates
                 WHERE tenant_id = ? AND zone_id = ?
                   AND aggregated_hour = ?
                """,
                (rs, rowNum) -> {
                    // total_kw_consumed is NUMERIC(14,4). Read as BigDecimal
                    // (the driver-safe mapping) and convert; return null for
                    // a SQL NULL value.
                    java.math.BigDecimal v = rs.getBigDecimal("total_kw_consumed");
                    return v == null ? null : v.doubleValue();
                },
                tenantId, zoneId, exactHour)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private OptionalDouble parseFirstNumber(Object value) {
        if (value instanceof Number number) {
            return OptionalDouble.of(number.doubleValue());
        }
        if (value != null && value.getClass().isArray() && Array.getLength(value) > 0) {
            return parseFirstNumber(Array.get(value, 0));
        }
        return OptionalDouble.empty();
    }
}
