package com.voltix.workflow.incident;

import com.voltix.security.TenantContext;
import com.voltix.workflow.incident.dto.IncidentCreateRequest;
import com.voltix.workflow.incident.dto.IncidentResponse;
import com.voltix.workflow.incident.dto.IncidentUpdateRequest;

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
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * Returns a list of incidents for the current tenant, ordered by createdAt descending.
     *
     * @return list of incidents for the current tenant
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<IncidentResponse>> listIncidents() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("listIncidents called with no tenant context; returning empty list.");
            return ResponseEntity.ok(List.of());
        }

        log.info("[GET_INCIDENTS] tenantId={}", tenantId);

        List<IncidentResponse> incidents = incidentService.getIncidentsForTenant(tenantId);

        log.info("[GET_INCIDENTS] tenantId={}, returned={}", tenantId, incidents.size());
        return ResponseEntity.ok(incidents);
    }

    /**
     * Creates a new incident from an acknowledged alert.
     *
     * @param request the incident creation request
     * @return the created incident
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody IncidentCreateRequest request) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("createIncident called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        // Get the authenticated username from the JWT token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;

        if (username == null) {
            log.warn("createIncident called with no authenticated user.");
            return ResponseEntity.status(401).build();
        }

        try {
            IncidentResponse created = incidentService.createIncident(tenantId, username, request);
            log.info("Incident created: incidentId={}, tenantId={}, createdBy={}", created.getIncidentId(), tenantId, username);
            return ResponseEntity.status(201).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Incident creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Returns a single incident by ID for the current tenant.
     *
     * @param id the incident ID
     * @return the incident
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("getIncident called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        log.info("[GET_INCIDENT] tenantId={}, incidentId={}", tenantId, id);

        try {
            IncidentResponse incident = incidentService.getIncidentById(id, tenantId);
            return ResponseEntity.ok(incident);
        } catch (IllegalArgumentException e) {
            log.warn("Incident not found: id={}, tenantId={}", id, tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates an incident (status, assignedTo).
     *
     * @param id the incident ID
     * @param request the update request
     * @return the updated incident
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<IncidentResponse> updateIncident(@PathVariable Long id, @Valid @RequestBody IncidentUpdateRequest request) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("updateIncident called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        log.info("[PATCH_INCIDENT] tenantId={}, incidentId={}, newStatus={}", tenantId, id, request.getStatus());

        try {
            IncidentResponse updated = incidentService.updateIncident(id, tenantId, request);
            log.info("Incident updated: incidentId={}, status={}", updated.getIncidentId(), updated.getStatus());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Incident not found or invalid: id={}, tenantId={}, error={}", id, tenantId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Invalid status transition for incident id={}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}