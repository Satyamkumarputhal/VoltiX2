package com.voltix.security;

import com.voltix.workflow.complaints.PublicComplaint;
import com.voltix.workflow.complaints.PublicComplaintRepository;
import com.voltix.workflow.complaints.ComplaintStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MultiTenancySecurityIntegrationTest {

    @Autowired
    private PublicComplaintRepository complaintRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long complaintTenant1Id;
    private Long complaintTenant2Id;

    // Dedicated fixture IDs for this test only -- chosen well outside the
    // range used by real dev/demo seed data (tenants 1-2, zones 1-2, meters
    // SM-0..SM-4, users operator/inspector/admin) so this test can never
    // collide with or delete them. This test used to unconditionally
    // DELETE FROM smart_meters/grid_zones/users/tenants (ALL rows), which
    // silently destroyed the shared dev database's seed data on every
    // "mvn test" run.
    private static final long TEST_TENANT_1_ID = 999_005L;
    private static final long TEST_TENANT_2_ID = 999_006L;
    private static final long TEST_ZONE_1_ID = 999_005L;
    private static final long TEST_ZONE_2_ID = 999_006L;

    @BeforeEach
    void setUp() {
        // Only ever touches this test's own dedicated fixture rows -- never
        // a blanket DELETE affecting other tenants/zones/meters/users.
        jdbcTemplate.update("DELETE FROM public_complaints WHERE tenant_id IN (?, ?)", TEST_TENANT_1_ID, TEST_TENANT_2_ID);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id IN (?, ?)", TEST_ZONE_1_ID, TEST_ZONE_2_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", TEST_TENANT_1_ID, TEST_TENANT_2_ID);

        // Seed 2 tenants
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_1_ID, "MultiTenancy Test Tenant 1");
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_2_ID, "MultiTenancy Test Tenant 2");

        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_1_ID, TEST_TENANT_1_ID, "MultiTenancy Test Zone 1");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_2_ID, TEST_TENANT_2_ID, "MultiTenancy Test Zone 2");

        // Seed tenant 1 complaint
        PublicComplaint c1 = new PublicComplaint();
        c1.setTenantId(TEST_TENANT_1_ID);
        c1.setZoneId(TEST_ZONE_1_ID);
        c1.setIncidentAddress("123 Street T1");
        c1.setAddressHash("hash1-" + TEST_TENANT_1_ID);
        c1.setDescription("T1 Anomaly");
        c1.setSubmitterIpHash("ip1");
        c1.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c1.setSubmittedAt(ZonedDateTime.now());
        c1 = complaintRepository.save(c1);
        complaintTenant1Id = c1.getComplaintId();

        // Seed tenant 2 complaint
        PublicComplaint c2 = new PublicComplaint();
        c2.setTenantId(TEST_TENANT_2_ID);
        c2.setZoneId(TEST_ZONE_2_ID);
        c2.setIncidentAddress("456 Street T2");
        c2.setAddressHash("hash2-" + TEST_TENANT_2_ID);
        c2.setDescription("T2 Anomaly");
        c2.setSubmitterIpHash("ip2");
        c2.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c2.setSubmittedAt(ZonedDateTime.now());
        c2 = complaintRepository.save(c2);
        complaintTenant2Id = c2.getComplaintId();
    }

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private com.voltix.platform.config.VoltixProperties properties;

    private String generateToken(String username, Long tenantId, String role) throws Exception {
        com.nimbusds.jwt.JWTClaimsSet claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                .subject(username)
                .claim("tenant_id", tenantId)
                .claim("roles", java.util.List.of(role))
                .expirationTime(new java.util.Date(System.currentTimeMillis() + 86400000))
                .build();
        com.nimbusds.jwt.SignedJWT signedJWT = new com.nimbusds.jwt.SignedJWT(
                new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(new com.nimbusds.jose.crypto.MACSigner(properties.getSecurity().getJwtSecret()));
        return signedJWT.serialize();
    }

    @Test
    void whenTenantContextMatchesEntity_thenReturnEntitySuccessfully() throws Exception {
        String token = generateToken("operator1", TEST_TENANT_1_ID, "OPERATOR");
        
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/complaints/" + complaintTenant1Id + "/triage")
                .param("status", "VERIFIED")
                .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test
    void whenTenantContextDiffers_thenThrowAccessDeniedException() throws Exception {
        String token = generateToken("operator1", TEST_TENANT_1_ID, "OPERATOR");
        
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/complaints/" + complaintTenant2Id + "/triage")
                .param("status", "VERIFIED")
                .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void whenQueryingDeduplicationAndListByStatus_thenOnlyReturnSameTenantData() {
        // Querying for Tenant 1 context
        TenantContext.setCurrentTenant(TEST_TENANT_1_ID);
        try {
            // Deduplication query: should find complaint for address hash "hash1" since it belongs to Tenant 1
            Optional<PublicComplaint> complaintT1 = complaintRepository
                    .findFirstByAddressHashAndStatusAndSubmittedAtAfterAndTenantId(
                            "hash1-" + TEST_TENANT_1_ID,
                            ComplaintStatus.PENDING_VERIFICATION,
                            ZonedDateTime.now().minusHours(1),
                            TEST_TENANT_1_ID
                    );
            assertEquals(true, complaintT1.isPresent());

            // Deduplication query: should NOT find complaint for address hash "hash2" since it belongs to Tenant 2
            Optional<PublicComplaint> complaintT2 = complaintRepository
                    .findFirstByAddressHashAndStatusAndSubmittedAtAfterAndTenantId(
                            "hash2-" + TEST_TENANT_2_ID,
                            ComplaintStatus.PENDING_VERIFICATION,
                            ZonedDateTime.now().minusHours(1),
                            TEST_TENANT_1_ID
                    );
            assertEquals(false, complaintT2.isPresent());

            // List by status query: should only return Tenant 1's complaint
            java.util.List<PublicComplaint> list = complaintRepository
                    .findByStatusAndTenantIdOrderBySubmittedAtDesc(ComplaintStatus.PENDING_VERIFICATION, TEST_TENANT_1_ID);
            assertEquals(1, list.size());
            assertEquals("123 Street T1", list.get(0).getIncidentAddress());
        } finally {
            TenantContext.clear();
        }
    }
}
