package com.voltix.analytics.forecasting;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.voltix.analytics.onnx.OnnxSessionPool;
import com.voltix.platform.config.VoltixProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that the DEFAULT production configuration now loads the 4-feature
 * Model B — WITHOUT any test-only property override.
 *
 * Unlike ZoneLoadForecasterModelBTest, this class does NOT use
 * @TestPropertySource. It relies entirely on the value in application.yml
 * (voltix.analytics.load-forecaster-model = classpath:models/load_forecaster.onnx).
 * If the promotion succeeded, that classpath resource is now the 4-feature
 * Model B and a full ONNX inference must succeed through it.
 *
 * Fixture IDs use 999_009 (unused by any other test).
 */
@SpringBootTest
@DirtiesContext
class DefaultConfigModelBPromotionTest {

    @Autowired private ZoneLoadForecaster forecaster;
    @Autowired private OnnxSessionPool loadForecasterSessionPool;
    @Autowired private OrtEnvironment ortEnvironment;
    @Autowired private ResourceLoader resourceLoader;
    @Autowired private VoltixProperties properties;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final long TEST_TENANT_ID = 999_009L;
    private static final long TEST_ZONE_ID   = 999_009L;
    private static final String TEST_METER_ID = "METER-PROMOTE-TEST-999009";

    private static final String ONNX_BRANCH_MARKER = "Evaluating Track 2 Macro Load Monitor via ONNX";
    private static final String FALLBACK_LAG_MARKER = "Exact lag aggregate missing";
    private static final String ONNX_FAILED_MARKER  = "ONNX load forecasting inference failed";

    private ListAppender<ILoggingEvent> logAppender;
    private Logger forecasterLogger;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS metrics_history_bootstrap");
        cleanFixtures();

        forecasterLogger = (Logger) LoggerFactory.getLogger(ZoneLoadForecaster.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        forecasterLogger.addAppender(logAppender);
        forecasterLogger.setLevel(Level.DEBUG);

        jdbcTemplate.update(
                "INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "Promote Test Tenant");
        jdbcTemplate.update(
                "INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_ID, TEST_TENANT_ID, "Promote Test Zone");
        jdbcTemplate.update(
                "INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);
    }

    @AfterEach
    void tearDown() {
        if (forecasterLogger != null && logAppender != null) {
            forecasterLogger.detachAppender(logAppender);
            logAppender.stop();
        }
        cleanFixtures();
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM zone_hourly_aggregates WHERE zone_id = ?", TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE zone_id = ?",          TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM metrics_history WHERE zone_id = ?",        TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM telemetry_staging WHERE zone_id = ?",      TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE zone_id = ?",           TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id = ?",             TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?",              TEST_TENANT_ID);
    }

    /**
     * The default configured resource must be the classpath production model
     * AND it must declare exactly 4 inputs (Model B). We open the resource the
     * same way the application does and inspect the ONNX input shape.
     */
    @Test
    void defaultConfiguredModelResource_isFourFeatureModelB() throws Exception {
        String configuredPath = properties.getAnalytics().getLoadForecasterModel();
        assertEquals("classpath:models/load_forecaster.onnx", configuredPath,
                "Default config must still point at the classpath production resource.");

        Resource modelResource = resourceLoader.getResource(configuredPath);
        assertTrue(modelResource.exists(), "Configured production model resource must exist.");

        byte[] bytes = modelResource.getInputStream().readAllBytes();
        try (OrtSession session = ortEnvironment.createSession(bytes, new OrtSession.SessionOptions())) {
            String inputName = session.getInputNames().iterator().next();
            ai.onnxruntime.NodeInfo info = session.getInputInfo().get(inputName);
            long[] shape = ((ai.onnxruntime.TensorInfo) info.getInfo()).getShape();
            assertEquals(2, shape.length, "Input tensor must be 2-D (batch × features).");
            assertEquals(4, shape[1],
                    "Default production model must be the 4-feature Model B, not " + shape[1] + "-feature.");
        }
    }

    /**
     * End-to-end: with exact lag rows present, the forecaster loads the DEFAULT
     * classpath resource and performs a successful ONNX inference (no fallback,
     * no inference failure). This proves Model B works via default config.
     */
    @Test
    void defaultConfig_onnxInferenceSucceeds_withExactLags() {
        assertTrue(loadForecasterSessionPool.available(),
                "Default-config ONNX pool must be available (classpath Model B loaded).");

        ZonedDateTime now        = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime targetTime = now.plusHours(2); // anchor T = targetTime - 2h = now
        // Anchor-relative exact lag rows (Option A): lag_1h at T-1h, lag_2h at T-2h.
        insertAggregate(now.minusHours(1), 6.0); // lag_1h (T-1h)
        insertAggregate(now.minusHours(2), 8.0); // lag_2h (T-2h)

        logAppender.list.clear();
        BigDecimal forecast = forecaster.predictLoad2h(TEST_TENANT_ID, TEST_ZONE_ID, targetTime);

        assertNotNull(forecast);
        assertTrue(forecast.doubleValue() > 0.0 && Double.isFinite(forecast.doubleValue()));

        boolean enteredOnnx = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains(ONNX_BRANCH_MARKER));
        boolean fellBackForLag = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains(FALLBACK_LAG_MARKER));
        boolean onnxFailed = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains(ONNX_FAILED_MARKER));

        assertTrue(enteredOnnx, "ONNX branch must be entered via the default classpath resource.");
        assertFalse(fellBackForLag, "Exact lags are present, so no missing-lag fallback should occur.");
        assertFalse(onnxFailed,
                "ONNX inference must NOT fail — a 5-feature model would fail on a [1,4] tensor. "
                + "Success here proves the promoted classpath model is the 4-feature Model B.");
    }

    private void insertAggregate(ZonedDateTime hour, double kw) {
        jdbcTemplate.update("""
                INSERT INTO zone_hourly_aggregates
                    (tenant_id, zone_id, aggregated_hour, total_kw_consumed, avg_voltage, avg_current_amp)
                VALUES (?, ?, ?, ?, 230.0, 5.0)
                ON CONFLICT (tenant_id, zone_id, aggregated_hour)
                DO UPDATE SET total_kw_consumed = EXCLUDED.total_kw_consumed
                """,
                TEST_TENANT_ID, TEST_ZONE_ID, java.sql.Timestamp.from(hour.toInstant()), kw);
    }
}
