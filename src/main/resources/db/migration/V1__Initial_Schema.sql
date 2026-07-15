CREATE TABLE tenants (
    tenant_id BIGSERIAL PRIMARY KEY,
    tenant_name VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE TABLE grid_zones (
    zone_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    zone_name VARCHAR(100) NOT NULL,
    risk_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, zone_name),
    CONSTRAINT chk_grid_zones_risk_multiplier CHECK (risk_multiplier >= 0.50 AND risk_multiplier <= 5.00)
);

CREATE TABLE smart_meters (
    meter_id VARCHAR(50) PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    installed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_smart_meters_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'RETIRED'))
);

CREATE TABLE metrics_history (
    record_id BIGSERIAL,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    meter_id VARCHAR(50) NOT NULL REFERENCES smart_meters(meter_id),
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    voltage NUMERIC(6,2) NOT NULL,
    current_amp NUMERIC(8,3) NOT NULL,
    kw_consumed NUMERIC(10,4) NOT NULL,
    anomaly_score NUMERIC(6,5),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (record_id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE TABLE zone_hourly_aggregates (
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    aggregated_hour TIMESTAMP WITH TIME ZONE NOT NULL,
    total_kw_consumed NUMERIC(14,4) NOT NULL,
    avg_voltage NUMERIC(6,2),
    avg_current_amp NUMERIC(8,3),
    avg_temperature NUMERIC(5,2),
    forecast_kw_2h NUMERIC(14,4),
    forecast_generated_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (tenant_id, zone_id, aggregated_hour)
);

CREATE TABLE system_alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    meter_id VARCHAR(50) REFERENCES smart_meters(meter_id),
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    alert_type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    anomaly_score NUMERIC(6,5),
    priority_score NUMERIC(8,4) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'OPEN',
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_to BIGINT,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_system_alerts_status CHECK (status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT chk_system_alerts_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE public_complaints (
    complaint_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    incident_address TEXT NOT NULL,
    address_hash VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    submitter_ip_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    triaged_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_public_complaints_status CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'ESCALATED'))
);

CREATE TABLE IF NOT EXISTS metrics_history_bootstrap
PARTITION OF metrics_history
FOR VALUES FROM ('2026-01-01T00:00:00Z') TO ('2027-01-01T00:00:00Z');

CREATE INDEX idx_metrics_meter_time ON metrics_history(meter_id, recorded_at DESC);
CREATE INDEX idx_metrics_zone_time ON metrics_history(zone_id, recorded_at DESC);
CREATE INDEX idx_alerts_unresolved ON system_alerts(tenant_id, status, detected_at DESC)
WHERE status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS');
CREATE INDEX idx_complaints_pending ON public_complaints(tenant_id, submitted_at DESC)
WHERE status = 'PENDING_VERIFICATION';
CREATE INDEX idx_complaints_abuse ON public_complaints(submitter_ip_hash, submitted_at DESC);
CREATE INDEX idx_complaints_dedup ON public_complaints(address_hash, submitted_at DESC)
WHERE status = 'PENDING_VERIFICATION';
