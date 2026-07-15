package com.voltix.workflow.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemAlertRepository extends JpaRepository<SystemAlert, Long> {
    List<SystemAlert> findByStatus(AlertStatus status);
    List<SystemAlert> findByTenantIdAndStatus(Long tenantId, AlertStatus status);
}
