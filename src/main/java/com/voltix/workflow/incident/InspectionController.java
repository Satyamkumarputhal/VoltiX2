package com.voltix.workflow.incident;

import com.voltix.security.TenantContext;
import com.voltix.workflow.incident.dto.InspectionCreateRequest;
import com.voltix.workflow.incident.dto.InspectionResponse;
import com.voltix.workflow.incident.dto.InspectionUpdateRequest;

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
@RequestMapping("/api/v1/inspections")
public class InspectionController {

    private static final Logger log = LoggerFactory.getLogger(InspectionController.class);

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /**
     * Returns a list of inspections for the current tenant, ordered by createdAt descending.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'INSPECTOR')")
    public ResponseEntity<List<InspectionResponse>> listInspections() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("listInspections called with no tenant context; returning empty list.");
            return ResponseEntity.ok(List.of());
        }

        log.info("[GET_INSPECTIONS] tenantId={}", tenantId);

        List<InspectionResponse> inspections = inspectionService.getInspectionsForTenant(tenantId);

        log.info("[GET_INSPECTIONS] tenantId={}, returned={}", tenantId, inspections.size());
        return ResponseEntity.ok(inspections);
    }

    /**
     * Returns a single inspection by ID for the current tenant.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'INSPECTOR')")
    public ResponseEntity<InspectionResponse> getInspection(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("getInspection called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        log.info("[GET_INSPECTION] tenantId={}, inspectionId={}", tenantId, id);

        try {
            InspectionResponse inspection = inspectionService.getInspectionById(id, tenantId);
            return ResponseEntity.ok(inspection);
        } catch (IllegalArgumentException e) {
            log.warn("Inspection not found: id={}, tenantId={}", id, tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Returns the inspection for a specific field job.
     */
    @GetMapping("/field-job/{fieldJobId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'INSPECTOR')")
    public ResponseEntity<InspectionResponse> getInspectionByFieldJob(@PathVariable Long fieldJobId) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("getInspectionByFieldJob called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        log.info("[GET_INSPECTION_BY_FIELD_JOB] tenantId={}, fieldJobId={}", tenantId, fieldJobId);

        try {
            InspectionResponse inspection = inspectionService.getInspectionByFieldJobId(fieldJobId, tenantId);
            return ResponseEntity.ok(inspection);
        } catch (IllegalArgumentException e) {
            log.warn("Inspection not found for field job: fieldJobId={}, tenantId={}", fieldJobId, tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates an inspection (finding, conclusion, evidence, recommendation, result).
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'INSPECTOR')")
    public ResponseEntity<InspectionResponse> updateInspection(@PathVariable Long id, @Valid @RequestBody InspectionUpdateRequest request) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("updateInspection called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        // Get the authenticated username from the JWT token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;

        if (username == null) {
            log.warn("updateInspection called with no authenticated user.");
            return ResponseEntity.status(401).build();
        }

        log.info("[PATCH_INSPECTION] tenantId={}, inspectionId={}, username={}", tenantId, id, username);

        try {
            InspectionResponse updated = inspectionService.updateInspection(id, tenantId, username, request);
            log.info("Inspection updated: inspectionId={}, tenantId={}", id, tenantId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message != null && message.contains("can only update their assigned inspections")) {
                log.warn("Authorization failed for inspection update: id={}, tenantId={}, error={}", id, tenantId, message);
                return ResponseEntity.status(403).build();
            }
            log.warn("Inspection not found or invalid: id={}, tenantId={}, error={}", id, tenantId, message);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Invalid inspection update for inspection id={}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}