package com.voltix.analytics.anomaly;

import com.voltix.telemetry.dto.TelemetryPacket;
import org.springframework.stereotype.Component;

@Component
public class RulesFallbackEngine {
    public AnomalyResult evaluate(TelemetryPacket packet) {
        boolean zeroCurrentUnderLoad = packet.getKwConsumed() > 0.25 && packet.getCurrent() <= 0.01;
        boolean voltageOutOfBand = packet.getVoltage() < 200.0 || packet.getVoltage() > 260.0;
        boolean suspiciousLoad = packet.getKwConsumed() > 0.0 && packet.getVoltage() > 0.0
                && packet.getCurrent() > 0.0
                && Math.abs(packet.getKwConsumed() - ((packet.getVoltage() * packet.getCurrent()) / 1000.0)) > 5.0;

        if (zeroCurrentUnderLoad) {
            // Severity proportional to load with near-zero current
            double score = Math.min(0.98, 0.70 + (packet.getKwConsumed() * 0.05));
            return new AnomalyResult(score, true, "RULES_FALLBACK");
        }
        if (voltageOutOfBand) {
            // Severity proportional to how far out of band
            double deviation = packet.getVoltage() < 200.0
                    ? (200.0 - packet.getVoltage()) / 50.0
                    : (packet.getVoltage() - 260.0) / 50.0;
            double score = Math.min(0.95, 0.60 + deviation);
            return new AnomalyResult(score, true, "RULES_FALLBACK");
        }
        if (suspiciousLoad) {
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
