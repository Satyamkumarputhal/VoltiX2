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
        packet.setVoltage(230.0);
        packet.setCurrent(10.0);
        packet.setKwConsumed(2.3); // P = V * I / 1000 = 230 * 10 / 1000 = 2.3kW
    }

    @Test
    void whenTelemetryIsNormal_thenReturnNotAnomalous() {
        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertFalse(result.anomalous());
        assertEquals("ONNX", result.source());
        assertEquals(1.0, result.score(), 0.01);
    }

    @Test
    void whenZeroCurrentUnderLoad_thenReturnAnomalous() {
        packet.setCurrent(0.0);
        packet.setKwConsumed(8.0); // Extreme mismatch to trigger ONNX Isolation Forest anomaly

        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertTrue(result.anomalous());
        assertEquals("ONNX", result.source());
        assertEquals(1.0, result.score(), 0.01);
    }

    @Test
    void whenVoltageOutOfBand_thenReturnAnomalous() {
        packet.setVoltage(180.0); // Out of band (voltage sag)
        packet.setCurrent(5.0);
        packet.setKwConsumed(1.0);

        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertTrue(result.anomalous());
        assertEquals("ONNX", result.source());
        assertEquals(1.0, result.score(), 0.01);
    }

    @Test
    void whenSuspiciousLoadDelta_thenReturnAnomalous() {
        packet.setVoltage(230.0);
        packet.setCurrent(1.0);
        packet.setKwConsumed(15.0); // Extremely high power to trigger Isolation Forest

        AnomalyResult result = anomalyDetectionEngine.evaluate(packet);
        assertTrue(result.anomalous());
        assertEquals("ONNX", result.source());
        assertEquals(1.0, result.score(), 0.01);
    }
}
