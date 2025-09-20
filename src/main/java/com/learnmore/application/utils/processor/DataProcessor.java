package com.learnmore.application.utils.processor;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

/**
 * Generic data processor interface for multiple data formats
 * Supports Excel, CSV, Parquet, ORC, and other structured data formats
 */
public interface DataProcessor<T> {
    
    /**
     * Process data synchronously with batch processing
     * 
     * @param inputStream Input data stream
     * @param targetClass Target class to map data to
     * @param configuration Processing configuration
     * @param batchProcessor Consumer to process batches of data
     * @return Processing result with statistics
     */
    ProcessingResult process(InputStream inputStream, 
                           Class<T> targetClass, 
                           ProcessingConfiguration configuration,
                           Consumer<List<T>> batchProcessor);
    
    /**
     * Process data asynchronously with batch processing
     * 
     * @param inputStream Input data stream
     * @param targetClass Target class to map data to
     * @param configuration Processing configuration
     * @param batchProcessor Consumer to process batches of data
     * @return Future containing processing result
     */
    CompletableFuture<ProcessingResult> processAsync(InputStream inputStream, 
                                                   Class<T> targetClass, 
                                                   ProcessingConfiguration configuration,
                                                   Consumer<List<T>> batchProcessor);
    
    /**
     * Get supported format types
     * 
     * @return List of supported data format extensions
     */
    List<String> getSupportedFormats();
    
    /**
     * Check if this processor can handle the given format
     * 
     * @param format File format/extension
     * @return true if format is supported
     */
    boolean canProcess(String format);
    
    /**
     * Get processor name/identifier
     * 
     * @return Processor name
     */
    String getProcessorName();
    
    /**
     * Validate input stream for processing
     * 
     * @param inputStream Input stream to validate
     * @param configuration Processing configuration
     * @return Validation result
     */
    ValidationResult validateInput(InputStream inputStream, ProcessingConfiguration configuration);
    
    /**
     * Processing result containing statistics and status
     */
    class ProcessingResult {
        private final long processedRecords;
        private final long errorCount;
        private final long processingTimeMs;
        private final boolean success;
        private final String errorMessage;
        private final Object additionalMetadata;
        
        public ProcessingResult(long processedRecords, long errorCount, long processingTimeMs, 
                              boolean success, String errorMessage, Object additionalMetadata) {
            this.processedRecords = processedRecords;
            this.errorCount = errorCount;
            this.processingTimeMs = processingTimeMs;
            this.success = success;
            this.errorMessage = errorMessage;
            this.additionalMetadata = additionalMetadata;
        }
        
        // Getters
        public long getProcessedRecords() { return processedRecords; }
        public long getErrorCount() { return errorCount; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Object getAdditionalMetadata() { return additionalMetadata; }
        
        public double getRecordsPerSecond() {
            return processingTimeMs > 0 ? (processedRecords * 1000.0) / processingTimeMs : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ProcessingResult{processed=%d, errors=%d, time=%dms, success=%s, rate=%.1f rec/sec}", 
                    processedRecords, errorCount, processingTimeMs, success, getRecordsPerSecond());
        }
    }
    
    /**
     * Validation result for input validation
     */
    class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final long estimatedRecords;
        
        public ValidationResult(boolean valid, String errorMessage, long estimatedRecords) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.estimatedRecords = estimatedRecords;
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public long getEstimatedRecords() { return estimatedRecords; }
        
        public static ValidationResult valid(long estimatedRecords) {
            return new ValidationResult(true, null, estimatedRecords);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage, 0);
        }
    }
    
    /**
     * Generic processing configuration
     */
    interface ProcessingConfiguration {
        int getBatchSize();
        boolean isStrictMode();
        int getMaxErrors();
        Object getFormatSpecificConfig();
    }
}