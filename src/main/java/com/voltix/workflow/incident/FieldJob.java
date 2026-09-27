package com.voltix.workflow.incident;

import jakarta.persistence.*;
import lombok.Data;
import java.time.ZonedDateTime;

@Entity
@Table(name = "field_jobs")
@Data
public class FieldJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fieldJobId;

    private Long tenantId;

    @Column(nullable = false)
    private Long incidentId;

    private Long assignedInspectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldJobStatus status = FieldJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldJobPriority priority = FieldJobPriority.NORMAL;

    private String instructions;
    private ZonedDateTime scheduledAt;
    private ZonedDateTime assignedAt;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    private Long createdBy;
}