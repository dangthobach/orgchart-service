package com.learnmore.application.utils.factory;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.validation.ExcelDimensionValidator;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Factory class for creating appropriate Excel processors based on file characteristics
 * Implements Strategy Pattern for optimal processing selection
 */
@Slf4j
public class ExcelFactory {
    
    // Processing strategy thresholds
    private static final long SMALL_FILE_THRESHOLD = 10_000L;      // < 10K rows: In-memory
    private static final long MEDIUM_FILE_THRESHOLD = 100_000L;    // < 100K rows: XSSF
    private static final long LARGE_FILE_THRESHOLD = 1_000_000L;   // < 1M rows: SXSSF
    // > 1M rows: SAX Streaming
    
    private ExcelFactory() {
        // Private constructor - static factory
    }
    
    /**
     * Create optimal Excel processor based on file size and configuration
     */
    public static <T> ExcelProcessor<T> createProcessor(
            InputStream inputStream,
            Class<T> beanClass,
            ExcelConfig config) throws ExcelProcessException {
        
        // Wrap stream for mark/reset support
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 8192);
        bufferedStream.mark(Integer.MAX_VALUE);
        
        try {
            // Analyze file to determine optimal strategy
            FileAnalysis analysis = analyzeFile(bufferedStream, config);
            bufferedStream.reset();
            
            // Select strategy based on analysis
            ProcessingStrategy strategy = selectStrategy(analysis, config);
            
            log.info("Selected processing strategy: {} for {} rows, {} cells", 
                    strategy, analysis.estimatedRows, analysis.estimatedCells);
            
            return createProcessorForStrategy(strategy, beanClass, config, bufferedStream);
            
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to create Excel processor", e);
        }
    }
    
    /**
     * Create processor with explicit strategy
     */
    public static <T> ExcelProcessor<T> createProcessor(
            ProcessingStrategy strategy,
            Class<T> beanClass,
            ExcelConfig config) {
        
        return createProcessorForStrategy(strategy, beanClass, config, null);
    }
    
    /**
     * Create pre-configured processors for common scenarios
     */
    public static class Presets {
        
        /**
         * Small file processor - Optimized for < 10K rows
         */
        public static <T> ExcelProcessor<T> smallFile(Class<T> beanClass) {
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(1000)
                    .memoryThreshold(100)
                    .useStreamingParser(false)
                    .enableProgressTracking(false)
                    .build();
            
            return createProcessor(ProcessingStrategy.IN_MEMORY, beanClass, config);
        }
        
        /**
         * Medium file processor - Optimized for 10K-100K rows
         */
        public static <T> ExcelProcessor<T> mediumFile(Class<T> beanClass) {
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(5000)
                    .memoryThreshold(200)
                    .useStreamingParser(false)
                    .enableProgressTracking(true)
                    .progressReportInterval(10000)
                    .build();
            
            return createProcessor(ProcessingStrategy.XSSF, beanClass, config);
        }
        
        /**
         * Large file processor - Optimized for 100K-1M rows
         */
        public static <T> ExcelProcessor<T> largeFile(Class<T> beanClass) {
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(10000)
                    .memoryThreshold(500)
                    .useStreamingParser(true)
                    .forceStreamingMode(true)
                    .sxssfRowAccessWindowSize(1000)
                    .enableProgressTracking(true)
                    .progressReportInterval(50000)
                    .build();
            
            return createProcessor(ProcessingStrategy.SXSSF, beanClass, config);
        }
        
        /**
         * Extra large file processor - Optimized for > 1M rows
         */
        public static <T> ExcelProcessor<T> extraLargeFile(Class<T> beanClass) {
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(50000)
                    .memoryThreshold(1000)
                    .useStreamingParser(true)
                    .forceStreamingMode(true)
                    .enableProgressTracking(true)
                    .progressReportInterval(100000)
                    .minimizeMemoryFootprint(true)
                    .build();
            
            return createProcessor(ProcessingStrategy.SAX_STREAMING, beanClass, config);
        }
        
        /**
         * Memory-constrained processor - Minimal memory usage
         */
        public static <T> ExcelProcessor<T> lowMemory(Class<T> beanClass) {
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(100)
                    .memoryThreshold(50)
                    .useStreamingParser(true)
                    .forceStreamingMode(true)
                    .minimizeMemoryFootprint(true)
                    .disableAutoSizing(true)
                    .useSharedStrings(false)
                    .build();
            
            return createProcessor(ProcessingStrategy.SAX_STREAMING, beanClass, config);
        }
        
        /**
         * High-performance processor - Maximum speed
         */
        public static <T> ExcelProcessor<T> highPerformance(Class<T> beanClass) {
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(100000)
                    .parallelProcessing(true)
                    .threadPoolSize(Runtime.getRuntime().availableProcessors())
                    .useStreamingParser(true)
                    .enableReflectionCache(true)
                    .enableDataTypeCache(true)
                    .disableAutoSizing(true)
                    .build();
            
            return createProcessor(ProcessingStrategy.PARALLEL_SAX, beanClass, config);
        }
    }
    
    /**
     * Configuration profiles for different environments
     */
    public static class Profiles {
        
        /**
         * Development profile - Verbose logging, strict validation
         */
        public static ExcelConfig development() {
            return ExcelConfig.builder()
                    .batchSize(100)
                    .strictValidation(true)
                    .failOnFirstError(true)
                    .enableProgressTracking(true)
                    .progressReportInterval(1000)
                    .maxErrorsBeforeAbort(10)
                    .build();
        }
        
        /**
         * Staging profile - Balanced performance and validation
         */
        public static ExcelConfig staging() {
            return ExcelConfig.builder()
                    .batchSize(5000)
                    .strictValidation(true)
                    .failOnFirstError(false)
                    .enableProgressTracking(true)
                    .progressReportInterval(10000)
                    .maxErrorsBeforeAbort(100)
                    .build();
        }
        
        /**
         * Production profile - Optimized for performance
         */
        public static ExcelConfig production() {
            return ExcelConfig.builder()
                    .batchSize(10000)
                    .strictValidation(false)
                    .failOnFirstError(false)
                    .enableProgressTracking(true)
                    .progressReportInterval(50000)
                    .maxErrorsBeforeAbort(500)
                    .useStreamingParser(true)
                    .enableReflectionCache(true)
                    .enableDataTypeCache(true)
                    .minimizeMemoryFootprint(true)
                    .build();
        }
        
        /**
         * Batch processing profile - For scheduled jobs
         */
        public static ExcelConfig batch() {
            return ExcelConfig.builder()
                    .batchSize(50000)
                    .memoryThreshold(2000)
                    .parallelProcessing(true)
                    .threadPoolSize(Runtime.getRuntime().availableProcessors())
                    .enableProgressTracking(true)
                    .progressReportInterval(100000)
                    .useStreamingParser(true)
                    .forceStreamingMode(true)
                    .build();
        }
    }
    
    // Private helper methods
    
    private static FileAnalysis analyzeFile(BufferedInputStream stream, ExcelConfig config) {
        try {
            // Use ExcelDimensionValidator to get file metrics if available
            // For now, return default analysis
            return new FileAnalysis(0, 0, 0);
        } catch (Exception e) {
            log.warn("Failed to analyze file, using default strategy: {}", e.getMessage());
            return new FileAnalysis(0, 0, 0);
        }
    }
    
    private static ProcessingStrategy selectStrategy(FileAnalysis analysis, ExcelConfig config) {
        // Force streaming if configured
        if (config.isForceStreamingMode()) {
            return ProcessingStrategy.SAX_STREAMING;
        }
        
        // Check CSV preference for large files
        if (config.isPreferCSVForLargeData() && 
            analysis.estimatedCells > config.getCsvThreshold()) {
            log.info("Recommending CSV format for {} cells (threshold: {})", 
                    analysis.estimatedCells, config.getCsvThreshold());
        }
        
        // Select based on size thresholds
        if (analysis.estimatedRows < SMALL_FILE_THRESHOLD) {
            return ProcessingStrategy.IN_MEMORY;
        } else if (analysis.estimatedRows < MEDIUM_FILE_THRESHOLD) {
            return ProcessingStrategy.XSSF;
        } else if (analysis.estimatedRows < LARGE_FILE_THRESHOLD) {
            return ProcessingStrategy.SXSSF;
        } else if (config.isParallelProcessing()) {
            return ProcessingStrategy.PARALLEL_SAX;
        } else {
            return ProcessingStrategy.SAX_STREAMING;
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> ExcelProcessor<T> createProcessorForStrategy(
            ProcessingStrategy strategy,
            Class<T> beanClass,
            ExcelConfig config,
            InputStream stream) {
        
        switch (strategy) {
            case IN_MEMORY:
                return new InMemoryProcessor<>(beanClass, config);
                
            case XSSF:
                return new XSSFProcessor<>(beanClass, config);
                
            case SXSSF:
                return new SXSSFProcessor<>(beanClass, config);
                
            case SAX_STREAMING:
                return new SAXStreamingProcessor<>(beanClass, config);
                
            case PARALLEL_SAX:
                return new ParallelSAXProcessor<>(beanClass, config);
                
            default:
                log.warn("Unknown strategy {}, defaulting to SAX_STREAMING", strategy);
                return new SAXStreamingProcessor<>(beanClass, config);
        }
    }
    
    /**
     * Processing strategies
     */
    public enum ProcessingStrategy {
        IN_MEMORY("In-Memory", "Best for small files < 10K rows"),
        XSSF("XSSF", "Standard POI for medium files < 100K rows"),
        SXSSF("SXSSF Streaming", "Streaming write for large files < 1M rows"),
        SAX_STREAMING("SAX Streaming", "True streaming for very large files > 1M rows"),
        PARALLEL_SAX("Parallel SAX", "Multi-threaded SAX for maximum performance");
        
        private final String name;
        private final String description;
        
        ProcessingStrategy(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        @Override
        public String toString() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * File analysis result
     */
    private static class FileAnalysis {
        final long estimatedRows;
        final long estimatedColumns;
        final long estimatedCells;
        
        FileAnalysis(long rows, long columns, long cells) {
            this.estimatedRows = rows;
            this.estimatedColumns = columns;
            this.estimatedCells = cells;
        }
    }
    
    /**
     * Common interface for all Excel processors
     */
    public interface ExcelProcessor<T> {
        /**
         * Process Excel file and return results
         */
        List<T> process(InputStream inputStream) throws ExcelProcessException;
        
        /**
         * Process Excel file with streaming consumer
         */
        void processStream(InputStream inputStream, Consumer<List<T>> consumer) 
            throws ExcelProcessException;
        
        /**
         * Get processing statistics
         */
        ProcessingStatistics getStatistics();
    }
    
    /**
     * Processing statistics
     */
    public static class ProcessingStatistics {
        private long totalRows;
        private long processedRows;
        private long errorRows;
        private long processingTimeMs;
        private double recordsPerSecond;
        private String strategy;
        
        // Getters and setters
        public long getTotalRows() { return totalRows; }
        public void setTotalRows(long totalRows) { this.totalRows = totalRows; }
        
        public long getProcessedRows() { return processedRows; }
        public void setProcessedRows(long processedRows) { this.processedRows = processedRows; }
        
        public long getErrorRows() { return errorRows; }
        public void setErrorRows(long errorRows) { this.errorRows = errorRows; }
        
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public double getRecordsPerSecond() { return recordsPerSecond; }
        public void setRecordsPerSecond(double recordsPerSecond) { this.recordsPerSecond = recordsPerSecond; }
        
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        
        @Override
        public String toString() {
            return String.format("ProcessingStatistics{strategy='%s', processedRows=%d, errorRows=%d, timeMs=%d, recordsPerSec=%.2f}",
                    strategy, processedRows, errorRows, processingTimeMs, recordsPerSecond);
        }
    }
}