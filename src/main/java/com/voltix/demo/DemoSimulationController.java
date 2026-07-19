package com.voltix.demo;

import com.voltix.telemetry.dto.TelemetryPacket;
import com.voltix.telemetry.service.TelemetryIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo-only controller for simulating telemetry events during live reviews.
 * <p>
 * Generates a small batch of realistic telemetry readings (with at least one
 * deliberately anomalous pattern) and submits them through the REAL ingestion
 * pipeline — staging insert → async worker → ONNX inference → alert dispatch.
 * <p>
 * This is NOT a shortcut that fabricates alerts directly. It exercises the
 * genuine anomaly-detection pipeline end-to-end.
 */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoSimulationController {

    private static final Logger log = LoggerFactory.getLogger(DemoSimulationController.class);

    private final TelemetryIngestionService ingestionService;

    // Use seeded meter IDs from the load-test data (SM-0 through SM-999, zone 1, tenant 1)
    private static final String[] DEMO_METERS = {"SM-0", "SM-1", "SM-2", "SM-3", "SM-4"};
    private static final long DEMO_TENANT_ID = 1L;
    private static final long DEMO_ZONE_ID = 1L;

    public DemoSimulationController(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @PostMapping("/simulate-telemetry")
    public ResponseEntity<Map<String, Object>> simulateTelemetry() {
        log.info("[DEMO] Simulate-telemetry triggered by authenticated operator/admin");

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int batchSize = rng.nextInt(5, 9); // 5-8 readings
        int anomalyIndex = rng.nextInt(batchSize); // at least one anomalous reading

        List<Map<String, Object>> results = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();

        for (int i = 0; i < batchSize; i++) {
            TelemetryPacket packet = new TelemetryPacket();
            packet.setMeterId(DEMO_METERS[rng.nextInt(DEMO_METERS.length)]);
            packet.setTenantId(DEMO_TENANT_ID);
            packet.setZoneId(DEMO_ZONE_ID);
            packet.setRecordedAt(now.minusSeconds(batchSize - i)); // stagger timestamps

            if (i == anomalyIndex) {
                // Anomalous pattern: zero-current-under-load
                // High power draw with near-zero current is physically impossible —
                // this is the same pattern used in the ML validation work to trigger detection.
                packet.setKwConsumed(rng.nextDouble(3.5, 6.0));    // High active power
                packet.setVoltage(rng.nextDouble(230.0, 245.0));   // Normal voltage
                packet.setCurrent(rng.nextDouble(0.01, 0.15));     // Near-zero current (anomalous)
                log.info("[DEMO] Generating ANOMALOUS reading (zero-current-under-load) for meter={}",
                        packet.getMeterId());
            } else {
                // Normal realistic reading — values consistent with typical UK smart meters
                double voltage = rng.nextDouble(225.0, 248.0);
                double current = rng.nextDouble(2.0, 12.0);
                double kwConsumed = (voltage * current) / 1000.0; // Physically consistent P=V*I
                packet.setVoltage(voltage);
                packet.setCurrent(current);
                packet.setKwConsumed(kwConsumed);
            }

            UUID transactionId = ingestionService.accept(packet);
            results.add(Map.of(
                    "transactionId", transactionId.toString(),
                    "meterId", packet.getMeterId(),
                    "anomalous", i == anomalyIndex
            ));

            log.info("[DEMO] Submitted reading {}/{}: meter={}, txId={}, anomalous={}",
                    i + 1, batchSize, packet.getMeterId(), transactionId, i == anomalyIndex);
        }

        log.info("[DEMO] Batch complete: {} readings submitted through real ingestion pipeline", batchSize);

        return ResponseEntity.ok(Map.of(
                "status", "SUBMITTED",
                "message", String.format("%d telemetry readings submitted through real ingestion pipeline", batchSize),
                "count", batchSize,
                "readings", results
        ));
    }
}
