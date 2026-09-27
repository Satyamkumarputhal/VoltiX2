-- V10: Incident Management Foundation
-- Creates core tables for Incident, FieldJob, and Inspection domain

-- INCIDENTS TABLE
CREATE TABLE incidents (
    incident_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id),
    incident_number VARCHAR(30) NOT NULL UNIQUE,
    source_alert_id BIGINT NOT NULL REFERENCES system_alerts(alert_id) UNIQUE,
    meter_id VARCHAR(50) REFERENCES smart_meters(meter_id),
    zone_id BIGINT REFERENCES grid_zones(zone_id),
    alert_type VARCHAR(40),
    severity VARCHAR(20),
    title VARCHAR(200),
    description TEXT,
    status VARCHAR(25) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT REFERENCES users(user_id),
    assigned_to BIGINT REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMPTZ,
    assigned_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    CONSTRAINT chk_incidents_status CHECK (status IN ('OPEN','ACKNOWLEDGED','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED','ESCALATED','CANCELLED')),
    CONSTRAINT chk_incidents_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX idx_incidents_tenant_status ON incidents(tenant_id, status, created_at DESC);
CREATE INDEX idx_incidents_source_alert ON incidents(source_alert_id);

-- FIELD JOBS TABLE
CREATE TABLE field_jobs (
    field_job_id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(incident_id),
    assigned_inspector_id BIGINT REFERENCES users(user_id),
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    instructions TEXT,
    scheduled_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    CONSTRAINT chk_field_jobs_status CHECK (status IN ('PENDING','ASSIGNED','EN_ROUTE','ON_SITE','COMPLETED','FAILED')),
    CONSTRAINT chk_field_jobs_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT'))
);

CREATE INDEX idx_field_jobs_incident ON field_jobs(incident_id);
CREATE INDEX idx_field_jobs_inspector_status ON field_jobs(assigned_inspector_id, status);

-- INSPECTIONS TABLE
CREATE TABLE inspections (
    inspection_id BIGSERIAL PRIMARY KEY,
    field_job_id BIGINT NOT NULL REFERENCES field_jobs(field_job_id) UNIQUE,
    inspector_id BIGINT REFERENCES users(user_id),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    finding TEXT,
    conclusion TEXT,
    evidence_metadata JSONB,
    recommendation TEXT,
    result VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inspections_result CHECK (result IN ('CONFIRMED','FALSE_POSITIVE','INCONCLUSIVE','ESCALATE'))
);

CREATE INDEX idx_inspections_field_job ON inspections(field_job_id);