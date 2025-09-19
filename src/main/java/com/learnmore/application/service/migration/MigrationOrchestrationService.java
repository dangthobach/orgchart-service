package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main Migration Service để orchestrate toàn bộ quá trình migration
 * Phối hợp các phase: Ingest → Validation → Apply → Reconcile
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationOrchestrationService {
    
    private final ExcelIngestService excelIngestService;
    private final ValidationService validationService;
    private final DataApplyService dataApplyService;
    private final MonitoringService monitoringService;
    
    /**
     * Thực hiện toàn bộ quá trình migration (đồng bộ)
     */
    public MigrationResultDTO performFullMigration(InputStream inputStream, String filename, String createdBy) {
        
        log.info("Starting full migration process for file: {}", filename);
        
        try {
            // Phase 1: Ingest
            log.info("=== Phase 1: Excel Ingest ===");
            MigrationResultDTO ingestResult = excelIngestService.startIngestProcess(inputStream, filename, createdBy);
            
            if (ingestResult.isFailed()) {
                log.error("Migration failed at ingest phase: {}", ingestResult.getErrorMessage());
                return ingestResult;
            }
            
            String jobId = ingestResult.getJobId();
            log.info("Ingest completed successfully for JobId: {}", jobId);
            
            // Phase 2: Validation
            log.info("=== Phase 2: Data Validation ===");
            MigrationResultDTO validationResult = validationService.startValidation(jobId);
            
            if (validationResult.isFailed()) {
                log.error("Migration failed at validation phase: {}", validationResult.getErrorMessage());
                return validationResult;
            }
            
            log.info("Validation completed successfully for JobId: {}, Valid: {}, Errors: {}", 
                    jobId, validationResult.getValidRows(), validationResult.getErrorRows());
            
            // Check if we have valid rows to proceed
            if (validationResult.getValidRows() == 0) {
                log.warn("No valid rows found for JobId: {}, stopping migration", jobId);
                return validationResult;
            }
            
            // Phase 3: Apply Data
            log.info("=== Phase 3: Apply Data to Master Tables ===");
            MigrationResultDTO applyResult = dataApplyService.startApplyProcess(jobId);
            
            if (applyResult.isFailed()) {
                log.error("Migration failed at apply phase: {}", applyResult.getErrorMessage());
                return applyResult;
            }
            
            log.info("Apply data completed successfully for JobId: {}, Inserted: {}", 
                    jobId, applyResult.getInsertedRows());
            
            // Phase 4: Reconciliation
            log.info("=== Phase 4: Reconciliation & Monitoring ===");
            MigrationResultDTO finalResult = monitoringService.performReconciliation(jobId);
            
            // Merge results from all phases
            finalResult.setIngestTimeMs(ingestResult.getIngestTimeMs());
            finalResult.setValidationTimeMs(validationResult.getValidationTimeMs());
            finalResult.setApplyTimeMs(applyResult.getApplyTimeMs());
            
            log.info("Full migration completed successfully for JobId: {}, Total time: {}ms", 
                    jobId, finalResult.getProcessingTimeMs());
            
            // Cleanup staging data (keep errors for analysis)
            try {
                monitoringService.cleanupStagingData(jobId, true);
            } catch (Exception e) {
                log.warn("Failed to cleanup staging data for JobId: {}, Error: {}", jobId, e.getMessage());
            }
            
            return finalResult;
            
        } catch (Exception e) {
            log.error("Migration process failed for file: {}, Error: {}", filename, e.getMessage(), e);
            
            return MigrationResultDTO.builder()
                    .status("FAILED")
                    .filename(filename)
                    .currentPhase("MIGRATION_FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Thực hiện migration bất đồng bộ
     */
    @Async("migrationExecutor")
    public CompletableFuture<MigrationResultDTO> performFullMigrationAsync(
            InputStream inputStream, String filename, String createdBy) {
        
        log.info("Starting async full migration process for file: {}", filename);
        
        try {
            MigrationResultDTO result = performFullMigration(inputStream, filename, createdBy);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Async migration process failed for file: {}, Error: {}", filename, e.getMessage(), e);
            
            MigrationResultDTO errorResult = MigrationResultDTO.builder()
                    .status("FAILED")
                    .filename(filename)
                    .currentPhase("ASYNC_MIGRATION_FAILED")
                    .errorMessage(e.getMessage())
                    .build();
                    
            return CompletableFuture.completedFuture(errorResult);
        }
    }
    
    /**
     * Thực hiện từng phase riêng biệt (cho debugging hoặc manual control)
     */
    public MigrationResultDTO performIngestOnly(InputStream inputStream, String filename, String createdBy) {
        log.info("Performing ingest only for file: {}", filename);
        return excelIngestService.startIngestProcess(inputStream, filename, createdBy);
    }
    
    public MigrationResultDTO performValidationOnly(String jobId) {
        log.info("Performing validation only for JobId: {}", jobId);
        return validationService.startValidation(jobId);
    }
    
    public MigrationResultDTO performApplyOnly(String jobId) {
        log.info("Performing apply only for JobId: {}", jobId);
        return dataApplyService.startApplyProcess(jobId);
    }
    
    public MigrationResultDTO performReconciliationOnly(String jobId) {
        log.info("Performing reconciliation only for JobId: {}", jobId);
        return monitoringService.performReconciliation(jobId);
    }
    
    /**
     * Get job status and statistics
     */
    public Map<String, Object> getJobStatistics(String jobId) {
        return monitoringService.getJobStatistics(jobId);
    }
    
    /**
     * Get system performance metrics
     */
    public Map<String, Object> getSystemMetrics() {
        return monitoringService.getSystemMetrics();
    }
    
    /**
     * Cleanup staging data for completed jobs
     */
    public void cleanupStagingData(String jobId, boolean keepErrors) {
        monitoringService.cleanupStagingData(jobId, keepErrors);
    }
}
