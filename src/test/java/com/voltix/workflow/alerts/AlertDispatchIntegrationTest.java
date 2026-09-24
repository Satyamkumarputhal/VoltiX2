package com.voltix.workflow.alerts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        // Severity is now determined from continuousScore (0.85) alone, not priorityScore.
        // continuousScore 0.85 >= 0.63 threshold → CRITICAL
        assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
        assertEquals(AlertStatus.OPEN, alert.getStatus());

        // Verify stored in DB (scoped to this test's own meter, since other
        // tests/dev traffic may have unrelated alerts in the shared database)
        List<SystemAlert> stored = alertRepository.findAll().stream()
                .filter(a -> TEST_METER_ID.equals(a.getMeterId()))
                .toList();
        assertEquals(1, stored.size());
        assertEquals(alert.getAlertId(), stored.get(0).getAlertId());
    }

    @Test
    void testAcknowledgeAlertPersistsInPostgreSQL() {
        // 1. Create a known OPEN alert
        SystemAlert created = alertDispatchService.createAlert(TEST_TENANT_ID, TEST_METER_ID, TEST_ZONE_ID, "NTL_ANOMALY", 0.85);
        Long alertId = created.getAlertId();
        assertNotNull(alertId);
        assertEquals(AlertStatus.OPEN, created.getStatus());
        assertNotNull(created.getDetectedAt());

        // 2. Invoke the same service method used by PATCH /api/v1/alerts/{id}/acknowledge
        SystemAlert acknowledged = alertDispatchService.acknowledgeAlert(alertId, TEST_TENANT_ID);

        // 3. Verify service returns non-null with ACKNOWLEDGED status
        assertNotNull(acknowledged, "acknowledgeAlert returned null - alert not found or not OPEN");
        assertEquals(AlertStatus.ACKNOWLEDGED, acknowledged.getStatus());
        assertNotNull(acknowledged.getResolvedAt(), "resolvedAt should be populated on acknowledge");
        assertEquals(alertId, acknowledged.getAlertId());

        // 4. Verify directly in PostgreSQL via JdbcTemplate (bypassing Hibernate cache)
        String sql = "SELECT status, resolved_at FROM system_alerts WHERE alert_id = ? AND tenant_id = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, alertId, TEST_TENANT_ID);
        assertEquals(1, rows.size(), "Alert row should exist in PostgreSQL");

        Map<String, Object> row = rows.get(0);
        assertEquals("ACKNOWLEDGED", row.get("status"), "PostgreSQL status should be ACKNOWLEDGED");
        assertNotNull(row.get("resolved_at"), "PostgreSQL resolved_at should be populated");

        // 5. Verify resolved_at is a valid timestamp (not null, not epoch)
        ZonedDateTime resolvedAt = ((java.sql.Timestamp) row.get("resolved_at")).toInstant().atZone(java.time.ZoneOffset.UTC);
        assertTrue(resolvedAt.isAfter(created.getDetectedAt()), "resolvedAt should be after detectedAt");
    }

    @Test
    void testAcknowledgeExistingAlertInTenant1() {
        // This test targets the REAL tenant 1 with existing data to reproduce
        // the reported issue: "SELECT returns 0 rows inside Spring transaction
        // despite row existing in DB"
        Long realTenantId = 1L;

        // First, check what OPEN alerts exist for tenant 1 via JdbcTemplate (bypassing Hibernate)
        String findOpenSql = "SELECT alert_id FROM system_alerts WHERE tenant_id = ? AND status = 'OPEN' LIMIT 1";
        List<Map<String, Object>> openAlerts = jdbcTemplate.queryForList(findOpenSql, realTenantId);

        if (openAlerts.isEmpty()) {
            // No OPEN alerts in tenant 1 - create one to test
            // Need a valid meter/zone for tenant 1
            String meterSql = "SELECT meter_id FROM smart_meters WHERE tenant_id = ? AND status = 'ACTIVE' LIMIT 1";
            List<Map<String, Object>> meters = jdbcTemplate.queryForList(meterSql, realTenantId);
            if (meters.isEmpty()) {
                // No meters available for tenant 1, skip this test scenario
                return;
            }
            String meterId = (String) meters.get(0).get("meter_id");
            Long zoneId = ((Number) jdbcTemplate.queryForObject(
                "SELECT zone_id FROM smart_meters WHERE meter_id = ?", Long.class, meterId)).longValue();

            SystemAlert created = alertDispatchService.createAlert(realTenantId, meterId, zoneId, "NTL_ANOMALY", 0.85);
            Long alertId = created.getAlertId();
            assertNotNull(alertId);

            // Now try to acknowledge it
            SystemAlert acknowledged = alertDispatchService.acknowledgeAlert(alertId, realTenantId);
            assertNotNull(acknowledged, "acknowledgeAlert returned null for newly created alert in tenant 1");
            assertEquals(AlertStatus.ACKNOWLEDGED, acknowledged.getStatus());
            assertNotNull(acknowledged.getResolvedAt());
        } else {
            // There are OPEN alerts - try to acknowledge the first one
            Long existingAlertId = ((Number) openAlerts.get(0).get("alert_id")).longValue();

            // Verify it exists in DB via JdbcTemplate
            String verifySql = "SELECT status FROM system_alerts WHERE alert_id = ? AND tenant_id = ?";
            String dbStatus = jdbcTemplate.queryForObject(verifySql, String.class, existingAlertId, realTenantId);
            assertEquals("OPEN", dbStatus, "Pre-condition: alert should be OPEN in DB");

            // Now invoke acknowledgeAlert - this is where the bug reportedly occurs
            SystemAlert acknowledged = alertDispatchService.acknowledgeAlert(existingAlertId, realTenantId);

            // If the bug exists, this will be null (SELECT returned 0 rows)
            assertNotNull(acknowledged, "acknowledgeAlert returned null - SELECT returned 0 rows despite row existing in DB for alertId=" + existingAlertId);
            assertEquals(AlertStatus.ACKNOWLEDGED, acknowledged.getStatus());
            assertNotNull(acknowledged.getResolvedAt());
        }
    }

    @Test
    void testAcknowledgeAfterJpaLoad_StaleHibernateSessionScenario() {
        // This test reproduces the "stale Hibernate session" scenario:
        // 1. Create an alert (goes through JPA/Hibernate)
        // 2. Load it via JPA repository (puts it in Hibernate session/cache)
        // 3. Try to acknowledge via service (uses JdbcTemplate inside @Transactional)
        // This simulates: Dashboard loads alerts via JPA -> User clicks ACK -> Service uses JdbcTemplate
        Long realTenantId = 1L;

        // Need a valid meter/zone for tenant 1
        String meterSql = "SELECT meter_id FROM smart_meters WHERE tenant_id = ? AND status = 'ACTIVE' LIMIT 1";
        List<Map<String, Object>> meters = jdbcTemplate.queryForList(meterSql, realTenantId);
        if (meters.isEmpty()) {
            return; // Skip if no meters
        }
        String meterId = (String) meters.get(0).get("meter_id");
        Long zoneId = ((Number) jdbcTemplate.queryForObject(
            "SELECT zone_id FROM smart_meters WHERE meter_id = ?", Long.class, meterId)).longValue();

        // 1. Create alert via service (uses JPA internally)
        SystemAlert created = alertDispatchService.createAlert(realTenantId, meterId, zoneId, "NTL_ANOMALY", 0.85);
        Long alertId = created.getAlertId();
        assertNotNull(alertId);

        // 2. Load the alert via JPA repository - this puts it in Hibernate session
        SystemAlert jpaLoaded = alertRepository.findById(alertId).orElse(null);
        assertNotNull(jpaLoaded, "JPA should find the alert");
        assertEquals(AlertStatus.OPEN, jpaLoaded.getStatus());

        // 3. Verify it exists in DB via JdbcTemplate (bypassing Hibernate)
        String verifySql = "SELECT status FROM system_alerts WHERE alert_id = ? AND tenant_id = ?";
        String dbStatus = jdbcTemplate.queryForObject(verifySql, String.class, alertId, realTenantId);
        assertEquals("OPEN", dbStatus, "Pre-condition: alert should be OPEN in DB");

        // 4. Now invoke acknowledgeAlert - uses JdbcTemplate inside @Transactional
        // This is where the bug reportedly occurs: JdbcTemplate SELECT returns 0 rows
        // because Hibernate session has a stale/cached view
        SystemAlert acknowledged = alertDispatchService.acknowledgeAlert(alertId, realTenantId);

        // If the bug exists, this will be null (SELECT returned 0 rows)
        assertNotNull(acknowledged, "acknowledgeAlert returned null - SELECT returned 0 rows despite row existing in DB (stale Hibernate session scenario) for alertId=" + alertId);
        assertEquals(AlertStatus.ACKNOWLEDGED, acknowledged.getStatus());
        assertNotNull(acknowledged.getResolvedAt());
    }
}
