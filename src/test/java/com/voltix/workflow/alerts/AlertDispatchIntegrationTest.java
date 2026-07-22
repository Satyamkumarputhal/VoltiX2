package com.voltix.workflow.alerts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DirtiesContext
class AlertDispatchIntegrationTest {

    @Autowired
    private AlertDispatchService alertDispatchService;

    @Autowired
    private SystemAlertRepository alertRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Dedicated fixture IDs for this test only -- chosen well outside the
    // range used by real dev/demo seed data (tenants 1-2, zones 1-2, meters
    // SM-0..SM-4, users operator/inspector/admin) so this test can never
    // collide with or delete them. This test used to unconditionally
    // DELETE FROM smart_meters/grid_zones/users/tenants (ALL rows), which
    // silently destroyed the shared dev database's seed data on every
    // "mvn test" run.
    private static final long TEST_TENANT_ID = 999_004L;
    private static final long TEST_ZONE_ID = 999_004L;
    private static final String TEST_METER_ID = "METER-ALERT-TEST-999004";

    @BeforeEach
    void setUp() {
        // Only ever touches this test's own dedicated fixture rows -- never
        // a blanket DELETE affecting other tenants/zones/meters/users.
        jdbcTemplate.update("DELETE FROM system_alerts WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM metrics_history WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM telemetry_staging WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id = ?", TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?", TEST_TENANT_ID);

        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "Alert Test Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 2.50)",
                TEST_ZONE_ID, TEST_TENANT_ID, "Alert Test High Risk Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);
    }

    @Test
    void testAlertCreationAndScoring() {
        SystemAlert alert = alertDispatchService.createAlert(TEST_TENANT_ID, TEST_METER_ID, TEST_ZONE_ID, "NTL_ANOMALY", 0.85);

        assertNotNull(alert.getAlertId());
        assertEquals("NTL_ANOMALY", alert.getAlertType());

        // Expected Anomaly Score: 0.85000
        BigDecimal expectedAnomalyScore = BigDecimal.valueOf(0.85).setScale(5, RoundingMode.HALF_UP);
        assertEquals(expectedAnomalyScore, alert.getAnomalyScore());

        // Expected Priority Score: 0.85 * 2.50 = 2.1250
        BigDecimal expectedPriorityScore = BigDecimal.valueOf(2.125).setScale(4, RoundingMode.HALF_UP);
        assertEquals(expectedPriorityScore, alert.getPriorityScore());

        // Priority Score is 2.1250 which is between 1.0 and 2.5, so severity should be MEDIUM
        assertEquals(AlertSeverity.MEDIUM, alert.getSeverity());
        assertEquals(AlertStatus.OPEN, alert.getStatus());

        // Verify stored in DB (scoped to this test's own meter, since other
        // tests/dev traffic may have unrelated alerts in the shared database)
        List<SystemAlert> stored = alertRepository.findAll().stream()
                .filter(a -> TEST_METER_ID.equals(a.getMeterId()))
                .toList();
        assertEquals(1, stored.size());
        assertEquals(alert.getAlertId(), stored.get(0).getAlertId());
    }
}
