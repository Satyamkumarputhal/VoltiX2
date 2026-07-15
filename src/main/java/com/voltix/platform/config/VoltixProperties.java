package com.voltix.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "voltix")
public class VoltixProperties {
    private final Tenant tenant = new Tenant();
    private final Telemetry telemetry = new Telemetry();
    private final Analytics analytics = new Analytics();
    private final Complaints complaints = new Complaints();
    private final Security security = new Security();

    public Tenant getTenant() {
        return tenant;
    }

    public Telemetry getTelemetry() {
        return telemetry;
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    public Complaints getComplaints() {
        return complaints;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Tenant {
        private long defaultId = 1L;

        public long getDefaultId() {
            return defaultId;
        }

        public void setDefaultId(long defaultId) {
            this.defaultId = defaultId;
        }
    }

    public static class Telemetry {
        private final Executor executor = new Executor();
        private final Validation validation = new Validation();
        private final Batch batch = new Batch();
        private final Partitions partitions = new Partitions();

        public Executor getExecutor() {
            return executor;
        }

        public Validation getValidation() {
            return validation;
        }

        public Batch getBatch() {
            return batch;
        }

        public Partitions getPartitions() {
            return partitions;
        }
    }

    public static class Executor {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 5000;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    public static class Validation {
        private double minVoltage = 200.0;
        private double maxVoltage = 260.0;
        private long maxPastSkewMinutes = 5;
        private long maxFutureSkewMinutes = 1;

        public double getMinVoltage() {
            return minVoltage;
        }

        public void setMinVoltage(double minVoltage) {
            this.minVoltage = minVoltage;
        }

        public double getMaxVoltage() {
            return maxVoltage;
        }

        public void setMaxVoltage(double maxVoltage) {
            this.maxVoltage = maxVoltage;
        }

        public long getMaxPastSkewMinutes() {
            return maxPastSkewMinutes;
        }

        public void setMaxPastSkewMinutes(long maxPastSkewMinutes) {
            this.maxPastSkewMinutes = maxPastSkewMinutes;
        }

        public long getMaxFutureSkewMinutes() {
            return maxFutureSkewMinutes;
        }

        public void setMaxFutureSkewMinutes(long maxFutureSkewMinutes) {
            this.maxFutureSkewMinutes = maxFutureSkewMinutes;
        }
    }

    public static class Batch {
        private int maxSize = 500;
        private long flushIntervalMs = 250;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public long getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(long flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public Duration flushInterval() {
            return Duration.ofMillis(flushIntervalMs);
        }
    }

    public static class Partitions {
        private int daysAhead = 14;

        public int getDaysAhead() {
            return daysAhead;
        }

        public void setDaysAhead(int daysAhead) {
            this.daysAhead = daysAhead;
        }
    }

    public static class Analytics {
        private String anomalyModel = "classpath:models/anomaly_forest.onnx";
        private String loadForecasterModel = "classpath:models/load_forecaster.onnx";
        private int onnxSessionPoolSize = 4;

        public String getAnomalyModel() {
            return anomalyModel;
        }

        public void setAnomalyModel(String anomalyModel) {
            this.anomalyModel = anomalyModel;
        }

        public String getLoadForecasterModel() {
            return loadForecasterModel;
        }

        public void setLoadForecasterModel(String loadForecasterModel) {
            this.loadForecasterModel = loadForecasterModel;
        }

        public int getOnnxSessionPoolSize() {
            return onnxSessionPoolSize;
        }

        public void setOnnxSessionPoolSize(int onnxSessionPoolSize) {
            this.onnxSessionPoolSize = onnxSessionPoolSize;
        }
    }

    public static class Complaints {
        private long dedupWindowMinutes = 60;
        private long rateLimitCapacity = 20;
        private long rateLimitRefillMinutes = 1;

        public long getDedupWindowMinutes() {
            return dedupWindowMinutes;
        }

        public void setDedupWindowMinutes(long dedupWindowMinutes) {
            this.dedupWindowMinutes = dedupWindowMinutes;
        }

        public long getRateLimitCapacity() {
            return rateLimitCapacity;
        }

        public void setRateLimitCapacity(long rateLimitCapacity) {
            this.rateLimitCapacity = rateLimitCapacity;
        }

        public long getRateLimitRefillMinutes() {
            return rateLimitRefillMinutes;
        }

        public void setRateLimitRefillMinutes(long rateLimitRefillMinutes) {
            this.rateLimitRefillMinutes = rateLimitRefillMinutes;
        }
    }

    public static class Security {
        private String jwtSecret;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }
}
