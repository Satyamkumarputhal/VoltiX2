package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.FieldJob;
import com.voltix.workflow.incident.FieldJobPriority;
import com.voltix.workflow.incident.FieldJobStatus;

import java.time.ZonedDateTime;

public class FieldJobResponse {

    private Long fieldJobId;
    private Long tenantId;
    private Long incidentId;
    private Long assignedInspectorId;
    private FieldJobStatus status;
    private FieldJobPriority priority;
    private String instructions;
    private ZonedDateTime scheduledAt;
    private ZonedDateTime assignedAt;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private ZonedDateTime createdAt;
    private Long createdBy;

    public FieldJobResponse() {}

    public FieldJobResponse(FieldJob fieldJob) {
        this.fieldJobId = fieldJob.getFieldJobId();
        this.tenantId = fieldJob.getTenantId();
        this.incidentId = fieldJob.getIncidentId();
        this.assignedInspectorId = fieldJob.getAssignedInspectorId();
        this.status = fieldJob.getStatus();
        this.priority = fieldJob.getPriority();
        this.instructions = fieldJob.getInstructions();
        this.scheduledAt = fieldJob.getScheduledAt();
        this.assignedAt = fieldJob.getAssignedAt();
        this.startedAt = fieldJob.getStartedAt();
        this.completedAt = fieldJob.getCompletedAt();
        this.createdAt = fieldJob.getCreatedAt();
        this.createdBy = fieldJob.getCreatedBy();
    }

    public Long getFieldJobId() {
        return fieldJobId;
    }

    public void setFieldJobId(Long fieldJobId) {
        this.fieldJobId = fieldJobId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public Long getAssignedInspectorId() {
        return assignedInspectorId;
    }

    public void setAssignedInspectorId(Long assignedInspectorId) {
        this.assignedInspectorId = assignedInspectorId;
    }

    public FieldJobStatus getStatus() {
        return status;
    }

    public void setStatus(FieldJobStatus status) {
        this.status = status;
    }

    public FieldJobPriority getPriority() {
        return priority;
    }

    public void setPriority(FieldJobPriority priority) {
        this.priority = priority;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public ZonedDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(ZonedDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public ZonedDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(ZonedDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}