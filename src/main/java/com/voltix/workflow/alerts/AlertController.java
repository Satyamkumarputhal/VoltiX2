package com.voltix.workflow.alerts;

import com.voltix.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final SystemAlertRepository alertRepository;
    private final AlertDispatchService alertDispatchService;

    public AlertController(SystemAlertRepository alertRepository, AlertDispatchService alertDispatchService) {
        this.alertRepository = alertRepository;
        this.alertDispatchService = alertDispatchService;
    }

    /**
     * Returns a list of alerts for the current tenant, optionally filtered by status.
     * Ordered by detectedAt descending (newest first).
     *
     * @param status optional filter by alert status
     * @return list of alerts for the current tenant
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<SystemAlert>> listAlerts(
            @RequestParam(required = false) AlertStatus status
    ) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("listAlerts called with no tenant context; returning empty list.");
            return ResponseEntity.ok(List.of());
        }

        log.info("[GET_ALERTS] tenantId={}, statusFilter={}", tenantId, status);

        List<SystemAlert> alerts;
        if (status != null) {
            alerts = alertRepository.findByStatusAndTenantId(status, tenantId);
        } else {
            alerts = alertRepository.findByTenantId(tenantId);
        }

        log.info("[GET_ALERTS] tenantId={}, returned={}, IDs={}",
                tenantId, alerts.size(),
                alerts.stream().map(SystemAlert::getAlertId).toList());

        // Check for ghost alerts
        for (SystemAlert a : alerts) {
            if (a.getAlertId() <= 3) {
                log.warn("[GET_ALERTS] Ghost candidate: alertId={}, status={}, resolvedAt={}",
                        a.getAlertId(), a.getStatus(), a.getResolvedAt());
            }
        }

        log.info("Returning {} alert(s) for tenantId={}", alerts.size(), tenantId);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Acknowledge a single alert.
     *
     * @param id the alert ID
     * @return the updated alert
     */
    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<SystemAlert> acknowledgeAlert(@PathVariable Long id) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("acknowledgeAlert called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        SystemAlert updated = alertDispatchService.acknowledgeAlert(id, tenantId);
        if (updated == null) {
            log.warn("Alert not found: id={}, tenantId={}", id, tenantId);
            return ResponseEntity.notFound().build();
        }

        log.info("Alert acknowledged: id={}, tenantId={}", id, tenantId);
        return ResponseEntity.ok(updated);
    }

    /**
     * Acknowledge all OPEN alerts for the current tenant.
     *
     * @return the number of alerts acknowledged
     */
    @PostMapping("/clear")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> clearAllAlerts() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("clearAllAlerts called with no tenant context.");
            return ResponseEntity.status(401).build();
        }

        int updated = alertDispatchService.clearAllAlertsForTenant(tenantId);
        log.info("Cleared {} alerts for tenantId={}", updated, tenantId);

        return ResponseEntity.ok(Map.of(
                "acknowledged", updated,
                "status", "OK"
        ));
    }
}
