package com.voltix.telemetry.worker;

import com.voltix.analytics.anomaly.AnomalyDetectionEngine;
import com.voltix.analytics.anomaly.AnomalyResult;
import com.voltix.persistence.MetricsBatchWriter;
import com.voltix.persistence.MetricsHistoryRow;
import com.voltix.telemetry.dto.TelemetryPacket;
import com.voltix.telemetry.repository.TelemetryStagingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TelemetryWorker {
    private static final Logger log = LoggerFactory.getLogger(TelemetryWorker.class);

    private final AnomalyDetectionEngine anomalyDetectionEngine;
    private final MetricsBatchWriter metricsBatchWriter;
    private final TelemetryStagingRepository stagingRepository;
    private final com.voltix.workflow.alerts.AlertDispatchService alertDispatchService;

    public TelemetryWorker(AnomalyDetectionEngine anomalyDetectionEngine,
                           MetricsBatchWriter metricsBatchWriter,
                           TelemetryStagingRepository stagingRepository,
                           com.voltix.workflow.alerts.AlertDispatchService alertDispatchService) {
        this.anomalyDetectionEngine = anomalyDetectionEngine;
        this.metricsBatchWriter = metricsBatchWriter;
        this.stagingRepository = stagingRepository;
        this.alertDispatchService = alertDispatchService;
    }

    public void process(long stagingId, long tenantId, long zoneId, UUID transactionId, TelemetryPacket packet) {
        MDC.put("TransactionID", transactionId.toString());
        MDC.put("MeterID", packet.getMeterId());
        MDC.put("ZoneID", Long.toString(zoneId));
        try {
            AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
            log.info("[{}] [{}] [{}] Features: kW={}, V={}, A={} → score={}, anomalous={}, source={}",
                    transactionId, packet.getMeterId(), zoneId,
                    packet.getKwConsumed(), packet.getVoltage(), packet.getCurrent(),
                    result.score(), result.anomalous(), result.source());
            if (result.anomalous()) {
                alertDispatchService.createAlert(tenantId, packet.getMeterId(), zoneId, "NTL_ANOMALY", result.score());
            }
            metricsBatchWriter.enqueue(new MetricsHistoryRow(
                    stagingId,
                    tenantId,
                    zoneId,
                    packet.getMeterId(),
                    packet.getVoltage(),
                    packet.getCurrent(),
                    packet.getKwConsumed(),
                    result.score(),
                    packet.getRecordedAt()
            ));
            log.info("[{}] [{}] [{}] Telemetry evaluated via {} with score {}",
                    transactionId, packet.getMeterId(), zoneId, result.source(), result.score());
        } catch (RuntimeException ex) {
            stagingRepository.markFailed(stagingId, tenantId, ex.getMessage());
            log.error("[{}] [{}] [{}] Telemetry processing failed for staging {}",
                    transactionId, packet.getMeterId(), zoneId, stagingId, ex);
        } finally {
            MDC.clear();
        }
    }
}
