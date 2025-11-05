-- ============================================================
-- V1.6: Add Unique Constraints for Staging Tables
-- ============================================================
-- Purpose: Prevent duplicate rows in staging tables during ingestion
-- Issue: If retry occurs, same rows may be inserted twice
-- Solution: Add unique constraint on (job_id, sheet_name, row_number)
-- ============================================================

-- Add unique constraint on staging_raw_hopd
ALTER TABLE staging_raw_hopd
ADD CONSTRAINT uk_staging_raw_hopd_row
UNIQUE (job_id, row_number);

-- Add unique constraint on staging_raw_cif
ALTER TABLE staging_raw_cif
ADD CONSTRAINT uk_staging_raw_cif_row
UNIQUE (job_id, row_number);

-- Add unique constraint on staging_raw_tap
ALTER TABLE staging_raw_tap
ADD CONSTRAINT uk_staging_raw_tap_row
UNIQUE (job_id, row_number);

-- Add composite indexes for faster queries
CREATE INDEX idx_staging_raw_hopd_job ON staging_raw_hopd(job_id);
CREATE INDEX idx_staging_raw_cif_job ON staging_raw_cif(job_id);
CREATE INDEX idx_staging_raw_tap_job ON staging_raw_tap(job_id);

-- Add comments for documentation
COMMENT ON CONSTRAINT uk_staging_raw_hopd_row ON staging_raw_hopd
IS 'Prevents duplicate rows for same job. Each job can only have unique row numbers.';

COMMENT ON CONSTRAINT uk_staging_raw_cif_row ON staging_raw_cif
IS 'Prevents duplicate rows for same job. Each job can only have unique row numbers.';

COMMENT ON CONSTRAINT uk_staging_raw_tap_row ON staging_raw_tap
IS 'Prevents duplicate rows for same job. Each job can only have unique row numbers.';

-- ============================================================
-- Add unique constraints for staging_valid tables (if exist)
-- ============================================================

-- Check if staging_valid tables exist and add constraints
-- These tables should have been created in earlier migrations

-- staging_valid_hopd
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'staging_valid_hopd') THEN
        ALTER TABLE staging_valid_hopd
        ADD CONSTRAINT uk_staging_valid_hopd_row
        UNIQUE (job_id, row_number);

        CREATE INDEX idx_staging_valid_hopd_job ON staging_valid_hopd(job_id);

        COMMENT ON CONSTRAINT uk_staging_valid_hopd_row ON staging_valid_hopd
        IS 'Prevents duplicate validated rows for same job.';
    END IF;
END $$;

-- staging_valid_cif
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'staging_valid_cif') THEN
        ALTER TABLE staging_valid_cif
        ADD CONSTRAINT uk_staging_valid_cif_row
        UNIQUE (job_id, row_number);

        CREATE INDEX idx_staging_valid_cif_job ON staging_valid_cif(job_id);

        COMMENT ON CONSTRAINT uk_staging_valid_cif_row ON staging_valid_cif
        IS 'Prevents duplicate validated rows for same job.';
    END IF;
END $$;

-- staging_valid_tap
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'staging_valid_tap') THEN
        ALTER TABLE staging_valid_tap
        ADD CONSTRAINT uk_staging_valid_tap_row
        UNIQUE (job_id, row_number);

        CREATE INDEX idx_staging_valid_tap_job ON staging_valid_tap(job_id);

        COMMENT ON CONSTRAINT uk_staging_valid_tap_row ON staging_valid_tap
        IS 'Prevents duplicate validated rows for same job.';
    END IF;
END $$;
