package com.voltix.workflow.complaints;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltix.workflow.complaints.dto.ComplaintRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicComplaintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PublicComplaintRepository complaintRepository;

    private ComplaintRequest complaintRequest;

    // IMPORTANT: this test used to unconditionally DELETE FROM smart_meters/
    // grid_zones/users/tenants (ALL rows) and reseed only a minimal fixture,
    // which silently destroyed the shared dev database's real seed data
    // (operator/inspector/admin users, SM-0..SM-4 meters, Zone A/B) on every
    // "mvn test" run -- there is no separate test datasource for this
    // project. Fixed to never delete anything outside its own rows.
    // <p>
    // Several tests here log in as the REAL seeded "operator" user via the
    // actual /auth/login endpoint, and that user's real tenant_id is 1 (see
    // V3__Auth_And_Seed_Test_Users.sql) -- so this class intentionally reuses
    // the real, pre-existing tenant_id=1 / zone_id=1 (never deletes or
    // recreates them) rather than inventing a fresh tenant for "operator"'s
    // own complaints. Only the dynamically-created "second tenant" used in
    // the foreign-tenant tests gets a dedicated, cleaned-up fixture ID.
    private static final long OPERATOR_REAL_TENANT_ID = 1L;
    private static final long OPERATOR_REAL_ZONE_ID = 1L;
    private static final long TEST_TENANT_2_ID = 999_008L;
    private static final long TEST_ZONE_2_ID = 999_008L;
    private static final String TEST_OPERATOR_2_USERNAME = "operator2-test999008";

    @BeforeEach
    void setUp() {
        // Only ever touches this test's own dedicated fixture rows -- never
        // a blanket DELETE affecting other tenants/zones/users. Complaints
        // are cleared for the real tenant 1 (this test's own writes) and the
        // dynamically-created test tenant 2; the tenant/zone rows themselves
        // are never deleted for tenant 1 (real, pre-existing), only for the
        // test-local tenant 2.
        jdbcTemplate.update("DELETE FROM public_complaints WHERE tenant_id IN (?, ?)", OPERATOR_REAL_TENANT_ID, TEST_TENANT_2_ID);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_OPERATOR_2_USERNAME);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id = ?", TEST_ZONE_2_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?", TEST_TENANT_2_ID);

        complaintRequest = new ComplaintRequest();
        complaintRequest.setZoneId(OPERATOR_REAL_ZONE_ID);
        complaintRequest.setIncidentAddress("123 Power Grid Lane");
        complaintRequest.setDescription("Wire tapping anomaly observed on transformer node.");
        complaintRequest.setTenantId(OPERATOR_REAL_TENANT_ID);
    }

    private long countComplaintsForTestTenant() {
        return complaintRepository.findAll().stream()
                .filter(c -> OPERATOR_REAL_TENANT_ID == c.getTenantId()
                        && "123 Power Grid Lane".equals(c.getIncidentAddress()))
                .count();
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

    @Test
    void whenSubmitAnonymousComplaint_thenReturn201Created() throws Exception {
        mockMvc.perform(post("/api/v1/complaints/anonymous")
                        .with(request -> { request.setRemoteAddr("10.0.0.1"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complaintRequest)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.message").value("Complaint submitted successfully and is pending verification."));

        assertEquals(1, countComplaintsForTestTenant());
    }

    @Test
    void whenDuplicateSubmitWithinWindow_thenReturn202AndDeduplicate() throws Exception {
        // First submission
        mockMvc.perform(post("/api/v1/complaints/anonymous")
                        .with(request -> { request.setRemoteAddr("10.0.0.2"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complaintRequest)))
                .andExpect(status().isCreated());

        // Duplicate submission (same address)
        mockMvc.perform(post("/api/v1/complaints/anonymous")
                        .with(request -> { request.setRemoteAddr("10.0.0.2"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complaintRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value("A complaint for this address is already under verification."));

        // Count should still be 1
        assertEquals(1, countComplaintsForTestTenant());
    }

    @Test
    void whenRateLimitExceeded_thenReturn429() throws Exception {
        // The default capacity in application.yml is 20, let's execute 20 requests
        for (int i = 0; i < 20; i++) {
            // Modify address slightly to avoid address dedup issues
            complaintRequest.setIncidentAddress("Address " + i);
            mockMvc.perform(post("/api/v1/complaints/anonymous")
                            .with(request -> { request.setRemoteAddr("10.0.0.3"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(complaintRequest)))
                    .andExpect(status().isCreated());
        }

        // 21st request from same IP
        complaintRequest.setIncidentAddress("Address 21");
        mockMvc.perform(post("/api/v1/complaints/anonymous")
                        .with(request -> { request.setRemoteAddr("10.0.0.3"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complaintRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("RATE_LIMITED"));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "operator", roles = "OPERATOR")
    void whenTriageComplaint_thenReturn200Success() throws Exception {
        // Create an anonymous complaint first
        mockMvc.perform(post("/api/v1/complaints/anonymous")
                        .with(request -> { request.setRemoteAddr("10.0.0.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complaintRequest)))
                .andExpect(status().isCreated());

        // Get the ID of the complaint (scoped to this test's own tenant)
        Long complaintId = complaintRepository.findAll().stream()
                .filter(c -> OPERATOR_REAL_TENANT_ID == c.getTenantId())
                .findFirst().orElseThrow()
                .getComplaintId();

        // Perform patch request to triage
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/complaints/{complaintId}/triage", complaintId)
                        .param("status", "VERIFIED")
                        .with(request -> { request.setRemoteAddr("10.0.0.4"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Complaint state successfully updated to VERIFIED"));

        // Verify status in DB
        PublicComplaint updated = complaintRepository.findById(complaintId).orElseThrow();
        assertEquals(ComplaintStatus.VERIFIED, updated.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getTriagedAt());
    }

    @Test
    void whenSubmitAnonymousComplaintWithoutTenantId_thenResolveFromZone() throws Exception {
        // Remove tenantId from payload to force server-side resolution
        complaintRequest.setTenantId(null);

        mockMvc.perform(post("/api/v1/complaints/anonymous")
                        .with(request -> { request.setRemoteAddr("10.0.0.9"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complaintRequest)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        PublicComplaint saved = complaintRepository.findAll().stream()
                .filter(c -> "123 Power Grid Lane".equals(c.getIncidentAddress()) && OPERATOR_REAL_ZONE_ID == c.getZoneId())
                .findFirst().orElseThrow();
        assertEquals(OPERATOR_REAL_TENANT_ID, saved.getTenantId()); // Resolved from this test's zone
    }

    @Test
    void whenSubmitAnonymousComplaintWithNonExistentZone_thenReturn400BadRequest() throws Exception {
        complaintRequest.setZoneId(999L); // non-existent zone
        complaintRequest.setTenantId(null);
        complaintRequest.setIncidentAddress("999 Unknown St");

        mockMvc.perform(post("/api/v1/complaints/anonymous")
                        .with(request -> { request.setRemoteAddr("10.0.0.10"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complaintRequest)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_ZONE"))
                .andExpect(jsonPath("$.message").value("The specified zone ID does not exist."));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "operator", roles = "OPERATOR")
    void whenOperatorListComplaints_thenReturnSameTenantComplaintsOnly() throws Exception {
        // Seed Tenant 1's complaint (real, pre-existing tenant 1)
        PublicComplaint c1 = new PublicComplaint();
        c1.setTenantId(OPERATOR_REAL_TENANT_ID);
        c1.setZoneId(OPERATOR_REAL_ZONE_ID);
        c1.setIncidentAddress("123 Power Grid Lane");
        c1.setAddressHash("hashT1-" + OPERATOR_REAL_TENANT_ID);
        c1.setDescription("T1 Complaint");
        c1.setSubmitterIpHash("ip1");
        c1.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c1.setSubmittedAt(ZonedDateTime.now());
        complaintRepository.saveAndFlush(c1);

        // Seed Tenant 2 and its complaint
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_2_ID, "Complaint Test Tenant 2");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_2_ID, TEST_TENANT_2_ID, "Complaint Test Zone 2");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, 'OPERATOR')",
                TEST_TENANT_2_ID, TEST_OPERATOR_2_USERNAME, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq");

        PublicComplaint c2 = new PublicComplaint();
        c2.setTenantId(TEST_TENANT_2_ID);
        c2.setZoneId(TEST_ZONE_2_ID);
        c2.setIncidentAddress("456 Street T2");
        c2.setAddressHash("hashT2-" + TEST_TENANT_2_ID);
        c2.setDescription("T2 Complaint");
        c2.setSubmitterIpHash("ip2");
        c2.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c2.setSubmittedAt(ZonedDateTime.now());
        complaintRepository.saveAndFlush(c2);

        String token1 = loginAndGetToken("operator", "password");
        String token2 = loginAndGetToken(TEST_OPERATOR_2_USERNAME, "password");

        // Operator of Tenant 1 fetches data
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token1)
                        .with(request -> { request.setRemoteAddr("10.0.0.12"); return request; }))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].incidentAddress").value("123 Power Grid Lane"));

        // Operator of Tenant 2 fetches data
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token2)
                        .with(request -> { request.setRemoteAddr("10.0.0.12"); return request; }))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].incidentAddress").value("456 Street T2"));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "operator", roles = "OPERATOR")
    void whenOperatorTriageForeignComplaint_thenReturn403Forbidden() throws Exception {
        // Seed a dedicated second tenant/zone for this test (cleaned up in setUp())
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_2_ID, "Complaint Test Tenant 2");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_2_ID, TEST_TENANT_2_ID, "Complaint Test Zone 2");

        PublicComplaint c2 = new PublicComplaint();
        c2.setTenantId(TEST_TENANT_2_ID);
        c2.setZoneId(TEST_ZONE_2_ID);
        c2.setIncidentAddress("456 Street T2");
        c2.setAddressHash("hashT2-" + TEST_TENANT_2_ID);
        c2.setDescription("T2 Complaint");
        c2.setSubmitterIpHash("ip2");
        c2.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c2.setSubmittedAt(ZonedDateTime.now());
        c2 = complaintRepository.saveAndFlush(c2);

        String token1 = loginAndGetToken("operator", "password");

        // Operator of Tenant 1 tries to triage Tenant 2's complaint
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/complaints/" + c2.getComplaintId() + "/triage")
                        .header("Authorization", "Bearer " + token1)
                        .param("status", "VERIFIED")
                        .with(request -> { request.setRemoteAddr("10.0.0.12"); return request; }))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isForbidden());
    }
}
