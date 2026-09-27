-- V11: Add tenant_id to inspections table for tenant isolation
-- The inspections table was created in V10 without tenant_id, but it's needed for tenant-scoped queries

ALTER TABLE inspections
    ADD COLUMN tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id);

CREATE INDEX idx_inspections_tenant ON inspections(tenant_id);

-- Backfill existing inspections with tenant_id from their field_job -> incident
UPDATE inspections i
SET tenant_id = fj.incident_id
FROM field_jobs fj
JOIN incidents inc ON fj.incident_id = inc.incident_id
WHERE i.field_job_id = fj.field_job_id
  AND i.tenant_id IS NULL;