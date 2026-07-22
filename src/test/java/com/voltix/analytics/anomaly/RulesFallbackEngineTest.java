package com.voltix.analytics.anomaly;

import com.voltix.telemetry.dto.TelemetryPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RulesFallbackEngine — the deterministic scoring fallback
 * used when ONNX inference is unavailable. Verifies that scores genuinely
 * vary with input severity (not clamped to a constant across the realistic
 * input range for each anomaly pattern).
 */
class RulesFallbackEngineTest {

    private RulesFallbackEngine engine;
    private TelemetryPacket packet;

    @BeforeEach
    void setUp() {
        engine = new RulesFallbackEngine();
        packet = new TelemetryPacket();
        packet.setMeterId("SM-TEST");
        packet.setVoltage(230.0);
        packet.setCurrent(6.0);
        packet.setKwConsumed(1.5);
    }

    // ── Zero-current-under-load ──────────────────────────────────────

    @Test
    void zeroCurrentUnderLoad_scoreVariesWithPower() {
        packet.setCurrent(0.005); // near-zero → triggers this branch

        packet.setKwConsumed(3.0);
        AnomalyResult low = engine.evaluate(packet);

        packet.setKwConsumed(7.0);
        AnomalyResult mid = engine.evaluate(packet);

        packet.setKwConsumed(9.0);
        AnomalyResult high = engine.evaluate(packet);

        // All should be flagged anomalous
        assertTrue(low.anomalous());
        assertTrue(mid.anomalous());
        assertTrue(high.anomalous());
        assertEquals("RULES_FALLBACK", low.source());

        // Scores must be strictly increasing with power severity
        assertTrue(low.score() < mid.score(),
                "Score at 3kW (" + low.score() + ") should be less than at 7kW (" + mid.score() + ")");
        assertTrue(mid.score() < high.score(),
                "Score at 7kW (" + mid.score() + ") should be less than at 9kW (" + high.score() + ")");

        // None should be clamped to the ceiling across the demo's realistic range
        assertTrue(low.score() < 0.98, "3kW should not hit the 0.98 ceiling");
        assertTrue(mid.score() < 0.98, "7kW should not hit the 0.98 ceiling");
        assertTrue(high.score() < 0.98, "9kW should not hit the 0.98 ceiling");
    }

    @Test
    void zeroCurrentUnderLoad_ceilOnlyAtExtremeLoad() {
        packet.setCurrent(0.005);
        packet.setKwConsumed(16.0); // extreme — should be at or near ceiling
        AnomalyResult extreme = engine.evaluate(packet);
        assertTrue(extreme.anomalous());
        assertTrue(extreme.score() >= 0.95, "Extreme 16kW load should score near the ceiling");
    }

    // ── Voltage out-of-band ──────────────────────────────────────────

    @Test
    void voltageOutOfBand_scoreVariesWithDeviation() {
        packet.setCurrent(5.0);
        packet.setKwConsumed(1.0);

        packet.setVoltage(195.0); // 5V sag
        AnomalyResult mild = engine.evaluate(packet);

        packet.setVoltage(180.0); // 20V sag
        AnomalyResult moderate = engine.evaluate(packet);

        packet.setVoltage(170.0); // 30V sag (validation center)
        AnomalyResult severe = engine.evaluate(packet);

        assertTrue(mild.anomalous());
        assertTrue(moderate.anomalous());
        assertTrue(severe.anomalous());

        // Scores must be strictly increasing with deviation severity
        assertTrue(mild.score() < moderate.score(),
                "5V sag (" + mild.score() + ") should score less than 20V sag (" + moderate.score() + ")");
        assertTrue(moderate.score() < severe.score(),
                "20V sag (" + moderate.score() + ") should score less than 30V sag (" + severe.score() + ")");

        // The typical 170V sag (validation center) should NOT be clamped
        assertTrue(severe.score() < 0.95,
                "30V sag (V=170) should not hit the 0.95 ceiling, got " + severe.score());
    }

    @Test
    void voltageOutOfBand_ceilOnlyAtExtremeDeviation() {
        packet.setCurrent(5.0);
        packet.setKwConsumed(1.0);
        packet.setVoltage(140.0); // 60V sag — genuinely extreme
        AnomalyResult extreme = engine.evaluate(packet);
        assertTrue(extreme.anomalous());
        assertEquals(0.95, extreme.score(), 0.001, "60V sag should hit the 0.95 ceiling");
    }

    // ── Suspicious load mismatch ─────────────────────────────────────

    @Test
    void suspiciousLoad_scoreVariesWithMismatch() {
        // V*A/1000 = 230*5/1000 = 1.15, so kW=7 gives mismatch = 5.85, kW=15 gives 13.85
        packet.setVoltage(230.0);
        packet.setCurrent(5.0);

        packet.setKwConsumed(7.0); // mismatch = 5.85
        AnomalyResult low = engine.evaluate(packet);

        packet.setKwConsumed(12.0); // mismatch = 10.85
        AnomalyResult mid = engine.evaluate(packet);

        packet.setKwConsumed(18.0); // mismatch = 16.85
        AnomalyResult high = engine.evaluate(packet);

        assertTrue(low.anomalous());
        assertTrue(mid.anomalous());
        assertTrue(high.anomalous());

        assertTrue(low.score() < mid.score(),
                "Mismatch 5.85 (" + low.score() + ") should score less than 10.85 (" + mid.score() + ")");
        assertTrue(mid.score() < high.score(),
                "Mismatch 10.85 (" + mid.score() + ") should score less than 16.85 (" + high.score() + ")");
    }

    // ── Normal readings ──────────────────────────────────────────────

    @Test
    void normalReading_notAnomalous() {
        AnomalyResult result = engine.evaluate(packet);
        assertFalse(result.anomalous());
        assertTrue(result.score() < 0.25, "Normal score should be well below anomaly range");
    }

    @Test
    void normalReading_scoreVariesWithVoltagePosition() {
        packet.setVoltage(230.0); // centered
        AnomalyResult centered = engine.evaluate(packet);

        packet.setVoltage(250.0); // off-center but still in band
        AnomalyResult offCenter = engine.evaluate(packet);

        assertFalse(centered.anomalous());
        assertFalse(offCenter.anomalous());
        assertTrue(centered.score() < offCenter.score(),
                "Centered voltage should score lower than off-center");
    }
}
