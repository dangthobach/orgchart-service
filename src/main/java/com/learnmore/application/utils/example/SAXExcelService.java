package com.learnmore.application.utils.example;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.streaming.StreamingExcelProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Example service demonstrating SAX-based Excel processing with validation
 */
@Slf4j
@Service
public class SAXExcelService {
    
    /**
     * Process Excel file with SAX parser and comprehensive validation
     */
    public <T> com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult processExcelWithSAX(
            InputStream inputStream, Class<T> beanClass) {
        
        try {
            // Configure Excel processing with enhanced validation
            ExcelConfig config = ExcelConfig.builder()
                .batchSize(2000)                    // Larger batch size for SAX processing
                .memoryThreshold(100)               // Lower memory threshold
                .useStreamingParser(true)           // Enable SAX processing
                .strictValidation(true)             // Enable strict validation
                .failOnFirstError(false)            // Continue processing on errors
                .enableProgressTracking(true)      // Track progress
                .enableMemoryMonitoring(true)      // Monitor memory usage
                .progressReportInterval(5000)      // Report every 5k records
                .maxErrorsBeforeAbort(50)          // Abort after 50 errors
                .enableRangeValidation(true)       // Enable numeric range validation
                .minValue(0.0)                     // Minimum numeric value
                .maxValue(999999.0)                // Maximum numeric value
                .startRow(0)                       // Header row index
                .requiredFields(Set.of("name", "email"))    // Required fields
                .uniqueFields(Set.of("email", "id"))        // Unique fields
                .build();
            
            log.info("Starting SAX-based Excel processing for class: {}", beanClass.getSimpleName());
            
            // Process with true streaming for optimal memory efficiency
            var result = ExcelUtil.processExcelTrueStreaming(
                inputStream, beanClass, config, batch -> {
                    log.debug("Processing batch of {} records", batch.size());
                    // Process batch here (save to DB, validate business rules, etc.)
                    processBatch(batch);
                }
            );
            
            log.info("SAX processing completed: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("SAX Excel processing failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process Excel file with SAX parser", e);
        }
    }
    
    /**
     * Process Excel with custom validation rules
     */
    public <T> List<T> processExcelWithCustomValidation(
            InputStream inputStream, Class<T> beanClass, Set<String> requiredFields, Set<String> uniqueFields) {
        
        try {
            ExcelConfig config = ExcelConfig.builder()
                .useStreamingParser(true)
                .strictValidation(true)
                .requiredFields(requiredFields)
                .uniqueFields(uniqueFields)
                .enableRangeValidation(false)  // Disable range validation
                .build();
            
            log.info("Processing Excel with custom validation rules");
            log.debug("Required fields: {}", requiredFields);
            log.debug("Unique fields: {}", uniqueFields);
            
            return ExcelUtil.processExcelToList(inputStream, beanClass, config);
            
        } catch (Exception e) {
            log.error("Custom validation processing failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process Excel with custom validation", e);
        }
    }
    
    /**
     * High-performance processing for very large files
     */
    public <T> com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult processLargeExcelFile(
            InputStream inputStream, Class<T> beanClass) {
        
        try {
            // Optimized configuration for large files
            ExcelConfig config = ExcelConfig.builder()
                .batchSize(5000)                    // Large batch size
                .memoryThreshold(50)                // Very low memory threshold
                .useStreamingParser(true)           // SAX processing mandatory
                .parallelProcessing(false)          // Disable parallel processing for memory conservation
                .strictValidation(false)            // Relaxed validation for performance
                .enableProgressTracking(true)      // Monitor progress
                .enableMemoryMonitoring(true)      // Critical for large files
                .progressReportInterval(10000)     // Report every 10k records
                .maxErrorsBeforeAbort(100)         // Higher error threshold
                .enableDataTypeCache(true)         // Enable all caching
                .enableReflectionCache(true)
                .build();
            
            log.info("Starting high-performance processing for large Excel file");
            
            return ExcelUtil.processExcelTrueStreaming(inputStream, beanClass, config, batch -> {
                log.info("Processing large batch of {} records", batch.size());
                // Efficient batch processing for large datasets
                processLargeBatch(batch);
            });
            
        } catch (Exception e) {
            log.error("Large file processing failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process large Excel file", e);
        }
    }
    
    /**
     * Process batch with business logic
     */
    private <T> void processBatch(List<T> batch) {
        // Implementation for batch processing
        // Could include: validation, transformation, database saving, etc.
        log.debug("Processing batch of {} items", batch.size());
        
        // Example: Save to database
        // batchRepository.saveAll(batch);
        
        // Example: Apply business rules
        // batch.forEach(this::applyBusinessRules);
    }
    
    /**
     * Process large batch with optimized logic
     */
    private <T> void processLargeBatch(List<T> batch) {
        // Optimized processing for large batches
        log.debug("Processing large batch of {} items with optimized logic", batch.size());
        
        // Example: Bulk operations
        // bulkProcessor.process(batch);
        
        // Example: Parallel processing within batch
        // batch.parallelStream().forEach(this::processItem);
    }
}