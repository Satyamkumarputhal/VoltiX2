package com.voltix.telemetry.validation;

import com.voltix.platform.config.VoltixProperties;
import com.voltix.telemetry.dto.TelemetryPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelemetryValidatorTest {

    private TelemetryValidator validator;
    private VoltixProperties properties;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("UTC"));
        properties = new VoltixProperties();
        // The default validation limits: minVoltage=200, maxVoltage=260, maxPastSkewMinutes=5, maxFutureSkewMinutes=1
        validator = new TelemetryValidator(properties, fixedClock);
    }

    private TelemetryPacket createValidPacket() {
        TelemetryPacket packet = new TelemetryPacket();
        packet.setMeterId("METER-001");
        packet.setVoltage(230.0);
        packet.setCurrent(10.5);
        packet.setKwConsumed(2.415);
        packet.setRecordedAt(ZonedDateTime.now(fixedClock));
        return packet;
    }

    @Test
    void whenPacketIsValid_thenNoExceptionThrown() {
        TelemetryPacket packet = createValidPacket();
        assertDoesNotThrow(() -> validator.validate(packet));
    }

    @Test
    void whenVoltageIsTooLow_thenThrowsException() {
        TelemetryPacket packet = createValidPacket();
        packet.setVoltage(199.9);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(packet));
    }

    @Test
    void whenVoltageIsTooHigh_thenThrowsException() {
        TelemetryPacket packet = createValidPacket();
        packet.setVoltage(260.1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(packet));
    }

    @Test
    void whenCurrentIsNegative_thenThrowsException() {
        TelemetryPacket packet = createValidPacket();
        packet.setCurrent(-0.1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(packet));
    }

    @Test
    void whenRecordedAtIsTooFarInPast_thenThrowsException() {
        TelemetryPacket packet = createValidPacket();
        // limit is 5 minutes in past
        packet.setRecordedAt(ZonedDateTime.now(fixedClock).minusMinutes(6));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(packet));
    }

    @Test
    void whenRecordedAtIsTooFarInFuture_thenThrowsException() {
        TelemetryPacket packet = createValidPacket();
        // limit is 1 minute in future
        packet.setRecordedAt(ZonedDateTime.now(fixedClock).plusMinutes(2));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(packet));
    }
}
