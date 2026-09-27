package com.voltix.workflow.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltix.workflow.incident.dto.FieldJobCreateRequest;
import com.voltix.workflow.incident.dto.FieldJobResponse;
import com.voltix.workflow.incident.dto.FieldJobStatusUpdateRequest;
import com.voltix.workflow.incident.dto.InspectionResponse;
import com.voltix.workflow.incident.dto.InspectionUpdateRequest;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InspectionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private FieldJobService fieldJobService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private FieldJobRepository fieldJobRepository;

    // Dedicated fixture IDs
    private static final long IT_TENANT_ID = 999_035L;
    private static final long IT_ZONE_ID = 999_035L;
    private static final String IT_METER_ID = "METER-INSP-TEST-999035";
    private static final String IT_OPERATOR = "insp-operatorA-999035";
    private static final String IT_ADMIN = "insp-adminA-999035";
    private static final String IT_INSPECTOR = "insp-inspectorA-999035";

    // Tenant B for isolation tests
    private static final long IT_TENANT_B_ID = 999_036L;
    private static final long IT_ZONE_B_ID = 999_036L;
    private static final String IT_OPERATOR_B = "insp-operatorB-999036";

    private static final String PW_HASH = "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq";

    @BeforeEach
    void setUp() {
        cleanFixtures();

        // Tenant A + zone + users
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                IT_TENANT_ID, "Inspection Test Tenant A");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                IT_ZONE_ID, IT_TENANT_ID, "Inspection Test Zone A");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                IT_METER_ID, IT_TENANT_ID, IT_ZONE_ID, "SN-" + IT_METER_ID);
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                IT_TENANT_ID, IT_OPERATOR, PW_HASH, "OPERATOR");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                IT_TENANT_ID, IT_ADMIN, PW_HASH, "ADMIN");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                IT_TENANT_ID, IT_INSPECTOR, PW_HASH, "INSPECTOR");

        // Tenant B + zone + operator
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                IT_TENANT_B_ID, "Inspection Test Tenant B");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                IT_ZONE_B_ID, IT_TENANT_B_ID, "Inspection Test Zone B");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                IT_TENANT_B_ID, IT_OPERATOR_B, PW_HASH, "OPERATOR");

        // Create system alerts for testing
        createSystemAlert(70001L);
        createSystemAlert(70002L);
        createSystemAlert(70003L);
        createSystemAlert(70004L);
    }

    private void createSystemAlert(long alertId) {
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, alertId, IT_TENANT_ID, IT_METER_ID, IT_ZONE_ID);
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM inspections WHERE tenant_id IN (?, ?)", IT_TENANT_ID, IT_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM field_jobs WHERE tenant_id IN (?, ?)", IT_TENANT_ID, IT_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM incidents WHERE tenant_id IN (?, ?)", IT_TENANT_ID, IT_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE tenant_id IN (?, ?)", IT_TENANT_ID, IT_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", IT_METER_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id IN (?, ?)", IT_ZONE_ID, IT_ZONE_B_ID);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id IN (?, ?)", IT_TENANT_ID, IT_TENANT_B_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", IT_TENANT_ID, IT_TENANT_B_ID);
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
        incident.setTenantId(IT_TENANT_ID);
        incident.setIncidentNumber("INC-20260101-" + alertId);
        incident.setSourceAlertId(alertId);
        incident.setMeterId(IT_METER_ID);
        incident.setZoneId(IT_ZONE_ID);
        incident.setAlertType("NTL_ANOMALY");
        incident.setSeverity("HIGH");
        incident.setTitle("Test Incident for Inspection " + alertId);
        incident.setDescription("Test incident description");
        incident.setStatus(IncidentStatus.OPEN);
        incident.setCreatedBy(1L);
        incident.setCreatedAt(ZonedDateTime.now());
        return incidentRepository.save(incident);
    }

    private Long getInspectorId() {
        return jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, IT_INSPECTOR);
    }

    private InspectionResponse createCompletedFieldJobWithInspection(long alertId) throws Exception {
        Incident incident = createIncident(alertId);
        FieldJobCreateRequest createRequest = new FieldJobCreateRequest();
        createRequest.setIncidentId(incident.getIncidentId());
        createRequest.setAssignedInspectorId(getInspectorId());
        FieldJobResponse created = fieldJobService.createFieldJob(IT_TENANT_ID, IT_OPERATOR, createRequest);

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(created.getFieldJobId(), IT_TENANT_ID, IT_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(created.getFieldJobId(), IT_TENANT_ID, IT_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(created.getFieldJobId(), IT_TENANT_ID, IT_INSPECTOR, updateRequest);

        // Return the auto-created inspection
        return inspectionService.getInspectionByFieldJobId(created.getFieldJobId(), IT_TENANT_ID);
    }

    // 1. GET /api/v1/inspections
    @Test
    void authenticatedTenant_canListInspections() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70001L);

        String token = loginAndGetToken(IT_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/inspections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inspectionId").value(inspection.getInspectionId()))
                .andExpect(jsonPath("$[0].fieldJobId").value(inspection.getFieldJobId()));
    }

    // 2. GET /api/v1/inspections/{id}
    @Test
    void authenticatedTenant_canGetInspectionById() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70002L);

        String token = loginAndGetToken(IT_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionId").value(inspection.getInspectionId()))
                .andExpect(jsonPath("$.fieldJobId").value(inspection.getFieldJobId()))
                .andExpect(jsonPath("$.tenantId").value(IT_TENANT_ID));
    }

    // 3. GET /api/v1/inspections/field-job/{fieldJobId}
    @Test
    void authenticatedTenant_canGetInspectionByFieldJobId() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70003L);

        String token = loginAndGetToken(IT_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/inspections/field-job/{fieldJobId}", inspection.getFieldJobId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionId").value(inspection.getInspectionId()))
                .andExpect(jsonPath("$.fieldJobId").value(inspection.getFieldJobId()));
    }

    // 4. PATCH /api/v1/inspections/{id} - assigned inspector can update
    @Test
    void assignedInspector_canUpdateInspection() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70004L);

        String token = loginAndGetToken(IT_INSPECTOR, "password");

        InspectionUpdateRequest updateRequest = new InspectionUpdateRequest();
        updateRequest.setFinding("Found meter tampering");
        updateRequest.setConclusion("Confirmed NTL");
        updateRequest.setEvidenceMetadata("{\"photo\":\"evidence.jpg\"}");
        updateRequest.setRecommendation("Replace meter");
        updateRequest.setResult(InspectionResult.CONFIRMED);

        mockMvc.perform(patch("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finding").value("Found meter tampering"))
                .andExpect(jsonPath("$.conclusion").value("Confirmed NTL"))
                .andExpect(jsonPath("$.result").value("CONFIRMED"))
                .andExpect(jsonPath("$.completedAt").exists());

        // Verify in database
        InspectionResponse refreshed = inspectionService.getInspectionById(inspection.getInspectionId(), IT_TENANT_ID);
        assertEquals("Found meter tampering", refreshed.getFinding());
        assertEquals(InspectionResult.CONFIRMED, refreshed.getResult());
        assertNotNull(refreshed.getCompletedAt());
    }

    // 5. wrong inspector PATCH rejected
    @Test
    void wrongInspector_cannotUpdateInspection() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70001L);

        // Create second inspector
        String otherInspector = "insp-inspectorB-999035";
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                IT_TENANT_ID, otherInspector, PW_HASH, "INSPECTOR");

        String token = loginAndGetToken(otherInspector, "password");

        InspectionUpdateRequest updateRequest = new InspectionUpdateRequest();
        updateRequest.setFinding("Should not work");
        updateRequest.setResult(InspectionResult.CONFIRMED);

        mockMvc.perform(patch("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        // Verify inspection unchanged
        InspectionResponse refreshed = inspectionService.getInspectionById(inspection.getInspectionId(), IT_TENANT_ID);
        assertNull(refreshed.getFinding());
        assertNull(refreshed.getResult());
    }

    // 6. OPERATOR/ADMIN read access
    @Test
    void operator_canReadInspection() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70001L);

        String token = loginAndGetToken(IT_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionId").value(inspection.getInspectionId()));
    }

    @Test
    void admin_canReadInspection() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70002L);

        String token = loginAndGetToken(IT_ADMIN, "password");

        mockMvc.perform(get("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionId").value(inspection.getInspectionId()));
    }

    // 7. unauthorized role rejected (e.g., INSPECTOR can read list but not create)
    @Test
    void inspector_canReadList() throws Exception {
        createCompletedFieldJobWithInspection(70001L);

        String token = loginAndGetToken(IT_INSPECTOR, "password");

        mockMvc.perform(get("/api/v1/inspections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // 8. cross-tenant access rejected
    @Test
    void tenantA_cannotAccessTenantBInspection() throws Exception {
        // Create inspection for tenant A
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70001L);

        // Tenant B already exists from setUp, just use it
        // Tenant B operator tries to access Tenant A's inspection
        String tokenB = loginAndGetToken(IT_OPERATOR_B, "password");

        mockMvc.perform(get("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // 9. invalid/missing inspection returns expected HTTP status
    @Test
    void nonexistentInspection_returns404() throws Exception {
        String token = loginAndGetToken(IT_OPERATOR, "password");

        mockMvc.perform(get("/api/v1/inspections/{id}", 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // 10. unauthenticated is rejected
    @Test
    void unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/inspections"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(patch("/api/v1/inspections/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    // 11. inspector can list their own inspections only
    @Test
    void inspector_canListOwnInspections() throws Exception {
        // Create two inspections - one for this inspector, one for another
        InspectionResponse inspection1 = createCompletedFieldJobWithInspection(70001L);
        InspectionResponse inspection2 = createCompletedFieldJobWithInspection(70002L);

        String token = loginAndGetToken(IT_INSPECTOR, "password");

        mockMvc.perform(get("/api/v1/inspections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // 12. OPERATOR/ADMIN can update inspection (admin fields)
    @Test
    void admin_canUpdateInspection() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70002L);

        String token = loginAndGetToken(IT_ADMIN, "password");

        InspectionUpdateRequest updateRequest = new InspectionUpdateRequest();
        updateRequest.setFinding("Admin updated finding");
        updateRequest.setConclusion("Admin conclusion");
        updateRequest.setRecommendation("Admin recommendation");
        updateRequest.setResult(InspectionResult.FALSE_POSITIVE);

        mockMvc.perform(patch("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finding").value("Admin updated finding"))
                .andExpect(jsonPath("$.result").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    // 13. completedAt preserved on subsequent updates
    @Test
    void completedAtPreservedOnSubsequentUpdates() throws Exception {
        InspectionResponse inspection = createCompletedFieldJobWithInspection(70003L);

        String token = loginAndGetToken(IT_INSPECTOR, "password");

        // First update with result
        InspectionUpdateRequest update1 = new InspectionUpdateRequest();
        update1.setResult(InspectionResult.CONFIRMED);
        String response1 = mockMvc.perform(patch("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        InspectionResponse updated1 = objectMapper.readValue(response1, InspectionResponse.class);
        ZonedDateTime firstCompletedAt = updated1.getCompletedAt();
        assertNotNull(firstCompletedAt);

        // Second update with different result
        InspectionUpdateRequest update2 = new InspectionUpdateRequest();
        update2.setResult(InspectionResult.FALSE_POSITIVE);
        String response2 = mockMvc.perform(patch("/api/v1/inspections/{id}", inspection.getInspectionId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        InspectionResponse updated2 = objectMapper.readValue(response2, InspectionResponse.class);
        // Compare milliseconds to handle nanosecond precision differences
        assertEquals(firstCompletedAt.toInstant().toEpochMilli(), updated2.getCompletedAt().toInstant().toEpochMilli());
        assertEquals(InspectionResult.FALSE_POSITIVE, updated2.getResult());
    }
}