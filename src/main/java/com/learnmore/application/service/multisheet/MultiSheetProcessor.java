package com.learnmore.application.service.multisheet;

import com.learnmore.application.config.SheetMigrationConfig;
import com.learnmore.infrastructure.persistence.entity.MigrationJobSheetEntity;
import com.learnmore.infrastructure.repository.MigrationJobSheetRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Service to process multiple Excel sheets in parallel
 * Orchestrates ingestion, validation, and insertion for each sheet
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiSheetProcessor {

    private final SheetMigrationConfig config;
    private final MigrationJobSheetRepository jobSheetRepository;
    private final SheetIngestService ingestService;
    private final SheetValidationService validationService;
    private final SheetInsertService insertService;

    // Track active executor for graceful shutdown
    private ExecutorService currentExecutor;

    /**
     * Process all sheets in Excel file
     * Can run in parallel or sequential based on configuration
     */
    public MultiSheetProcessResult processAllSheets(String jobId, String filePath) {
        log.info("Starting multi-sheet processing for JobId: {}, File: {}", jobId, filePath);

        List<SheetMigrationConfig.SheetConfig> enabledSheets = config.getEnabledSheetsOrdered();

        if (enabledSheets.isEmpty()) {
            log.warn("No enabled sheets found in configuration");
            return MultiSheetProcessResult.builder()
                    .jobId(jobId)
                    .totalSheets(0)
                    .successSheets(0)
                    .failedSheets(0)
                    .build();
        }

        // Initialize tracking for each sheet
        initializeSheetTracking(jobId, enabledSheets);

        // Process sheets
        boolean useParallel = config.getGlobal().isUseParallelSheetProcessing();
        List<SheetProcessResult> results;

        if (useParallel) {
            results = processInParallel(jobId, filePath, enabledSheets);
        } else {
            results = processSequentially(jobId, filePath, enabledSheets);
        }

        // Aggregate results
        return aggregateResults(jobId, results);
    }

    /**
     * Process sheets in parallel using ExecutorService
     */
    private List<SheetProcessResult> processInParallel(String jobId,
                                                        String filePath,
                                                        List<SheetMigrationConfig.SheetConfig> sheets) {
        int maxThreads = config.getGlobal().getMaxConcurrentSheets();
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        this.currentExecutor = executor; // Track for graceful shutdown
        List<Future<SheetProcessResult>> futures = new ArrayList<>();

        log.info("Processing {} sheets in parallel with {} threads", sheets.size(), maxThreads);

        // Submit tasks for each sheet with error handling
        for (SheetMigrationConfig.SheetConfig sheetConfig : sheets) {
            Future<SheetProcessResult> future = executor.submit(() -> {
                try {
                    return processSheet(jobId, filePath, sheetConfig);
                } catch (Exception e) {
                    log.error("Uncaught exception in sheet processing thread for sheet: {}",
                              sheetConfig.getName(), e);

                    // Mark sheet as failed in database
                    updateSheetStatus(jobId, sheetConfig.getName(), "FAILED", e.getMessage());

                    return SheetProcessResult.error(sheetConfig.getName(),
                            "Thread exception: " + e.getMessage());
                }
            });
            futures.add(future);
        }

        // Wait for all sheets to complete
        List<SheetProcessResult> results = new ArrayList<>();
        for (Future<SheetProcessResult> future : futures) {
            try {
                SheetProcessResult result = future.get(30, TimeUnit.MINUTES); // 30 min timeout per sheet
                results.add(result);
            } catch (TimeoutException e) {
                log.error("Sheet processing timeout after 30 minutes", e);
                results.add(SheetProcessResult.timeout("Unknown", "Timeout after 30 minutes"));
            } catch (Exception e) {
                log.error("Error waiting for sheet processing", e);
                results.add(SheetProcessResult.error("Unknown", e.getMessage()));
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return results;
    }

    /**
     * Process sheets sequentially (one by one)
     */
    private List<SheetProcessResult> processSequentially(String jobId,
                                                          String filePath,
                                                          List<SheetMigrationConfig.SheetConfig> sheets) {
        List<SheetProcessResult> results = new ArrayList<>();

        log.info("Processing {} sheets sequentially", sheets.size());

        for (SheetMigrationConfig.SheetConfig sheetConfig : sheets) {
            try {
                SheetProcessResult result = processSheet(jobId, filePath, sheetConfig);
                results.add(result);

                // Stop if configured and sheet failed
                if (!result.isSuccess() && !config.getGlobal().isContinueOnSheetFailure()) {
                    log.warn("Stopping sheet processing due to failure in sheet: {}", sheetConfig.getName());
                    break;
                }

            } catch (Exception e) {
                log.error("Error processing sheet: {}", sheetConfig.getName(), e);
                results.add(SheetProcessResult.error(sheetConfig.getName(), e.getMessage()));

                if (!config.getGlobal().isContinueOnSheetFailure()) {
                    break;
                }
            }
        }

        return results;
    }

    /**
     * Process a single sheet through all phases
     * Uses REQUIRES_NEW transaction to ensure each sheet has independent transaction
     * Retries on transient failures with exponential backoff
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 1800)
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000, multiplier = 2),
            retryFor = {org.springframework.dao.TransientDataAccessException.class,
                        org.springframework.dao.QueryTimeoutException.class}
    )
    private SheetProcessResult processSheet(String jobId, String filePath, SheetMigrationConfig.SheetConfig sheetConfig) {
        String sheetName = sheetConfig.getName();
        log.info("Processing sheet: {} for JobId: {}", sheetName, jobId);

        SheetProcessResult result = SheetProcessResult.builder()
                .sheetName(sheetName)
                .success(false)
                .build();

        try {
            // Phase 1: Ingest
            updateSheetStatus(jobId, sheetName, "INGESTING");
            long startIngest = System.currentTimeMillis();

            IngestResult ingestResult = ingestService.ingestSheet(jobId, filePath, sheetConfig);
            result.setIngestedRows(ingestResult.getIngestedRows());
            result.setIngestTimeMs(System.currentTimeMillis() - startIngest);

            log.info("Sheet '{}' ingested: {} rows in {}ms", sheetName, ingestResult.getIngestedRows(), result.getIngestTimeMs());

            // Phase 2: Validate
            updateSheetStatus(jobId, sheetName, "VALIDATING");
            long startValidation = System.currentTimeMillis();

            ValidationResult validationResult = validationService.validateSheet(jobId, sheetConfig);
            result.setValidRows(validationResult.getValidRows());
            result.setErrorRows(validationResult.getErrorRows());
            result.setValidationTimeMs(System.currentTimeMillis() - startValidation);

            log.info("Sheet '{}' validated: {} valid, {} errors in {}ms",
                     sheetName, validationResult.getValidRows(), validationResult.getErrorRows(), result.getValidationTimeMs());

            // Phase 3: Insert (if has valid rows)
            if (validationResult.getValidRows() > 0) {
                updateSheetStatus(jobId, sheetName, "INSERTING");
                long startInsertion = System.currentTimeMillis();

                InsertResult insertResult = insertService.insertSheet(jobId, sheetConfig);
                result.setInsertedRows(insertResult.getInsertedRows());
                result.setInsertTimeMs(System.currentTimeMillis() - startInsertion);

                log.info("Sheet '{}' inserted: {} rows in {}ms",
                         sheetName, insertResult.getInsertedRows(), result.getInsertTimeMs());
            }

            // Mark as success
            result.setSuccess(true);
            updateSheetStatus(jobId, sheetName, "COMPLETED");

            log.info("Sheet '{}' completed successfully. Total time: {}ms",
                     sheetName, result.getTotalTimeMs());

        } catch (Exception e) {
            log.error("Error processing sheet '{}': {}", sheetName, e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            updateSheetStatus(jobId, sheetName, "FAILED", e.getMessage());
        }

        return result;
    }

    /**
     * Initialize tracking records for all sheets
     */
    private void initializeSheetTracking(String jobId, List<SheetMigrationConfig.SheetConfig> sheets) {
        for (SheetMigrationConfig.SheetConfig sheet : sheets) {
            MigrationJobSheetEntity entity = MigrationJobSheetEntity.builder()
                    .jobId(jobId)
                    .sheetName(sheet.getName())
                    .sheetOrder(sheet.getOrder())
                    .status("PENDING")
                    .build();

            jobSheetRepository.save(entity);
        }
        log.info("Initialized tracking for {} sheets", sheets.size());
    }

    /**
     * Update sheet status
     */
    private void updateSheetStatus(String jobId, String sheetName, String status) {
        updateSheetStatus(jobId, sheetName, status, null);
    }

    /**
     * Update sheet status with error message
     */
    private void updateSheetStatus(String jobId, String sheetName, String status, String errorMessage) {
        jobSheetRepository.findByJobIdAndSheetName(jobId, sheetName).ifPresent(entity -> {
            entity.setStatus(status);
            entity.setCurrentPhase(status);

            if ("INGESTING".equals(status)) {
                entity.setIngestStartTime(LocalDateTime.now());
            } else if ("VALIDATING".equals(status)) {
                entity.setIngestEndTime(LocalDateTime.now());
                entity.setValidationStartTime(LocalDateTime.now());
            } else if ("INSERTING".equals(status)) {
                entity.setValidationEndTime(LocalDateTime.now());
                entity.setInsertionStartTime(LocalDateTime.now());
            } else if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                entity.setInsertionEndTime(LocalDateTime.now());
                if (errorMessage != null) {
                    entity.setErrorMessage(errorMessage);
                }
            }

            jobSheetRepository.save(entity);
        });
    }

    /**
     * Aggregate results from all sheets
     */
    private MultiSheetProcessResult aggregateResults(String jobId, List<SheetProcessResult> results) {
        long totalSheets = results.size();
        long successSheets = results.stream().filter(SheetProcessResult::isSuccess).count();
        long failedSheets = totalSheets - successSheets;

        long totalIngested = results.stream().mapToLong(SheetProcessResult::getIngestedRows).sum();
        long totalValid = results.stream().mapToLong(SheetProcessResult::getValidRows).sum();
        long totalErrors = results.stream().mapToLong(SheetProcessResult::getErrorRows).sum();
        long totalInserted = results.stream().mapToLong(SheetProcessResult::getInsertedRows).sum();

        return MultiSheetProcessResult.builder()
                .jobId(jobId)
                .totalSheets((int) totalSheets)
                .successSheets((int) successSheets)
                .failedSheets((int) failedSheets)
                .totalIngestedRows(totalIngested)
                .totalValidRows(totalValid)
                .totalErrorRows(totalErrors)
                .totalInsertedRows(totalInserted)
                .sheetResults(results)
                .build();
    }

    /**
     * Graceful shutdown of ExecutorService
     * Waits up to 5 minutes for running tasks to complete
     */
    @PreDestroy
    public void shutdown() {
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            return;
        }

        log.info("Shutting down MultiSheetProcessor gracefully...");

        currentExecutor.shutdown();

        try {
            if (!currentExecutor.awaitTermination(5, TimeUnit.MINUTES)) {
                log.warn("Tasks did not complete within 5 minutes, forcing shutdown...");
                List<Runnable> droppedTasks = currentExecutor.shutdownNow();
                log.warn("Dropped {} tasks during forced shutdown", droppedTasks.size());

                // TODO: Mark dropped tasks as failed in database
                // This would require extracting jobId and sheetName from the tasks
                // For now, log warning - manual intervention may be needed
            } else {
                log.info("MultiSheetProcessor shutdown completed successfully");
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted, forcing immediate shutdown", e);
            currentExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =====================================================
    // Inner Classes for Results
    // =====================================================

    @lombok.Data
    @lombok.Builder
    public static class SheetProcessResult {
        private String sheetName;
        private boolean success;
        private long ingestedRows;
        private long validRows;
        private long errorRows;
        private long insertedRows;
        private Long ingestTimeMs;
        private Long validationTimeMs;
        private Long insertTimeMs;
        private String errorMessage;

        public long getTotalTimeMs() {
            long total = 0;
            if (ingestTimeMs != null) total += ingestTimeMs;
            if (validationTimeMs != null) total += validationTimeMs;
            if (insertTimeMs != null) total += insertTimeMs;
            return total;
        }

        public static SheetProcessResult error(String sheetName, String errorMessage) {
            return SheetProcessResult.builder()
                    .sheetName(sheetName)
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }

        public static SheetProcessResult timeout(String sheetName, String message) {
            return SheetProcessResult.builder()
                    .sheetName(sheetName)
                    .success(false)
                    .errorMessage(message)
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class IngestResult {
        private long ingestedRows;
    }

    @lombok.Data
    @lombok.Builder
    public static class ValidationResult {
        private long validRows;
        private long errorRows;
    }

    @lombok.Data
    @lombok.Builder
    public static class InsertResult {
        private long insertedRows;
    }

    @lombok.Data
    @lombok.Builder
    public static class MultiSheetProcessResult {
        private String jobId;
        private int totalSheets;
        private int successSheets;
        private int failedSheets;
        private long totalIngestedRows;
        private long totalValidRows;
        private long totalErrorRows;
        private long totalInsertedRows;
        private List<SheetProcessResult> sheetResults;

        public boolean isAllSuccess() {
            return failedSheets == 0;
        }

        public double getSuccessRate() {
            return totalSheets > 0 ? (successSheets * 100.0 / totalSheets) : 0;
        }
    }
}
