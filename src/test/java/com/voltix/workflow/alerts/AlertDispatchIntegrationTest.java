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

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM system_alerts");
        jdbcTemplate.execute("DELETE FROM public_complaints");
        jdbcTemplate.execute("DELETE FROM zone_hourly_aggregates");
        jdbcTemplate.execute("DELETE FROM metrics_history");
        jdbcTemplate.execute("DELETE FROM telemetry_staging");
        jdbcTemplate.execute("DELETE FROM smart_meters");
        jdbcTemplate.execute("DELETE FROM grid_zones");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenants");

        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (1, 'Test Tenant', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (1, 1, 'High Risk Zone', 2.50)");
        jdbcTemplate.execute("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES ('METER-001', 1, 1, 'SN-001', 'ACTIVE')");
    }

    @Test
    void testAlertCreationAndScoring() {
        SystemAlert alert = alertDispatchService.createAlert(1L, "METER-001", 1L, "NTL_ANOMALY", 0.85);

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

        // Verify stored in DB
        List<SystemAlert> stored = alertRepository.findAll();
        assertEquals(1, stored.size());
        assertEquals(alert.getAlertId(), stored.get(0).getAlertId());
    }
}
