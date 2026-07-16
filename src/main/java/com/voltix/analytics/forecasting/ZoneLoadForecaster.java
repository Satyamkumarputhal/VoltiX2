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

            Double avgTemp = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(AVG(avg_temperature), 15.0)
                      FROM zone_hourly_aggregates
                     WHERE tenant_id = ? AND zone_id = ?
                       AND aggregated_hour >= ?
                    """, Double.class, tenantId, zoneId, ZonedDateTime.now().minusHours(24));

            Double lag1h = jdbcTemplate.queryForObject("""
                    SELECT total_kw_consumed
                      FROM zone_hourly_aggregates
                     WHERE tenant_id = ? AND zone_id = ?
                       AND aggregated_hour <= ?
                     ORDER BY aggregated_hour DESC
                     LIMIT 1
                    """, Double.class, tenantId, zoneId, targetTime.minusHours(1));

            Double lag2h = jdbcTemplate.queryForObject("""
                    SELECT total_kw_consumed
                      FROM zone_hourly_aggregates
                     WHERE tenant_id = ? AND zone_id = ?
                       AND aggregated_hour <= ?
                     ORDER BY aggregated_hour DESC
                     LIMIT 1
                    """, Double.class, tenantId, zoneId, targetTime.minusHours(2));

            if (lag1h == null || lag2h == null) {
                log.warn("Insufficient historical data for lag features (zoneId={}). Using heuristic.", zoneId);
                return evaluateHeuristic(tenantId, zoneId, targetTime);
            }

            float tempVal = avgTemp != null ? avgTemp.floatValue() : 15.0f;
            float hourVal = (float) targetTime.getHour();
            float dayVal = (float) (targetTime.getDayOfWeek().getValue() - 1);
            float lag1Val = lag1h.floatValue();
            float lag2Val = lag2h.floatValue();

            String inputName = session.getInputNames().iterator().next();
            float[][] features = new float[][] { { hourVal, dayVal, tempVal, lag1Val, lag2Val } };

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
            Double avgKw = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(AVG(total_kw_consumed), 1.5)
                      FROM zone_hourly_aggregates
                     WHERE tenant_id = ? AND zone_id = ?
                       AND aggregated_hour >= ?
                    """, Double.class, tenantId, zoneId, ZonedDateTime.now().minusHours(24));

            double prediction = (avgKw != null ? avgKw : 1.5) * 1.10;
            return BigDecimal.valueOf(prediction);
        } catch (Exception e) {
            log.debug("Failed to calculate heuristic load prediction for zoneId={}. Defaulting to base load.", zoneId,
                    e);
            return BigDecimal.valueOf(2.50);
        }
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
