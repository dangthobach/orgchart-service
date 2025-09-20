package com.learnmore.application.utils.checkpoint;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Recoverable Excel processor with checkpoint/resume functionality
 * Provides error recovery and resume capabilities for large Excel processing operations
 */
public class RecoverableExcelProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RecoverableExcelProcessor.class);
    
    private final CheckpointManager checkpointManager;
    private final ExecutorService executorService;
    private final boolean shutdownExecutorOnClose;
    
    public RecoverableExcelProcessor(CheckpointManager checkpointManager) {
        this(checkpointManager, null);
    }
    
    public RecoverableExcelProcessor(CheckpointManager checkpointManager, ExecutorService executorService) {
        this.checkpointManager = checkpointManager;
        this.shutdownExecutorOnClose = executorService == null;
        this.executorService = executorService != null ? executorService : 
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * Process Excel with checkpoint/resume capability
     */
    public <T> RecoverableProcessingResult<T> processExcelWithCheckpoint(
            InputStream inputStream,
            Class<T> clazz,
            ExcelConfig config,
            Consumer<List<T>> batchProcessor,
            String fileName) {
        
        String sessionId = UUID.randomUUID().toString();
        return processExcelWithCheckpoint(inputStream, clazz, config, batchProcessor, fileName, sessionId);
    }
    
    /**
     * Process Excel with checkpoint/resume capability using specific session ID
     */
    public <T> RecoverableProcessingResult<T> processExcelWithCheckpoint(
            InputStream inputStream,
            Class<T> clazz,
            ExcelConfig config,
            Consumer<List<T>> batchProcessor,
            String fileName,
            String sessionId) {
        
        logger.info("Starting recoverable Excel processing for session: {} file: {}", sessionId, fileName);
        
        RecoverableProcessingResult<T> result = new RecoverableProcessingResult<>(sessionId);
        
        try {
            // Check for existing checkpoint
            ProcessingCheckpoint existingCheckpoint = checkpointManager.loadCheckpoint(sessionId);
            long startRow = 0;
            
            if (existingCheckpoint != null && existingCheckpoint.canResume()) {
                startRow = existingCheckpoint.getProcessedRows();
                logger.info("Resuming processing from row: {} for session: {}", startRow, sessionId);
                result.setResumedFromCheckpoint(true);
                result.setCheckpoint(existingCheckpoint);
            } else {
                // Create new checkpoint
                ProcessingCheckpoint checkpoint = checkpointManager.createCheckpoint(sessionId, fileName, 0);
                result.setCheckpoint(checkpoint);
            }
            
            // Create enhanced config with checkpoint awareness
            ExcelConfig enhancedConfig = createEnhancedConfig(config, sessionId, startRow);
            
            // Create checkpoint-aware batch processor
            Consumer<List<T>> checkpointAwareBatchProcessor = createCheckpointAwareBatchProcessor(
                    batchProcessor, sessionId, result
            );
            
            // Process Excel with enhanced configuration
            TrueStreamingSAXProcessor.ProcessingResult processingResult = ExcelUtil.processExcelTrueStreaming(
                    inputStream, clazz, enhancedConfig, checkpointAwareBatchProcessor
            );
            
            // Update final result
            result.setProcessingResult(processingResult);
            result.setSuccess(true);
            
            // Mark checkpoint as completed
            checkpointManager.completeCheckpoint(sessionId);
            
            logger.info("Completed recoverable Excel processing for session: {}", sessionId);
            
        } catch (Exception e) {
            logger.error("Failed recoverable Excel processing for session: {}", sessionId, e);
            
            // Mark checkpoint as failed
            checkpointManager.failCheckpoint(sessionId, e.getMessage(), e);
            
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setException(e);
        }
        
        return result;
    }
    
    /**
     * Process Excel asynchronously with checkpoint/resume capability
     */
    public <T> CompletableFuture<RecoverableProcessingResult<T>> processExcelWithCheckpointAsync(
            InputStream inputStream,
            Class<T> clazz,
            ExcelConfig config,
            Consumer<List<T>> batchProcessor,
            String fileName) {
        
        return CompletableFuture.supplyAsync(() -> 
                processExcelWithCheckpoint(inputStream, clazz, config, batchProcessor, fileName),
                executorService
        );
    }
    
    /**
     * Resume processing from existing checkpoint
     */
    public <T> RecoverableProcessingResult<T> resumeProcessing(
            InputStream inputStream,
            Class<T> clazz,
            ExcelConfig config,
            Consumer<List<T>> batchProcessor,
            String sessionId) {
        
        ProcessingCheckpoint checkpoint = checkpointManager.loadCheckpoint(sessionId);
        if (checkpoint == null) {
            throw new IllegalArgumentException("No checkpoint found for session: " + sessionId);
        }
        
        if (!checkpoint.canResume()) {
            throw new IllegalStateException("Cannot resume processing for session: " + sessionId + 
                    " with status: " + checkpoint.getStatus());
        }
        
        logger.info("Resuming processing for session: {} from row: {}", 
                sessionId, checkpoint.getProcessedRows());
        
        return processExcelWithCheckpoint(inputStream, clazz, config, batchProcessor, 
                checkpoint.getFileName(), sessionId);
    }
    
    /**
     * Get checkpoint information for a session
     */
    public ProcessingCheckpoint getCheckpoint(String sessionId) {
        return checkpointManager.loadCheckpoint(sessionId);
    }
    
    /**
     * Get all checkpoint statistics
     */
    public CheckpointStatistics getStatistics() {
        return checkpointManager.getStatistics();
    }
    
    /**
     * Clean up old checkpoint files
     */
    public void cleanupOldCheckpoints(int maxAgeHours) {
        checkpointManager.cleanupOldCheckpoints(maxAgeHours);
    }
    
    /**
     * Shutdown the processor and cleanup resources
     */
    public void shutdown() {
        if (shutdownExecutorOnClose && executorService != null) {
            executorService.shutdown();
        }
    }
    
    // Private helper methods
    
    private ExcelConfig createEnhancedConfig(ExcelConfig originalConfig, String sessionId, long startRow) {
        return ExcelConfig.builder()
                .batchSize(originalConfig.getBatchSize())
                .memoryThreshold(originalConfig.getMemoryThresholdMB())
                .strictValidation(originalConfig.isStrictValidation())
                .parallelProcessing(originalConfig.isParallelProcessing())
                .threadPoolSize(originalConfig.getThreadPoolSize())
                .startRow((int) startRow)
                .build();
    }
    
    private <T> Consumer<List<T>> createCheckpointAwareBatchProcessor(
            Consumer<List<T>> originalProcessor,
            String sessionId,
            RecoverableProcessingResult<T> result) {
        
        return batch -> {
            try {
                // Process the batch
                originalProcessor.accept(batch);
                
                // Update checkpoint with progress
                ProcessingCheckpoint checkpoint = result.getCheckpoint();
                if (checkpoint != null) {
                    long newProcessedRows = checkpoint.getProcessedRows() + batch.size();
                    checkpointManager.updateCheckpoint(sessionId, newProcessedRows, null);
                    
                    // Update result statistics
                    result.incrementProcessedBatches();
                    result.addProcessedRows(batch.size());
                }
                
            } catch (Exception e) {
                logger.error("Error processing batch in session: {}", sessionId, e);
                
                // Mark checkpoint as failed
                checkpointManager.failCheckpoint(sessionId, "Batch processing failed: " + e.getMessage(), e);
                
                // Rethrow exception to stop processing
                throw new RuntimeException("Batch processing failed", e);
            }
        };
    }
    
    /**
     * Builder pattern for RecoverableExcelProcessor
     */
    public static class Builder {
        private CheckpointManager checkpointManager;
        private ExecutorService executorService;
        
        public Builder checkpointManager(CheckpointManager checkpointManager) {
            this.checkpointManager = checkpointManager;
            return this;
        }
        
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }
        
        public RecoverableExcelProcessor build() {
            if (checkpointManager == null) {
                throw new IllegalArgumentException("CheckpointManager is required");
            }
            return new RecoverableExcelProcessor(checkpointManager, executorService);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}