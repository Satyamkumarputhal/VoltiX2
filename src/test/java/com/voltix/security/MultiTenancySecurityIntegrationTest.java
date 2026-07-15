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

@SpringBootTest
class MultiTenancySecurityIntegrationTest {

    @Autowired
    private PublicComplaintRepository complaintRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long complaintTenant1Id;
    private Long complaintTenant2Id;

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

        // Seed 2 tenants
        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (1, 'Tenant 1', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (2, 'Tenant 2', 'ACTIVE')");

        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (1, 1, 'Zone 1', 1.0)");
        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (2, 2, 'Zone 2', 1.0)");

        // Seed tenant 1 complaint
        PublicComplaint c1 = new PublicComplaint();
        c1.setTenantId(1L);
        c1.setZoneId(1L);
        c1.setIncidentAddress("123 Street T1");
        c1.setAddressHash("hash1");
        c1.setDescription("T1 Anomaly");
        c1.setSubmitterIpHash("ip1");
        c1.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c1.setSubmittedAt(ZonedDateTime.now());
        c1 = complaintRepository.save(c1);
        complaintTenant1Id = c1.getComplaintId();

        // Seed tenant 2 complaint
        PublicComplaint c2 = new PublicComplaint();
        c2.setTenantId(2L);
        c2.setZoneId(2L);
        c2.setIncidentAddress("456 Street T2");
        c2.setAddressHash("hash2");
        c2.setDescription("T2 Anomaly");
        c2.setSubmitterIpHash("ip2");
        c2.setStatus(ComplaintStatus.PENDING_VERIFICATION);
        c2.setSubmittedAt(ZonedDateTime.now());
        c2 = complaintRepository.save(c2);
        complaintTenant2Id = c2.getComplaintId();
    }

    @Test
    void whenTenantContextMatchesEntity_thenReturnEntitySuccessfully() {
        TenantContext.setCurrentTenant(1L);
        try {
            Optional<PublicComplaint> complaint = complaintRepository.findById(complaintTenant1Id);
            assertEquals(true, complaint.isPresent());
            assertEquals(1L, complaint.get().getTenantId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void whenTenantContextDiffers_thenThrowAccessDeniedException() {
        TenantContext.setCurrentTenant(1L);
        try {
            // Attempting to retrieve Tenant 2's complaint should trigger the Aspect and throw AccessDeniedException
            assertThrows(AccessDeniedException.class, () -> {
                complaintRepository.findById(complaintTenant2Id);
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void whenQueryingDeduplicationAndListByStatus_thenOnlyReturnSameTenantData() {
        // Querying for Tenant 1 context
        TenantContext.setCurrentTenant(1L);
        try {
            // Deduplication query: should find complaint for address hash "hash1" since it belongs to Tenant 1
            Optional<PublicComplaint> complaintT1 = complaintRepository
                    .findFirstByAddressHashAndStatusAndSubmittedAtAfterAndTenantId(
                            "hash1",
                            ComplaintStatus.PENDING_VERIFICATION,
                            ZonedDateTime.now().minusHours(1),
                            1L
                    );
            assertEquals(true, complaintT1.isPresent());

            // Deduplication query: should NOT find complaint for address hash "hash2" since it belongs to Tenant 2
            Optional<PublicComplaint> complaintT2 = complaintRepository
                    .findFirstByAddressHashAndStatusAndSubmittedAtAfterAndTenantId(
                            "hash2",
                            ComplaintStatus.PENDING_VERIFICATION,
                            ZonedDateTime.now().minusHours(1),
                            1L
                    );
            assertEquals(false, complaintT2.isPresent());

            // List by status query: should only return Tenant 1's complaint
            java.util.List<PublicComplaint> list = complaintRepository
                    .findByStatusAndTenantIdOrderBySubmittedAtDesc(ComplaintStatus.PENDING_VERIFICATION, 1L);
            assertEquals(1, list.size());
            assertEquals("123 Street T1", list.get(0).getIncidentAddress());
        } finally {
            TenantContext.clear();
        }
    }
}
