package com.voltix.workflow.incident;

import com.voltix.security.User;
import com.voltix.security.UserRepository;
import com.voltix.workflow.incident.InspectionService;
import com.voltix.workflow.incident.dto.FieldJobAdminUpdateRequest;
import com.voltix.workflow.incident.dto.FieldJobCreateRequest;
import com.voltix.workflow.incident.dto.FieldJobResponse;
import com.voltix.workflow.incident.dto.FieldJobStatusUpdateRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class FieldJobServiceImpl implements FieldJobService {

    private static final Logger log = LoggerFactory.getLogger(FieldJobServiceImpl.class);

    private final FieldJobRepository fieldJobRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final InspectionService inspectionService;

    public FieldJobServiceImpl(FieldJobRepository fieldJobRepository,
                               IncidentRepository incidentRepository,
                               UserRepository userRepository,
                               InspectionService inspectionService) {
        this.fieldJobRepository = fieldJobRepository;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.inspectionService = inspectionService;
    }

    @Override
    @Transactional
    public FieldJobResponse createFieldJob(Long tenantId, String username, FieldJobCreateRequest request) {
        log.info("Creating field job for tenantId={}, username={}, incidentId={}", tenantId, username, request.getIncidentId());

        // Validate incident exists and belongs to the authenticated tenant
        Incident incident = incidentRepository.findByIncidentIdAndTenantId(request.getIncidentId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found for tenant: " + request.getIncidentId()));

        // Find the authenticated user
        User creator = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // Validate assigned inspector if provided
        if (request.getAssignedInspectorId() != null) {
            Long inspectorId = request.getAssignedInspectorId();
            User inspector = userRepository.findById(inspectorId)
                    .orElseThrow(() -> new IllegalArgumentException("Assigned inspector not found: " + inspectorId));

            if (!inspector.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Assigned inspector does not belong to the same tenant");
            }

            // Validate inspector has appropriate role (INSPECTOR)
            if (!"INSPECTOR".equalsIgnoreCase(inspector.getRole())) {
                throw new IllegalArgumentException("Assigned user must have INSPECTOR role");
            }
        }

        FieldJob fieldJob = new FieldJob();
        fieldJob.setTenantId(tenantId);
        fieldJob.setIncidentId(request.getIncidentId());
        fieldJob.setAssignedInspectorId(request.getAssignedInspectorId());
        fieldJob.setPriority(request.getPriority() != null ? request.getPriority() : FieldJobPriority.NORMAL);
        fieldJob.setStatus(FieldJobStatus.PENDING);
        fieldJob.setInstructions(request.getInstructions());
        fieldJob.setScheduledAt(request.getScheduledAt());
        fieldJob.setCreatedBy(creator.getUserId());
        fieldJob.setCreatedAt(ZonedDateTime.now());

        // If inspector is assigned at creation, set status to ASSIGNED and assignedAt timestamp
        if (request.getAssignedInspectorId() != null) {
            fieldJob.setStatus(FieldJobStatus.ASSIGNED);
            fieldJob.setAssignedAt(ZonedDateTime.now());
        }

        FieldJob saved = fieldJobRepository.save(fieldJob);
        log.info("Created field job: fieldJobId={}, tenantId={}", saved.getFieldJobId(), tenantId);

        return new FieldJobResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FieldJobResponse> getFieldJobsForIncident(Long incidentId, Long tenantId) {
        log.debug("Fetching field jobs for incidentId={} for tenantId={}", incidentId, tenantId);

        // First verify incident belongs to tenant
        incidentRepository.findByIncidentIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: id=" + incidentId + " for tenant=" + tenantId));

        List<FieldJob> fieldJobs = fieldJobRepository.findByIncidentIdAndTenantIdOrderByCreatedAtDesc(incidentId, tenantId);
        return fieldJobs.stream().map(FieldJobResponse::new).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FieldJobResponse getFieldJobById(Long fieldJobId, Long tenantId) {
        log.debug("Fetching field job by id={} for tenantId={}", fieldJobId, tenantId);

        FieldJob fieldJob = fieldJobRepository.findByFieldJobIdAndTenantId(fieldJobId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Field job not found: id=" + fieldJobId + " for tenant=" + tenantId));

        return new FieldJobResponse(fieldJob);
    }

    @Override
    @Transactional
    public FieldJobResponse updateFieldJobStatus(Long fieldJobId, Long tenantId, String username, FieldJobStatusUpdateRequest request) {
        log.info("Updating field job status for fieldJobId={}, tenantId={}, username={}, newStatus={}", fieldJobId, tenantId, username, request.getStatus());

        FieldJob fieldJob = fieldJobRepository.findByFieldJobIdAndTenantId(fieldJobId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Field job not found: id=" + fieldJobId + " for tenant=" + tenantId));

        // Validate the user exists and belongs to tenant (inspector validation)
        User user = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // INSPECTOR can only update status - verify they are the assigned inspector
        if (!"ADMIN".equalsIgnoreCase(user.getRole()) && !"OPERATOR".equalsIgnoreCase(user.getRole())) {
            if (!user.getUserId().equals(fieldJob.getAssignedInspectorId())) {
                throw new IllegalArgumentException("Inspector can only update status of their assigned field jobs");
            }
        }

        FieldJobStatus currentStatus = fieldJob.getStatus();
        FieldJobStatus newStatus = request.getStatus();

        // Additional validation: EN_ROUTE and ON_SITE require assigned inspector
        if ((newStatus == FieldJobStatus.EN_ROUTE || newStatus == FieldJobStatus.ON_SITE) 
                && fieldJob.getAssignedInspectorId() == null) {
            throw new IllegalStateException("Cannot transition to " + newStatus + " without an assigned inspector");
        }

        // Validate status transition
        validateStatusTransition(currentStatus, newStatus);

        fieldJob.setStatus(newStatus);

        // Set timestamps based on status transitions
        ZonedDateTime now = ZonedDateTime.now();
        switch (newStatus) {
            case ASSIGNED -> {
                if (fieldJob.getAssignedAt() == null) {
                    fieldJob.setAssignedAt(now);
                }
            }
            case EN_ROUTE, ON_SITE -> {
                if (fieldJob.getStartedAt() == null) {
                    fieldJob.setStartedAt(now);
                }
            }
            case COMPLETED, FAILED -> {
                if (fieldJob.getCompletedAt() == null) {
                    fieldJob.setCompletedAt(now);
                }
            }
        }

        FieldJob saved = fieldJobRepository.save(fieldJob);
        log.info("Updated field job status: fieldJobId={}, status={}", saved.getFieldJobId(), saved.getStatus());

        // Auto-create inspection when field job transitions to COMPLETED
        if (newStatus == FieldJobStatus.COMPLETED) {
            try {
                inspectionService.createInspectionForFieldJob(tenantId, saved.getFieldJobId());
                log.info("Auto-created inspection for completed field job: fieldJobId={}", saved.getFieldJobId());
            } catch (IllegalStateException e) {
                // Log but don't fail the field job completion if inspection creation fails
                log.warn("Could not create inspection for completed field job {}: {}", saved.getFieldJobId(), e.getMessage());
            } catch (IllegalArgumentException e) {
                log.warn("Could not create inspection for completed field job {}: {}", saved.getFieldJobId(), e.getMessage());
            }
        }

        return new FieldJobResponse(saved);
    }

    @Override
    @Transactional
    public FieldJobResponse updateFieldJobAdmin(Long fieldJobId, Long tenantId, FieldJobAdminUpdateRequest request) {
        log.info("Admin updating field job id={} for tenantId={}", fieldJobId, tenantId);

        FieldJob fieldJob = fieldJobRepository.findByFieldJobIdAndTenantId(fieldJobId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Field job not found: id=" + fieldJobId + " for tenant=" + tenantId));

        // Determine the effective assigned inspector after this update
        // Note: We only change the assigned inspector if explicitly provided in the request
        // A null value in request means "don't change" not "unassign"
        Long effectiveAssignedInspectorId = fieldJob.getAssignedInspectorId();
        if (request.getAssignedInspectorId() != null) {
            effectiveAssignedInspectorId = request.getAssignedInspectorId();
        }
        // If request.getAssignedInspectorId() is null, we keep the existing inspector (don't unassign)

        // Handle status update if provided
        if (request.getStatus() != null) {
            FieldJobStatus currentStatus = fieldJob.getStatus();
            FieldJobStatus newStatus = request.getStatus();

            // Admin can transition from PENDING to ASSIGNED when assigning inspector
            // or follow normal transitions
            validateStatusTransition(currentStatus, newStatus);

            // Additional validation: EN_ROUTE and ON_SITE require assigned inspector
            if ((newStatus == FieldJobStatus.EN_ROUTE || newStatus == FieldJobStatus.ON_SITE) 
                    && effectiveAssignedInspectorId == null) {
                throw new IllegalStateException("Cannot transition to " + newStatus + " without an assigned inspector");
            }

            fieldJob.setStatus(newStatus);

            // Set timestamps based on status transitions
            ZonedDateTime now = ZonedDateTime.now();
            switch (newStatus) {
                case ASSIGNED -> {
                    if (fieldJob.getAssignedAt() == null) {
                        fieldJob.setAssignedAt(now);
                    }
                }
                case EN_ROUTE, ON_SITE -> {
                    if (fieldJob.getStartedAt() == null) {
                        fieldJob.setStartedAt(now);
                    }
                }
                case COMPLETED, FAILED -> {
                    if (fieldJob.getCompletedAt() == null) {
                        fieldJob.setCompletedAt(now);
                    }
                }
            }
        }

        // Validate assigned inspector if explicitly provided (not null)
        if (request.getAssignedInspectorId() != null) {
            Long inspectorId = request.getAssignedInspectorId();
            User inspector = userRepository.findById(inspectorId)
                    .orElseThrow(() -> new IllegalArgumentException("Assigned inspector not found: " + inspectorId));

            if (!inspector.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Assigned inspector does not belong to the same tenant");
            }

            if (!"INSPECTOR".equalsIgnoreCase(inspector.getRole())) {
                throw new IllegalArgumentException("Assigned user must have INSPECTOR role");
            }

            // If assigning inspector and status is PENDING, auto-transition to ASSIGNED
            if (fieldJob.getAssignedInspectorId() == null && fieldJob.getStatus() == FieldJobStatus.PENDING) {
                fieldJob.setStatus(FieldJobStatus.ASSIGNED);
                fieldJob.setAssignedAt(ZonedDateTime.now());
            }

            fieldJob.setAssignedInspectorId(inspectorId);
        }
        // Note: We don't unassign when request.getAssignedInspectorId() is null
        // Unassigning requires explicit separate endpoint or different mechanism

        // Update other fields if provided
        if (request.getPriority() != null) {
            fieldJob.setPriority(request.getPriority());
        }

        if (request.getInstructions() != null) {
            fieldJob.setInstructions(request.getInstructions());
        }

        if (request.getScheduledAt() != null) {
            fieldJob.setScheduledAt(request.getScheduledAt());
        }

        FieldJob saved = fieldJobRepository.save(fieldJob);
        log.info("Admin updated field job: fieldJobId={}, status={}", saved.getFieldJobId(), saved.getStatus());

        // Auto-create inspection when field job transitions to COMPLETED (via admin update)
        if (request.getStatus() != null && request.getStatus() == FieldJobStatus.COMPLETED) {
            try {
                inspectionService.createInspectionForFieldJob(tenantId, saved.getFieldJobId());
                log.info("Auto-created inspection for completed field job (admin): fieldJobId={}", saved.getFieldJobId());
            } catch (IllegalStateException e) {
                log.warn("Could not create inspection for completed field job {}: {}", saved.getFieldJobId(), e.getMessage());
            } catch (IllegalArgumentException e) {
                log.warn("Could not create inspection for completed field job {}: {}", saved.getFieldJobId(), e.getMessage());
            }
        }

        return new FieldJobResponse(saved);
    }

    private void validateStatusTransition(FieldJobStatus from, FieldJobStatus to) {
        if (from == to) {
            return; // No change
        }

        // Define valid transitions
        // PENDING -> ASSIGNED
        // ASSIGNED -> EN_ROUTE
        // EN_ROUTE -> ON_SITE
        // ON_SITE -> COMPLETED / FAILED
        // Terminal states: COMPLETED, FAILED
        boolean valid = switch (from) {
            case PENDING -> to == FieldJobStatus.ASSIGNED;
            case ASSIGNED -> to == FieldJobStatus.EN_ROUTE;
            case EN_ROUTE -> to == FieldJobStatus.ON_SITE;
            case ON_SITE -> to == FieldJobStatus.COMPLETED || to == FieldJobStatus.FAILED;
            case COMPLETED, FAILED -> false; // Terminal states - no transitions allowed
        };

        if (!valid) {
            throw new IllegalStateException("Invalid status transition from " + from + " to " + to);
        }
    }
}