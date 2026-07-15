package com.voltix.persistence;

import com.voltix.platform.config.VoltixProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class MetricsPartitionMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(MetricsPartitionMaintenanceService.class);

    private final JdbcTemplate jdbcTemplate;
    private final VoltixProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public MetricsPartitionMaintenanceService(JdbcTemplate jdbcTemplate, VoltixProperties properties) {
        this(jdbcTemplate, properties, Clock.systemUTC());
    }

    MetricsPartitionMaintenanceService(JdbcTemplate jdbcTemplate, VoltixProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 3_600_000)
    public void ensureForwardPartitions() {
        LocalDate today = LocalDate.now(clock);
        int daysAhead = properties.getTelemetry().getPartitions().getDaysAhead();
        for (int offset = 0; offset <= daysAhead; offset++) {
            LocalDate from = today.plusDays(offset);
            LocalDate to = from.plusDays(1);
            createDailyPartition(from, to);
        }
    }

    private void createDailyPartition(LocalDate from, LocalDate to) {
        String partitionName = "metrics_history_" + from.toString().replace("-", "_");
        String sql = """
                CREATE TABLE IF NOT EXISTS %s
                PARTITION OF metrics_history
                FOR VALUES FROM ('%s') TO ('%s')
                """.formatted(partitionName, from, to);
        jdbcTemplate.execute(sql);
        log.debug("Ensured metrics partition {} for {} to {}", partitionName, from, to);
    }
}
