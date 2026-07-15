CREATE TABLE telemetry_staging (
    staging_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    meter_id VARCHAR(50) NOT NULL REFERENCES smart_meters(meter_id),
    transaction_id UUID NOT NULL UNIQUE,
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    voltage NUMERIC(6,2) NOT NULL,
    current_amp NUMERIC(8,3) NOT NULL,
    kw_consumed NUMERIC(10,4) NOT NULL,
    client_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT
);

CREATE INDEX idx_telemetry_unprocessed ON telemetry_staging(received_at)
WHERE processed = FALSE;

CREATE INDEX idx_telemetry_meter_time ON telemetry_staging(meter_id, client_timestamp DESC);
