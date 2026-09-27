package com.voltix.workflow.incident;

import com.voltix.security.TenantContext;
import com.voltix.workflow.incident.dto.FieldJobAdminUpdateRequest;
import com.voltix.workflow.incident.dto.FieldJobCreateRequest;
import com.voltix.workflow.incident.dto.FieldJobResponse;
import com.voltix.workflow.incident.dto.FieldJobStatusUpdateRequest;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
public class FieldJobController {

    private static final Logger log = LoggerFactory.getLogger(FieldJobController.class);

    private final FieldJobService fieldJobService;

    public FieldJobController(FieldJobService fieldJobService) {
        this.fieldJobService = fieldJobService;
    }

    /**
     * Returns a list of field jobs for a specific incident for the current tenant.
     *
     * @param incidentId the incident ID
     * @return list of field jobs for the incident
     */
    @GetMapping("/{incidentId}/field-jobs")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'INSPECTOR')")
    public ResponseEntity<List<FieldJobResponse>> listFieldJobsForIncident(@PathVariable Long incidentId) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("listFieldJobsForIncident called with no tenant context; returning empty list.");
            return ResponseEntity.ok(List.of());
        }

        log.info("[GET_FIELD_JOBS] tenantId={}, incidentId={}", tenantId, incidentId);

        List<FieldJobResponse> fieldJobs = fieldJobService.getFieldJobsForIncident(incidentId, tenantId);

        log.info("[GET_FIELD_JOBS] tenantId={}, incidentId={}, returned={}", tenantId, incidentId, fieldJobs.size());
        return ResponseEntity.ok(fieldJobs);
    }

    /**
     * Creates a new field job for an incident.
     *
     * @param incidentId the incident ID
     * @param request the field job creation request
     * @return the created field job
     */
    @PostMapping("/{incidentId}/field-jobs")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<FieldJobResponse> createFieldJob(@PathVariable Long incidentId,
                                                            @Valid @RequestBody FieldJobCreateRequest request) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("createFieldJob called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        // Ensure the request incidentId matches the path variable
        request.setIncidentId(incidentId);

        // Get the authenticated username from the JWT token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;

        if (username == null) {
            log.warn("createFieldJob called with no authenticated user.");
            return ResponseEntity.status(401).build();
        }

        try {
            FieldJobResponse created = fieldJobService.createFieldJob(tenantId, username, request);
            log.info("Field job created: fieldJobId={}, tenantId={}, createdBy={}", created.getFieldJobId(), tenantId, username);
            return ResponseEntity.status(201).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Field job creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Returns a single field job by ID for the current tenant.
     *
     * @param id the field job ID
     * @return the field job
     */
    @GetMapping("/field-jobs/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'INSPECTOR')")
    public ResponseEntity<FieldJobResponse> getFieldJob(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("getFieldJob called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        log.info("[GET_FIELD_JOB] tenantId={}, fieldJobId={}", tenantId, id);

        try {
            FieldJobResponse fieldJob = fieldJobService.getFieldJobById(id, tenantId);
            return ResponseEntity.ok(fieldJob);
        } catch (IllegalArgumentException e) {
            log.warn("Field job not found: id={}, tenantId={}", id, tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates a field job operational status (INSPECTOR endpoint).
     *
     * @param id the field job ID
     * @param request the status update request
     * @return the updated field job
     */
    @PatchMapping("/field-jobs/{id}/status")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'INSPECTOR')")
    public ResponseEntity<FieldJobResponse> updateFieldJobStatus(@PathVariable Long id, @Valid @RequestBody FieldJobStatusUpdateRequest request) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("updateFieldJobStatus called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        // Get the authenticated username from the JWT token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;

        if (username == null) {
            log.warn("updateFieldJobStatus called with no authenticated user.");
            return ResponseEntity.status(401).build();
        }

        log.info("[PATCH_FIELD_JOB_STATUS] tenantId={}, fieldJobId={}, newStatus={}", tenantId, id, request.getStatus());

        try {
            FieldJobResponse updated = fieldJobService.updateFieldJobStatus(id, tenantId, username, request);
            log.info("Field job status updated: fieldJobId={}, status={}", updated.getFieldJobId(), updated.getStatus());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message != null && message.contains("Inspector can only update status of their assigned field jobs")) {
                log.warn("Authorization failed for field job status update: id={}, tenantId={}, error={}", id, tenantId, message);
                return ResponseEntity.status(403).build();
            }
            log.warn("Field job not found or invalid: id={}, tenantId={}, error={}", id, tenantId, message);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Invalid status transition for field job id={}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Updates a field job administrative fields (OPERATOR/ADMIN endpoint).
     *
     * @param id the field job ID
     * @param request the admin update request
     * @return the updated field job
     */
    @PatchMapping("/field-jobs/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<FieldJobResponse> updateFieldJobAdmin(@PathVariable Long id, @Valid @RequestBody FieldJobAdminUpdateRequest request) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("updateFieldJobAdmin called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        log.info("[PATCH_FIELD_JOB_ADMIN] tenantId={}, fieldJobId={}", tenantId, id);

        try {
            FieldJobResponse updated = fieldJobService.updateFieldJobAdmin(id, tenantId, request);
            log.info("Field job admin updated: fieldJobId={}, status={}", updated.getFieldJobId(), updated.getStatus());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Field job not found or invalid: id={}, tenantId={}, error={}", id, tenantId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Invalid status transition for field job id={}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}