package com.voltix.telemetry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
public class TelemetryPacket {

    @NotBlank(message = "Meter ID must not be empty")
    private String meterId;

    private Long tenantId;
    private Long zoneId;
    private UUID transactionId;

    @NotNull(message = "Voltage is required")
    private Double voltage;

    @NotNull(message = "Current is required")
    @PositiveOrZero(message = "Current boundary must be positive (I >= 0A)")
    private Double current;

    @NotNull(message = "Active power consumption is required")
    @PositiveOrZero(message = "Power consumption metrics cannot be negative")
    private Double kwConsumed;

    @NotNull(message = "Telemetry timestamp is required")
    private ZonedDateTime recordedAt;
}
