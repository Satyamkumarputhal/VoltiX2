package com.voltix.telemetry.service;

import com.voltix.platform.config.VoltixProperties;
import com.voltix.security.TenantContext;
import com.voltix.telemetry.dto.TelemetryPacket;
import com.voltix.telemetry.entity.TelemetryStaging;
import com.voltix.telemetry.repository.TelemetryStagingRepository;
import com.voltix.telemetry.validation.TelemetryValidator;
import com.voltix.telemetry.worker.TelemetryWorker;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TelemetryIngestionService {
    private static final String MDC_TRANSACTION_ID = "TransactionID";
    private static final String MDC_METER_ID = "MeterID";
    private static final String MDC_ZONE_ID = "ZoneID";

    private final TelemetryValidator validator;
    private final TelemetryStagingRepository stagingRepository;
    private final TaskExecutor telemetryIngestionExecutor;
    private final TelemetryWorker telemetryWorker;
    private final VoltixProperties properties;

    public TelemetryIngestionService(TelemetryValidator validator,
                                     TelemetryStagingRepository stagingRepository,
                                     @Qualifier("telemetryIngestionExecutor") TaskExecutor telemetryIngestionExecutor,
                                     TelemetryWorker telemetryWorker,
                                     VoltixProperties properties) {
        this.validator = validator;
        this.stagingRepository = stagingRepository;
        this.telemetryIngestionExecutor = telemetryIngestionExecutor;
        this.telemetryWorker = telemetryWorker;
        this.properties = properties;
    }

    public UUID accept(TelemetryPacket packet) throws DataAccessException {
        validator.validate(packet);

        UUID transactionId = packet.getTransactionId() == null ? UUID.randomUUID() : packet.getTransactionId();

        // Tenant isolation: derive tenant from authenticated JWT (populated by JwtAuthenticationFilter).
        // The telemetry endpoint requires authentication, so TenantContext must be populated.
        Long jwtTenantId = TenantContext.getCurrentTenant();
        if (jwtTenantId == null) {
            throw new IllegalStateException("Tenant context not available. Telemetry ingestion requires authenticated request.");
        }
        long tenantId = jwtTenantId;

        // If caller provides tenantId, validate it matches the authenticated tenant.
        if (packet.getTenantId() != null && packet.getTenantId() != tenantId) {
            throw new IllegalArgumentException("Telemetry tenantId (" + packet.getTenantId() + ") does not match authenticated tenant (" + tenantId + ")");
        }

        long zoneId = packet.getZoneId() == null ? 1L : packet.getZoneId();

        TelemetryStaging staging = new TelemetryStaging();
        staging.setTenantId(tenantId);
        staging.setMeterId(packet.getMeterId());
        staging.setTransactionId(transactionId);
        staging.setZoneId(zoneId);
        staging.setVoltage(java.math.BigDecimal.valueOf(packet.getVoltage()));
        staging.setCurrent(java.math.BigDecimal.valueOf(packet.getCurrent()));
        staging.setKwConsumed(java.math.BigDecimal.valueOf(packet.getKwConsumed()));
        staging.setClientTimestamp(packet.getRecordedAt());

        TelemetryStaging saved = stagingRepository.saveAndFlush(staging);
        withMdc(transactionId, packet.getMeterId(), zoneId, () ->
                telemetryIngestionExecutor.execute(() ->
                        telemetryWorker.process(saved.getStagingId(), tenantId, zoneId, transactionId, packet)));
        return transactionId;
    }

    private void withMdc(UUID transactionId, String meterId, long zoneId, Runnable runnable) {
        MDC.put(MDC_TRANSACTION_ID, transactionId.toString());
        MDC.put(MDC_METER_ID, meterId);
        MDC.put(MDC_ZONE_ID, Long.toString(zoneId));
        try {
            runnable.run();
        } finally {
            MDC.remove(MDC_TRANSACTION_ID);
            MDC.remove(MDC_METER_ID);
            MDC.remove(MDC_ZONE_ID);
        }
    }
}
