package com.voltix.telemetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltix.platform.config.VoltixProperties;
import com.voltix.telemetry.dto.TelemetryPacket;
import com.voltix.telemetry.entity.TelemetryStaging;
import com.voltix.telemetry.repository.TelemetryStagingRepository;
import com.voltix.telemetry.worker.TelemetryWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TelemetryIngressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VoltixProperties properties;

    @MockBean
    private TelemetryStagingRepository stagingRepository;

    @MockBean
    @Qualifier("telemetryIngestionExecutor")
    private TaskExecutor telemetryIngestionExecutor;

    @MockBean
    private TelemetryWorker telemetryWorker;

    private TelemetryPacket validPacket;

    @BeforeEach
    void setUp() {
        validPacket = new TelemetryPacket();
        validPacket.setMeterId("SM-10029");
        validPacket.setVoltage(230.0);
        validPacket.setCurrent(5.0);
        validPacket.setKwConsumed(1.15);
        validPacket.setRecordedAt(ZonedDateTime.now());
        validPacket.setTenantId(1L);
        validPacket.setZoneId(1L);
        validPacket.setTransactionId(UUID.randomUUID());
    }

    @Test
    @WithMockUser
    void whenIngestSuccess_thenReturn202() throws Exception {
        TelemetryStaging mockSaved = new TelemetryStaging();
        mockSaved.setStagingId(42L);
        mockSaved.setTenantId(1L);
        mockSaved.setMeterId(validPacket.getMeterId());
        mockSaved.setTransactionId(validPacket.getTransactionId());

        when(stagingRepository.saveAndFlush(any(TelemetryStaging.class))).thenReturn(mockSaved);

        mockMvc.perform(post("/api/v1/telemetry/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPacket)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.transactionId").value(validPacket.getTransactionId().toString()));
    }

    @Test
    @WithMockUser
    void whenValidationFails_thenReturn400() throws Exception {
        validPacket.setVoltage(180.0); // Outside nominal 200-260V range

        mockMvc.perform(post("/api/v1/telemetry/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPacket)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Voltage must be within 200-260V operating bounds"));
    }

    @Test
    @WithMockUser
    void whenStagingDbThrowsError_thenReturn503() throws Exception {
        when(stagingRepository.saveAndFlush(any(TelemetryStaging.class)))
                .thenThrow(new QueryTimeoutException("Database connection timeout"));

        mockMvc.perform(post("/api/v1/telemetry/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPacket)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Telemetry durability store is unavailable"));
    }

    @Test
    @WithMockUser
    void whenExecutorQueueSaturated_thenReturn429() throws Exception {
        TelemetryStaging mockSaved = new TelemetryStaging();
        mockSaved.setStagingId(42L);
        when(stagingRepository.saveAndFlush(any(TelemetryStaging.class))).thenReturn(mockSaved);

        doThrow(new RejectedExecutionException("Queue full"))
                .when(telemetryIngestionExecutor).execute(any(Runnable.class));

        mockMvc.perform(post("/api/v1/telemetry/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPacket)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("VoltiX ingestion capacity is saturated"));
    }
}
