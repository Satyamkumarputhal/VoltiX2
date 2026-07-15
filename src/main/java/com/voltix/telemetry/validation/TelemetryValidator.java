package com.voltix.telemetry.validation;

import com.voltix.platform.config.VoltixProperties;
import com.voltix.telemetry.dto.TelemetryPacket;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;

@Component
public class TelemetryValidator {
    private final VoltixProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TelemetryValidator(VoltixProperties properties) {
        this(properties, Clock.systemUTC());
    }

    TelemetryValidator(VoltixProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void validate(TelemetryPacket packet) {
        VoltixProperties.Validation validation = properties.getTelemetry().getValidation();
        if (packet.getVoltage() < validation.getMinVoltage() || packet.getVoltage() > validation.getMaxVoltage()) {
            throw new IllegalArgumentException("Voltage must be within 200-260V operating bounds");
        }
        if (packet.getCurrent() < 0) {
            throw new IllegalArgumentException("Current must be greater than or equal to 0A");
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime earliest = now.minusMinutes(validation.getMaxPastSkewMinutes());
        ZonedDateTime latest = now.plusMinutes(validation.getMaxFutureSkewMinutes());
        if (packet.getRecordedAt().isBefore(earliest) || packet.getRecordedAt().isAfter(latest)) {
            throw new IllegalArgumentException("Telemetry timestamp falls outside the allowed clock skew window");
        }
    }
}
