-- ============================================================
-- V1.5: Fix Race Condition - Job Creation
-- ============================================================
-- Purpose: Prevent duplicate job-sheet combinations at database level
-- Issue: Check-then-act pattern in controller allows concurrent requests
--        to create duplicate jobs
-- Solution: Add unique constraint to enforce atomicity
-- ============================================================

-- Add unique constraint on (job_id, sheet_name)
-- This prevents two concurrent requests from creating the same job
ALTER TABLE migration_job_sheet
ADD CONSTRAINT uk_migration_job_sheet_job_sheet
UNIQUE (job_id, sheet_name);

-- Add composite index for faster lookups by job_id and status
-- Improves performance for queries like: findByJobIdAndStatus()
CREATE INDEX idx_migration_job_sheet_job_status
ON migration_job_sheet(job_id, status);

-- Add index for job_id alone (used by findByJobIdOrderBySheetOrder)
CREATE INDEX idx_migration_job_sheet_job_id
ON migration_job_sheet(job_id);

-- Add comment for documentation
COMMENT ON CONSTRAINT uk_migration_job_sheet_job_sheet ON migration_job_sheet
IS 'Prevents duplicate job-sheet combinations. Used for atomic job creation and idempotency.';
