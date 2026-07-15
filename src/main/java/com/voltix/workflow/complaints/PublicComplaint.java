package com.voltix.workflow.complaints;

import jakarta.persistence.*;
import lombok.Data;

import java.time.ZonedDateTime;

@Entity
@Table(name = "public_complaints")
@Data
public class PublicComplaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long complaintId;

    private Long tenantId;
    private Long zoneId;
    private String incidentAddress;
    private String addressHash;
    private String description;
    private String submitterIpHash;

    @Enumerated(EnumType.STRING)
    private ComplaintStatus status = ComplaintStatus.PENDING_VERIFICATION;

    private ZonedDateTime submittedAt = ZonedDateTime.now();
    private ZonedDateTime triagedAt;
}
