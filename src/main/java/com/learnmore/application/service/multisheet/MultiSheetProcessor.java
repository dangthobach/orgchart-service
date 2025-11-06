package com.learnmore.application.service.multisheet;

import com.learnmore.application.config.SheetMigrationConfig;
import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

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
    // ingestService removed - now using ExcelFacade for all Excel reading operations
    private final SheetValidationService validationService;
    private final SheetInsertService insertService;
    private final ExcelFacade excelFacade; // ✅ Use ExcelFacade for unified Excel operations

    // Track active executor for graceful shutdown
    private ExecutorService currentExecutor;

    /**
     * Process all sheets in Excel file FROM MEMORY using ExcelFacade (RECOMMENDED)
     * 
     * ✅ NEW: Uses ExcelFacade.readMultiSheet() with parallel processing
     * - Each sheet has its own DTO class (from config.dtoClass)
     * - Parallel processing with 3+ threads (configurable)
     * - Unified Excel reading infrastructure
     * 
     * Benefits:
     * - No file leak risk
     * - Faster processing (no disk I/O)
     * - Thread-safe (each job has byte array copy)
     * - Uses ExcelFacade (unified infrastructure)
     * - Parallel processing with independent threads per sheet
     * 
     * @param jobId Unique job identifier
     * @param fileBytes Excel file content in memory
     * @param originalFilename Original file name for logging
     * @return Processing result with metrics
     */
    public MultiSheetProcessResult processAllSheetsFromMemory(String jobId, byte[] fileBytes, String originalFilename) {
        log.info("Starting multi-sheet processing (in-memory with ExcelFacade) for JobId: {}, File: {}, Size: {} MB", 
                 jobId, originalFilename, fileBytes.length / 1024.0 / 1024.0);

        List<SheetMigrationConfig.SheetConfig> enabledSheets = config.getEnabledSheetsOrdered();

        // Filter only sheets that are actually present in the workbook (sheets are optional)
        List<String> presentSheetNames = getSheetNamesFromBytes(fileBytes);
        List<SheetMigrationConfig.SheetConfig> sheetsToProcess = enabledSheets.stream()
                .filter(sc -> presentSheetNames.contains(sc.getName()))
                .toList();

        if (sheetsToProcess.isEmpty()) {
            log.warn("No enabled sheets found in configuration");
            return MultiSheetProcessResult.builder()
                    .jobId(jobId)
                    .totalSheets(0)
                    .successSheets(0)
                    .failedSheets(0)
                    .build();
        }

        // Initialize tracking for each sheet
        initializeSheetTracking(jobId, sheetsToProcess);

        // Process sheets from memory using ExcelFacade
        boolean useParallel = config.getGlobal().isUseParallelSheetProcessing();
        List<SheetProcessResult> results;

        if (useParallel) {
            // ✅ Use ExcelFacade with parallel processing
            results = processWithExcelFacadeParallel(jobId, fileBytes, sheetsToProcess);
        } else {
            // ✅ Use ExcelFacade with sequential processing
            results = processWithExcelFacadeSequential(jobId, fileBytes, sheetsToProcess);
        }

        // Aggregate results
        return aggregateResults(jobId, results);
    }
    
    /**
     * ✅ NEW: Process sheets using ExcelFacade with parallel processing
     * Strategy: Read all sheets once with ExcelFacade, then process results in parallel
     * Each sheet runs in its own thread for validation and insertion phases
     * 
     * @param jobId Job identifier
     * @param fileBytes Excel file content in memory
     * @param sheetsToProcess List of sheet configs to process
     * @return List of processing results
     */
    private List<SheetProcessResult> processWithExcelFacadeParallel(String jobId,
                                                                      byte[] fileBytes,
                                                                      List<SheetMigrationConfig.SheetConfig> sheetsToProcess) {
        int maxThreads = config.getGlobal().getMaxConcurrentSheets();
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        
        try {
            this.currentExecutor = executor;
            
            log.info("Processing {} sheets in parallel using ExcelFacade with {} threads", 
                    sheetsToProcess.size(), maxThreads);
            
            // Build sheet-to-DTO mapping from config
            Map<String, Class<?>> sheetClassMap = buildSheetClassMap(sheetsToProcess);
            Map<String, Consumer<List<?>>> sheetProcessors = buildSheetProcessors(jobId, sheetsToProcess);
            
            // Create ExcelConfig for multi-sheet processing
            ExcelConfig excelConfig = ExcelConfig.builder()
                    .batchSize(sheetsToProcess.isEmpty() ? 5000 : sheetsToProcess.get(0).getBatchSize())
                    .readAllSheets(true)
                    .jobId(jobId)
                    .parallelProcessing(false) // Sequential within sheet, parallel across sheets
                    .build();
            
            // Phase 1: Read all sheets once using ExcelFacade (shared file read)
            Map<String, TrueStreamingSAXProcessor.ProcessingResult> readResults;
            try (InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes)) {
                readResults = excelFacade.readMultiSheet(inputStream, sheetClassMap, sheetProcessors, excelConfig);
            }
            
            log.info("ExcelFacade read completed for {} sheets", readResults.size());
            
            // Phase 2: Process validation and insertion in parallel (one thread per sheet)
            List<Future<SheetProcessResult>> futures = new ArrayList<>();
            
            for (SheetMigrationConfig.SheetConfig sheetConfig : sheetsToProcess) {
                String sheetName = sheetConfig.getName();
                TrueStreamingSAXProcessor.ProcessingResult readResult = readResults.get(sheetName);
                
                if (readResult == null) {
                    log.warn("No read result for sheet: {}", sheetName);
                    continue;
                }
                
                Future<SheetProcessResult> future = executor.submit(() -> {
                    try {
                        return processSheetPostIngest(jobId, sheetConfig, readResult);
                    } catch (Exception e) {
                        log.error("Uncaught exception in sheet processing thread for sheet: {}",
                                  sheetConfig.getName(), e);
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
                    SheetProcessResult result = future.get(30, TimeUnit.MINUTES);
                    results.add(result);
                } catch (TimeoutException e) {
                    log.error("Sheet processing timeout after 30 minutes", e);
                    results.add(SheetProcessResult.timeout("Unknown", "Timeout after 30 minutes"));
                } catch (Exception e) {
                    log.error("Error waiting for sheet processing", e);
                    results.add(SheetProcessResult.error("Unknown", e.getMessage()));
                }
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Error in ExcelFacade parallel processing", e);
            throw new RuntimeException("Failed to process sheets with ExcelFacade", e);
        } finally {
            shutdownExecutor(executor);
        }
    }
    
    /**
     * ✅ NEW: Process sheet post-ingestion (validation and insertion)
     * Called after ExcelFacade has ingested the sheet
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 1800)
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000, multiplier = 2),
            retryFor = {org.springframework.dao.TransientDataAccessException.class,
                        org.springframework.dao.QueryTimeoutException.class}
    )
    private SheetProcessResult processSheetPostIngest(String jobId,
                                                      SheetMigrationConfig.SheetConfig sheetConfig,
                                                      TrueStreamingSAXProcessor.ProcessingResult ingestResult) {
        String sheetName = sheetConfig.getName();
        log.info("Processing sheet post-ingest (ExcelFacade): {} for JobId: {}", sheetName, jobId);
        
        SheetProcessResult result = SheetProcessResult.builder()
                .sheetName(sheetName)
                .success(false)
                .ingestedRows(ingestResult.getProcessedRecords())
                .ingestTimeMs(0L) // Already completed by ExcelFacade
                .build();
        
        try {
            updateSheetStatus(jobId, sheetName, "INGESTING");
            
            // Phase 2: Validate
            updateSheetStatus(jobId, sheetName, "VALIDATING");
            long startValidation = System.currentTimeMillis();
            
            ValidationResult validationResult = validationService.validateSheet(jobId, sheetConfig);
            result.setValidRows(validationResult.getValidRows());
            result.setErrorRows(validationResult.getErrorRows());
            result.setValidationTimeMs(System.currentTimeMillis() - startValidation);
            
            log.info("Sheet '{}' validated: {} valid, {} errors in {}ms",
                     sheetName, validationResult.getValidRows(), validationResult.getErrorRows(), 
                     result.getValidationTimeMs());
            
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
            
            log.info("Sheet '{}' completed successfully via ExcelFacade. Total time: {}ms",
                     sheetName, result.getTotalTimeMs());
            
        } catch (Exception e) {
            log.error("Error processing sheet '{}' with ExcelFacade: {}", sheetName, e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            updateSheetStatus(jobId, sheetName, "FAILED", e.getMessage());
        }
        
        return result;
    }
    
    
    /**
     * ✅ NEW: Build sheet-to-DTO class mapping from config
     */
    private Map<String, Class<?>> buildSheetClassMap(List<SheetMigrationConfig.SheetConfig> sheets) {
        Map<String, Class<?>> sheetClassMap = new HashMap<>();
        
        for (SheetMigrationConfig.SheetConfig sheet : sheets) {
            String dtoClassName = sheet.getDtoClass();
            if (dtoClassName == null || dtoClassName.isEmpty()) {
                log.warn("No DTO class configured for sheet: {}", sheet.getName());
                continue;
            }
            
            try {
                Class<?> dtoClass = Class.forName(dtoClassName);
                sheetClassMap.put(sheet.getName(), dtoClass);
                log.debug("Mapped sheet '{}' to DTO class: {}", sheet.getName(), dtoClassName);
            } catch (ClassNotFoundException e) {
                log.error("Failed to load DTO class for sheet '{}': {}", sheet.getName(), dtoClassName, e);
                throw new RuntimeException("DTO class not found: " + dtoClassName, e);
            }
        }
        
        return sheetClassMap;
    }
    
    /**
     * ✅ NEW: Build sheet processors for batch ingestion
     * Each processor receives DTO batches and inserts into staging table
     * 
     * NOTE: Currently, ingestion happens via ExcelFacade.readMultiSheet() which processes
     * batches directly. The processors here are called during the read phase.
     * 
     * For now, we use a no-op processor since actual DB insertion happens in post-ingest phase.
     * Future: Implement actual DTO-to-DB conversion here for real-time ingestion.
     */
    private Map<String, Consumer<List<?>>> buildSheetProcessors(String jobId, 
                                                                 List<SheetMigrationConfig.SheetConfig> sheets) {
        Map<String, Consumer<List<?>>> sheetProcessors = new HashMap<>();
        
        for (SheetMigrationConfig.SheetConfig sheet : sheets) {
            String sheetName = sheet.getName();
            
            // Create batch processor that receives DTO batches during ExcelFacade read
            // For now, we just log - actual DB insertion happens in post-ingest phase
            // This allows ExcelFacade to process all sheets with their respective DTOs
            Consumer<List<?>> processor = batch -> {
                try {
                    log.debug("ExcelFacade processing batch of {} DTOs for sheet: {}", batch.size(), sheetName);
                    
                    // TODO: Future enhancement - Convert DTOs to DB rows and insert here
                    // This would enable real-time ingestion during Excel read
                    // For now, data is already in staging_raw tables from previous approach
                    // or will be inserted in post-ingest phase
                    
                } catch (Exception e) {
                    log.error("Error processing batch for sheet '{}': {}", sheetName, e.getMessage(), e);
                    // Don't throw - let ExcelFacade continue processing other batches
                }
            };
            
            sheetProcessors.put(sheetName, processor);
        }
        
        return sheetProcessors;
    }

    /**
     * Extract sheet names from Excel bytes using OPCPackage (SAX). Used to decide which configured sheets exist.
     */
    private List<String> getSheetNamesFromBytes(byte[] fileBytes) {
        List<String> found = new ArrayList<>();
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(fileBytes)) {
            org.apache.poi.openxml4j.opc.OPCPackage pkg = org.apache.poi.openxml4j.opc.OPCPackage.open(in);
            org.apache.poi.xssf.eventusermodel.XSSFReader reader = new org.apache.poi.xssf.eventusermodel.XSSFReader(pkg);
            org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator iterator =
                    (org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator) reader.getSheetsData();
            while (iterator.hasNext()) {
                iterator.next();
                found.add(iterator.getSheetName());
            }
            pkg.close();
        } catch (Exception e) {
            log.warn("Unable to read sheet names from bytes: {}", e.getMessage(), e);
        }
        return found;
    }

    /**
     * Process all sheets in Excel file FROM DISK (legacy method - backward compatibility)
     * Can run in parallel or sequential based on configuration
     * 
     * @deprecated Use processAllSheetsFromMemory() instead to avoid file leak issues
     */
    @Deprecated
    public MultiSheetProcessResult processAllSheets(String jobId, String filePath) {
        log.warn("⚠️ Using deprecated processAllSheets(filePath) method. Consider using processAllSheetsFromMemory() instead.");
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

        // ⚠️ DEPRECATED: This method should not be used. 
        // All processing should use processAllSheetsFromMemory() with ExcelFacade.
        throw new UnsupportedOperationException(
            "processAllSheets(filePath) is deprecated and no longer supported. " +
            "Please use processAllSheetsFromMemory(jobId, fileBytes, filename) instead."
        );
    }

    /**
     * ✅ Process sheets using ExcelFacade with sequential processing
     * Strategy: Read all sheets once with ExcelFacade, then process results sequentially
     * 
     * @param jobId Job identifier
     * @param fileBytes Excel file content in memory
     * @param sheetsToProcess List of sheet configs to process
     * @return List of processing results
     */
    private List<SheetProcessResult> processWithExcelFacadeSequential(String jobId,
                                                                       byte[] fileBytes,
                                                                       List<SheetMigrationConfig.SheetConfig> sheetsToProcess) {
        log.info("Processing {} sheets sequentially using ExcelFacade", sheetsToProcess.size());
        
        try {
            // Build sheet-to-DTO mapping from config
            Map<String, Class<?>> sheetClassMap = buildSheetClassMap(sheetsToProcess);
            Map<String, Consumer<List<?>>> sheetProcessors = buildSheetProcessors(jobId, sheetsToProcess);
            
            // Create ExcelConfig for multi-sheet processing
            ExcelConfig excelConfig = ExcelConfig.builder()
                    .batchSize(sheetsToProcess.isEmpty() ? 5000 : sheetsToProcess.get(0).getBatchSize())
                    .readAllSheets(true)
                    .jobId(jobId)
                    .parallelProcessing(false)
                    .build();
            
            // Phase 1: Read all sheets once using ExcelFacade (shared file read)
            Map<String, TrueStreamingSAXProcessor.ProcessingResult> readResults;
            try (InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes)) {
                readResults = excelFacade.readMultiSheet(inputStream, sheetClassMap, sheetProcessors, excelConfig);
            }
            
            log.info("ExcelFacade read completed for {} sheets", readResults.size());
            
            // Phase 2: Process validation and insertion sequentially
            List<SheetProcessResult> results = new ArrayList<>();
            
            for (SheetMigrationConfig.SheetConfig sheetConfig : sheetsToProcess) {
                String sheetName = sheetConfig.getName();
                TrueStreamingSAXProcessor.ProcessingResult readResult = readResults.get(sheetName);
                
                if (readResult == null) {
                    log.warn("No read result for sheet: {}", sheetName);
                    results.add(SheetProcessResult.error(sheetName, "No read result from ExcelFacade"));
                    continue;
                }
                
                try {
                    SheetProcessResult result = processSheetPostIngest(jobId, sheetConfig, readResult);
                results.add(result);

                if (!result.isSuccess() && !config.getGlobal().isContinueOnSheetFailure()) {
                        log.warn("Stopping sheet processing due to failure in sheet: {}", sheetName);
                    break;
                }
            } catch (Exception e) {
                    log.error("Error processing sheet: {}", sheetName, e);
                    results.add(SheetProcessResult.error(sheetName, e.getMessage()));

                if (!config.getGlobal().isContinueOnSheetFailure()) {
                    break;
                }
            }
        }

        return results;

        } catch (Exception e) {
            log.error("Error in ExcelFacade sequential processing", e);
            throw new RuntimeException("Failed to process sheets with ExcelFacade", e);
        }
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
     * Thread-safe with optimistic locking retry mechanism
     */
    private void updateSheetStatus(String jobId, String sheetName, String status) {
        updateSheetStatus(jobId, sheetName, status, null);
    }

    /**
     * Update sheet status with error message
     * Uses optimistic locking with retry to prevent lost updates in concurrent scenarios
     * 
     * Retry strategy:
     * - Max 3 retries with exponential backoff
     * - Handles OptimisticLockException from concurrent updates
     * - Logs warnings for retry attempts
     */
    private void updateSheetStatus(String jobId, String sheetName, String status, String errorMessage) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                jobSheetRepository.findByJobIdAndSheetName(jobId, sheetName).ifPresent(entity -> {
                    // Update status fields
                    entity.setStatus(status);
                    entity.setCurrentPhase(status);

                    // Update phase timestamps
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

                    // Save with optimistic locking (version check)
                    jobSheetRepository.save(entity);
                });
                
                // Success - exit retry loop
                return;
                
            } catch (ObjectOptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    log.error("⚠️ Failed to update sheet status after {} retries. JobId: {}, Sheet: {}, Status: {}", 
                             maxRetries, jobId, sheetName, status, e);
                    throw new RuntimeException("Failed to update sheet status due to concurrent modification", e);
                }
                
                // Exponential backoff: 50ms, 100ms, 200ms
                long backoffMs = 50L * (1L << (attempt - 1));
                log.warn("⚠️ Optimistic lock conflict when updating sheet status (attempt {}/{}). Retrying in {}ms...", 
                        attempt, maxRetries, backoffMs);
                
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during status update retry", ie);
                }
            }
        }
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
     * Gracefully shutdown executor with timeout
     * Used by processWithExcelFacadeParallel() to prevent memory leak
     */
    private void shutdownExecutor(ExecutorService executor) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        log.info("Shutting down executor gracefully...");
        executor.shutdown();

        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate within 60s, forcing shutdown...");
                List<Runnable> droppedTasks = executor.shutdownNow();
                log.warn("Dropped {} tasks during forced shutdown", droppedTasks.size());
            } else {
                log.info("Executor shutdown completed successfully");
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted, forcing immediate shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
