package com.voltix.workflow.incident;

import com.voltix.workflow.incident.dto.IncidentCreateRequest;
import com.voltix.workflow.incident.dto.IncidentResponse;
import com.voltix.workflow.incident.dto.IncidentUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext
class IncidentServiceIntegrationTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TEST_TENANT_ID = 999_009L;
    private static final long TEST_ZONE_ID = 999_009L;
    private static final String TEST_METER_ID = "METER-INCIDENT-TEST-999009";
    private static final String TEST_USERNAME = "incident-test-operator";
    private static final String TEST_PASSWORD = "password";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM incidents WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_010L);
        jdbcTemplate.update("DELETE FROM field_jobs WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_010L);
        jdbcTemplate.update("DELETE FROM inspections WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_010L);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_010L);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id = ?", TEST_ZONE_ID);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_010L);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", "test-operator");  // Clean up any leftover from other tests
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_010L);

        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "Incident Test Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 2.50)",
                TEST_ZONE_ID, TEST_TENANT_ID, "Incident Test Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);

        // Insert test user
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, TEST_USERNAME, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "OPERATOR");
    }

    private void createSystemAlert(long alertId) {
        createSystemAlertForTenant(alertId, TEST_TENANT_ID);
    }

    private void createSystemAlertForTenant(long alertId, long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, alertId, tenantId, TEST_METER_ID, TEST_ZONE_ID);
    }

    @Test
    void testCreateIncident() {
        createSystemAlert(1001L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(1001L);
        request.setTitle("Test Incident");
        request.setDescription("Test incident description");
        request.setMeterId(TEST_METER_ID);
        request.setZoneId(TEST_ZONE_ID);
        request.setAlertType("NTL_ANOMALY");
        request.setSeverity("HIGH");

        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);

        assertNotNull(created.getIncidentId());
        assertNotNull(created.getIncidentNumber());
        assertEquals("Test Incident", created.getTitle());
        assertEquals("Test incident description", created.getDescription());
        assertEquals(TEST_TENANT_ID, created.getTenantId());
        assertEquals(1001L, created.getSourceAlertId());
        assertEquals(TEST_METER_ID, created.getMeterId());
        assertEquals(TEST_ZONE_ID, created.getZoneId());
        assertEquals("NTL_ANOMALY", created.getAlertType());
        assertEquals("HIGH", created.getSeverity());
        assertEquals(IncidentStatus.OPEN, created.getStatus());
        assertNotNull(created.getCreatedAt());
        // Note: createdBy is now the userId looked up from username
        assertNotNull(created.getCreatedBy());

        // Verify in DB
        List<Incident> stored = incidentRepository.findByTenantIdOrderByCreatedAtDesc(TEST_TENANT_ID);
        assertEquals(1, stored.size());
        assertEquals(created.getIncidentId(), stored.get(0).getIncidentId());
    }

    @Test
    void testCreateIncident_DuplicateSourceAlertId_ThrowsException() {
        createSystemAlert(1002L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(1002L);
        request.setTitle("Test Incident");
        request.setDescription("Test incident description");

        incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);

        // Try to create another incident with same sourceAlertId
        IncidentCreateRequest duplicateRequest = new IncidentCreateRequest();
        duplicateRequest.setSourceAlertId(1002L);
        duplicateRequest.setTitle("Duplicate Incident");
        duplicateRequest.setDescription("Duplicate description");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, duplicateRequest));
        assertTrue(ex.getMessage().contains("already exists for source alert ID"));
    }

    @Test
    void testGetIncidentsForTenant() {
        // Create multiple incidents
        for (int i = 1; i <= 3; i++) {
            long alertId = 2000L + i;
            createSystemAlert(alertId);
            IncidentCreateRequest request = new IncidentCreateRequest();
            request.setSourceAlertId(alertId);
            request.setTitle("Incident " + i);
            request.setDescription("Description " + i);
            incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);
        }

        List<IncidentResponse> incidents = incidentService.getIncidentsForTenant(TEST_TENANT_ID);
        assertEquals(3, incidents.size());

        // Should be ordered by createdAt DESC (newest first)
        assertTrue(incidents.get(0).getCreatedAt().isAfter(incidents.get(1).getCreatedAt()));
        assertTrue(incidents.get(1).getCreatedAt().isAfter(incidents.get(2).getCreatedAt()));
    }

    @Test
    void testGetIncidentById() {
        createSystemAlert(3001L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(3001L);
        request.setTitle("Specific Incident");
        request.setDescription("Specific description");

        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);

        IncidentResponse fetched = incidentService.getIncidentById(created.getIncidentId(), TEST_TENANT_ID);
        assertEquals(created.getIncidentId(), fetched.getIncidentId());
        assertEquals("Specific Incident", fetched.getTitle());
    }

    @Test
    void testGetIncidentById_NotFound_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> incidentService.getIncidentById(999999L, TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Incident not found"));
    }

    @Test
    void testUpdateIncident_ValidTransitions() {
        createSystemAlert(4001L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(4001L);
        request.setTitle("Transition Test");
        request.setDescription("Testing status transitions");

        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);
        assertEquals(IncidentStatus.OPEN, created.getStatus());

        // OPEN -> ACKNOWLEDGED
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ACKNOWLEDGED);
        IncidentResponse updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.ACKNOWLEDGED, updated.getStatus());
        assertNotNull(updated.getAcknowledgedAt());

        // ACKNOWLEDGED -> ASSIGNED
        updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ASSIGNED);
        updateRequest.setAssignedTo(created.getCreatedBy()); // Use the creator's userId
        updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.ASSIGNED, updated.getStatus());
        assertNotNull(updated.getAssignedAt());
        assertEquals(created.getCreatedBy(), updated.getAssignedTo());

        // ASSIGNED -> IN_PROGRESS
        updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.IN_PROGRESS);
        updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.IN_PROGRESS, updated.getStatus());

        // IN_PROGRESS -> RESOLVED
        updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.RESOLVED);
        updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.RESOLVED, updated.getStatus());
        assertNotNull(updated.getResolvedAt());

        // RESOLVED -> CLOSED
        updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.CLOSED);
        updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.CLOSED, updated.getStatus());
        assertNotNull(updated.getClosedAt());
    }

    @Test
    void testUpdateIncident_InvalidTransition_ThrowsException() {
        createSystemAlert(5001L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(5001L);
        request.setTitle("Invalid Transition Test");
        request.setDescription("Testing invalid transition");

        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);

        // Try OPEN -> RESOLVED (invalid, should go through ACKNOWLEDGED, ASSIGNED, IN_PROGRESS first)
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.RESOLVED);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest));
        assertTrue(ex.getMessage().contains("Invalid status transition from OPEN to RESOLVED"));
    }

    @Test
    void testUpdateIncident_TerminalStates_NoTransitions() {
        createSystemAlert(6001L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(6001L);
        request.setTitle("Terminal State Test");
        request.setDescription("Testing terminal states");

        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);

        // Move to CLOSED
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ACKNOWLEDGED);
        incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        updateRequest.setStatus(IncidentStatus.ASSIGNED);
        incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        updateRequest.setStatus(IncidentStatus.IN_PROGRESS);
        incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        updateRequest.setStatus(IncidentStatus.RESOLVED);
        incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        updateRequest.setStatus(IncidentStatus.CLOSED);
        IncidentResponse closed = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.CLOSED, closed.getStatus());

        // Try to transition from CLOSED -> anything should fail
        IncidentUpdateRequest finalUpdateRequest = new IncidentUpdateRequest();
        finalUpdateRequest.setStatus(IncidentStatus.ESCALATED);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, finalUpdateRequest));
        assertTrue(ex.getMessage().contains("Invalid status transition from CLOSED to ESCALATED"));
    }

    @Test
    void testUpdateIncident_ESCALATED_AllowedFromOpen() {
        createSystemAlert(7001L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(7001L);
        request.setTitle("Escalation Test");
        request.setDescription("Testing escalation from OPEN");

        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);

        // OPEN -> ESCALATED (allowed)
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ESCALATED);
        IncidentResponse updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.ESCALATED, updated.getStatus());
    }

    @Test
    void testUpdateIncident_CANCELLED_AllowedFromOpen() {
        createSystemAlert(8001L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(8001L);
        request.setTitle("Cancellation Test");
        request.setDescription("Testing cancellation from OPEN");

        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);

        // OPEN -> CANCELLED (allowed)
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.CANCELLED);
        IncidentResponse updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);
        assertEquals(IncidentStatus.CANCELLED, updated.getStatus());
    }

    @Test
    void testTenantIsolation_DifferentTenantsCannotAccessEachOthersIncidents() {
        // Create tenant 2
        long tenant2Id = 999_010L;
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                tenant2Id, "Tenant 2b");

        // Create incident for tenant 1
        createSystemAlert(9003L);
        IncidentCreateRequest request1 = new IncidentCreateRequest();
        request1.setSourceAlertId(9003L);
        request1.setTitle("Tenant 1 Incident");
        request1.setDescription("Tenant 1's incident");
        IncidentResponse incident1 = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request1);

        // Create incident for tenant 2 - use a different username to avoid unique constraint
        String tenant2Username = TEST_USERNAME + "-2";
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                tenant2Id, tenant2Username, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "OPERATOR");
        
        createSystemAlertForTenant(9004L, tenant2Id);
        IncidentCreateRequest request2 = new IncidentCreateRequest();
        request2.setSourceAlertId(9004L);
        request2.setTitle("Tenant 2 Incident");
        request2.setDescription("Tenant 2's incident");
        IncidentResponse incident2 = incidentService.createIncident(tenant2Id, tenant2Username, request2);

        // Tenant 1 should only see their incident
        List<IncidentResponse> tenant1Incidents = incidentService.getIncidentsForTenant(TEST_TENANT_ID);
        assertEquals(1, tenant1Incidents.size());
        assertEquals(incident1.getIncidentId(), tenant1Incidents.get(0).getIncidentId());

        // Tenant 2 should only see their incident
        List<IncidentResponse> tenant2Incidents = incidentService.getIncidentsForTenant(tenant2Id);
        assertEquals(1, tenant2Incidents.size());
        assertEquals(incident2.getIncidentId(), tenant2Incidents.get(0).getIncidentId());

        // Tenant 1 cannot access tenant 2's incident
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> incidentService.getIncidentById(incident2.getIncidentId(), TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Incident not found"));

        // Tenant 1 cannot update tenant 2's incident
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ACKNOWLEDGED);
        ex = assertThrows(IllegalArgumentException.class,
                () -> incidentService.updateIncident(incident2.getIncidentId(), TEST_TENANT_ID, updateRequest));
        assertTrue(ex.getMessage().contains("Incident not found"));
    }

    @Test
    void testCreateIncident_SetsTimestampsCorrectly() {
        createSystemAlert(9999L);
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(9999L);
        request.setTitle("Timestamp Test");
        request.setDescription("Testing timestamps");

        ZonedDateTime beforeCreate = ZonedDateTime.now();
        IncidentResponse created = incidentService.createIncident(TEST_TENANT_ID, TEST_USERNAME, request);
        ZonedDateTime afterCreate = ZonedDateTime.now();

        assertNotNull(created.getCreatedAt());
        assertTrue(created.getCreatedAt().isAfter(beforeCreate.minusSeconds(5)));
        assertTrue(created.getCreatedAt().isBefore(afterCreate.plusSeconds(5)));

        // Update to ACKNOWLEDGED
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ACKNOWLEDGED);
        IncidentResponse updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);

        assertNotNull(updated.getAcknowledgedAt());
        assertTrue(updated.getAcknowledgedAt().isAfter(beforeCreate.minusSeconds(5)));
        assertTrue(updated.getAcknowledgedAt().isBefore(afterCreate.plusSeconds(5)));

        // Update to RESOLVED
        updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ASSIGNED);
        updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);

        updateRequest.setStatus(IncidentStatus.IN_PROGRESS);
        updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);

        updateRequest.setStatus(IncidentStatus.RESOLVED);
        updated = incidentService.updateIncident(created.getIncidentId(), TEST_TENANT_ID, updateRequest);

        assertNotNull(updated.getResolvedAt());
        assertTrue(updated.getResolvedAt().isAfter(updated.getAcknowledgedAt()));
    }
}