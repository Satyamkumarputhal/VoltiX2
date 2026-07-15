package com.voltix.analytics.anomaly;

public record AnomalyResult(
        double score,
        boolean anomalous,
        String source
) {
}
