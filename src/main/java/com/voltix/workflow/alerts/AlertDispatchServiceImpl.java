package com.voltix.workflow.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.ZonedDateTime;

@Service
public class AlertDispatchServiceImpl implements AlertDispatchService {
    private static final Logger log = LoggerFactory.getLogger(AlertDispatchServiceImpl.class);

    private static final String ALERT_TOPIC = "/topic/alerts";

    private final SystemAlertRepository alertRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;
    private final DataSource dataSource;

    public AlertDispatchServiceImpl(SystemAlertRepository alertRepository,
                                    JdbcTemplate jdbcTemplate,
                                    SimpMessagingTemplate messagingTemplate,
                                    PlatformTransactionManager transactionManager,
                                    DataSource dataSource) {
        this.alertRepository = alertRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.messagingTemplate = messagingTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dataSource = dataSource;
    }

    @Override
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

    @Override
    @Transactional
    public int clearAllAlertsForTenant(Long tenantId) {
        return transactionTemplate.execute(status -> {
            ZonedDateTime now = ZonedDateTime.now();
            Timestamp resolvedAt = Timestamp.from(now.toInstant());
            String sql = "UPDATE system_alerts SET status = ?, resolved_at = ? WHERE tenant_id = ? AND status = ?";
            Object[] params = new Object[]{AlertStatus.ACKNOWLEDGED.toString(), resolvedAt, tenantId, AlertStatus.OPEN.toString()};

            int updated = jdbcTemplate.update(sql, params);

            log.info("Cleared {} alerts for tenantId={}", updated, tenantId);
            return updated;
        });
    }

    @Override
    @Transactional
    public SystemAlert acknowledgeAlert(Long alertId, Long tenantId) {
        // Use JdbcTemplate to load the alert, bypassing Hibernate cache
        var alertRow = jdbcTemplate.query(
                "SELECT alert_id, tenant_id, meter_id, zone_id, alert_type, severity, anomaly_score, priority_score, status, detected_at, assigned_to, resolved_at FROM system_alerts WHERE alert_id = ? AND tenant_id = ?",
                (rs, rowNum) -> {
                    SystemAlert alert = new SystemAlert();
                    alert.setAlertId(rs.getLong("alert_id"));
                    alert.setTenantId(rs.getLong("tenant_id"));
                    alert.setMeterId(rs.getString("meter_id"));
                    alert.setZoneId(rs.getLong("zone_id"));
                    alert.setAlertType(rs.getString("alert_type"));
                    alert.setSeverity(AlertSeverity.valueOf(rs.getString("severity")));
                    alert.setAnomalyScore(rs.getBigDecimal("anomaly_score"));
                    alert.setPriorityScore(rs.getBigDecimal("priority_score"));
                    alert.setStatus(AlertStatus.valueOf(rs.getString("status")));
                    // Convert timestamptz to ZonedDateTime via Timestamp
                    java.sql.Timestamp detectedTs = rs.getTimestamp("detected_at");
                    if (detectedTs != null) {
                        alert.setDetectedAt(detectedTs.toInstant().atZone(java.time.ZoneOffset.UTC));
                    }
                    alert.setAssignedTo(rs.getObject("assigned_to", Long.class));
                    java.sql.Timestamp resolvedTs = rs.getTimestamp("resolved_at");
                    if (resolvedTs != null) {
                        alert.setResolvedAt(resolvedTs.toInstant().atZone(java.time.ZoneOffset.UTC));
                    }
                    return alert;
                },
                alertId, tenantId
        );

        if (alertRow.isEmpty()) {
            log.warn("Alert not found: id={}, tenantId={}", alertId, tenantId);
            return null;
        }

        SystemAlert alert = alertRow.get(0);
        if (alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
            log.info("Alert already acknowledged: id={}, tenantId={}", alertId, tenantId);
            return alert;
        }

        ZonedDateTime now = ZonedDateTime.now();
        Timestamp resolvedAt = Timestamp.from(now.toInstant());
        String sql = "UPDATE system_alerts SET status = ?, resolved_at = ? WHERE alert_id = ? AND tenant_id = ? AND status = ?";
        Object[] params = new Object[]{AlertStatus.ACKNOWLEDGED.toString(), resolvedAt, alertId, tenantId, AlertStatus.OPEN.toString()};

        int updated = jdbcTemplate.update(sql, params);

        if (updated == 0) {
            log.warn("Alert not updated (not OPEN or not found): id={}, tenantId={}", alertId, tenantId);
            return null;
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setResolvedAt(now);
        log.info("Alert acknowledged via JdbcTemplate (bypassing Hibernate): id={}, tenantId={}", alertId, tenantId);

        return alert;
    }
}