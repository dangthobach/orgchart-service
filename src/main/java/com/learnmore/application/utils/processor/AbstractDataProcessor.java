package com.learnmore.application.utils.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Abstract base class for data processors providing common functionality
 */
public abstract class AbstractDataProcessor<T> implements DataProcessor<T> {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ExecutorService executorService;
    private final boolean ownsExecutor;
    
    protected AbstractDataProcessor() {
        this(ForkJoinPool.commonPool());
    }
    
    protected AbstractDataProcessor(ExecutorService executorService) {
        this.executorService = executorService;
        this.ownsExecutor = false;
    }
    
    @Override
    public CompletableFuture<ProcessingResult> processAsync(InputStream inputStream, 
                                                          Class<T> targetClass, 
                                                          ProcessingConfiguration configuration,
                                                          Consumer<List<T>> batchProcessor) {
        
        return CompletableFuture.supplyAsync(() -> 
                process(inputStream, targetClass, configuration, batchProcessor), 
                executorService
        );
    }
    
    @Override
    public boolean canProcess(String format) {
        if (format == null) return false;
        
        String normalizedFormat = format.toLowerCase().trim();
        if (normalizedFormat.startsWith(".")) {
            normalizedFormat = normalizedFormat.substring(1);
        }
        
        return getSupportedFormats().contains(normalizedFormat);
    }
    
    @Override
    public ValidationResult validateInput(InputStream inputStream, ProcessingConfiguration configuration) {
        if (inputStream == null) {
            return ValidationResult.invalid("Input stream cannot be null");
        }
        
        if (configuration == null) {
            return ValidationResult.invalid("Configuration cannot be null");
        }
        
        return performFormatSpecificValidation(inputStream, configuration);
    }
    
    /**
     * Template method for format-specific processing logic
     * 
     * @param inputStream Input data stream
     * @param targetClass Target class to map data to
     * @param configuration Processing configuration
     * @param batchProcessor Consumer to process batches of data
     * @return Processing result
     */
    protected abstract ProcessingResult doProcess(InputStream inputStream, 
                                                Class<T> targetClass, 
                                                ProcessingConfiguration configuration,
                                                Consumer<List<T>> batchProcessor);
    
    /**
     * Format-specific validation logic
     * 
     * @param inputStream Input stream to validate
     * @param configuration Processing configuration
     * @return Validation result
     */
    protected abstract ValidationResult performFormatSpecificValidation(InputStream inputStream, 
                                                                       ProcessingConfiguration configuration);
    
    @Override
    public final ProcessingResult process(InputStream inputStream, 
                                        Class<T> targetClass, 
                                        ProcessingConfiguration configuration,
                                        Consumer<List<T>> batchProcessor) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Starting {} processing for target class: {}", getProcessorName(), targetClass.getSimpleName());
            
            // Validate input
            ValidationResult validation = validateInput(inputStream, configuration);
            if (!validation.isValid()) {
                return new ProcessingResult(0, 1, System.currentTimeMillis() - startTime, 
                        false, "Validation failed: " + validation.getErrorMessage(), null);
            }
            
            // Perform actual processing
            ProcessingResult result = doProcess(inputStream, targetClass, configuration, batchProcessor);
            
            logger.info("Completed {} processing: {}", getProcessorName(), result);
            return result;
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("{} processing failed after {}ms", getProcessorName(), processingTime, e);
            
            return new ProcessingResult(0, 1, processingTime, false, 
                    "Processing failed: " + e.getMessage(), null);
        }
    }
    
    /**
     * Create error result with timing information
     */
    protected ProcessingResult createErrorResult(long startTime, String errorMessage) {
        return new ProcessingResult(0, 1, System.currentTimeMillis() - startTime, 
                false, errorMessage, null);
    }
    
    /**
     * Create success result with timing information
     */
    protected ProcessingResult createSuccessResult(long startTime, long processedRecords, 
                                                 long errorCount, Object metadata) {
        return new ProcessingResult(processedRecords, errorCount, 
                System.currentTimeMillis() - startTime, errorCount == 0, null, metadata);
    }
    
    /**
     * Cleanup method for resource management
     */
    public void shutdown() {
        if (ownsExecutor && executorService != null) {
            executorService.shutdown();
        }
    }
}