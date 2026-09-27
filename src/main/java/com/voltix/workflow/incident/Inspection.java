package com.voltix.workflow.incident;

import jakarta.persistence.*;
import lombok.Data;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inspections")
@Data
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inspectionId;

    private Long tenantId;

    @Column(nullable = false, unique = true)
    private Long fieldJobId;

    private Long inspectorId;

    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;

    private String finding;
    private String conclusion;

    @Column(columnDefinition = "JSONB")
    private String evidenceMetadata;

    private String recommendation;

    @Enumerated(EnumType.STRING)
    private InspectionResult result;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();
}