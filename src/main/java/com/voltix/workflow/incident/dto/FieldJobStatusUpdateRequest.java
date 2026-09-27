package com.voltix.workflow.incident.dto;

import com.voltix.workflow.incident.FieldJobStatus;
import jakarta.validation.constraints.NotNull;

public class FieldJobStatusUpdateRequest {

    @NotNull
    private FieldJobStatus status;

    public FieldJobStatusUpdateRequest() {}

    public FieldJobStatus getStatus() {
        return status;
    }

    public void setStatus(FieldJobStatus status) {
        this.status = status;
    }
}