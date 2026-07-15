package com.voltix.persistence;

import java.time.ZonedDateTime;

public record MetricsHistoryRow(
        long stagingId,
        long tenantId,
        long zoneId,
        String meterId,
        double voltage,
        double currentAmp,
        double kwConsumed,
        double anomalyScore,
        ZonedDateTime recordedAt
) {
}
