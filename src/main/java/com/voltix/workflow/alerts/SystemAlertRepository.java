package com.voltix.workflow.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemAlertRepository extends JpaRepository<SystemAlert, Long> {
    List<SystemAlert> findByStatusAndTenantId(AlertStatus status, Long tenantId);
    List<SystemAlert> findByTenantIdAndStatus(Long tenantId, AlertStatus status);
    List<SystemAlert> findByTenantId(Long tenantId);

    Optional<SystemAlert> findByAlertIdAndTenantId(Long alertId, Long tenantId);

    @Modifying
    @Query("UPDATE SystemAlert a SET a.status = :ackStatus, a.resolvedAt = :now WHERE a.tenantId = :tenantId AND a.status = :openStatus")
    int clearAllAlertsForTenant(@Param("tenantId") Long tenantId, @Param("now") ZonedDateTime now, @Param("ackStatus") AlertStatus ackStatus, @Param("openStatus") AlertStatus openStatus);

    @Modifying
    @Query("UPDATE SystemAlert a SET a.status = :ackStatus, a.resolvedAt = :now WHERE a.alertId = :alertId AND a.tenantId = :tenantId AND a.status = :openStatus")
    int acknowledgeAlertByIdAndTenant(@Param("alertId") Long alertId, @Param("tenantId") Long tenantId, @Param("now") ZonedDateTime now, @Param("ackStatus") AlertStatus ackStatus, @Param("openStatus") AlertStatus openStatus);
}
