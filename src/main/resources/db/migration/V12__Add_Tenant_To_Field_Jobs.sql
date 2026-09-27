-- V12: Add tenant_id to field_jobs table
-- Adds tenant isolation to field_jobs table for multi-tenant security

-- Step 1: Add tenant_id as nullable first (safe for existing rows)
ALTER TABLE field_jobs
    ADD COLUMN tenant_id BIGINT REFERENCES tenants(tenant_id);

-- Step 2: Backfill existing rows with tenant_id from their incident
-- Each field_job belongs to an incident, which has a tenant_id
UPDATE field_jobs fj
SET tenant_id = inc.tenant_id
FROM incidents inc
WHERE fj.incident_id = inc.incident_id
  AND fj.tenant_id IS NULL;

-- Step 3: Make tenant_id NOT NULL now that all rows have a value
ALTER TABLE field_jobs
    ALTER COLUMN tenant_id SET NOT NULL;

-- Step 4: Add index for tenant-scoped queries
CREATE INDEX idx_field_jobs_tenant ON field_jobs(tenant_id);