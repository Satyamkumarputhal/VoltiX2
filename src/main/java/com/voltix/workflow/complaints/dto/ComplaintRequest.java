package com.voltix.workflow.complaints.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ComplaintRequest {

    @NotNull(message = "Zone ID is required")
    private Long zoneId;

    @NotBlank(message = "Incident address is required")
    private String incidentAddress;

    @NotBlank(message = "Description is required")
    private String description;

    private Long tenantId;
}
