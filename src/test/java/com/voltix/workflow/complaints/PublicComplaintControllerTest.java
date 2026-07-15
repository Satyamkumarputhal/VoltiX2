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

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM system_alerts");
        jdbcTemplate.execute("DELETE FROM public_complaints");
        jdbcTemplate.execute("DELETE FROM zone_hourly_aggregates");
        jdbcTemplate.execute("DELETE FROM metrics_history");
        jdbcTemplate.execute("DELETE FROM telemetry_staging");
        jdbcTemplate.execute("DELETE FROM smart_meters");
        jdbcTemplate.execute("DELETE FROM grid_zones");
        jdbcTemplate.execute("DELETE FROM tenants");

        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (1, 'Test Tenant', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (1, 1, 'Zone A', 1.0)");

        complaintRequest = new ComplaintRequest();
        complaintRequest.setZoneId(1L);
        complaintRequest.setIncidentAddress("123 Power Grid Lane");
        complaintRequest.setDescription("Wire tapping anomaly observed on transformer node.");
        complaintRequest.setTenantId(1L);
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

        assertEquals(1, complaintRepository.count());
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
        assertEquals(1, complaintRepository.count());
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

        // Get the ID of the complaint
        Long complaintId = complaintRepository.findAll().get(0).getComplaintId();

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
                .filter(c -> "123 Power Grid Lane".equals(c.getIncidentAddress()))
                .findFirst().orElseThrow();
        assertEquals(1L, saved.getTenantId()); // Resolved from Zone A (tenant_id = 1)
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
        // Seed Tenant 1's complaint
        PublicComplaint c1 = new PublicComplaint();
        c1.setTenantId(1L);
        c1.setZoneId(1L);
        c1.setIncidentAddress("123 Power Grid Lane");
        c1.setAddressHash("hashT1");
        c1.setDescription("T1 Complaint");
        c1.setSubmitterIpHash("ip1");
        c1.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c1.setSubmittedAt(ZonedDateTime.now());
        complaintRepository.saveAndFlush(c1);

        // Seed Tenant 2 and its complaint
        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (2, 'Tenant 2', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (2, 2, 'Zone B', 1.0)");

        PublicComplaint c2 = new PublicComplaint();
        c2.setTenantId(2L);
        c2.setZoneId(2L);
        c2.setIncidentAddress("456 Street T2");
        c2.setAddressHash("hashT2");
        c2.setDescription("T2 Complaint");
        c2.setSubmitterIpHash("ip2");
        c2.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c2.setSubmittedAt(ZonedDateTime.now());
        complaintRepository.saveAndFlush(c2);

        // Operator of Tenant 1 fetches data
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/complaints")
                        .header("Authorization", "Bearer eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAib3BlcmF0b3ItMSIsICJ0ZW5hbnRfaWQiOiAxLCAicm9sZXMiOiBbIk9QRVJBVE9SIl0sICJleHAiOiAxODgyNzI4MDAwfQ.c2lnbmF0dXJl")
                        .with(request -> { request.setRemoteAddr("10.0.0.12"); return request; }))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].incidentAddress").value("123 Power Grid Lane"));

        // Operator of Tenant 2 fetches data
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/complaints")
                        .header("Authorization", "Bearer eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAib3BlcmF0b3ItMiIsICJ0ZW5hbnRfaWQiOiAyLCAicm9sZXMiOiBbIk9QRVJBVE9SIl0sICJleHAiOiAxODgyNzI4MDAwfQ.c2lnbmF0dXJl")
                        .with(request -> { request.setRemoteAddr("10.0.0.12"); return request; }))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].incidentAddress").value("456 Street T2"));
    }
}
