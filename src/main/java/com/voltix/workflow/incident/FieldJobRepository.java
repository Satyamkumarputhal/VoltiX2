package com.voltix.workflow.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldJobRepository extends JpaRepository<FieldJob, Long> {

    Optional<FieldJob> findByFieldJobIdAndTenantId(Long fieldJobId, Long tenantId);

    List<FieldJob> findByIncidentIdAndTenantId(Long incidentId, Long tenantId);

    List<FieldJob> findByAssignedInspectorIdAndTenantId(Long assignedInspectorId, Long tenantId);

    List<FieldJob> findByIncidentIdAndTenantIdOrderByCreatedAtDesc(Long incidentId, Long tenantId);
}