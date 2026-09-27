package com.voltix.workflow.incident;

import jakarta.persistence.*;
import lombok.Data;
import java.time.ZonedDateTime;

@Entity
@Table(name = "incidents")
@Data
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long incidentId;

    private Long tenantId;

    @Column(nullable = false, unique = true, length = 30)
    private String incidentNumber;

    @Column(nullable = false, unique = true)
    private Long sourceAlertId;

    private String meterId;
    private Long zoneId;
    private String alertType;
    private String severity;
    private String title;
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.OPEN;

    private Long createdBy;
    private Long assignedTo;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    private ZonedDateTime acknowledgedAt;
    private ZonedDateTime assignedAt;
    private ZonedDateTime resolvedAt;
    private ZonedDateTime closedAt;
}