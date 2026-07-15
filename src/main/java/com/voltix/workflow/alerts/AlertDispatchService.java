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

        AlertSeverity severity = determineSeverity(priorityScore);

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

    private AlertSeverity determineSeverity(BigDecimal priorityScore) {
        double score = priorityScore.doubleValue();
        if (score >= 4.0) {
            return AlertSeverity.CRITICAL;
        } else if (score >= 2.5) {
            return AlertSeverity.HIGH;
        } else if (score >= 1.0) {
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
