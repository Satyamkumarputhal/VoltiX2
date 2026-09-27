-- V14: Add assigned_at timestamp to field_jobs table
-- This field tracks when a field job was assigned to an inspector
-- Separate from started_at (work commencement) and completed_at (completion/failure)

ALTER TABLE field_jobs
    ADD COLUMN assigned_at TIMESTAMPTZ;

CREATE INDEX idx_field_jobs_assigned_at ON field_jobs(assigned_at);