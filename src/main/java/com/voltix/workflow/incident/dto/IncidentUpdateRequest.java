package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public class IncidentUpdateRequest {

    @NotNull
    private IncidentStatus status;

    private Long assignedTo;

    public IncidentUpdateRequest() {}

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }
}