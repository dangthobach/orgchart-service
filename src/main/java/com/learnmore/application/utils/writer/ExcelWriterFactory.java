package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating optimized Excel writers based on data characteristics
 * Implements Strategy Pattern for optimal writing performance
 */
@Slf4j
public class ExcelWriterFactory {
    
    // Writing strategy thresholds
    private static final int SMALL_FILE_THRESHOLD = 10_000;      // < 10K rows: In-memory
    private static final int MEDIUM_FILE_THRESHOLD = 100_000;    // < 100K rows: Streaming SXSSF
    private static final int LARGE_FILE_THRESHOLD = 1_000_000;   // < 1M rows: Optimized Streaming
    // > 1M rows: Parallel Write
    
    private ExcelWriterFactory() {
        // Private constructor - static factory
    }
    
    /**
     * Writing strategies based on data size and requirements
     */
    public enum WritingStrategy {
        IN_MEMORY("In-Memory XSSF", "Best for < 10K records with complex formatting"),
        STREAMING_SXSSF("Streaming SXSSF", "Best for 10K-100K records with moderate memory usage"),
        OPTIMIZED_STREAMING("Optimized Streaming", "Best for 100K-1M records with MethodHandle caching"),
        PARALLEL_WRITE("Parallel Write", "Multi-threaded writing for maximum speed > 1M records"),
        CSV_FALLBACK("CSV Export", "Ultra-fast for data-only export without formatting");
        
        private final String name;
        private final String description;
        
        WritingStrategy(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        @Override
        public String toString() { return name; }
        public String getDescription() { return description; }
    }
    
    /**
     * Create optimal writer based on data size and configuration
     */
    public static <T> ExcelWriter<T> createWriter(
            Class<T> beanClass,
            int estimatedRecords,
            ExcelConfig config) throws ExcelProcessException {
        
        WritingStrategy strategy = selectWritingStrategy(estimatedRecords, config);
        log.info("Selected writing strategy: {} for {} records", strategy, estimatedRecords);
        
        return createWriterForStrategy(strategy, beanClass, config);
    }
    
    /**
     * Create writer with explicit strategy
     */
    public static <T> ExcelWriter<T> createWriter(
            WritingStrategy strategy,
            Class<T> beanClass,
            ExcelConfig config) throws ExcelProcessException {
        
        return createWriterForStrategy(strategy, beanClass, config);
    }
    
    /**
     * Preset writers for common scenarios
     */
    public static class WriterPresets {
        
        /**
         * Fast data export - Optimized for speed over formatting
         */
        public static <T> ExcelWriter<T> fastDataExport(Class<T> beanClass) throws ExcelProcessException {
            ExcelConfig config = ExcelConfig.builder()
                .batchSize(50000)
                .useStreamingParser(true)
                .minimizeMemoryFootprint(true)
                .disableAutoSizing(true)
                .enableCellStyleOptimization(true)
                .compressOutput(false) // Faster without compression
                .flushInterval(5000)
                .build();
            
            return createWriterForStrategy(WritingStrategy.OPTIMIZED_STREAMING, beanClass, config);
        }
        
        /**
         * Formatted report - Balance of performance and presentation
         */
        public static <T> ExcelWriter<T> formattedReport(Class<T> beanClass) throws ExcelProcessException {
            ExcelConfig config = ExcelConfig.builder()
                .batchSize(5000)
                .enableCellStyleOptimization(true)
                .compressOutput(true)
                .autoSizeColumns(true)
                .flushInterval(1000)
                .build();
            
            return createWriterForStrategy(WritingStrategy.STREAMING_SXSSF, beanClass, config);
        }
        
        /**
         * High performance - Maximum throughput for large datasets
         */
        public static <T> ExcelWriter<T> highPerformance(Class<T> beanClass) throws ExcelProcessException {
            ExcelConfig config = ExcelConfig.builder()
                .batchSize(100000)
                .parallelProcessing(true)
                .threadPoolSize(Runtime.getRuntime().availableProcessors())
                .enableCellStyleOptimization(true)
                .enableDataTypeCache(true)
                .enableReflectionCache(true)
                .disableAutoSizing(true)
                .minimizeMemoryFootprint(true)
                .flushInterval(10000)
                .build();
            
            return createWriterForStrategy(WritingStrategy.PARALLEL_WRITE, beanClass, config);
        }
        
        /**
         * Memory constrained - Minimal memory usage
         */
        public static <T> ExcelWriter<T> lowMemory(Class<T> beanClass) throws ExcelProcessException {
            ExcelConfig config = ExcelConfig.builder()
                .batchSize(100)
                .memoryThreshold(50)
                .minimizeMemoryFootprint(true)
                .disableAutoSizing(true)
                .useSharedStrings(false)
                .enableMemoryMonitoring(true)
                .flushInterval(50)
                .build();
            
            return createWriterForStrategy(WritingStrategy.OPTIMIZED_STREAMING, beanClass, config);
        }
        
        /**
         * Small file - In-memory with full formatting
         */
        public static <T> ExcelWriter<T> smallFile(Class<T> beanClass) throws ExcelProcessException {
            ExcelConfig config = ExcelConfig.builder()
                .batchSize(1000)
                .memoryThreshold(100)
                .autoSizeColumns(true)
                .compressOutput(true)
                .build();
            
            return createWriterForStrategy(WritingStrategy.IN_MEMORY, beanClass, config);
        }
    }
    
    /**
     * Configuration profiles for different environments
     */
    public static class WriterProfiles {
        
        /**
         * Development profile - Comprehensive formatting, slower but detailed
         */
        public static ExcelConfig development() {
            return ExcelConfig.builder()
                .batchSize(100)
                .autoSizeColumns(true)
                .compressOutput(true)
                .enableProgressTracking(true)
                .progressReportInterval(100)
                .enableMemoryMonitoring(true)
                .flushInterval(50)
                .build();
        }
        
        /**
         * Production profile - Optimized for performance
         */
        public static ExcelConfig production() {
            return ExcelConfig.builder()
                .batchSize(10000)
                .disableAutoSizing(true)
                .enableCellStyleOptimization(true)
                .enableDataTypeCache(true)
                .enableReflectionCache(true)
                .minimizeMemoryFootprint(true)
                .enableProgressTracking(true)
                .progressReportInterval(50000)
                .flushInterval(5000)
                .build();
        }
        
        /**
         * Batch processing profile - For scheduled jobs
         */
        public static ExcelConfig batch() {
            return ExcelConfig.builder()
                .batchSize(50000)
                .parallelProcessing(true)
                .threadPoolSize(Runtime.getRuntime().availableProcessors())
                .enableCellStyleOptimization(true)
                .enableDataTypeCache(true)
                .enableReflectionCache(true)
                .disableAutoSizing(true)
                .minimizeMemoryFootprint(true)
                .compressOutput(false) // Faster without compression for batch
                .enableProgressTracking(true)
                .progressReportInterval(100000)
                .flushInterval(10000)
                .build();
        }
    }
    
    // Private helper methods
    
    private static WritingStrategy selectWritingStrategy(int records, ExcelConfig config) {
        // Force parallel if configured and sufficient records
        if (config.isParallelProcessing() && records > LARGE_FILE_THRESHOLD) {
            return WritingStrategy.PARALLEL_WRITE;
        }
        
        // Select based on size thresholds
        if (records < SMALL_FILE_THRESHOLD) {
            return WritingStrategy.IN_MEMORY;
        } else if (records < MEDIUM_FILE_THRESHOLD) {
            return WritingStrategy.STREAMING_SXSSF;
        } else if (records < LARGE_FILE_THRESHOLD) {
            return WritingStrategy.OPTIMIZED_STREAMING;
        } else {
            // For very large datasets, default to optimized streaming
            // unless parallel processing is explicitly enabled
            return config.isParallelProcessing() ? 
                WritingStrategy.PARALLEL_WRITE : WritingStrategy.OPTIMIZED_STREAMING;
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> ExcelWriter<T> createWriterForStrategy(
            WritingStrategy strategy, Class<T> beanClass, ExcelConfig config) throws ExcelProcessException {
        
        try {
            switch (strategy) {
                case IN_MEMORY:
                    return new InMemoryExcelWriter<>(beanClass, config);
                    
                case STREAMING_SXSSF:
                    return new StreamingSXSSFWriter<>(beanClass, config);
                    
                case OPTIMIZED_STREAMING:
                    return new OptimizedStreamingWriter<>(beanClass, config);
                    
                case PARALLEL_WRITE:
                    return new ParallelExcelWriter<>(beanClass, config);
                    
                case CSV_FALLBACK:
                    return new CSVFallbackWriter<>(beanClass, config);
                    
                default:
                    log.warn("Unknown strategy {}, defaulting to OPTIMIZED_STREAMING", strategy);
                    return new OptimizedStreamingWriter<>(beanClass, config);
            }
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to create writer for strategy: " + strategy, e);
        }
    }
    
    /**
     * Get all available strategies with descriptions
     */
    public static String getAvailableStrategies() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available Writing Strategies:\n");
        for (WritingStrategy strategy : WritingStrategy.values()) {
            sb.append(String.format("- %s: %s\n", strategy, strategy.getDescription()));
        }
        return sb.toString();
    }
    
    /**
     * Get recommended strategy for given parameters
     */
    public static WritingStrategy getRecommendedStrategy(int records, boolean needsFormatting, boolean memoryConstrained) {
        if (memoryConstrained) {
            return WritingStrategy.OPTIMIZED_STREAMING;
        }
        
        if (!needsFormatting && records > LARGE_FILE_THRESHOLD) {
            return WritingStrategy.CSV_FALLBACK;
        }
        
        if (records > LARGE_FILE_THRESHOLD) {
            return WritingStrategy.PARALLEL_WRITE;
        } else if (records > MEDIUM_FILE_THRESHOLD) {
            return WritingStrategy.OPTIMIZED_STREAMING;
        } else if (records > SMALL_FILE_THRESHOLD) {
            return WritingStrategy.STREAMING_SXSSF;
        } else {
            return WritingStrategy.IN_MEMORY;
        }
    }
}