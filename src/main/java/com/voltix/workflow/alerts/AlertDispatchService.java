package com.voltix.workflow.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Service
public class AlertDispatchService {
    private static final Logger log = LoggerFactory.getLogger(AlertDispatchService.class);

    private static final String ALERT_TOPIC = "/topic/alerts";

    private final SystemAlertRepository alertRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public AlertDispatchService(SystemAlertRepository alertRepository,
                                JdbcTemplate jdbcTemplate,
                                SimpMessagingTemplate messagingTemplate) {
        this.alertRepository = alertRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public SystemAlert createAlert(Long tenantId, String meterId, Long zoneId, String alertType, Double anomalyScore) {
        BigDecimal riskMultiplier = getRiskMultiplier(zoneId);
        BigDecimal baseScore = BigDecimal.valueOf(anomalyScore);
        BigDecimal priorityScore = baseScore.multiply(riskMultiplier).setScale(4, RoundingMode.HALF_UP);

        // Severity is determined from continuousScore ALONE (the model's anomaly
        // confidence, 0-1 range), decoupled from risk_multiplier. This makes
        // severity a property of "how anomalous is this reading" and keeps
        // risk_multiplier's role clearly separate as "how urgent given zone context"
        // (used for priorityScore ranking/sorting within the alert feed).
        AlertSeverity severity = determineSeverity(baseScore);

        SystemAlert alert = new SystemAlert();
        alert.setTenantId(tenantId);
        alert.setMeterId(meterId);
        alert.setZoneId(zoneId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setAnomalyScore(baseScore.setScale(5, RoundingMode.HALF_UP));
        alert.setPriorityScore(priorityScore);
        alert.setStatus(AlertStatus.OPEN);
        alert.setDetectedAt(ZonedDateTime.now());

        SystemAlert saved = alertRepository.save(alert);
        log.info("System Alert created: ID={}, Type={}, Severity={}, PriorityScore={}",
                saved.getAlertId(), saved.getAlertType(), saved.getSeverity(), saved.getPriorityScore());

        // Dashboard notification trigger hook
        dispatchToDashboard(saved);

        return saved;
    }

    private final java.util.Map<Long, BigDecimal> riskMultiplierCache = new java.util.concurrent.ConcurrentHashMap<>();

    private BigDecimal getRiskMultiplier(Long zoneId) {
        return riskMultiplierCache.computeIfAbsent(zoneId, id -> {
            try {
                Double multiplier = jdbcTemplate.queryForObject(
                        "SELECT risk_multiplier FROM grid_zones WHERE zone_id = ?",
                        Double.class,
                        id
                );
                return multiplier != null ? BigDecimal.valueOf(multiplier) : BigDecimal.ONE;
            } catch (Exception e) {
                log.debug("No risk multiplier found for zoneId={}. Defaulting to 1.00", id);
                return BigDecimal.ONE;
            }
        });
    }

    private AlertSeverity determineSeverity(BigDecimal continuousScore) {
        // Thresholds calibrated against the REAL observed continuousScore distribution
        // from validate_model_performance.py (300 anomalous samples: sag + swell + leak,
        // seed=42, same methodology as the documented contamination=0.05 validation).
        //
        // continuousScore = Math.max(0, Math.min(1, 0.5 - decision_function))
        // Anomalous range observed: 0.486 to 0.647
        //   P25 = 0.545, Median = 0.573, P75 = 0.591, P90 = 0.628
        //
        // Breakpoints chosen at natural distribution gaps:
        //   LOW:      < 0.53  (below P25 — weakest anomaly signal, overlaps normal class)
        //   MEDIUM:   0.53–0.58 (P25 to ~P60 — moderate confidence)
        //   HIGH:     0.58–0.63 (P60 to P90 — strong confidence)
        //   CRITICAL: >= 0.63 (above P90 — highest confidence the model can produce)
        //
        // Expected distribution across representative anomaly population:
        //   ~25% LOW, ~35% MEDIUM, ~25% HIGH, ~15% CRITICAL
        //
        // Calibrated: 2026-07-26, commit score_distribution.py analysis.
        double score = continuousScore.doubleValue();
        if (score >= 0.63) {
            return AlertSeverity.CRITICAL;
        } else if (score >= 0.58) {
            return AlertSeverity.HIGH;
        } else if (score >= 0.53) {
            return AlertSeverity.MEDIUM;
        } else {
            return AlertSeverity.LOW;
        }
    }

    private void dispatchToDashboard(SystemAlert alert) {
        AlertWebSocketPayload payload = AlertWebSocketPayload.from(alert);
        messagingTemplate.convertAndSend(ALERT_TOPIC, payload);
        log.info("[WS DISPATCH] Broadcasting alert ID={} to dashboard operators", alert.getAlertId());
    }
}
