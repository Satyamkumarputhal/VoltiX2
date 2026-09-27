package com.voltix.workflow.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltix.workflow.incident.dto.IncidentCreateRequest;
import com.voltix.workflow.incident.dto.IncidentResponse;
import com.voltix.workflow.incident.dto.IncidentUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentService incidentService;

    // Dedicated fixture IDs
    private static final long TA_TENANT_ID = 999_011L;
    private static final long TA_ZONE_ID = 999_011L;
    private static final String TA_METER_ID = "METER-INC-TEST-999011";
    private static final String TA_OPERATOR = "inc-operatorA-999011";
    private static final String TA_ADMIN = "inc-adminA-999011";
    private static final String TA_INSPECTOR = "inc-inspectorA-999011";

    // Tenant B for isolation tests
    private static final long TB_TENANT_ID = 999_012L;
    private static final long TB_ZONE_ID = 999_012L;
    private static final String TB_OPERATOR = "inc-operatorB-999012";

    private static final String PW_HASH = "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq";

    @BeforeEach
    void setUp() {
        cleanFixtures();

        // Tenant A + zone + users
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TA_TENANT_ID, "Incident Test Tenant A");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TA_ZONE_ID, TA_TENANT_ID, "Incident Test Zone A");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TA_METER_ID, TA_TENANT_ID, TA_ZONE_ID, "SN-" + TA_METER_ID);
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TA_TENANT_ID, TA_OPERATOR, PW_HASH, "OPERATOR");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TA_TENANT_ID, TA_ADMIN, PW_HASH, "ADMIN");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TA_TENANT_ID, TA_INSPECTOR, PW_HASH, "INSPECTOR");

        // Tenant B + zone + operator
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TB_TENANT_ID, "Incident Test Tenant B");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TB_ZONE_ID, TB_TENANT_ID, "Incident Test Zone B");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TB_TENANT_ID, TB_OPERATOR, PW_HASH, "OPERATOR");

        // Create system alerts for testing (after tenants exist)
        createSystemAlert(10001L);
        createSystemAlert(20001L);
        createSystemAlert(20002L);
        createSystemAlert(30001L);
        createSystemAlert(40001L);
        createSystemAlert(50001L);
        createSystemAlert(60001L);
        createSystemAlert(9001L);
        createSystemAlert(9002L);
    }

    private void createSystemAlert(long alertId) {
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, alertId, TA_TENANT_ID, TA_METER_ID, TA_ZONE_ID);
    }

    private void cleanFixtures() {
        // Delete in correct order due to FK constraints: child tables first
        jdbcTemplate.update("DELETE FROM inspections");
        jdbcTemplate.update("DELETE FROM field_jobs");
        jdbcTemplate.update("DELETE FROM incidents");
        jdbcTemplate.update("DELETE FROM system_alerts");
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TA_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id IN (?, ?)", TA_ZONE_ID, TB_ZONE_ID);
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?, ?, ?)", TA_OPERATOR, TA_ADMIN, TA_INSPECTOR, TB_OPERATOR);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", TA_TENANT_ID, TB_TENANT_ID);
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

    // ── Test 1: Create incident ─────────────────────────────────────────────
    @Test
    void authorizedOperator_canCreateIncident() throws Exception {
        String token = loginAndGetToken(TA_OPERATOR, "password");

        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(10001L);
        request.setTitle("Test Incident via API");
        request.setDescription("Created via REST API");
        request.setMeterId("METER-INC-TEST-999011");
        request.setZoneId(999011L);
        request.setAlertType("NTL_ANOMALY");
        request.setSeverity("HIGH");

        mockMvc.perform(post("/api/v1/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").exists())
                .andExpect(jsonPath("$.incidentNumber").exists())
                .andExpect(jsonPath("$.title").value("Test Incident via API"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.sourceAlertId").value(10001))
                .andExpect(jsonPath("$.tenantId").value(999011));
    }

    // ── Test 2: List incidents ─────────────────────────────────────────────
    @Test
    void authorizedOperator_canListIncidents() throws Exception {
        // Create some incidents via service
        IncidentCreateRequest r1 = new IncidentCreateRequest();
        r1.setSourceAlertId(20001L);
        r1.setTitle("API Incident 1");
        r1.setDescription("First");
        IncidentResponse created1 = incidentService.createIncident(999_011L, TA_OPERATOR, r1);

        IncidentCreateRequest r2 = new IncidentCreateRequest();
        r2.setSourceAlertId(20002L);
        r2.setTitle("API Incident 2");
        r2.setDescription("Second");
        IncidentResponse created2 = incidentService.createIncident(999_011L, TA_OPERATOR, r2);

        String token = loginAndGetToken(TA_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].incidentId").value(created2.getIncidentId())) // newest first
                .andExpect(jsonPath("$[1].incidentId").value(created1.getIncidentId()));
    }

    // ── Test 3: Get incident by ID ─────────────────────────────────────────
    @Test
    void authorizedOperator_canGetIncidentById() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(30001L);
        request.setTitle("Specific Incident");
        request.setDescription("Test get by ID");
        IncidentResponse created = incidentService.createIncident(999_011L, TA_OPERATOR, request);

        String token = loginAndGetToken(TA_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/incidents/{id}", created.getIncidentId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(created.getIncidentId()))
                .andExpect(jsonPath("$.title").value("Specific Incident"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    // ── Test 4: Update incident status ─────────────────────────────────────
    @Test
    void authorizedOperator_canUpdateIncidentStatus() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(40001L);
        request.setTitle("Status Update Test");
        request.setDescription("Test status transitions");
        IncidentResponse created = incidentService.createIncident(999_011L, TA_OPERATOR, request);

        String token = loginAndGetToken(TA_OPERATOR, "password");

        // OPEN -> ACKNOWLEDGED
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.ACKNOWLEDGED);

        mockMvc.perform(patch("/api/v1/incidents/{id}", created.getIncidentId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").exists());

        // ACKNOWLEDGED -> ASSIGNED (assign to the creator)
        updateRequest.setStatus(IncidentStatus.ASSIGNED);
        updateRequest.setAssignedTo(created.getCreatedBy());

        mockMvc.perform(patch("/api/v1/incidents/{id}", created.getIncidentId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAt").exists())
                .andExpect(jsonPath("$.assignedTo").value(created.getCreatedBy()));
    }

    // ── Test 5: Invalid status transition rejected ─────────────────────────
    @Test
    void invalidStatusTransition_rejected() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setSourceAlertId(50001L);
        request.setTitle("Invalid Transition Test");
        request.setDescription("Testing invalid transition via API");
        IncidentResponse created = incidentService.createIncident(999_011L, TA_OPERATOR, request);

        String token = loginAndGetToken(TA_OPERATOR, "password");

        // Try OPEN -> RESOLVED (invalid)
        IncidentUpdateRequest updateRequest = new IncidentUpdateRequest();
        updateRequest.setStatus(IncidentStatus.RESOLVED);

        mockMvc.perform(patch("/api/v1/incidents/{id}", created.getIncidentId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    // ── Test 6: Tenant isolation - Tenant A cannot access Tenant B incident ─
    @Test
    void tenantA_cannotAccessTenantBIncident() throws Exception {
        // Create a system alert for tenant B
        createSystemAlertForTenantB(90001L);
        
        // Create incident for tenant B
        IncidentCreateRequest requestB = new IncidentCreateRequest();
        requestB.setSourceAlertId(90001L);
        requestB.setTitle("Tenant B Incident");
        requestB.setDescription("Tenant B's incident");
        IncidentResponse incidentB = incidentService.createIncident(999_012L, TB_OPERATOR, requestB);

        // Tenant A operator tries to get Tenant B's incident
        String tokenA = loginAndGetToken(TA_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/incidents/{id}", incidentB.getIncidentId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    private void createSystemAlertForTenantB(long alertId) {
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, alertId, TB_TENANT_ID, TA_METER_ID, TB_ZONE_ID);
    }

    // ── Test 7: Unauthorized role cannot access endpoint ───────────────────
    @Test
    void inspectorRole_isForbidden() throws Exception {
        String inspectorToken = loginAndGetToken(TA_INSPECTOR, "password");

        // INSPECTOR role should be forbidden
        mockMvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/incidents")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAlertId\":1,\"title\":\"test\",\"description\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Test 8: Missing/invalid incident data rejected ─────────────────────
    @Test
    void missingRequiredFields_rejected() throws Exception {
        String token = loginAndGetToken(TA_OPERATOR, "password");

        // Missing sourceAlertId
        String json = "{\"title\":\"test\",\"description\":\"test\"}";

        mockMvc.perform(post("/api/v1/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // ── Test 9: Admin role is allowed ──────────────────────────────────────
    @Test
    void adminRole_isAllowed() throws Exception {
        String adminToken = loginAndGetToken(TA_ADMIN, "password");

        mockMvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── Test 10: Unauthenticated is rejected ───────────────────────────────
    @Test
    void unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAlertId\":1,\"title\":\"test\",\"description\":\"test\"}"))
                .andExpect(status().is4xxClientError());
    }
}