package com.voltix.telemetry.worker;

import com.voltix.telemetry.dto.TelemetryPacket;

import java.util.UUID;

public record TelemetryWorkItem(
        long stagingId,
        long tenantId,
        long zoneId,
        UUID transactionId,
        TelemetryPacket packet
) {
}
