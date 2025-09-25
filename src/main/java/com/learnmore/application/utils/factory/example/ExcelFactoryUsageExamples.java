package com.learnmore.application.utils.factory.example;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.config.ExcelConfigValidator;
import com.learnmore.application.utils.factory.ExcelFactory;
import com.learnmore.application.utils.factory.ExcelFactory.ExcelProcessor;
import com.learnmore.application.utils.factory.ExcelFactory.ProcessingStatistics;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Usage examples for ExcelFactory and ExcelConfig
 * Demonstrates various ways to configure and use the Excel processing system
 */
@Slf4j
public class ExcelFactoryUsageExamples {
    
    /**
     * Example 1: Quick setup with presets
     */
    public static void quickSetupExample() {
        log.info("=== Quick Setup Example ===");
        
        // Small file processing
        ExcelProcessor<ExcelRowDTO> smallProcessor = 
            ExcelFactory.Presets.smallFile(ExcelRowDTO.class);
        log.info("Small file processor strategy: {}", 
                smallProcessor.getStatistics().getStrategy());
        
        // Large file processing
        ExcelProcessor<ExcelRowDTO> largeProcessor = 
            ExcelFactory.Presets.largeFile(ExcelRowDTO.class);
        log.info("Large file processor strategy: {}", 
                largeProcessor.getStatistics().getStrategy());
        
        // High performance processing
        ExcelProcessor<ExcelRowDTO> perfProcessor = 
            ExcelFactory.Presets.highPerformance(ExcelRowDTO.class);
        log.info("High performance processor strategy: {}", 
                perfProcessor.getStatistics().getStrategy());
    }
    
    /**
     * Example 2: Environment-specific configurations
     */
    public static void environmentConfigExample() {
        log.info("=== Environment Config Example ===");
        
        // Development environment
        ExcelConfig devConfig = ExcelFactory.Profiles.development();
        log.info("Dev config - Strict validation: {}, Batch size: {}", 
                devConfig.isStrictValidation(), devConfig.getBatchSize());
        
        // Production environment
        ExcelConfig prodConfig = ExcelFactory.Profiles.production();
        log.info("Prod config - Strict validation: {}, Batch size: {}, Caching: {}", 
                prodConfig.isStrictValidation(), 
                prodConfig.getBatchSize(),
                prodConfig.isEnableReflectionCache());
        
        // Batch processing
        ExcelConfig batchConfig = ExcelFactory.Profiles.batch();
        log.info("Batch config - Parallel: {}, Threads: {}, Batch size: {}", 
                batchConfig.isParallelProcessing(),
                batchConfig.getThreadPoolSize(),
                batchConfig.getBatchSize());
    }
    
    /**
     * Example 3: Custom configuration with validation
     */
    public static void customConfigExample() {
        log.info("=== Custom Config Example ===");
        
        // Create custom configuration
        ExcelConfig customConfig = ExcelConfig.builder()
            .batchSize(5000)
            .memoryThreshold(200)
            .useStreamingParser(true)
            .enableProgressTracking(true)
            .progressReportInterval(10000)
            .strictValidation(false)
            .maxErrorsBeforeAbort(100)
            .enableReflectionCache(true)
            .enableDataTypeCache(true)
            .jobId("custom-excel-job-001")
            .build();
        
        // Validate configuration
        ExcelConfigValidator.ValidationResult validation = 
            ExcelConfigValidator.validate(customConfig);
        
        if (validation.isValid()) {
            log.info("Custom config is valid");
            if (!validation.getWarnings().isEmpty()) {
                log.warn("Warnings: {}", validation.getWarnings());
            }
            
            // Create immutable copy for thread safety
            ExcelConfig immutableConfig = ExcelConfigValidator.makeImmutable(customConfig);
            log.info("Created immutable config with job ID: {}", immutableConfig.getJobId());
        } else {
            log.error("Custom config is invalid: {}", validation.getErrors());
        }
    }
    
    /**
     * Example 4: File size-based recommendations
     */
    public static void fileSizeRecommendationExample() {
        log.info("=== File Size Recommendation Example ===");
        
        // Different file sizes
        long[] fileSizes = {5000, 50000, 500000, 2000000};
        String[] environments = {"development", "staging", "production"};
        
        for (long fileSize : fileSizes) {
            for (String env : environments) {
                ExcelConfig recommendedConfig = 
                    ExcelConfigValidator.getRecommendedConfig(fileSize, env);
                
                log.info("File size: {}, Environment: {} -> Batch: {}, Streaming: {}, Validation: {}", 
                        fileSize, env,
                        recommendedConfig.getBatchSize(),
                        recommendedConfig.isUseStreamingParser(),
                        recommendedConfig.isStrictValidation());
            }
        }
    }
    
    /**
     * Example 5: Strategy selection and processing
     */
    public static void strategySelectionExample() {
        log.info("=== Strategy Selection Example ===");
        
        // Show all available strategies
        for (ExcelFactory.ProcessingStrategy strategy : 
             ExcelFactory.ProcessingStrategy.values()) {
            log.info("Strategy: {} - {}", strategy, strategy.getDescription());
        }
        
        // Create processors with explicit strategies
        ExcelConfig config = ExcelFactory.Profiles.production();
        
        ExcelProcessor<ExcelRowDTO> saxProcessor = 
            ExcelFactory.createProcessor(
                ExcelFactory.ProcessingStrategy.SAX_STREAMING, 
                ExcelRowDTO.class, 
                config);
        
        ExcelProcessor<ExcelRowDTO> parallelProcessor = 
            ExcelFactory.createProcessor(
                ExcelFactory.ProcessingStrategy.PARALLEL_SAX, 
                ExcelRowDTO.class, 
                config);
        
        log.info("SAX processor: {}", saxProcessor.getStatistics().getStrategy());
        log.info("Parallel processor: {}", parallelProcessor.getStatistics().getStrategy());
    }
    
    /**
     * Example 6: Processing with streaming consumer
     */
    public static void streamingProcessingExample() {
        log.info("=== Streaming Processing Example ===");
        
        // Create processor for large files
        ExcelProcessor<ExcelRowDTO> processor = 
            ExcelFactory.Presets.extraLargeFile(ExcelRowDTO.class);
        
        // Simulate processing with consumer
        try (InputStream mockStream = new ByteArrayInputStream(new byte[0])) {
            
            processor.processStream(mockStream, batch -> {
                log.info("Processing batch of {} records", batch.size());
                
                // Process each record in the batch
                batch.forEach(record -> {
                    // Business logic here
                    log.debug("Processing record: {}", record.getBusinessKey());
                });
            });
            
            // Get processing statistics
            ProcessingStatistics stats = processor.getStatistics();
            log.info("Processing completed: {}", stats);
            
        } catch (Exception e) {
            log.error("Processing failed", e);
        }
    }
    
    /**
     * Example 7: Complete workflow with error handling
     */
    public static void completeWorkflowExample(String filePath) {
        log.info("=== Complete Workflow Example ===");
        
        try {
            // Step 1: Create configuration based on file analysis
            ExcelConfig config = ExcelConfigValidator.getRecommendedConfig(100000, "production");
            
            // Step 2: Validate configuration
            ExcelConfigValidator.ValidationResult validation = 
                ExcelConfigValidator.validate(config);
            
            if (!validation.isValid()) {
                log.error("Configuration validation failed: {}", validation.getErrors());
                return;
            }
            
            // Step 3: Create processor (auto-detect strategy)
            try (InputStream fileStream = new FileInputStream(filePath)) {
                ExcelProcessor<ExcelRowDTO> processor = 
                    ExcelFactory.createProcessor(fileStream, ExcelRowDTO.class, config);
                
                log.info("Selected processing strategy: {}", 
                        processor.getStatistics().getStrategy());
                
                // Step 4: Process file
                List<ExcelRowDTO> results = processor.process(fileStream);
                
                // Step 5: Get and log statistics
                ProcessingStatistics stats = processor.getStatistics();
                log.info("Processing completed successfully: {}", stats);
                log.info("Processed {} records with {} errors in {}ms", 
                        stats.getProcessedRows(), 
                        stats.getErrorRows(),
                        stats.getProcessingTimeMs());
                
                // Step 6: Business logic with results
                processBusinessLogic(results);
                
            }
            
        } catch (Exception e) {
            log.error("Complete workflow failed", e);
        }
    }
    
    private static void processBusinessLogic(List<ExcelRowDTO> records) {
        log.info("Processing {} records with business logic", records.size());
        
        // Example business logic
        long validRecords = records.stream()
            .filter(record -> record.getMaDonVi() != null && !record.getMaDonVi().isEmpty())
            .count();
        
        log.info("Found {} valid records out of {}", validRecords, records.size());
    }
    
    /**
     * Main method to run all examples
     */
    public static void main(String[] args) {
        log.info("Starting ExcelFactory Usage Examples");
        
        quickSetupExample();
        environmentConfigExample();
        customConfigExample();
        fileSizeRecommendationExample();
        strategySelectionExample();
        streamingProcessingExample();
        
        // Only run file example if file path provided
        if (args.length > 0) {
            completeWorkflowExample(args[0]);
        }
        
        log.info("All examples completed successfully!");
    }
}