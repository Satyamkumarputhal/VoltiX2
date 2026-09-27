package com.voltix.workflow.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentIdAndTenantId(Long incidentId, Long tenantId);

    List<Incident> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<Incident> findBySourceAlertIdAndTenantId(Long sourceAlertId, Long tenantId);

    List<Incident> findByTenantIdAndStatusOrderByCreatedAtDesc(Long tenantId, IncidentStatus status);
}