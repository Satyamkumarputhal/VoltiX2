package com.voltix.analytics.anomaly;

import com.voltix.telemetry.dto.TelemetryPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AnomalyDetectionEngineTest {

    @Autowired
    private AnomalyDetectionEngine anomalyDetectionEngine;

    private TelemetryPacket packet;

    @BeforeEach
    void setUp() {
        packet = new TelemetryPacket();
        packet.setMeterId("SM-99");
        packet.setVoltage(240.0);
        packet.setCurrent(6.0);
        packet.setKwConsumed(1.5); // 1.5kW
    }

    @Test
    void whenTelemetryIsNormal_thenReturnNotAnomalous() {
        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertFalse(result.anomalous());
        assertEquals("ONNX", result.source());
        // Severity score for a normal reading should be on the low side of the 0..1
        // range (see AnomalyDetectionEngine: continuousScore = 0.5 - decision_function,
        // and normal readings have a positive decision_function).
        assertTrue(result.score() < 0.5, "Expected a low severity score for a normal reading, got " + result.score());
    }

    @Test
    void whenTelemetryIsBorderlineHighLoad_thenReturnAnomalous() {
        // Documenting model behavior change: at contamination=0.05, this previously normal 
        // borderline-high load (2.3kW) is now flagged as an anomaly.
        packet.setVoltage(230.0);
        packet.setCurrent(10.0);
        packet.setKwConsumed(2.3); 

        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertTrue(result.anomalous());
        assertEquals("ONNX", result.source());
        // Severity score for an anomalous reading should be on the high side.
        assertTrue(result.score() > 0.5, "Expected a high severity score for an anomalous reading, got " + result.score());
    }

    @Test
    void whenZeroCurrentUnderLoad_thenReturnAnomalous() {
        packet.setCurrent(0.0);
        packet.setKwConsumed(8.0); // Extreme mismatch to trigger ONNX Isolation Forest anomaly

        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertTrue(result.anomalous());
        assertEquals("ONNX", result.source());
        assertTrue(result.score() > 0.5, "Expected a high severity score for an anomalous reading, got " + result.score());
    }

    @Test
    void whenVoltageOutOfBand_thenReturnAnomalous() {
        packet.setVoltage(180.0); // Out of band (voltage sag)
        packet.setCurrent(5.0);
        packet.setKwConsumed(1.0);

        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertTrue(result.anomalous());
        assertEquals("ONNX", result.source());
        assertTrue(result.score() > 0.5, "Expected a high severity score for an anomalous reading, got " + result.score());
    }

    @Test
    void whenSuspiciousLoadDelta_thenReturnAnomalous() {
        packet.setVoltage(230.0);
        packet.setCurrent(1.0);
        packet.setKwConsumed(15.0); // Extremely high power to trigger Isolation Forest

        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertTrue(result.anomalous());
        assertEquals("ONNX", result.source());
        assertTrue(result.score() > 0.5, "Expected a high severity score for an anomalous reading, got " + result.score());
    }
}
