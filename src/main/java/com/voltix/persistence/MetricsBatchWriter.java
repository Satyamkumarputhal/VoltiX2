package com.voltix.persistence;

import com.voltix.platform.config.VoltixProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsBatchWriter {
    private static final Logger log = LoggerFactory.getLogger(MetricsBatchWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final VoltixProperties properties;
    private final LinkedBlockingQueue<MetricsHistoryRow> pendingRows = new LinkedBlockingQueue<>();
    private final AtomicLong writeLagMillis = new AtomicLong(0);

    public MetricsBatchWriter(JdbcTemplate jdbcTemplate,
                              VoltixProperties properties,
                              MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        meterRegistry.gauge("voltix.db.metrics.pending.batch.depth", pendingRows, LinkedBlockingQueue::size);
        meterRegistry.gauge("voltix.db.metrics.write.lag.ms", writeLagMillis);
    }

    public void enqueue(MetricsHistoryRow row) {
        pendingRows.offer(row);
    }

    @Scheduled(fixedDelayString = "${voltix.telemetry.batch.flush-interval-ms:250}")
    public void flushScheduled() {
        flush(false);
    }

    @PreDestroy
    public void flushOnShutdown() {
        flush(true);
    }

    public void flush(boolean drainAll) {
        int maxSize = drainAll ? Integer.MAX_VALUE : properties.getTelemetry().getBatch().getMaxSize();
        List<MetricsHistoryRow> batch = new ArrayList<>(Math.min(maxSize, properties.getTelemetry().getBatch().getMaxSize()));
        pendingRows.drainTo(batch, maxSize);
        if (batch.isEmpty()) {
            writeLagMillis.set(0);
            return;
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO metrics_history
                    (tenant_id, meter_id, zone_id, voltage, current_amp, kw_consumed, anomaly_score, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, batch, batch.size(), (ps, row) -> {
            ps.setLong(1, row.tenantId());
            ps.setString(2, row.meterId());
            ps.setLong(3, row.zoneId());
            ps.setDouble(4, row.voltage());
            ps.setDouble(5, row.currentAmp());
            ps.setDouble(6, row.kwConsumed());
            ps.setDouble(7, row.anomalyScore());
            ps.setTimestamp(8, Timestamp.from(row.recordedAt().toInstant()));
        });

        jdbcTemplate.batchUpdate("""
                UPDATE telemetry_staging
                   SET processed = TRUE, processed_at = CURRENT_TIMESTAMP, failure_reason = NULL
                 WHERE staging_id = ?
                """, batch, batch.size(), (ps, row) -> ps.setLong(1, row.stagingId()));

        MetricsHistoryRow oldest = batch.get(0);
        long lag = Duration.between(oldest.recordedAt().toInstant(), Instant.now()).toMillis();
        writeLagMillis.set(Math.max(lag, 0));
        log.debug("Flushed {} telemetry metrics rows", batch.size());

        if (drainAll && !pendingRows.isEmpty()) {
            flush(true);
        }
    }

    public MetricsHistoryRow pollForTest(Duration timeout) throws InterruptedException {
        return pendingRows.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
