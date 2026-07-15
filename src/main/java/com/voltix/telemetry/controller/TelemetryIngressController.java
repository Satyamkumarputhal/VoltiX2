package com.voltix.telemetry.controller;

import com.voltix.telemetry.dto.TelemetryPacket;
import com.voltix.telemetry.service.TelemetryIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryIngressController {
    private final TelemetryIngestionService ingestionService;

    public TelemetryIngressController(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, String>> ingestTelemetry(@Valid @RequestBody TelemetryPacket packet) {
        UUID transactionId = ingestionService.accept(packet);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "ACCEPTED",
                        "transactionId", transactionId.toString()
                ));
    }
}
