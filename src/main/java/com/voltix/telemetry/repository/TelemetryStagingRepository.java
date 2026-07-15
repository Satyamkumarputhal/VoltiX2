package com.voltix.telemetry.repository;

import com.voltix.telemetry.entity.TelemetryStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TelemetryStagingRepository extends JpaRepository<TelemetryStaging, Long> {
    @Transactional
    @Modifying
    @Query("""
            update TelemetryStaging staging
               set staging.failureReason = :failureReason
             where staging.stagingId = :stagingId
               and staging.tenantId = :tenantId
            """)
    void markFailed(Long stagingId, Long tenantId, String failureReason);
}
