package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.FieldJobPriority;
import com.voltix.workflow.incident.FieldJobStatus;
import jakarta.validation.constraints.Size;

public class FieldJobAdminUpdateRequest {

    private FieldJobStatus status;

    private Long assignedInspectorId;

    private FieldJobPriority priority;

    @Size(max = 2000)
    private String instructions;

    private java.time.ZonedDateTime scheduledAt;

    public FieldJobAdminUpdateRequest() {}

    public FieldJobStatus getStatus() {
        return status;
    }

    public void setStatus(FieldJobStatus status) {
        this.status = status;
    }

    public Long getAssignedInspectorId() {
        return assignedInspectorId;
    }

    public void setAssignedInspectorId(Long assignedInspectorId) {
        this.assignedInspectorId = assignedInspectorId;
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

    public java.time.ZonedDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(java.time.ZonedDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}