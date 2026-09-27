package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.Incident;
import com.voltix.workflow.incident.IncidentStatus;

import java.time.ZonedDateTime;

public class IncidentResponse {

    private Long incidentId;
    private String incidentNumber;
    private Long tenantId;
    private Long sourceAlertId;
    private String meterId;
    private Long zoneId;
    private String alertType;
    private String severity;
    private String title;
    private String description;
    private IncidentStatus status;
    private Long createdBy;
    private Long assignedTo;
    private ZonedDateTime createdAt;
    private ZonedDateTime acknowledgedAt;
    private ZonedDateTime assignedAt;
    private ZonedDateTime resolvedAt;
    private ZonedDateTime closedAt;

    public IncidentResponse() {}

    public IncidentResponse(Incident incident) {
        this.incidentId = incident.getIncidentId();
        this.incidentNumber = incident.getIncidentNumber();
        this.tenantId = incident.getTenantId();
        this.sourceAlertId = incident.getSourceAlertId();
        this.meterId = incident.getMeterId();
        this.zoneId = incident.getZoneId();
        this.alertType = incident.getAlertType();
        this.severity = incident.getSeverity();
        this.title = incident.getTitle();
        this.description = incident.getDescription();
        this.status = incident.getStatus();
        this.createdBy = incident.getCreatedBy();
        this.assignedTo = incident.getAssignedTo();
        this.createdAt = incident.getCreatedAt();
        this.acknowledgedAt = incident.getAcknowledgedAt();
        this.assignedAt = incident.getAssignedAt();
        this.resolvedAt = incident.getResolvedAt();
        this.closedAt = incident.getClosedAt();
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public void setIncidentNumber(String incidentNumber) {
        this.incidentNumber = incidentNumber;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSourceAlertId() {
        return sourceAlertId;
    }

    public void setSourceAlertId(Long sourceAlertId) {
        this.sourceAlertId = sourceAlertId;
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(ZonedDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public ZonedDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(ZonedDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public ZonedDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(ZonedDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public ZonedDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(ZonedDateTime closedAt) {
        this.closedAt = closedAt;
    }
}