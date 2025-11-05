-- ================================================================
-- Fix Race Condition: Add Optimistic Locking and Improve Constraints
-- ================================================================

-- ----------------------------------------------------------------
-- 1. Add version column for optimistic locking (migration_job_sheet)
-- ----------------------------------------------------------------
ALTER TABLE migration_job_sheet 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Update existing records
UPDATE migration_job_sheet SET version = 0 WHERE version IS NULL;

-- Make version NOT NULL
ALTER TABLE migration_job_sheet 
ALTER COLUMN version SET NOT NULL,
ALTER COLUMN version SET DEFAULT 0;

-- Add index for version (for optimistic locking performance)
CREATE INDEX IF NOT EXISTS idx_migration_job_sheet_version 
ON migration_job_sheet(job_id, sheet_name, version);

-- ----------------------------------------------------------------
-- 2. Ensure sheet_name is properly indexed in all staging tables
-- ----------------------------------------------------------------
-- staging_raw_hopd already has sheet_name with default value
-- Add composite index if not exists
CREATE INDEX IF NOT EXISTS idx_staging_raw_hopd_job_sheet 
ON staging_raw_hopd(job_id, sheet_name);

-- staging_raw_cif
CREATE INDEX IF NOT EXISTS idx_staging_raw_cif_job_sheet 
ON staging_raw_cif(job_id, sheet_name);

-- staging_raw_tap
CREATE INDEX IF NOT EXISTS idx_staging_raw_tap_job_sheet 
ON staging_raw_tap(job_id, sheet_name);

-- ----------------------------------------------------------------
-- 3. Improve error table indexing for concurrent operations
-- ----------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_staging_error_ms_job_sheet_row 
ON staging_error_multisheet(job_id, sheet_name, row_num);

-- ----------------------------------------------------------------
-- 4. Add comments for documentation
-- ----------------------------------------------------------------
COMMENT ON COLUMN migration_job_sheet.version IS 'Optimistic locking version - prevents lost updates in concurrent scenarios';

