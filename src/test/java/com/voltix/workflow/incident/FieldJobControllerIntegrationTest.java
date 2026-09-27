package com.voltix.workflow.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltix.workflow.incident.dto.FieldJobAdminUpdateRequest;
import com.voltix.workflow.incident.dto.FieldJobCreateRequest;
import com.voltix.workflow.incident.dto.FieldJobResponse;
import com.voltix.workflow.incident.dto.FieldJobStatusUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FieldJobControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private FieldJobRepository fieldJobRepository;

    @Autowired
    private FieldJobService fieldJobService;

    // Dedicated fixture IDs - chosen to avoid conflicts with other tests
    private static final long FJ_TENANT_ID = 999_020L;
    private static final long FJ_ZONE_ID = 999_020L;
    private static final String FJ_METER_ID = "METER-FJ-999020";
    private static final String FJ_OPERATOR = "fj-operator-999020";
    private static final String FJ_ADMIN = "fj-admin-999020";
    private static final String FJ_INSPECTOR = "fj-inspector-999020";

    // Tenant B for isolation tests
    private static final long FJ_TENANT_B_ID = 999_021L;
    private static final long FJ_ZONE_B_ID = 999_021L;
    private static final String FJ_OPERATOR_B = "fj-operator-999021";

    private static final String PW_HASH = "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq";

    @BeforeEach
    void setUp() {
        cleanFixtures();

        // Tenant A + zone + meter + users
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                FJ_TENANT_ID, "FieldJob Test Tenant A");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                FJ_ZONE_ID, FJ_TENANT_ID, "FieldJob Test Zone A");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                FJ_METER_ID, FJ_TENANT_ID, FJ_ZONE_ID, "SN-" + FJ_METER_ID);
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                FJ_TENANT_ID, FJ_OPERATOR, PW_HASH, "OPERATOR");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                FJ_TENANT_ID, FJ_ADMIN, PW_HASH, "ADMIN");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                FJ_TENANT_ID, FJ_INSPECTOR, PW_HASH, "INSPECTOR");

        // Tenant B for isolation tests
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                FJ_TENANT_B_ID, "FieldJob Test Tenant B");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                FJ_ZONE_B_ID, FJ_TENANT_B_ID, "FieldJob Test Zone B");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                FJ_TENANT_B_ID, FJ_OPERATOR_B, PW_HASH, "OPERATOR");

        // Create system alerts for testing
        createSystemAlert(50001L);
        createSystemAlert(50002L);
        createSystemAlert(50003L);
        createSystemAlert(50004L);
        createSystemAlert(50005L);
        createSystemAlert(50006L);
    }

    private void createSystemAlert(long alertId) {
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, alertId, FJ_TENANT_ID, FJ_METER_ID, FJ_ZONE_ID);
    }

    private void cleanFixtures() {
        // Delete in correct order due to FK constraints
        jdbcTemplate.update("DELETE FROM field_jobs WHERE tenant_id IN (?, ?)", FJ_TENANT_ID, FJ_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM incidents WHERE tenant_id IN (?, ?)", FJ_TENANT_ID, FJ_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE tenant_id IN (?, ?)", FJ_TENANT_ID, FJ_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE alert_id IN (50001, 50002, 50003, 50004, 50005, 50006)");
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", FJ_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id IN (?, ?)", FJ_ZONE_ID, FJ_ZONE_B_ID);
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?, ?, ?, ?)", FJ_OPERATOR, FJ_ADMIN, FJ_INSPECTOR, FJ_OPERATOR_B, "fj-inspector-b-999020");
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", FJ_TENANT_ID, FJ_TENANT_B_ID);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String content = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private Incident createIncident(long alertId) {
        Incident incident = new Incident();
        incident.setTenantId(FJ_TENANT_ID);
        incident.setIncidentNumber("INC-20260101-" + alertId);
        incident.setSourceAlertId(alertId);
        incident.setMeterId(FJ_METER_ID);
        incident.setZoneId(FJ_ZONE_ID);
        incident.setAlertType("NTL_ANOMALY");
        incident.setSeverity("HIGH");
        incident.setTitle("Test Incident for FieldJob " + alertId);
        incident.setDescription("Test incident description");
        incident.setStatus(IncidentStatus.OPEN);
        incident.setCreatedBy(1L);
        incident.setCreatedAt(ZonedDateTime.now());
        return incidentRepository.save(incident);
    }

    // =========================================================================
    // BATCH 1: Field Job Controller HTTP Tests
    // =========================================================================

    // 1. Unauthenticated GET /api/v1/incidents/field-jobs/{id} → 401/403
    @Test
    void unauthenticated_getFieldJob_rejected() throws Exception {
        // Create a field job directly via service
        Incident incident = createIncident(50001L);
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        mockMvc.perform(get("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId()))
                .andExpect(status().is4xxClientError());
    }

    // 2. OPERATOR can GET an existing tenant-owned field job → 200
    @Test
    void operator_canGetFieldJob() throws Exception {
        Incident incident = createIncident(50001L);
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldJobId").value(created.getFieldJobId()))
                .andExpect(jsonPath("$.incidentId").value(incident.getIncidentId()))
                .andExpect(jsonPath("$.tenantId").value(FJ_TENANT_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // 3. ADMIN can GET an existing tenant-owned field job → 200
    @Test
    void admin_canGetFieldJob() throws Exception {
        Incident incident = createIncident(50002L);
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_ADMIN, "password");

        mockMvc.perform(get("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldJobId").value(created.getFieldJobId()))
                .andExpect(jsonPath("$.incidentId").value(incident.getIncidentId()))
                .andExpect(jsonPath("$.tenantId").value(FJ_TENANT_ID));
    }

    // 4. INSPECTOR can GET an existing tenant-owned field job → 200
    @Test
    void inspector_canGetFieldJob() throws Exception {
        Incident incident = createIncident(50003L);
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_INSPECTOR, "password");

        mockMvc.perform(get("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldJobId").value(created.getFieldJobId()))
                .andExpect(jsonPath("$.incidentId").value(incident.getIncidentId()))
                .andExpect(jsonPath("$.tenantId").value(FJ_TENANT_ID));
    }

    // 5. OPERATOR can GET /api/v1/incidents/{incidentId}/field-jobs → 200
    @Test
    void operator_canListFieldJobsForIncident() throws Exception {
        Incident incident = createIncident(50004L);

        // Create multiple field jobs for this incident
        for (int i = 0; i < 3; i++) {
            FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
            createRequest.setIncidentId(incident.getIncidentId());
            fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);
        }

        String token = loginAndGetToken(FJ_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/incidents/{incidentId}/field-jobs", incident.getIncidentId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].incidentId").value(incident.getIncidentId()))
                .andExpect(jsonPath("$[0].tenantId").value(FJ_TENANT_ID));
    }

    // 6. POST /api/v1/incidents/{incidentId}/field-jobs as OPERATOR → successful creation
    @Test
    void operator_canCreateFieldJob() throws Exception {
        Incident incident = createIncident(50005L);

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setPriority(FieldJobPriority.HIGH);
        request.setInstructions("Test field job creation via API");
        request.setScheduledAt(ZonedDateTime.now().plusDays(1));

        String token = loginAndGetToken(FJ_OPERATOR, "password");

        mockMvc.perform(post("/api/v1/incidents/{incidentId}/field-jobs", incident.getIncidentId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldJobId").exists())
                .andExpect(jsonPath("$.incidentId").value(incident.getIncidentId()))
                .andExpect(jsonPath("$.tenantId").value(FJ_TENANT_ID))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.instructions").value("Test field job creation via API"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // 7. POST the same operation as INSPECTOR → 403
    @Test
    void inspector_cannotCreateFieldJob() throws Exception {
        Incident incident = createIncident(50006L);

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setPriority(FieldJobPriority.NORMAL);

        String token = loginAndGetToken(FJ_INSPECTOR, "password");

        mockMvc.perform(post("/api/v1/incidents/{incidentId}/field-jobs", incident.getIncidentId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // BATCH 2: Inspector Authorization Tests
    // =========================================================================

    // 8. INSPECTOR_STATUS_UPDATE_ALLOWED
    @Test
    void inspector_canUpdateStatus() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job assigned to the inspector
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_INSPECTOR, "password");

        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ROUTE"))
                .andExpect(jsonPath("$.startedAt").exists());

        // Verify startedAt is populated in database
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertNotNull(refreshed.getStartedAt());
    }

    // 9. INSPECTOR_CANNOT_REASSIGN
    @Test
    void inspector_cannotReassign() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job assigned to inspector A
        Long inspectorAId = getInspectorId();
        Long inspectorBId = createSecondInspector();

        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(inspectorAId);
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_INSPECTOR, "password");

        // Attempt to reassign via admin endpoint
        FieldJobAdminUpdateRequest adminRequest = new FieldJobAdminUpdateRequest();
        adminRequest.setAssignedInspectorId(inspectorBId);

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isForbidden());

        // Verify assignment remains unchanged
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(getInspectorId(), refreshed.getAssignedInspectorId());
    }

    // 10. INSPECTOR_CANNOT_CHANGE_ADMIN_FIELDS
    @Test
    void inspector_cannotChangeAdminFields() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job assigned to the inspector
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_INSPECTOR, "password");

        // Attempt to change priority via admin endpoint
        FieldJobAdminUpdateRequest adminRequest = new FieldJobAdminUpdateRequest();
        adminRequest.setPriority(FieldJobPriority.URGENT);
        adminRequest.setInstructions("Should not work");

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isForbidden());

        // Verify fields remain unchanged
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobPriority.NORMAL, refreshed.getPriority()); // Default priority
        assertNull(refreshed.getInstructions());
    }

    // 11. INSPECTOR_CANNOT_UPDATE_UNASSIGNED_JOB
    @Test
    void inspector_cannotUpdateUnassignedJob() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job assigned to inspector B
        Long inspectorBId = createSecondInspector();

        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(inspectorBId);
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        // Authenticate as inspector A
        String token = loginAndGetToken(FJ_INSPECTOR, "password");

        // Attempt status update
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        // Verify status remains unchanged
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobStatus.ASSIGNED, refreshed.getStatus());
    }

    private Long getInspectorId() {
        return jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, FJ_INSPECTOR);
    }

    private Long createSecondInspector() {
        String username = "fj-inspector-b-999020";
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                FJ_TENANT_ID, username, PW_HASH, "INSPECTOR");
        return jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, username);
    }

    // =========================================================================
    // BATCH 3: Operator/Admin Modification Tests
    // =========================================================================

    // 12. OPERATOR_CAN_UPDATE_ADMIN_FIELDS
    @Test
    void operator_canUpdateAdminFields() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job without inspector (PENDING)
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_OPERATOR, "password");

        // Update admin fields via admin endpoint
        FieldJobAdminUpdateRequest adminRequest = new FieldJobAdminUpdateRequest();
        adminRequest.setPriority(FieldJobPriority.URGENT);
        adminRequest.setInstructions("Updated by operator");
        adminRequest.setScheduledAt(ZonedDateTime.now().plusDays(2));

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.instructions").value("Updated by operator"))
                .andExpect(jsonPath("$.scheduledAt").exists());

        // Verify persistence in database
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobPriority.URGENT, refreshed.getPriority());
        assertEquals("Updated by operator", refreshed.getInstructions());
        assertNotNull(refreshed.getScheduledAt());
    }

    // 13. ADMIN_CAN_UPDATE_ADMIN_FIELDS
    @Test
    void admin_canUpdateAdminFields() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_ADMIN, "password");

        // Update admin fields via admin endpoint
        FieldJobAdminUpdateRequest adminRequest = new FieldJobAdminUpdateRequest();
        adminRequest.setPriority(FieldJobPriority.HIGH);
        adminRequest.setInstructions("Updated by admin");
        adminRequest.setScheduledAt(ZonedDateTime.now().plusDays(3));

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.instructions").value("Updated by admin"))
                .andExpect(jsonPath("$.scheduledAt").exists());

        // Verify persistence in database
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobPriority.HIGH, refreshed.getPriority());
        assertEquals("Updated by admin", refreshed.getInstructions());
        assertNotNull(refreshed.getScheduledAt());
    }

    // 14. OPERATOR_CAN_UPDATE_STATUS
    @Test
    void operator_canUpdateStatus() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job assigned to inspector so it can transition from ASSIGNED
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_OPERATOR, "password");

        // Move from ASSIGNED -> EN_ROUTE (valid transition)
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ROUTE"))
                .andExpect(jsonPath("$.startedAt").exists());

        // Verify timestamp persistence
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobStatus.EN_ROUTE, refreshed.getStatus());
        assertNotNull(refreshed.getStartedAt());
    }

    // 15. ADMIN_CAN_UPDATE_STATUS
    @Test
    void admin_canUpdateStatus() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job assigned to inspector so it can transition from ASSIGNED
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_ADMIN, "password");

        // Move from ASSIGNED -> EN_ROUTE (valid transition)
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ROUTE"))
                .andExpect(jsonPath("$.startedAt").exists());

        // Verify timestamp persistence
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobStatus.EN_ROUTE, refreshed.getStatus());
        assertNotNull(refreshed.getStartedAt());
    }

    // =========================================================================
    // BATCH 4: Cross-Tenant Security and Lifecycle Edge Cases
    // =========================================================================

    // 16. CROSS_TENANT_GET_FIELD_JOB_REJECTED
    @Test
    void crossTenant_getFieldJob_rejected() throws Exception {
        // Create field job under tenant A
        Incident incident = createIncident(50001L);
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        // Authenticate as user from tenant B
        String tokenB = loginAndGetToken(FJ_OPERATOR_B, "password");

        // Attempt to access tenant A's field job
        // The controller returns 404 for cross-tenant access (IllegalArgumentException -> 404)
        mockMvc.perform(get("/api/v1/incidents/field-jobs/{id}", created.getFieldJobId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Verify tenant B cannot see tenant A's job
        // (The service layer enforces tenant isolation, so the job is not found for tenant B)
    }

    // 17. CROSS_TENANT_LIST_INCIDENT_FIELD_JOBS_REJECTED
    @Test
    void crossTenant_listIncidentFieldJobs_rejected() throws Exception {
        // Create incident + field job under tenant A
        Incident incident = createIncident(50001L);
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        // Authenticate as user from tenant B
        String tokenB = loginAndGetToken(FJ_OPERATOR_B, "password");

        // Attempt to list field jobs for tenant A's incident
        // The controller returns 400 for cross-tenant access (IllegalArgumentException -> 400)
        mockMvc.perform(get("/api/v1/incidents/{incidentId}/field-jobs", incident.getIncidentId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        // Verify no tenant-A data is exposed to tenant B
    }

    // 18. CROSS_TENANT_CREATE_FIELD_JOB_REJECTED
    @Test
    void crossTenant_createFieldJob_rejected() throws Exception {
        // Create incident under tenant A
        Incident incident = createIncident(50001L);

        // Authenticate as user from tenant B
        String tokenB = loginAndGetToken(FJ_OPERATOR_B, "password");

        // Attempt to create field job for tenant A's incident
        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setPriority(FieldJobPriority.NORMAL);

        // The controller returns 400 for cross-tenant access (IllegalArgumentException -> 400)
        mockMvc.perform(post("/api/v1/incidents/{incidentId}/field-jobs", incident.getIncidentId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Verify no field job was created under tenant A
        List<FieldJob> jobs = fieldJobRepository.findByIncidentIdAndTenantIdOrderByCreatedAtDesc(incident.getIncidentId(), FJ_TENANT_ID);
        assertEquals(0, jobs.size());
    }

    // 19. NONEXISTENT_FIELD_JOB_RETURNS_404
    @Test
    void nonexistent_fieldJob_returns404() throws Exception {
        String token = loginAndGetToken(FJ_OPERATOR, "password");

        Long nonexistentId = 999999L;

        mockMvc.perform(get("/api/v1/incidents/field-jobs/{id}", nonexistentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // 20. INVALID_STATUS_TRANSITION_REJECTED
    @Test
    void invalid_status_transition_rejected() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job in PENDING state (no inspector assigned)
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        String token = loginAndGetToken(FJ_OPERATOR, "password");

        // Try to transition from PENDING directly to EN_ROUTE (invalid: must go through ASSIGNED)
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());

        // Verify status remains unchanged
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobStatus.PENDING, refreshed.getStatus());
    }

    // 21. TERMINAL_FIELD_JOB_CANNOT_TRANSITION
    @Test
    void terminal_fieldJob_cannot_transition() throws Exception {
        Incident incident = createIncident(50001L);

        // Create field job assigned to inspector
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(FJ_TENANT_ID, FJ_OPERATOR, createRequest);

        // Move through valid transitions to COMPLETED
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        String token = loginAndGetToken(FJ_INSPECTOR, "password");

        // ASSIGNED -> EN_ROUTE
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // EN_ROUTE -> ON_SITE
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // ON_SITE -> COMPLETED
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Now attempt to transition from COMPLETED (terminal)
        updateRequest.setStatus(FieldJobStatus.FAILED);

        mockMvc.perform(patch("/api/v1/incidents/field-jobs/{id}/status", created.getFieldJobId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());

        // Verify status remains COMPLETED
        FieldJobResponse refreshed = fieldJobService.getFieldJobById(created.getFieldJobId(), FJ_TENANT_ID);
        assertEquals(FieldJobStatus.COMPLETED, refreshed.getStatus());
    }
}