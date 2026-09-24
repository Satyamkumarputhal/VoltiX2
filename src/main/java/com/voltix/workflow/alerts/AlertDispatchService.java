package com.voltix.workflow.alerts;

import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

public interface AlertDispatchService {

    @Transactional
    SystemAlert createAlert(Long tenantId, String meterId, Long zoneId, String alertType, Double anomalyScore);

    int clearAllAlertsForTenant(Long tenantId);

    SystemAlert acknowledgeAlert(Long alertId, Long tenantId);
}