package com.voltix.workflow.incident;

import com.voltix.security.User;
import com.voltix.security.UserRepository;
import com.voltix.workflow.alerts.SystemAlert;
import com.voltix.workflow.alerts.SystemAlertRepository;
import com.voltix.workflow.incident.dto.IncidentCreateRequest;
import com.voltix.workflow.incident.dto.IncidentResponse;
import com.voltix.workflow.incident.dto.IncidentUpdateRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;

@Service
public class IncidentServiceImpl implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceImpl.class);

    private final IncidentRepository incidentRepository;
    private final SystemAlertRepository systemAlertRepository;
    private final UserRepository userRepository;

    public IncidentServiceImpl(IncidentRepository incidentRepository,
                               SystemAlertRepository systemAlertRepository,
                               UserRepository userRepository) {
        this.incidentRepository = incidentRepository;
        this.systemAlertRepository = systemAlertRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public IncidentResponse createIncident(Long tenantId, String username, IncidentCreateRequest request) {
        log.info("Creating incident for tenantId={}, username={}, sourceAlertId={}", tenantId, username, request.getSourceAlertId());

        // Validate source alert exists and belongs to the authenticated tenant
        SystemAlert sourceAlert = systemAlertRepository.findByAlertIdAndTenantId(request.getSourceAlertId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Source alert not found for tenant: " + request.getSourceAlertId()));

        // Check if incident already exists for this source alert
        if (incidentRepository.findBySourceAlertIdAndTenantId(request.getSourceAlertId(), tenantId).isPresent()) {
            throw new IllegalArgumentException("Incident already exists for source alert ID: " + request.getSourceAlertId());
        }

        // Find the authenticated user
        User creator = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Incident incident = new Incident();
        incident.setTenantId(tenantId);
        incident.setIncidentNumber(generateIncidentNumber());
        incident.setSourceAlertId(request.getSourceAlertId());

        // Derive incident fields from the source alert
        incident.setMeterId(sourceAlert.getMeterId());
        incident.setZoneId(sourceAlert.getZoneId());
        incident.setAlertType(sourceAlert.getAlertType());
        incident.setSeverity(sourceAlert.getSeverity().name());

        // Use request values for title/description, or derive from alert if appropriate
        incident.setTitle(request.getTitle());
        incident.setDescription(request.getDescription());

        incident.setStatus(IncidentStatus.OPEN);
        incident.setCreatedBy(creator.getUserId());
        incident.setCreatedAt(ZonedDateTime.now());

        Incident saved = incidentRepository.save(incident);
        log.info("Created incident: incidentId={}, incidentNumber={}, tenantId={}", saved.getIncidentId(), saved.getIncidentNumber(), tenantId);

        return new IncidentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsForTenant(Long tenantId) {
        log.debug("Fetching incidents for tenantId={}", tenantId);
        List<Incident> incidents = incidentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return incidents.stream().map(IncidentResponse::new).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getIncidentById(Long incidentId, Long tenantId) {
        log.debug("Fetching incident by id={} for tenantId={}", incidentId, tenantId);
        Incident incident = incidentRepository.findByIncidentIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: id=" + incidentId + " for tenant=" + tenantId));
        return new IncidentResponse(incident);
    }

    @Override
    @Transactional
    public IncidentResponse updateIncident(Long incidentId, Long tenantId, IncidentUpdateRequest request) {
        log.info("Updating incident id={} for tenantId={} to status={}", incidentId, tenantId, request.getStatus());

        Incident incident = incidentRepository.findByIncidentIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: id=" + incidentId + " for tenant=" + tenantId));

        IncidentStatus currentStatus = incident.getStatus();
        IncidentStatus newStatus = request.getStatus();

        validateStatusTransition(currentStatus, newStatus);

        // Validate assignedTo if provided
        if (request.getAssignedTo() != null) {
            Long assignedToId = request.getAssignedTo();
            User assignedUser = userRepository.findById(assignedToId)
                    .orElseThrow(() -> new IllegalArgumentException("Assigned user not found: " + assignedToId));

            if (!assignedUser.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Assigned user does not belong to the same tenant");
            }

            // Only users with INSPECTOR role can be assigned to incidents
            if (!"INSPECTOR".equalsIgnoreCase(assignedUser.getRole())) {
                throw new IllegalArgumentException("Assigned user must have INSPECTOR role");
            }

            incident.setAssignedTo(assignedToId);
        }

        incident.setStatus(newStatus);

        // Set timestamps based on status transitions
        ZonedDateTime now = ZonedDateTime.now();
        switch (newStatus) {
            case ACKNOWLEDGED -> {
                if (incident.getAcknowledgedAt() == null) {
                    incident.setAcknowledgedAt(now);
                }
            }
            case ASSIGNED -> {
                if (incident.getAssignedAt() == null) {
                    incident.setAssignedAt(now);
                }
            }
            case IN_PROGRESS -> {
                // No specific timestamp for IN_PROGRESS
            }
            case RESOLVED -> {
                if (incident.getResolvedAt() == null) {
                    incident.setResolvedAt(now);
                }
            }
            case CLOSED -> {
                if (incident.getClosedAt() == null) {
                    incident.setClosedAt(now);
                }
            }
            case ESCALATED, CANCELLED -> {
                // No specific timestamp for these terminal statuses
            }
        }

        Incident saved = incidentRepository.save(incident);
        log.info("Updated incident: incidentId={}, status={}", saved.getIncidentId(), saved.getStatus());

        return new IncidentResponse(saved);
    }

    private void validateStatusTransition(IncidentStatus from, IncidentStatus to) {
        if (from == to) {
            return; // No change
        }

        // Define valid transitions
        boolean valid = switch (from) {
            case OPEN -> to == IncidentStatus.ACKNOWLEDGED || to == IncidentStatus.ESCALATED || to == IncidentStatus.CANCELLED;
            case ACKNOWLEDGED -> to == IncidentStatus.ASSIGNED || to == IncidentStatus.ESCALATED || to == IncidentStatus.CANCELLED;
            case ASSIGNED -> to == IncidentStatus.IN_PROGRESS || to == IncidentStatus.ESCALATED || to == IncidentStatus.CANCELLED;
            case IN_PROGRESS -> to == IncidentStatus.RESOLVED || to == IncidentStatus.ESCALATED || to == IncidentStatus.CANCELLED;
            case RESOLVED -> to == IncidentStatus.CLOSED || to == IncidentStatus.ESCALATED;
            case CLOSED, ESCALATED, CANCELLED -> false; // Terminal states
        };

        if (!valid) {
            throw new IllegalStateException("Invalid status transition from " + from + " to " + to);
        }
    }

    private String generateIncidentNumber() {
        // Format: INC-YYYYMMDD-XXXXXX
        String datePart = ZonedDateTime.now().toLocalDate().toString().replace("-", "");
        String randomPart = String.format("%06d", new Random().nextInt(1_000_000));
        return "INC-" + datePart + "-" + randomPart;
    }
}