package com.voltix.telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "telemetry_staging")
@Data
public class TelemetryStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stagingId;

    private Long tenantId;
    private String meterId;
    private UUID transactionId;
    private Long zoneId;
    private java.math.BigDecimal voltage;

    @Column(name = "current_amp")
    private java.math.BigDecimal current;
    private java.math.BigDecimal kwConsumed;
    private ZonedDateTime clientTimestamp;
    private Boolean processed = false;
    private ZonedDateTime receivedAt = ZonedDateTime.now();
    private ZonedDateTime processedAt;
    private String failureReason;
}
