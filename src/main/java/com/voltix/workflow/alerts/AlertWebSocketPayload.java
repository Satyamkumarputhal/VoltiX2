package com.voltix.workflow.alerts;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Serializable payload sent over the WebSocket /topic/alerts channel.
 * Mirrors SystemAlert fields with JSON-safe types.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertWebSocketPayload {

    private Long alertId;
    private Long tenantId;
    private String meterId;
    private Long zoneId;
    private String alertType;
    private String severity;     // LOW | MEDIUM | HIGH | CRITICAL
    private BigDecimal anomalyScore;
    private BigDecimal priorityScore;
    private String status;       // OPEN | ACKNOWLEDGED | RESOLVED
    private String detectedAt;   // ISO-8601 string for JSON transport

    public static AlertWebSocketPayload from(SystemAlert alert) {
        AlertWebSocketPayload payload = new AlertWebSocketPayload();
        payload.setAlertId(alert.getAlertId());
        payload.setTenantId(alert.getTenantId());
        payload.setMeterId(alert.getMeterId());
        payload.setZoneId(alert.getZoneId());
        payload.setAlertType(alert.getAlertType());
        payload.setSeverity(alert.getSeverity() != null ? alert.getSeverity().name() : null);
        payload.setAnomalyScore(alert.getAnomalyScore());
        payload.setPriorityScore(alert.getPriorityScore());
        payload.setStatus(alert.getStatus() != null ? alert.getStatus().name() : null);
        payload.setDetectedAt(alert.getDetectedAt() != null ? alert.getDetectedAt().toString() : null);
        return payload;
    }
}
