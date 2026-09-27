package com.voltix.workflow.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    Optional<Inspection> findByInspectionIdAndTenantId(Long inspectionId, Long tenantId);

    Optional<Inspection> findByFieldJobIdAndTenantId(Long fieldJobId, Long tenantId);

    List<Inspection> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}