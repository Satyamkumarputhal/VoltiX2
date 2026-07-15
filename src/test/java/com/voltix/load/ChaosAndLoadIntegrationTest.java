package com.voltix.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltix.telemetry.dto.TelemetryPacket;
import com.voltix.persistence.MetricsBatchWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ChaosAndLoadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MetricsBatchWriter batchWriter;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM system_alerts");
        jdbcTemplate.execute("DELETE FROM public_complaints");
        jdbcTemplate.execute("DELETE FROM zone_hourly_aggregates");
        jdbcTemplate.execute("DELETE FROM metrics_history");
        jdbcTemplate.execute("DELETE FROM telemetry_staging");
        jdbcTemplate.execute("DELETE FROM smart_meters");
        jdbcTemplate.execute("DELETE FROM grid_zones");
        jdbcTemplate.execute("DELETE FROM tenants");

        jdbcTemplate.execute("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (1, 'Tenant 1', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (1, 1, 'Zone 1', 1.0)");
        jdbcTemplate.execute("INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status) VALUES ('SM-10029', 1, 1, 'SN-10029', 'ACTIVE')");
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void testEndToEndTelemetryIngestAndFallbackProcessing() throws Exception {
        TelemetryPacket packet = new TelemetryPacket();
        packet.setMeterId("SM-10029");
        packet.setVoltage(230.0);
        packet.setCurrent(10.0);
        packet.setKwConsumed(2.3);
        packet.setRecordedAt(ZonedDateTime.now());
        packet.setTenantId(1L);
        packet.setZoneId(1L);
        packet.setTransactionId(UUID.randomUUID());

        // 1. Submit packet via HTTP Ingress
        mockMvc.perform(post("/api/v1/telemetry/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(packet)))
                .andExpect(status().isAccepted());

        // 2. Poll and flush until staging record is marked processed (up to 30 iterations * 200ms = 6s timeout)
        boolean processed = false;
        for (int i = 0; i < 30; i++) {
            batchWriter.flush(true);
            Integer unprocessedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM telemetry_staging WHERE processed = FALSE", Integer.class);
            if (unprocessedCount != null && unprocessedCount == 0) {
                processed = true;
                break;
            }
            Thread.sleep(200);
        }
        assertTrue(processed, "Telemetry processing timed out");

        // 3. Verify metrics are in metrics_history
        Integer historyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM metrics_history", Integer.class);
        assertTrue(historyCount != null && historyCount == 1, "Metrics history table should have 1 record");
    }
}
