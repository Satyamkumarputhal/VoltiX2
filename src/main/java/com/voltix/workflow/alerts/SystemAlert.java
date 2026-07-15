package com.voltix.workflow.alerts;

import jakarta.persistence.*;
import lombok.Data;
import java.time.ZonedDateTime;

@Entity
@Table(name = "system_alerts")
@Data
public class SystemAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alertId;

    private Long tenantId;
    private String meterId;
    private Long zoneId;
    private String alertType;

    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    private java.math.BigDecimal anomalyScore;
    private java.math.BigDecimal priorityScore;

    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.OPEN;

    private ZonedDateTime detectedAt = ZonedDateTime.now();
    private Long assignedTo;
    private ZonedDateTime resolvedAt;
}
