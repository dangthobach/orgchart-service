package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.application.dto.migration.ValidationErrorDTO;
import com.learnmore.domain.migration.*;
import com.learnmore.infrastructure.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service xử lý Pha 4: Monitoring và Reconciliation
 * - Đối soát dữ liệu giữa staging và master tables
 * - Tạo báo cáo thống kê và metrics
 * - Cleanup dữ liệu staging sau khi hoàn thành
 * - Monitoring performance và memory usage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {
    
    private final MigrationJobRepository migrationJobRepository;
    private final StagingRawRepository stagingRawRepository;
    private final StagingValidRepository stagingValidRepository;
    private final StagingErrorRepository stagingErrorRepository;
    
    // Master repositories for reconciliation
    private final WarehouseRepository warehouseRepository;
    private final UnitRepository unitRepository;
    private final DocTypeRepository docTypeRepository;
    private final BoxRepository boxRepository;
    private final CaseDetailRepository caseDetailRepository;
    
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * Bắt đầu quá trình monitoring và reconciliation
     */
    @Transactional(readOnly = true)
    public MigrationResultDTO performReconciliation(String jobId) {
        
        log.info("Starting reconciliation process for JobId: {}", jobId);
        
        // Tìm migration job
        MigrationJob migrationJob = migrationJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Migration job not found: " + jobId));
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Thực hiện reconciliation
            ReconciliationResult result = performReconciliationChecks(jobId);
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Cập nhật job với reconciliation results
            migrationJob.setStatus("COMPLETED");
            migrationJob.setCurrentPhase("RECONCILIATION_COMPLETED");
            migrationJob.setCompletedAt(LocalDateTime.now());
            migrationJob.setProcessingTimeMs(
                migrationJob.getProcessingTimeMs() != null ? 
                migrationJob.getProcessingTimeMs() + processingTime : processingTime
            );
            migrationJobRepository.save(migrationJob);
            
            // Get validation errors for response
            List<ValidationErrorDTO> validationErrors = getValidationErrors(jobId);
            
            log.info("Reconciliation completed for JobId: {}, Data consistency: {}, Time: {}ms", 
                    jobId, result.isDataConsistent(), processingTime);
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("COMPLETED")
                    .filename(migrationJob.getFilename())
                    .totalRows(migrationJob.getTotalRows())
                    .processedRows(migrationJob.getProcessedRows())
                    .validRows(migrationJob.getValidRows())
                    .errorRows(migrationJob.getErrorRows())
                    .insertedRows(migrationJob.getInsertedRows())
                    .currentPhase("RECONCILIATION_COMPLETED")
                    .progressPercent(100.0)
                    .startedAt(migrationJob.getStartedAt())
                    .completedAt(migrationJob.getCompletedAt())
                    .processingTimeMs(migrationJob.getProcessingTimeMs())
                    .validationErrors(validationErrors)
                    .reconcileTimeMs(processingTime)
                    .maxMemoryUsedMB(result.getMaxMemoryUsedMB())
                    .avgProcessingRate(calculateProcessingRate(migrationJob))
                    .build();
                    
        } catch (Exception e) {
            log.error("Reconciliation failed for JobId: {}, Error: {}", jobId, e.getMessage(), e);
            
            migrationJob.setStatus("FAILED");
            migrationJob.setCurrentPhase("RECONCILIATION_FAILED");
            migrationJob.setErrorMessage(e.getMessage());
            migrationJobRepository.save(migrationJob);
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("FAILED")
                    .currentPhase("RECONCILIATION_FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Thực hiện các kiểm tra reconciliation
     */
    private ReconciliationResult performReconciliationChecks(String jobId) {
        
        log.info("Performing reconciliation checks for JobId: {}", jobId);
        
        boolean isConsistent = true;
        StringBuilder inconsistencyReport = new StringBuilder();
        
        // Check 1: Verify staging_valid count matches inserted records
        long validCount = stagingValidRepository.countByJobId(jobId);
        long insertedCount = getCaseDetailInsertedCount(jobId);
        
        if (validCount != insertedCount) {
            isConsistent = false;
            inconsistencyReport.append(String.format(
                "Mismatch: staging_valid=%d, inserted_case_detail=%d; ", 
                validCount, insertedCount));
            log.warn("Data count mismatch for JobId: {}, staging_valid={}, inserted={}", 
                    jobId, validCount, insertedCount);
        }
        
        // Check 2: Verify master data references
        List<String> referenceErrors = checkMasterDataReferences(jobId);
        if (!referenceErrors.isEmpty()) {
            isConsistent = false;
            inconsistencyReport.append("Reference errors: ").append(String.join(", ", referenceErrors));
        }
        
        // Check 3: Verify business key uniqueness
        long duplicatesInMaster = checkBusinessKeyDuplicates(jobId);
        if (duplicatesInMaster > 0) {
            isConsistent = false;
            inconsistencyReport.append(String.format("Found %d duplicates in master; ", duplicatesInMaster));
        }
        
        // Check 4: Verify data integrity
        List<String> integrityErrors = checkDataIntegrity(jobId);
        if (!integrityErrors.isEmpty()) {
            isConsistent = false;
            inconsistencyReport.append("Integrity errors: ").append(String.join(", ", integrityErrors));
        }
        
        // Get memory usage statistics
        long maxMemoryUsed = getMaxMemoryUsage();
        
        if (isConsistent) {
            log.info("Reconciliation successful for JobId: {}, all checks passed", jobId);
        } else {
            log.warn("Reconciliation issues found for JobId: {}, details: {}", 
                    jobId, inconsistencyReport.toString());
        }
        
        return ReconciliationResult.builder()
                .dataConsistent(isConsistent)
                .inconsistencyReport(inconsistencyReport.toString())
                .maxMemoryUsedMB(maxMemoryUsed)
                .build();
    }
    
    /**
     * Get count of inserted case_detail records for this job
     */
    private long getCaseDetailInsertedCount(String jobId) {
        String sql = """
            SELECT COUNT(cd.id)
            FROM case_detail cd
            JOIN unit u ON cd.unit_id = u.id
            JOIN box b ON cd.box_id = b.id
            WHERE EXISTS (
                SELECT 1 FROM staging_valid sv
                WHERE sv.job_id = ?
                AND sv.ma_don_vi_norm = u.code
                AND sv.ma_thung_norm = b.code
                AND sv.ngay_chung_tu_norm = cd.doc_date::text
                AND sv.so_luong_tap_norm = cd.quantity
            )
            """;
        
        Long count = jdbcTemplate.queryForObject(sql, Long.class, jobId);
        return count != null ? count : 0L;
    }
    
    /**
     * Check master data references
     */
    private List<String> checkMasterDataReferences(String jobId) {
        List<String> errors = jdbcTemplate.queryForList("""
            SELECT CONCAT('Missing reference: ', error_field, '=', error_value) as error
            FROM staging_error se
            WHERE se.job_id = ?
            AND se.error_type = 'REF_NOT_FOUND'
            LIMIT 10
            """, String.class, jobId);
        
        return errors;
    }
    
    /**
     * Check for business key duplicates in master data
     */
    private long checkBusinessKeyDuplicates(String jobId) {
        String sql = """
            WITH business_keys AS (
                SELECT u.code as unit_code, b.code as box_code, cd.doc_date, cd.quantity
                FROM case_detail cd
                JOIN unit u ON cd.unit_id = u.id
                JOIN box b ON cd.box_id = b.id
                WHERE EXISTS (
                    SELECT 1 FROM staging_valid sv
                    WHERE sv.job_id = ?
                    AND sv.ma_don_vi_norm = u.code
                    AND sv.ma_thung_norm = b.code
                )
            )
            SELECT COUNT(*) - COUNT(DISTINCT (unit_code, box_code, doc_date, quantity))
            FROM business_keys
            """;
        
        Long duplicates = jdbcTemplate.queryForObject(sql, Long.class, jobId);
        return duplicates != null ? duplicates : 0L;
    }
    
    /**
     * Check data integrity constraints
     */
    private List<String> checkDataIntegrity(String jobId) {
        List<String> errors = jdbcTemplate.queryForList("""
            SELECT CONCAT('Integrity violation: ', description) as error
            FROM (
                -- Check for invalid dates
                SELECT 'Invalid date sequence' as description
                FROM case_detail cd
                WHERE cd.due_date IS NOT NULL 
                AND cd.handover_date IS NOT NULL
                AND cd.due_date > cd.handover_date
                AND EXISTS (
                    SELECT 1 FROM staging_valid sv
                    JOIN unit u ON sv.ma_don_vi_norm = u.code
                    JOIN box b ON sv.ma_thung_norm = b.code
                    WHERE sv.job_id = ?
                    AND cd.unit_id = u.id
                    AND cd.box_id = b.id
                )
                LIMIT 5
                
                UNION ALL
                
                -- Check for negative quantities
                SELECT 'Negative quantity found' as description
                FROM case_detail cd
                WHERE cd.quantity <= 0
                AND EXISTS (
                    SELECT 1 FROM staging_valid sv
                    JOIN unit u ON sv.ma_don_vi_norm = u.code
                    JOIN box b ON sv.ma_thung_norm = b.code
                    WHERE sv.job_id = ?
                    AND cd.unit_id = u.id
                    AND cd.box_id = b.id
                )
                LIMIT 5
            ) errors
            """, String.class, jobId, jobId);
        
        return errors;
    }
    
    /**
     * Get validation errors for response
     */
    private List<ValidationErrorDTO> getValidationErrors(String jobId) {
        return stagingErrorRepository.findByJobIdOrderByRowNum(jobId)
                .stream()
                .limit(100) // Limit to first 100 errors
                .map(error -> ValidationErrorDTO.builder()
                        .rowNumber(error.getRowNum())
                        .errorType(error.getErrorType())
                        .errorField(error.getErrorField())
                        .errorValue(error.getErrorValue())
                        .errorMessage(error.getErrorMessage())
                        .originalData(error.getOriginalData())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Calculate processing rate (records per second)
     */
    private Double calculateProcessingRate(MigrationJob job) {
        if (job.getProcessingTimeMs() == null || job.getProcessingTimeMs() == 0 || 
            job.getProcessedRows() == null || job.getProcessedRows() == 0) {
            return 0.0;
        }
        
        double timeInSeconds = job.getProcessingTimeMs() / 1000.0;
        return job.getProcessedRows() / timeInSeconds;
    }
    
    /**
     * Get maximum memory usage (simplified implementation)
     */
    private long getMaxMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        return usedMemory / (1024 * 1024); // Convert to MB
    }
    
    /**
     * Get job statistics for monitoring
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getJobStatistics(String jobId) {
        
        MigrationJob job = migrationJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Migration job not found: " + jobId));
        
        // Get error statistics
        List<Object[]> errorStats = stagingErrorRepository.getErrorStatsByJobId(jobId);
        Map<String, Long> errorBreakdown = errorStats.stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0], 
                    row -> ((Number) row[1]).longValue()
                ));
        
        return Map.of(
            "jobId", job.getJobId(),
            "status", job.getStatus(),
            "filename", job.getFilename(),
            "totalRows", job.getTotalRows() != null ? job.getTotalRows() : 0L,
            "processedRows", job.getProcessedRows() != null ? job.getProcessedRows() : 0L,
            "validRows", job.getValidRows() != null ? job.getValidRows() : 0L,
            "errorRows", job.getErrorRows() != null ? job.getErrorRows() : 0L,
            "insertedRows", job.getInsertedRows() != null ? job.getInsertedRows() : 0L,
            "progressPercent", job.getProgressPercent() != null ? job.getProgressPercent() : 0.0,
            "processingTimeMs", job.getProcessingTimeMs() != null ? job.getProcessingTimeMs() : 0L,
            "errorBreakdown", errorBreakdown,
            "createdAt", job.getCreatedAt(),
            "startedAt", job.getStartedAt(),
            "completedAt", job.getCompletedAt(),
            "createdBy", job.getCreatedBy()
        );
    }
    
    /**
     * Cleanup staging data after successful migration
     */
    @Transactional
    public void cleanupStagingData(String jobId, boolean keepErrors) {
        
        log.info("Cleaning up staging data for JobId: {}, keepErrors: {}", jobId, keepErrors);
        
        try {
            // Always cleanup staging_raw and staging_valid
            stagingRawRepository.deleteByJobId(jobId);
            stagingValidRepository.deleteByJobId(jobId);
            
            // Cleanup staging_error if requested
            if (!keepErrors) {
                stagingErrorRepository.deleteByJobId(jobId);
            }
            
            log.info("Staging data cleanup completed for JobId: {}", jobId);
            
        } catch (Exception e) {
            log.error("Failed to cleanup staging data for JobId: {}, Error: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Staging data cleanup failed", e);
        }
    }
    
    /**
     * Get system performance metrics
     */
    public Map<String, Object> getSystemMetrics() {
        Runtime runtime = Runtime.getRuntime();
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return Map.of(
            "totalMemoryMB", totalMemory / (1024 * 1024),
            "usedMemoryMB", usedMemory / (1024 * 1024),
            "freeMemoryMB", freeMemory / (1024 * 1024),
            "maxMemoryMB", maxMemory / (1024 * 1024),
            "memoryUsagePercent", (double) usedMemory / maxMemory * 100,
            "availableProcessors", runtime.availableProcessors(),
            "timestamp", LocalDateTime.now()
        );
    }
    
    /**
     * Inner class cho kết quả reconciliation
     */
    @lombok.Data
    @lombok.Builder
    private static class ReconciliationResult {
        private boolean dataConsistent;
        private String inconsistencyReport;
        private long maxMemoryUsedMB;
    }
}
