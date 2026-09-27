package com.voltix.workflow.incident;

import com.voltix.workflow.incident.dto.FieldJobAdminUpdateRequest;
import com.voltix.workflow.incident.dto.FieldJobCreateRequest;
import com.voltix.workflow.incident.dto.FieldJobResponse;
import com.voltix.workflow.incident.dto.FieldJobStatusUpdateRequest;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext
class FieldJobServiceIntegrationTest {

    @Autowired
    private FieldJobService fieldJobService;

    @Autowired
    private FieldJobRepository fieldJobRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TEST_TENANT_ID = 999_015L;
    private static final long TEST_ZONE_ID = 999_015L;
    private static final String TEST_METER_ID = "METER-FJ-TEST-999015";
    private static final String TEST_USERNAME = "fj-test-operator";
    private static final String TEST_INSPECTOR_USERNAME = "fj-test-inspector";
    private static final String TEST_ADMIN_USERNAME = "fj-test-admin";
    private static final String TEST_PASSWORD = "password";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM field_jobs WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_016L);
        jdbcTemplate.update("DELETE FROM incidents WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_016L);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_016L);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", "METER-OTHER-999016");
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id IN (?, ?)", TEST_ZONE_ID, 999_016L);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_016L);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_016L);

        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "FieldJob Test Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 2.50)",
                TEST_ZONE_ID, TEST_TENANT_ID, "FieldJob Test Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);

        // Insert test user (OPERATOR)
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, TEST_USERNAME, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "OPERATOR");

        // Insert test inspector (INSPECTOR)
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");

        // Insert test admin (ADMIN)
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, TEST_ADMIN_USERNAME, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "ADMIN");
    }

    private Long getInspectorId() {
        return jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, TEST_INSPECTOR_USERNAME);
    }

    private Incident createIncident() {
        // Create system alert first - use unique alert_id range (9001+) to avoid conflicts with other tests
        long alertId = 9001L + (int)(System.currentTimeMillis() % 10000);
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, alertId, TEST_TENANT_ID, TEST_METER_ID, TEST_ZONE_ID);

        // Create incident
        Incident incident = new Incident();
        incident.setTenantId(TEST_TENANT_ID);
        incident.setIncidentNumber("INC-20260101-" + String.format("%06d", alertId % 1000000));
        incident.setSourceAlertId(alertId);
        incident.setMeterId(TEST_METER_ID);
        incident.setZoneId(TEST_ZONE_ID);
        incident.setAlertType("NTL_ANOMALY");
        incident.setSeverity("HIGH");
        incident.setTitle("Test Incident");
        incident.setDescription("Test incident description");
        incident.setStatus(IncidentStatus.OPEN);
        incident.setCreatedBy(1L);
        incident.setCreatedAt(ZonedDateTime.now());
        return incidentRepository.save(incident);
    }

    @Test
    void testCreateFieldJobForValidIncident() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setPriority(FieldJobPriority.HIGH);
        request.setInstructions("Inspect meter and verify anomaly");
        request.setScheduledAt(ZonedDateTime.now().plusDays(1));

        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        assertNotNull(created.getFieldJobId());
        assertEquals(TEST_TENANT_ID, created.getTenantId());
        assertEquals(incident.getIncidentId(), created.getIncidentId());
        assertEquals(FieldJobStatus.PENDING, created.getStatus());
        assertEquals(FieldJobPriority.HIGH, created.getPriority());
        assertEquals("Inspect meter and verify anomaly", created.getInstructions());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getCreatedBy());
        assertNull(created.getAssignedAt());

        // Verify in DB
        List<FieldJob> stored = fieldJobRepository.findByIncidentIdAndTenantIdOrderByCreatedAtDesc(incident.getIncidentId(), TEST_TENANT_ID);
        assertEquals(1, stored.size());
        assertEquals(created.getFieldJobId(), stored.get(0).getFieldJobId());
    }

    @Test
    void testCreateFieldJobWithAssignedInspector() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        request.setPriority(FieldJobPriority.NORMAL);

        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        assertNotNull(created.getFieldJobId());
        assertEquals(FieldJobStatus.ASSIGNED, created.getStatus()); // Should be ASSIGNED when inspector is provided
        assertEquals(getInspectorId(), created.getAssignedInspectorId());
        assertNotNull(created.getAssignedAt()); // assignedAt should be set
    }

    @Test
    void testCreateFieldJob_InvalidIncident_ThrowsException() {
        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(99999L); // Non-existent incident

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request));
        assertTrue(ex.getMessage().contains("Incident not found"));
    }

    @Test
    void testCreateFieldJob_CrossTenantIncident_ThrowsException() {
        // Create another tenant with incident
        long otherTenantId = 999_016L;
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                otherTenantId, "Other Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 2.50)",
                999_016L, otherTenantId, "Other Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                "METER-OTHER-999016", otherTenantId, 999_016L, "SN-OTHER");
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, 2002L, otherTenantId, "METER-OTHER-999016", 999_016L);

        Incident otherIncident = new Incident();
        otherIncident.setTenantId(otherTenantId);
        otherIncident.setIncidentNumber("INC-20260101-000002");
        otherIncident.setSourceAlertId(2002L);
        otherIncident.setMeterId("METER-OTHER-999016");
        otherIncident.setZoneId(999_016L);
        otherIncident.setAlertType("NTL_ANOMALY");
        otherIncident.setSeverity("HIGH");
        otherIncident.setTitle("Other Incident");
        otherIncident.setDescription("Other incident");
        otherIncident.setStatus(IncidentStatus.OPEN);
        otherIncident.setCreatedBy(1L);
        otherIncident.setCreatedAt(ZonedDateTime.now());
        otherIncident = incidentRepository.save(otherIncident);

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(otherIncident.getIncidentId());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request));
        assertTrue(ex.getMessage().contains("Incident not found"));
    }

    @Test
    void testCreateFieldJob_NonExistentInspector_ThrowsException() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(99999L); // Non-existent inspector

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request));
        assertTrue(ex.getMessage().contains("Assigned inspector not found"));
    }

    @Test
    void testCreateFieldJob_CrossTenantInspector_ThrowsException() {
        // Create inspector in another tenant
        long otherTenantId = 999_016L;
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                otherTenantId, "Other Tenant");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                otherTenantId, "other-inspector", "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");

        // Get the inspector ID
        Long otherInspectorId = jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, "other-inspector");

        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(otherInspectorId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request));
        assertTrue(ex.getMessage().contains("does not belong to the same tenant"));
    }

    @Test
    void testCreateFieldJob_InspectorWithWrongRole_ThrowsException() {
        // Create user with OPERATOR role (not INSPECTOR)
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, "fj-operator-not-inspector", "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "OPERATOR");

        Long operatorId = jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, "fj-operator-not-inspector");

        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(operatorId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request));
        assertTrue(ex.getMessage().contains("must have INSPECTOR role"));
    }

    @Test
    void testListFieldJobsForIncident() {
        Incident incident = createIncident();

        // Create multiple field jobs
        for (int i = 0; i < 3; i++) {
            FieldJobCreateRequest request = new FieldJobCreateRequest();
            request.setIncidentId(incident.getIncidentId());
            request.setPriority(FieldJobPriority.NORMAL);
            fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);
        }

        List<FieldJobResponse> fieldJobs = fieldJobService.getFieldJobsForIncident(incident.getIncidentId(), TEST_TENANT_ID);

        assertEquals(3, fieldJobs.size());
        // Should be ordered by createdAt descending
        assertTrue(fieldJobs.get(0).getCreatedAt().isAfter(fieldJobs.get(1).getCreatedAt()) ||
                   fieldJobs.get(0).getCreatedAt().isEqual(fieldJobs.get(1).getCreatedAt()));
    }

    @Test
    void testGetFieldJobById() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        FieldJobResponse fetched = fieldJobService.getFieldJobById(created.getFieldJobId(), TEST_TENANT_ID);

        assertEquals(created.getFieldJobId(), fetched.getFieldJobId());
        assertEquals(created.getIncidentId(), fetched.getIncidentId());
        assertEquals(created.getStatus(), fetched.getStatus());
    }

    @Test
    void testGetFieldJob_CrossTenantAccess_ThrowsException() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Try to access from different tenant
        long otherTenantId = 999_016L;
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                otherTenantId, "Other Tenant");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.getFieldJobById(created.getFieldJobId(), otherTenantId));
        assertTrue(ex.getMessage().contains("Field job not found"));
    }

    // --- Inspector Status Update Tests ---

    @Test
    void testInspectorUpdateFieldJob_ValidStatusTransitions() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        assertEquals(FieldJobStatus.ASSIGNED, created.getStatus());
        assertNotNull(created.getAssignedAt());

        // ASSIGNED -> EN_ROUTE (inspector updates status)
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        FieldJobResponse updated = fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);
        assertEquals(FieldJobStatus.EN_ROUTE, updated.getStatus());
        assertNotNull(updated.getStartedAt());

        // EN_ROUTE -> ON_SITE
        updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        updated = fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);
        assertEquals(FieldJobStatus.ON_SITE, updated.getStatus());

        // ON_SITE -> COMPLETED
        updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        updated = fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);
        assertEquals(FieldJobStatus.COMPLETED, updated.getStatus());
        assertNotNull(updated.getCompletedAt());
    }

    @Test
    void testInspectorUpdateFieldJob_InvalidTransition_ThrowsException() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Try to skip ASSIGNED and go directly to ON_SITE (invalid: ASSIGNED -> ON_SITE not allowed)
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ON_SITE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    void testInspectorUpdateFieldJob_TerminalStatesProtected() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);

        updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);

        updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        FieldJobResponse completed = fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);
        assertEquals(FieldJobStatus.COMPLETED, completed.getStatus());

        // Try to transition from COMPLETED (terminal)
        FieldJobStatusUpdateRequest invalidUpdateRequest = new FieldJobStatusUpdateRequest();
        invalidUpdateRequest.setStatus(FieldJobStatus.ON_SITE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, invalidUpdateRequest));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    void testAdminUpdateFieldJob_StatusTransitionValidation() {
        Incident incident = createIncident();

        // Create field job WITHOUT inspector (PENDING)
        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        assertEquals(FieldJobStatus.PENDING, created.getStatus());
        assertNull(created.getAssignedInspectorId());

        // Admin tries to transition PENDING -> EN_ROUTE (invalid status transition)
        FieldJobAdminUpdateRequest adminRequest = new FieldJobAdminUpdateRequest();
        adminRequest.setStatus(FieldJobStatus.EN_ROUTE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, adminRequest));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    void testInspectorUpdateFieldJob_InspectorCanOnlyUpdateOwnJobs() {
        // Create two inspectors
        Long inspector1Id = getInspectorId();
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, "fj-test-inspector-2", "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");
        Long inspector2Id = jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, "fj-test-inspector-2");

        Incident incident = createIncident();

        // Create field job assigned to inspector1
        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(inspector1Id);
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Try to update status as inspector2 (not assigned to this job)
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, "fj-test-inspector-2", updateRequest));
        assertTrue(ex.getMessage().contains("can only update status of their assigned field jobs"));
    }

    // --- Admin Update Tests ---

    @Test
    void testAdminUpdateFieldJob_AssignInspector() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        assertEquals(FieldJobStatus.PENDING, created.getStatus());
        assertNull(created.getAssignedInspectorId());

        // Admin assigns inspector
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setAssignedInspectorId(getInspectorId());
        FieldJobResponse updated = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);

        assertEquals(FieldJobStatus.ASSIGNED, updated.getStatus()); // Should auto-transition to ASSIGNED
        assertEquals(getInspectorId(), updated.getAssignedInspectorId());
        assertNotNull(updated.getAssignedAt());
    }

@Test
    void testAdminUpdateFieldJob_UnassignInspectorNotSupported() {
        // Verify that unassigning via null is not supported in admin update
        // Unassigning requires a separate mechanism
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        assertEquals(getInspectorId(), created.getAssignedInspectorId());

        // Try to unassign via null - this should not change the assigned inspector
        // (we removed unassigning via null in admin update)
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setAssignedInspectorId(null);
        FieldJobResponse updated = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);

        // The assigned inspector should remain unchanged
        assertEquals(getInspectorId(), updated.getAssignedInspectorId());
    }

    @Test
    void testAdminUpdateFieldJob_PriorityAndInstructions() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Admin updates priority and instructions
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setPriority(FieldJobPriority.URGENT);
        updateRequest.setInstructions("Updated instructions - urgent!");
        FieldJobResponse updated = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);

        assertEquals(FieldJobPriority.URGENT, updated.getPriority());
        assertEquals("Updated instructions - urgent!", updated.getInstructions());
    }

    @Test
    void testAdminUpdateFieldJob_ScheduledAt() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Admin updates scheduledAt
        ZonedDateTime newScheduledAt = ZonedDateTime.now().plusDays(2);
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setScheduledAt(newScheduledAt);
        FieldJobResponse updated = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);

        assertEquals(newScheduledAt, updated.getScheduledAt());
    }

    @Test
    void testAdminUpdateFieldJob_ValidStatusTransitions() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        assertEquals(FieldJobStatus.ASSIGNED, created.getStatus());

        // Admin can also update status
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        FieldJobResponse updated = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);
        assertEquals(FieldJobStatus.EN_ROUTE, updated.getStatus());
        assertNotNull(updated.getStartedAt());

        // EN_ROUTE -> ON_SITE
        updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        updated = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);
        assertEquals(FieldJobStatus.ON_SITE, updated.getStatus());

        // ON_SITE -> COMPLETED
        updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        updated = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);
        assertEquals(FieldJobStatus.COMPLETED, updated.getStatus());
        assertNotNull(updated.getCompletedAt());
    }

    @Test
    void testAdminUpdateFieldJob_InvalidTransition_ThrowsException() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Try to skip ASSIGNED and go directly to ON_SITE (invalid: ASSIGNED -> ON_SITE not allowed)
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ON_SITE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    void testAdminUpdateFieldJob_TerminalStatesProtected() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Complete the field job
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);

        updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);

        updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        FieldJobResponse completed = fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest);
        assertEquals(FieldJobStatus.COMPLETED, completed.getStatus());

        // Try to transition from COMPLETED (terminal)
        FieldJobAdminUpdateRequest invalidUpdateRequest = new FieldJobAdminUpdateRequest();
        invalidUpdateRequest.setStatus(FieldJobStatus.ON_SITE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, invalidUpdateRequest));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    void testAdminUpdateFieldJob_CrossTenantAccess_ThrowsException() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Try to update from different tenant
        long otherTenantId = 999_016L;
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                otherTenantId, "Other Tenant");

        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ASSIGNED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), otherTenantId, updateRequest));
        assertTrue(ex.getMessage().contains("Field job not found"));
    }

    @Test
    void testAdminUpdateFieldJob_TimestampsCorrect() {
        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Verify assignedAt is set on creation with inspector
        assertNotNull(created.getAssignedAt());
        ZonedDateTime assignedAt = created.getAssignedAt();

        // ASSIGNED -> EN_ROUTE should set startedAt
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        FieldJobResponse updated = fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);
        assertNotNull(updated.getStartedAt());
        ZonedDateTime startedAt = updated.getStartedAt();
        assertTrue(startedAt.isAfter(assignedAt) || startedAt.equals(assignedAt));

        // EN_ROUTE -> ON_SITE should keep startedAt
        updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        updated = fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);
        assertTrue(java.time.Duration.between(startedAt.toInstant(), updated.getStartedAt().toInstant()).abs().getSeconds() < 1);

        // ON_SITE -> COMPLETED should set completedAt
        updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        updated = fieldJobService.updateFieldJobStatus(created.getFieldJobId(), TEST_TENANT_ID, TEST_INSPECTOR_USERNAME, updateRequest);
        assertNotNull(updated.getCompletedAt());
        assertTrue(updated.getCompletedAt().toInstant().isAfter(startedAt.toInstant()) || updated.getCompletedAt().toInstant().equals(startedAt.toInstant()));
    }

    @Test
    void testUpdateFieldJob_CrossTenantInspector_ThrowsException() {
        // Create inspector in another tenant
        long otherTenantId = 999_016L;
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                otherTenantId, "Other Tenant");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                otherTenantId, "other-inspector", "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");

        Long otherInspectorId = jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, "other-inspector");

        Incident incident = createIncident();

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_USERNAME, request);

        // Admin tries to assign cross-tenant inspector
        FieldJobAdminUpdateRequest updateRequest = new FieldJobAdminUpdateRequest();
        updateRequest.setAssignedInspectorId(otherInspectorId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fieldJobService.updateFieldJobAdmin(created.getFieldJobId(), TEST_TENANT_ID, updateRequest));
        assertTrue(ex.getMessage().contains("does not belong to the same tenant"));
    }
}