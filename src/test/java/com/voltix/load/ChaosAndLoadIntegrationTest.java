package com.voltix.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltix.telemetry.dto.TelemetryPacket;
import com.voltix.persistence.MetricsBatchWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IMPORTANT: this test runs against the same Postgres instance/database used
 * by local dev (localhost:5433/voltix_grid) -- there is no separate test
 * datasource configured for this project. The @BeforeEach used to
 * unconditionally DELETE FROM smart_meters/grid_zones/users/tenants (wiping
 * ALL rows, not just its own) and replace them with a minimal one-row
 * fixture (no users at all). Every "mvn test" run silently destroyed the
 * dev/demo seed data (operator/inspector/admin users, SM-0..SM-4 demo
 * meters, Zone A/B), causing repeated "stat cards all zero / 503 on demo
 * trigger" incidents in the running dashboard afterward. A naive
 * @Transactional fix was tried and reverted: the HTTP request under test is
 * processed by an async worker on a different thread/connection than the
 * test method's transaction, so it can't see the test's uncommitted seed
 * rows, and the test times out waiting for processing that can never happen.
 * <p>
 * Fixed instead by scoping this test's fixture to its own dedicated
 * tenant/zone/meter IDs (well outside the range used by real dev/demo seed
 * data) and only ever deleting/inserting those specific rows -- never a
 * blanket DELETE FROM affecting other tenants' data. This is committed (not
 * transactional, since the async worker needs to see it), but it no longer
 * touches anything outside its own fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ChaosAndLoadIntegrationTest {

    // Dedicated fixture IDs for this test only -- chosen well outside the
    // range used by real dev/demo seed data (tenants 1-2, zones 1-2,
    // meters SM-0..SM-4) so this test can never collide with or delete them.
    private static final long TEST_TENANT_ID = 999_001L;
    private static final long TEST_ZONE_ID = 999_001L;
    private static final String TEST_METER_ID = "SM-CHAOS-TEST-999001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MetricsBatchWriter batchWriter;

    @BeforeEach
    void setUp() {
        // Only ever touches this test's own dedicated fixture rows (see IDs
        // above) -- never a blanket DELETE affecting other tenants/zones/
        // meters/users. Order matters: system_alerts and metrics_history/
        // telemetry_staging FK-reference smart_meters, so they must be
        // deleted first (a prior run's telemetry can trigger a real anomaly
        // alert for this meter, which would otherwise block the meter delete).
        jdbcTemplate.update("DELETE FROM system_alerts WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM metrics_history WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM telemetry_staging WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id = ?", TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?", TEST_TENANT_ID);

        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "Chaos Test Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_ID, TEST_TENANT_ID, "Chaos Test Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void testEndToEndTelemetryIngestAndFallbackProcessing() throws Exception {
        TelemetryPacket packet = new TelemetryPacket();
        packet.setMeterId(TEST_METER_ID);
        packet.setVoltage(230.0);
        packet.setCurrent(10.0);
        packet.setKwConsumed(2.3);
        packet.setRecordedAt(ZonedDateTime.now());
        packet.setTenantId(TEST_TENANT_ID);
        packet.setZoneId(TEST_ZONE_ID);
        packet.setTransactionId(UUID.randomUUID());

        // 1. Submit packet via HTTP Ingress
        mockMvc.perform(post("/api/v1/telemetry/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(packet)))
                .andExpect(status().isAccepted());

        // 2. Poll and flush until staging record is marked processed (up to 30 iterations * 200ms = 6s timeout)
        // Scoped to this test's own meter -- other tests/dev traffic may have
        // unrelated unprocessed staging rows, which must not affect this check.
        boolean processed = false;
        for (int i = 0; i < 30; i++) {
            batchWriter.flush(true);
            Integer unprocessedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM telemetry_staging WHERE processed = FALSE AND meter_id = ?",
                Integer.class, TEST_METER_ID);
            if (unprocessedCount != null && unprocessedCount == 0) {
                processed = true;
                break;
            }
            Thread.sleep(200);
        }
        assertTrue(processed, "Telemetry processing timed out");

        // 3. Verify metrics are in metrics_history (scoped to this test's own meter)
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM metrics_history WHERE meter_id = ?", Integer.class, TEST_METER_ID);
        assertTrue(historyCount != null && historyCount == 1, "Metrics history table should have 1 record");
    }
}
