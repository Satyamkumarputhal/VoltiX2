package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.FieldJobPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class FieldJobCreateRequest {

    @NotNull
    private Long incidentId;

    private Long assignedInspectorId;

    private FieldJobPriority priority = FieldJobPriority.NORMAL;

    @Size(max = 2000)
    private String instructions;

    private java.time.ZonedDateTime scheduledAt;

    public FieldJobCreateRequest() {}

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