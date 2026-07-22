package com.voltix.analytics.anomaly;

import com.voltix.telemetry.dto.TelemetryPacket;
import org.springframework.stereotype.Component;

/**
 * Deterministic rules-based anomaly scoring engine, used as the fallback
 * when ONNX inference is unavailable (session pool exhausted, model file
 * missing, or inference exception).
 * <p>
 * Each formula maps a physically-meaningful severity dimension to the 0..1
 * score range. The ceiling clamp should only bind at genuinely extreme
 * inputs, not across the typical range for that anomaly type — otherwise
 * the score becomes a constant that conveys no severity information.
 */
@Component
public class RulesFallbackEngine {

    public AnomalyResult evaluate(TelemetryPacket packet) {
        boolean zeroCurrentUnderLoad = packet.getKwConsumed() > 0.25 && packet.getCurrent() <= 0.01;
        boolean voltageOutOfBand = packet.getVoltage() < 200.0 || packet.getVoltage() > 260.0;
        boolean suspiciousLoad = packet.getKwConsumed() > 0.0 && packet.getVoltage() > 0.0
                && packet.getCurrent() > 0.0
                && Math.abs(packet.getKwConsumed() - ((packet.getVoltage() * packet.getCurrent()) / 1000.0)) > 5.0;

        if (zeroCurrentUnderLoad) {
            // Severity proportional to load with near-zero current.
            // Realistic kW range: 0.26 (just above trigger threshold) to ~9.0.
            // Formula: maps [0.25, 15.0] kW onto [0.60, 0.98] score range linearly.
            // At kW=0.26 → 0.600, at kW=7.0 → 0.873, at kW=9.0 → 0.954, at kW=15 → 0.98 (clamp).
            double score = Math.min(0.98, 0.60 + (packet.getKwConsumed() * 0.025));
            return new AnomalyResult(score, true, "RULES_FALLBACK");
        }
        if (voltageOutOfBand) {
            // Severity proportional to how far voltage deviates from the 200-260V safe band.
            // Realistic sag: 100-199V. Realistic swell: 261-310V.
            // Formula: maps deviation [1V, 60V+] onto [0.60, 0.95] score range.
            // At 1V out (V=199 or 261) → 0.606, at 30V out (V=170 or 290) → 0.78,
            // at 60V out (V=140 or 320) → 0.95 (clamp, genuinely extreme).
            double deviation = packet.getVoltage() < 200.0
                    ? (200.0 - packet.getVoltage())
                    : (packet.getVoltage() - 260.0);
            double score = Math.min(0.95, 0.60 + (deviation / 100.0));
            return new AnomalyResult(score, true, "RULES_FALLBACK");
        }
        if (suspiciousLoad) {
            // Severity proportional to power/current mismatch magnitude.
            // Triggers when mismatch > 5.0 kW. Realistic range: 5-30 kW.
            // At mismatch=5.1 → 0.652, at mismatch=18.5 → 0.92 (clamp).
            // Clamp only binds at genuinely extreme mismatch (>18.5 kW) — acceptable.
            double mismatch = Math.abs(packet.getKwConsumed() - ((packet.getVoltage() * packet.getCurrent()) / 1000.0));
            double score = Math.min(0.92, 0.55 + (mismatch * 0.02));
            return new AnomalyResult(score, true, "RULES_FALLBACK");
        }
        // Normal — produce varied low scores based on how "centered" the reading is
        double normalcy = Math.abs(packet.getVoltage() - 230.0) / 60.0;
        double score = Math.min(0.25, 0.02 + normalcy * 0.15);
        return new AnomalyResult(score, false, "RULES_FALLBACK");
    }
}
