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

        if (zeroCurrentUnderLoad || voltageOutOfBand || suspiciousLoad) {
            return new AnomalyResult(0.95, true, "RULES_FALLBACK");
        }
        return new AnomalyResult(0.05, false, "RULES_FALLBACK");
    }
}
