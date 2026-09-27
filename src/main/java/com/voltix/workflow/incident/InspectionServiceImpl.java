package com.voltix.workflow.incident;

import com.voltix.security.User;
import com.voltix.security.UserRepository;
import com.voltix.workflow.incident.dto.InspectionCreateRequest;
import com.voltix.workflow.incident.dto.InspectionResponse;
import com.voltix.workflow.incident.dto.InspectionUpdateRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class InspectionServiceImpl implements InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionServiceImpl.class);

    private final InspectionRepository inspectionRepository;
    private final FieldJobRepository fieldJobRepository;
    private final UserRepository userRepository;

    public InspectionServiceImpl(InspectionRepository inspectionRepository,
                                 FieldJobRepository fieldJobRepository,
                                 UserRepository userRepository) {
        this.inspectionRepository = inspectionRepository;
        this.fieldJobRepository = fieldJobRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public InspectionResponse createInspectionForFieldJob(Long tenantId, Long fieldJobId) {
        log.info("Creating inspection for tenantId={}, fieldJobId={}", tenantId, fieldJobId);

        // Load the field job and verify it belongs to the tenant
        FieldJob fieldJob = fieldJobRepository.findByFieldJobIdAndTenantId(fieldJobId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Field job not found: id=" + fieldJobId + " for tenant=" + tenantId));

        // Check if inspection already exists for this field job (idempotent)
        var existing = inspectionRepository.findByFieldJobIdAndTenantId(fieldJobId, tenantId);
        if (existing.isPresent()) {
            log.info("Inspection already exists for field job id={}, tenantId={}, returning existing", fieldJobId, tenantId);
            return new InspectionResponse(existing.get());
        }

        // Field job must be COMPLETED to create an inspection
        if (fieldJob.getStatus() != FieldJobStatus.COMPLETED) {
            throw new IllegalStateException("Cannot create inspection for field job with status: " + fieldJob.getStatus() + ". Only COMPLETED field jobs can have inspections.");
        }

        // Get the assigned inspector from the field job
        Long inspectorId = fieldJob.getAssignedInspectorId();
        if (inspectorId == null) {
            throw new IllegalStateException("Cannot create inspection for field job without assigned inspector");
        }

        // Verify inspector belongs to the same tenant and has INSPECTOR role
        User inspector = userRepository.findById(inspectorId)
                .orElseThrow(() -> new IllegalArgumentException("Assigned inspector not found: " + inspectorId));

        if (!inspector.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Assigned inspector does not belong to the same tenant");
        }

        if (!"INSPECTOR".equalsIgnoreCase(inspector.getRole())) {
            throw new IllegalArgumentException("Assigned user must have INSPECTOR role");
        }

        // Create the inspection
        Inspection inspection = new Inspection();
        inspection.setTenantId(tenantId);
        inspection.setFieldJobId(fieldJobId);
        inspection.setInspectorId(inspectorId);
        inspection.setStartedAt(fieldJob.getStartedAt() != null ? fieldJob.getStartedAt() : ZonedDateTime.now());
        inspection.setResult(null); // Initially null
        inspection.setCompletedAt(null);
        inspection.setCreatedAt(ZonedDateTime.now());

        Inspection saved = inspectionRepository.save(inspection);
        log.info("Created inspection: inspectionId={}, tenantId={}, fieldJobId={}", saved.getInspectionId(), tenantId, fieldJobId);

        return new InspectionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionResponse> getInspectionsForTenant(Long tenantId) {
        log.debug("Fetching inspections for tenantId={}", tenantId);
        List<Inspection> inspections = inspectionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return inspections.stream().map(InspectionResponse::new).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionResponse getInspectionById(Long inspectionId, Long tenantId) {
        log.debug("Fetching inspection by id={} for tenantId={}", inspectionId, tenantId);
        Inspection inspection = inspectionRepository.findByInspectionIdAndTenantId(inspectionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Inspection not found: id=" + inspectionId + " for tenant=" + tenantId));
        return new InspectionResponse(inspection);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionResponse getInspectionByFieldJobId(Long fieldJobId, Long tenantId) {
        log.debug("Fetching inspection by fieldJobId={} for tenantId={}", fieldJobId, tenantId);
        Inspection inspection = inspectionRepository.findByFieldJobIdAndTenantId(fieldJobId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Inspection not found for field job: id=" + fieldJobId + " for tenant=" + tenantId));
        return new InspectionResponse(inspection);
    }

    @Override
    @Transactional
    public InspectionResponse updateInspection(Long inspectionId, Long tenantId, String username, InspectionUpdateRequest request) {
        log.info("Updating inspection id={} for tenantId={}, username={}", inspectionId, tenantId, username);

        Inspection inspection = inspectionRepository.findByInspectionIdAndTenantId(inspectionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Inspection not found: id=" + inspectionId + " for tenant=" + tenantId));

        // Validate the user exists and belongs to tenant
        User user = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // Only the assigned inspector (or ADMIN/OPERATOR) can update the inspection
        if (!"ADMIN".equalsIgnoreCase(user.getRole()) && !"OPERATOR".equalsIgnoreCase(user.getRole())) {
            if (!user.getUserId().equals(inspection.getInspectorId())) {
                throw new IllegalArgumentException("Inspector can only update their assigned inspections");
            }
        }

        // Update fields if provided
        if (request.getFinding() != null) {
            inspection.setFinding(request.getFinding());
        }
        if (request.getConclusion() != null) {
            inspection.setConclusion(request.getConclusion());
        }
        if (request.getEvidenceMetadata() != null) {
            inspection.setEvidenceMetadata(request.getEvidenceMetadata());
        }
        if (request.getRecommendation() != null) {
            inspection.setRecommendation(request.getRecommendation());
        }
        if (request.getResult() != null) {
            inspection.setResult(request.getResult());
            // Set completedAt when result is submitted, if not already set
            if (inspection.getCompletedAt() == null) {
                inspection.setCompletedAt(ZonedDateTime.now());
            }
        }
        // Note: We do not overwrite existing completedAt once set

        Inspection saved = inspectionRepository.save(inspection);
        log.info("Updated inspection: inspectionId={}, tenantId={}", saved.getInspectionId(), tenantId);

        return new InspectionResponse(saved);
    }
}