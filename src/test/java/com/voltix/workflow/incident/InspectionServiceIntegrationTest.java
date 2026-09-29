package com.voltix.workflow.incident;

import com.voltix.workflow.incident.dto.FieldJobCreateRequest;
import com.voltix.workflow.incident.dto.FieldJobResponse;
import com.voltix.workflow.incident.dto.FieldJobStatusUpdateRequest;
import com.voltix.workflow.incident.dto.InspectionCreateRequest;
import com.voltix.workflow.incident.dto.InspectionResponse;
import com.voltix.workflow.incident.dto.InspectionUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext
class InspectionServiceIntegrationTest {

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private FieldJobService fieldJobService;

    @Autowired
    private FieldJobRepository fieldJobRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TEST_TENANT_ID = 999_030L;
    private static final long TEST_ZONE_ID = 999_030L;
    private static final String TEST_METER_ID = "METER-INSPECTION-TEST-999030";
    private static final String TEST_OPERATOR = "insp-test-operator";
    private static final String TEST_INSPECTOR = "insp-test-inspector";
    private static final String TEST_ADMIN = "insp-test-admin";
    private static final String TEST_PASSWORD = "password";

    @BeforeEach
    void setUp() {
        // Clean up test data - order matters due to FK constraints
        jdbcTemplate.update("DELETE FROM inspections WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_031L);
        jdbcTemplate.update("DELETE FROM field_jobs WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_031L);
        jdbcTemplate.update("DELETE FROM incidents WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_031L);
        jdbcTemplate.update("DELETE FROM system_alerts WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_031L);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", TEST_METER_ID);
        jdbcTemplate.update("DELETE FROM smart_meters WHERE meter_id = ?", "METER-OTHER-999031");
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id IN (?, ?)", TEST_ZONE_ID, 999_031L);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_031L);
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?, ?)", TEST_OPERATOR, TEST_INSPECTOR, TEST_ADMIN);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", TEST_TENANT_ID, 999_031L);

        // Create test tenant
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TEST_TENANT_ID, "Inspection Test Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                TEST_ZONE_ID, TEST_TENANT_ID, "Inspection Test Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                TEST_METER_ID, TEST_TENANT_ID, TEST_ZONE_ID, "SN-" + TEST_METER_ID);

        // Insert test users
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, TEST_OPERATOR, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "OPERATOR");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, TEST_INSPECTOR, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, TEST_ADMIN, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "ADMIN");
    }

    // Helper to store inspection1 for cross-tenant test
    private Long storedInspectionId;

    private Long getInspectorId() {
        return jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, TEST_INSPECTOR);
    }

    private Long createIncidentAndFieldJob() {
        // Create system alert
        long alertId = 8001L + (int)(System.currentTimeMillis() % 10000);
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
        incident.setTitle("Test Incident for Inspection");
        incident.setDescription("Test incident description");
        incident.setStatus(IncidentStatus.OPEN);
        incident.setCreatedBy(1L);
        incident.setCreatedAt(ZonedDateTime.now());
        incident = incidentRepository.save(incident);

        // Create field job assigned to inspector
        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(incident.getIncidentId());
        request.setAssignedInspectorId(getInspectorId());
        request.setPriority(FieldJobPriority.NORMAL);
        FieldJobResponse created = fieldJobService.createFieldJob(TEST_TENANT_ID, TEST_OPERATOR, request);

        return created.getFieldJobId();
    }

    // 1. auto-create Inspection on COMPLETED
    @Test
    void testAutoCreateInspectionOnCompleted() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Transition to COMPLETED via inspector
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        FieldJobResponse completed = fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        assertEquals(FieldJobStatus.COMPLETED, completed.getStatus());

        // Verify inspection was auto-created
        InspectionResponse inspection = inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID);
        assertNotNull(inspection);
        assertEquals(fieldJobId, inspection.getFieldJobId());
        assertEquals(TEST_TENANT_ID, inspection.getTenantId());
        assertEquals(getInspectorId(), inspection.getInspectorId());
        assertNotNull(inspection.getStartedAt());
        assertNull(inspection.getCompletedAt());
        assertNull(inspection.getResult());
    }

    // 2. no Inspection before COMPLETED
    @Test
    void testNoInspectionBeforeCompleted() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Verify no inspection exists at PENDING
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Inspection not found"));

        // Transition to ASSIGNED
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Still no inspection
        ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Inspection not found"));

        // Transition to ON_SITE
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Still no inspection
        ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Inspection not found"));
    }

    // 3. idempotent repeated COMPLETED handling
    @Test
    void testIdempotentCompletedTransition() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        FieldJobResponse completed1 = fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Get the inspection created
        InspectionResponse inspection1 = inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID);
        Long inspectionId = inspection1.getInspectionId();

        // Try to transition to COMPLETED again (idempotent)
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        FieldJobResponse completed2 = fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Verify inspection is the same (not duplicated)
        InspectionResponse inspection2 = inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID);
        assertEquals(inspectionId, inspection2.getInspectionId());
    }

    // 4. tenant isolation
    @Test
    void testTenantIsolation() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Verify inspection exists for tenant
        InspectionResponse inspection = inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID);
        assertNotNull(inspection);
        storedInspectionId = inspection.getInspectionId();

        // Try to access from different tenant
        long otherTenantId = 999_031L;
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                otherTenantId, "Other Tenant");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionByFieldJobId(fieldJobId, otherTenantId));
        assertTrue(ex.getMessage().contains("Inspection not found"));

        // Also test getInspectionById with wrong tenant
        ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionById(storedInspectionId, otherTenantId));
        assertTrue(ex.getMessage().contains("Inspection not found"));
    }

    // 5. cross-tenant FieldJob cannot create/access Inspection
    @Test
    void testCrossTenantFieldJobCannotAccessInspection() {
        // Create another tenant
        long otherTenantId = 999_031L;
        long otherZoneId = 999_031L;
        String otherMeterId = "METER-OTHER-999031";
        String otherInspector = "insp-other-inspector";

        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                otherTenantId, "Other Tenant");
        jdbcTemplate.update("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                otherZoneId, otherTenantId, "Other Zone");
        jdbcTemplate.update("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                otherMeterId, otherTenantId, otherZoneId, "SN-" + otherMeterId);
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                otherTenantId, otherInspector, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");

        // Create incident and field job for other tenant
        long otherAlertId = 9001L;
        jdbcTemplate.update("""
                INSERT INTO system_alerts (alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at)
                VALUES (?, ?, ?, ?, 'NTL_ANOMALY', 'HIGH', 0.85, 2.125, 'OPEN', NOW())
                """, otherAlertId, otherTenantId, otherMeterId, otherZoneId);

        Incident otherIncident = new Incident();
        otherIncident.setTenantId(otherTenantId);
        otherIncident.setIncidentNumber("INC-20260101-009001");
        otherIncident.setSourceAlertId(otherAlertId);
        otherIncident.setMeterId(otherMeterId);
        otherIncident.setZoneId(otherZoneId);
        otherIncident.setAlertType("NTL_ANOMALY");
        otherIncident.setSeverity("HIGH");
        otherIncident.setTitle("Other Tenant Incident");
        otherIncident.setDescription("Other tenant incident");
        otherIncident.setStatus(IncidentStatus.OPEN);
        otherIncident.setCreatedBy(1L);
        otherIncident.setCreatedAt(ZonedDateTime.now());
        otherIncident = incidentRepository.save(otherIncident);

        Long otherInspectorId = jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE username = ?", Long.class, otherInspector);

        // Create operator user for other tenant
        String otherOperator = "other-operator";
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                otherTenantId, otherOperator, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "OPERATOR");

        FieldJobCreateRequest request = new FieldJobCreateRequest();
        request.setIncidentId(otherIncident.getIncidentId());
        request.setAssignedInspectorId(otherInspectorId);
        FieldJobResponse otherFieldJob = fieldJobService.createFieldJob(otherTenantId, otherOperator, request);

        // Complete the other tenant's field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(otherFieldJob.getFieldJobId(), otherTenantId, otherInspector, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(otherFieldJob.getFieldJobId(), otherTenantId, otherInspector, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(otherFieldJob.getFieldJobId(), otherTenantId, otherInspector, updateRequest);

        // Tenant A tries to access Tenant B's inspection
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionByFieldJobId(otherFieldJob.getFieldJobId(), TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Inspection not found"));

        // Tenant A tries to access Tenant B's inspection by ID
        InspectionResponse otherInspection = inspectionService.getInspectionByFieldJobId(otherFieldJob.getFieldJobId(), otherTenantId);
        ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionById(otherInspection.getInspectionId(), TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Inspection not found"));
    }

    // 6. assigned inspector can update
    @Test
    void testAssignedInspectorCanUpdate() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Inspector updates the inspection
        InspectionUpdateRequest updateInspection = new InspectionUpdateRequest();
        updateInspection.setFinding("Found anomaly at meter");
        updateInspection.setConclusion("Meter tampering confirmed");
        updateInspection.setEvidenceMetadata("{\"photos\":[\"photo1.jpg\"]}");
        updateInspection.setRecommendation("Replace meter immediately");
        updateInspection.setResult(InspectionResult.CONFIRMED);

        InspectionResponse updated = inspectionService.updateInspection(
                inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID).getInspectionId(),
                TEST_TENANT_ID, TEST_INSPECTOR, updateInspection);

        assertEquals("Found anomaly at meter", updated.getFinding());
        assertEquals("Meter tampering confirmed", updated.getConclusion());
        assertEquals("{\"photos\":[\"photo1.jpg\"]}", updated.getEvidenceMetadata());
        assertEquals("Replace meter immediately", updated.getRecommendation());
        assertEquals(InspectionResult.CONFIRMED, updated.getResult());
        assertNotNull(updated.getCompletedAt());
    }

    // 7. wrong inspector cannot update
    @Test
    void testWrongInspectorCannotUpdate() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Create second inspector
        String otherInspector = "insp-test-inspector-2";
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                TEST_TENANT_ID, otherInspector, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");

        // Other inspector tries to update
        InspectionUpdateRequest updateInspection = new InspectionUpdateRequest();
        updateInspection.setFinding("Wrong inspector update");
        updateInspection.setResult(InspectionResult.CONFIRMED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.updateInspection(
                        inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID).getInspectionId(),
                        TEST_TENANT_ID, otherInspector, updateInspection));
        assertTrue(ex.getMessage().contains("can only update their assigned inspections"));
    }

    // 8. cross-tenant inspector assignment/update rejected
    @Test
    void testCrossTenantInspectorUpdateRejected() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Create inspector in another tenant
        long otherTenantId = 999_031L;
        String otherInspector = "insp-cross-tenant";
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                999_031L, "Other Tenant");
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                999_031L, otherInspector, "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq", "INSPECTOR");

        // Other tenant's inspector tries to update
        InspectionUpdateRequest updateInspection = new InspectionUpdateRequest();
        updateInspection.setFinding("Cross tenant update");
        updateInspection.setResult(InspectionResult.CONFIRMED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.updateInspection(
                        inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID).getInspectionId(),
                        TEST_TENANT_ID, otherInspector, updateInspection));
        assertTrue(ex.getMessage().contains("User not found") || ex.getMessage().contains("can only update their assigned inspections"));
    }

    // 9. result update sets completedAt
    @Test
    void testResultUpdateSetsCompletedAt() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Verify initial completedAt is null
        InspectionResponse inspection = inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID);
        assertNull(inspection.getCompletedAt());
        assertNull(inspection.getResult());

        // Update with result
        InspectionUpdateRequest updateInspection = new InspectionUpdateRequest();
        updateInspection.setResult(InspectionResult.CONFIRMED);

        InspectionResponse updated = inspectionService.updateInspection(
                inspection.getInspectionId(), TEST_TENANT_ID, TEST_INSPECTOR, updateInspection);

        assertNotNull(updated.getCompletedAt());
        assertEquals(InspectionResult.CONFIRMED, updated.getResult());
    }

    // 10. existing completedAt is preserved
    @Test
    void testExistingCompletedAtPreserved() {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // First update with result - sets completedAt
        InspectionUpdateRequest updateInspection = new InspectionUpdateRequest();
        updateInspection.setResult(InspectionResult.CONFIRMED);
        InspectionResponse updated1 = inspectionService.updateInspection(
                inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID).getInspectionId(),
                TEST_TENANT_ID, TEST_INSPECTOR, updateInspection);

        ZonedDateTime firstCompletedAt = updated1.getCompletedAt();
        assertNotNull(firstCompletedAt);

        // Second update with different result - completedAt should be preserved
        InspectionUpdateRequest updateInspection2 = new InspectionUpdateRequest();
        updateInspection2.setResult(InspectionResult.FALSE_POSITIVE);
        InspectionResponse updated2 = inspectionService.updateInspection(
                updated1.getInspectionId(), TEST_TENANT_ID, TEST_INSPECTOR, updateInspection2);

        // Compare instants with millisecond precision to handle nanosecond differences
        assertEquals(firstCompletedAt.toInstant().toEpochMilli(), updated2.getCompletedAt().toInstant().toEpochMilli());
        assertEquals(InspectionResult.FALSE_POSITIVE, updated2.getResult());
    }

    // 12. concurrent inspection creation race condition (idempotent under race)
    @Test
    void testConcurrentInspectionCreationRace() throws InterruptedException {
        Long fieldJobId = createIncidentAndFieldJob();

        // Complete the field job
        FieldJobStatusUpdateRequest updateRequest = new FieldJobStatusUpdateRequest();
        updateRequest.setStatus(FieldJobStatus.EN_ROUTE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.ON_SITE);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);
        updateRequest.setStatus(FieldJobStatus.COMPLETED);
        fieldJobService.updateFieldJobStatus(fieldJobId, TEST_TENANT_ID, TEST_INSPECTOR, updateRequest);

        // Get the inspection created
        InspectionResponse inspection1 = inspectionService.getInspectionByFieldJobId(fieldJobId, TEST_TENANT_ID);
        Long inspectionId = inspection1.getInspectionId();

        // Now simulate concurrent calls to createInspectionForFieldJob
        // by calling it directly multiple times concurrently
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<InspectionResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> inspectionService.createInspectionForFieldJob(TEST_TENANT_ID, fieldJobId)));
        }

        // All should succeed and return the same inspection
        Set<Long> inspectionIds = new HashSet<>();
        for (Future<InspectionResponse> future : futures) {
            try {
                InspectionResponse result = future.get(5, TimeUnit.SECONDS);
                assertNotNull(result);
                assertEquals(fieldJobId, result.getFieldJobId());
                assertEquals(TEST_TENANT_ID, result.getTenantId());
                inspectionIds.add(result.getInspectionId());
            } catch (ExecutionException | TimeoutException e) {
                throw new AssertionError("Concurrent inspection creation failed", e);
            }
        }
        executor.shutdown();

        // Should only have one unique inspection ID
        assertEquals(1, inspectionIds.size(), "All concurrent calls should return the same inspection");
        assertEquals(inspectionId, inspectionIds.iterator().next(), "Should return the originally created inspection");
    }

    // 11. missing inspection returns expected not-found behavior
    @Test
    void testMissingInspectionReturnsNotFound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionById(999999L, TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Inspection not found"));

        ex = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.getInspectionByFieldJobId(999999L, TEST_TENANT_ID));
        assertTrue(ex.getMessage().contains("Inspection not found"));
    }
}