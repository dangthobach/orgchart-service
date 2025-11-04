package com.learnmore.infrastructure.repository;

import com.learnmore.infrastructure.persistence.entity.MigrationJobSheetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for migration_job_sheet table
 * Tracks per-sheet progress in multi-sheet migration
 */
@Repository
public interface MigrationJobSheetRepository extends JpaRepository<MigrationJobSheetEntity, Long> {

    /**
     * Find all sheets for a job, ordered by sheet_order
     */
    List<MigrationJobSheetEntity> findByJobIdOrderBySheetOrder(String jobId);

    /**
     * Find specific sheet in a job
     */
    Optional<MigrationJobSheetEntity> findByJobIdAndSheetName(String jobId, String sheetName);

    /**
     * Count sheets by status
     */
    @Query("SELECT COUNT(s) FROM MigrationJobSheetEntity s WHERE s.jobId = :jobId AND s.status = :status")
    Long countByJobIdAndStatus(@Param("jobId") String jobId, @Param("status") String status);

    /**
     * Get total rows across all sheets in a job
     */
    @Query("SELECT SUM(s.totalRows) FROM MigrationJobSheetEntity s WHERE s.jobId = :jobId")
    Long getTotalRowsByJobId(@Param("jobId") String jobId);

    /**
     * Get total valid rows across all sheets in a job
     */
    @Query("SELECT SUM(s.validRows) FROM MigrationJobSheetEntity s WHERE s.jobId = :jobId")
    Long getTotalValidRowsByJobId(@Param("jobId") String jobId);

    /**
     * Get total error rows across all sheets in a job
     */
    @Query("SELECT SUM(s.errorRows) FROM MigrationJobSheetEntity s WHERE s.jobId = :jobId")
    Long getTotalErrorRowsByJobId(@Param("jobId") String jobId);

    /**
     * Get sheets that are currently in progress
     */
    @Query("SELECT s FROM MigrationJobSheetEntity s WHERE s.jobId = :jobId AND s.status IN ('INGESTING', 'VALIDATING', 'INSERTING')")
    List<MigrationJobSheetEntity> findInProgressSheetsByJobId(@Param("jobId") String jobId);

    /**
     * Check if all sheets in a job are completed
     */
    @Query("SELECT CASE WHEN COUNT(s) = 0 THEN true ELSE false END FROM MigrationJobSheetEntity s WHERE s.jobId = :jobId AND s.status NOT IN ('COMPLETED', 'FAILED')")
    Boolean areAllSheetsCompleted(@Param("jobId") String jobId);

    /**
     * Delete all sheets for a job (cleanup)
     */
    void deleteByJobId(String jobId);

    /**
     * Calculate overall progress for a job
     */
    @Query("SELECT AVG(s.progressPercent) FROM MigrationJobSheetEntity s WHERE s.jobId = :jobId")
    Double getAverageProgressByJobId(@Param("jobId") String jobId);
}
