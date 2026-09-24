package com.voltix.analytics.forecasting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authenticated HTTP integration tests for GET /api/v1/forecasts.
 *
 * These exercise the real security filter chain: they log in through the actual
 * /api/v1/auth/login endpoint to obtain a signed JWT (which populates
 * TenantContext via JwtAuthenticationFilter), then call the forecast endpoint
 * with that Bearer token. Tenant isolation is proven end-to-end, not mocked.
 *
 * IMPORTANT — deterministic counts: the shared dev database already contains
 * forecast rows for the real seeded tenant 1 (zone 1, demo/other tests). To make
 * "$.length()" assertions deterministic, ALL forecast data in this class is
 * written under TWO DEDICATED tenants (999_010 = tenant A, 999_011 = tenant B)
 * that no other test or seed data uses, each with its own operator/admin/
 * inspector users. Every fixture row is removed in @AfterEach. The class never
 * asserts counts against the real tenant 1.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ForecastControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Dedicated tenant A
    private static final long TA_TENANT_ID = 999_010L;
    private static final long TA_ZONE_A = 999_010L;
    private static final long TA_ZONE_B = 999_012L;
    private static final String TA_OPERATOR = "fc-operatorA-999010";
    private static final String TA_ADMIN    = "fc-adminA-999010";
    private static final String TA_INSPECTOR = "fc-inspectorA-999010";

    // Dedicated tenant B (for cross-tenant isolation)
    private static final long TB_TENANT_ID = 999_011L;
    private static final long TB_ZONE = 999_011L;
    private static final String TB_OPERATOR = "fc-operatorB-999011";

    // bcrypt hash of "password" (same hash the seeded users use)
    private static final String PW_HASH = "$2a$12$IfrQOxJ4vlVSWiDELUf1wuJdJRa4ixZcKIP2/hChCKlfAX6zDTZcq";

    @BeforeEach
    void setUp() {
        cleanFixtures();

        // Tenant A + zones + users
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TA_TENANT_ID, "Forecast Test Tenant A");
        insertZone(TA_ZONE_A, TA_TENANT_ID, "FC Tenant A Zone A");
        insertZone(TA_ZONE_B, TA_TENANT_ID, "FC Tenant A Zone B");
        insertUser(TA_TENANT_ID, TA_OPERATOR, "OPERATOR");
        insertUser(TA_TENANT_ID, TA_ADMIN, "ADMIN");
        insertUser(TA_TENANT_ID, TA_INSPECTOR, "INSPECTOR");

        // Tenant B + zone + operator
        jdbcTemplate.update("INSERT INTO tenants (tenant_id, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                TB_TENANT_ID, "Forecast Test Tenant B");
        insertZone(TB_ZONE, TB_TENANT_ID, "FC Tenant B Zone");
        insertUser(TB_TENANT_ID, TB_OPERATOR, "OPERATOR");
    }

    @AfterEach
    void tearDown() {
        cleanFixtures();
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM zone_hourly_aggregates WHERE zone_id IN (?, ?, ?)",
                TA_ZONE_A, TA_ZONE_B, TB_ZONE);
        jdbcTemplate.update("DELETE FROM grid_zones WHERE zone_id IN (?, ?, ?)",
                TA_ZONE_A, TA_ZONE_B, TB_ZONE);
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?, ?, ?)",
                TA_OPERATOR, TA_ADMIN, TA_INSPECTOR, TB_OPERATOR);
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", TA_TENANT_ID, TB_TENANT_ID);
    }

    private void insertZone(long zoneId, long tenantId, String name) {
        jdbcTemplate.update(
                "INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) VALUES (?, ?, ?, 1.0)",
                zoneId, tenantId, name);
    }

    private void insertUser(long tenantId, String username, String role) {
        jdbcTemplate.update("INSERT INTO users (tenant_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                tenantId, username, PW_HASH, role);
    }

    /**
     * Insert a forecast row with EXPLICIT, independently-chosen source/target/
     * value/generated timestamps so tests can prove the API reads the persisted
     * columns rather than deriving them.
     */
    private void insertForecast(long tenantId, long zoneId,
                                ZonedDateTime aggregatedHour,
                                ZonedDateTime forecastTargetHour,
                                Double forecastKw2h,
                                ZonedDateTime generatedAt) {
        jdbcTemplate.update("""
                INSERT INTO zone_hourly_aggregates
                    (tenant_id, zone_id, aggregated_hour, total_kw_consumed,
                     forecast_kw_2h, forecast_target_hour, forecast_generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId, zoneId,
                Timestamp.from(aggregatedHour.toInstant()),
                123.4567,  // total_kw_consumed (actual@source) — deliberately distinct from forecast
                forecastKw2h == null ? null : java.math.BigDecimal.valueOf(forecastKw2h),
                forecastTargetHour == null ? null : Timestamp.from(forecastTargetHour.toInstant()),
                generatedAt == null ? null : Timestamp.from(generatedAt.toInstant()));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    // ── Test 1: authorized user can retrieve forecasts ────────────────────────
    @Test
    void authorizedOperator_canRetrieveForecasts() throws Exception {
        ZonedDateTime src = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).minusHours(5);
        insertForecast(TA_TENANT_ID, TA_ZONE_A, src, src.plusHours(2), 10.5, src.plusMinutes(1));

        String token = loginAndGetToken(TA_OPERATOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].zoneId").value(TA_ZONE_A))
                .andExpect(jsonPath("$[0].forecastKw2h").value(10.5));
    }

    // ── Test 2 & 3: response carries the PERSISTED target hour, not source+2h ──
    @Test
    void response_containsPersistedForecastTargetHour_notDerivedFromSource() throws Exception {
        // Deliberately make forecast_target_hour NOT equal aggregated_hour + 2h.
        // If the API derived target as source+2h it would return 12:00; the
        // persisted column says 17:00, so the response must show 17:00.
        ZonedDateTime src = ZonedDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime persistedTarget = src.plusHours(7);   // 17:00 — the real column value
        insertForecast(TA_TENANT_ID, TA_ZONE_A, src, persistedTarget, 22.25, src.plusMinutes(2));

        String token = loginAndGetToken(TA_OPERATOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].aggregatedHour").value(org.hamcrest.Matchers.startsWith("2026-03-01T10:00")))
                // The persisted target (17:00) must be returned, NOT source+2h (12:00).
                .andExpect(jsonPath("$[0].forecastTargetHour").value(org.hamcrest.Matchers.startsWith("2026-03-01T17:00")))
                .andExpect(jsonPath("$[0].forecastKw2h").value(22.25));
    }

    // ── Test 4: latest forecast per zone chosen by forecast_generated_at ──────
    @Test
    void latestForecastPerZone_isSelected_whenMultipleRowsExist() throws Exception {
        ZonedDateTime base = ZonedDateTime.of(2026, 3, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        // Older forecast (generated earlier)
        insertForecast(TA_TENANT_ID, TA_ZONE_A, base, base.plusHours(2), 5.0, base.plusMinutes(1));
        // Newer forecast (generated later) — this one must win
        insertForecast(TA_TENANT_ID, TA_ZONE_A, base.plusHours(1), base.plusHours(3), 9.9,
                base.plusHours(1).plusMinutes(1));

        String token = loginAndGetToken(TA_OPERATOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].forecastKw2h").value(9.9))
                .andExpect(jsonPath("$[0].forecastTargetHour").value(org.hamcrest.Matchers.startsWith("2026-03-01T11:00")));
    }

    // ── Test 5: rows with null forecast_kw_2h are excluded ────────────────────
    @Test
    void rowsWithNullForecast_areExcluded() throws Exception {
        ZonedDateTime src = ZonedDateTime.of(2026, 3, 2, 9, 0, 0, 0, ZoneOffset.UTC);
        // Zone A: only a NULL-forecast row (aggregate exists, no forecast yet)
        insertForecast(TA_TENANT_ID, TA_ZONE_A, src, null, null, null);
        // Zone B: a real forecast
        insertForecast(TA_TENANT_ID, TA_ZONE_B, src, src.plusHours(2), 7.0, src.plusMinutes(1));

        String token = loginAndGetToken(TA_OPERATOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // Only Zone B should appear; Zone A's null-forecast row is excluded.
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].zoneId").value(TA_ZONE_B))
                .andExpect(jsonPath("$[0].forecastKw2h").value(7.0));
    }

    // ── Test 6: tenant A cannot read tenant B forecasts ───────────────────────
    @Test
    void tenantA_cannotRetrieveTenantB_forecasts() throws Exception {
        ZonedDateTime src = ZonedDateTime.of(2026, 3, 3, 6, 0, 0, 0, ZoneOffset.UTC);
        insertForecast(TB_TENANT_ID, TB_ZONE, src, src.plusHours(2), 88.0, src.plusMinutes(1));    // tenant B
        insertForecast(TA_TENANT_ID, TA_ZONE_A, src, src.plusHours(2), 11.0, src.plusMinutes(1));  // tenant A

        // Tenant A operator: sees ONLY tenant A's forecast (zone A), never tenant B's.
        String tokenA = loginAndGetToken(TA_OPERATOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].zoneId").value(TA_ZONE_A))
                .andExpect(jsonPath("$[0].forecastKw2h").value(11.0));

        // Tenant B operator: sees ONLY tenant B's forecast.
        String tokenB = loginAndGetToken(TB_OPERATOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].zoneId").value(TB_ZONE))
                .andExpect(jsonPath("$[0].forecastKw2h").value(88.0));
    }

    // ── Test 7: no forecasts → HTTP 200 + empty list ──────────────────────────
    @Test
    void noForecasts_returns200AndEmptyList() throws Exception {
        // Tenant A has zones but NO forecast rows at all.
        String token = loginAndGetToken(TA_OPERATOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── Test 8: role authorization intact — INSPECTOR is forbidden ────────────
    @Test
    void inspectorRole_isForbidden() throws Exception {
        String inspectorToken = loginAndGetToken(TA_INSPECTOR, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isForbidden());
    }

    // ── Test 8b: ADMIN role is allowed ────────────────────────────────────────
    @Test
    void adminRole_isAllowed() throws Exception {
        String adminToken = loginAndGetToken(TA_ADMIN, "password");
        mockMvc.perform(get("/api/v1/forecasts").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── Test 8c: no token → request is rejected (existing app behavior) ───────
    @Test
    void unauthenticated_isRejected() throws Exception {
        // The application does not configure a custom AuthenticationEntryPoint,
        // so an unauthenticated request to a method-secured endpoint is denied.
        // We assert the request is rejected with a 4xx (not served), matching the
        // application's existing security behavior rather than prescribing 401.
        // The application configures no custom AuthenticationEntryPoint, so an
        // unauthenticated request to this method-secured endpoint is denied with
        // HTTP 403 (verified). This matches the app's existing security behavior
        // for all other protected endpoints; we assert 4xx to stay decoupled from
        // the exact code while documenting that it is 403 today.
        mockMvc.perform(get("/api/v1/forecasts"))
                .andExpect(status().is4xxClientError());
    }
}
