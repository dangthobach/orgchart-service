package com.learnmore.application.utils.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for ExcelConfig to ensure configuration consistency
 */
public class ExcelConfigValidator {
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{valid=").append(valid);
            if (!errors.isEmpty()) {
                sb.append(", errors=").append(errors);
            }
            if (!warnings.isEmpty()) {
                sb.append(", warnings=").append(warnings);
            }
            sb.append("}");
            return sb.toString();
        }
    }
    
    public static ValidationResult validate(ExcelConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Batch size validation
        if (config.getBatchSize() <= 0) {
            errors.add("Batch size must be positive");
        }
        if (config.getBatchSize() > 100000) {
            warnings.add("Batch size > 100000 may cause memory issues");
        }
        
        // Memory threshold validation
        if (config.getMemoryThresholdMB() <= 0) {
            errors.add("Memory threshold must be positive");
        }
        long maxHeapMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        if (config.getMemoryThresholdMB() > maxHeapMB) {
            warnings.add("Memory threshold (" + config.getMemoryThresholdMB() + 
                        "MB) exceeds JVM max heap size (" + maxHeapMB + "MB)");
        }
        
        // Thread pool validation
        if (config.isParallelProcessing() && config.getThreadPoolSize() <= 0) {
            errors.add("Thread pool size must be positive when parallel processing enabled");
        }
        if (config.getThreadPoolSize() > Runtime.getRuntime().availableProcessors() * 2) {
            warnings.add("Thread pool size (" + config.getThreadPoolSize() + 
                        ") is much larger than available processors (" + 
                        Runtime.getRuntime().availableProcessors() + ")");
        }
        
        // Streaming configuration validation
        if (config.isForceStreamingMode() && config.getBatchSize() > 50000) {
            warnings.add("Large batch size (" + config.getBatchSize() + 
                        ") with streaming may impact performance");
        }
        
        // SXSSF window validation
        if (config.getSxssfRowAccessWindowSize() < 100) {
            warnings.add("SXSSF window size (" + config.getSxssfRowAccessWindowSize() + 
                        ") too small, may impact performance");
        }
        if (config.getSxssfRowAccessWindowSize() > 10000) {
            warnings.add("SXSSF window size (" + config.getSxssfRowAccessWindowSize() + 
                        ") too large, may cause memory issues");
        }
        
        // Range validation
        if (config.isEnableRangeValidation()) {
            if (config.getMinValue() != null && config.getMaxValue() != null) {
                if (config.getMinValue() > config.getMaxValue()) {
                    errors.add("Min value (" + config.getMinValue() + 
                              ") cannot be greater than max value (" + config.getMaxValue() + ")");
                }
            }
        }
        
        // Progress tracking validation
        if (config.isEnableProgressTracking() && config.getProgressReportInterval() <= 0) {
            errors.add("Progress report interval must be positive when progress tracking enabled");
        }
        
        // Error handling validation
        if (config.getMaxErrorsBeforeAbort() < 0) {
            errors.add("Max errors before abort cannot be negative");
        }
        
        // Threshold consistency validation
        if (config.getCellCountThresholdForSXSSF() <= 0) {
            errors.add("Cell count threshold for SXSSF must be positive");
        }
        if (config.getMaxCellsForXSSF() <= 0) {
            errors.add("Max cells for XSSF must be positive");
        }
        if (config.getCsvThreshold() <= 0) {
            errors.add("CSV threshold must be positive");
        }
        
        // Performance optimization warnings
        if (config.isDisableAutoSizing() && config.isAutoSizeColumns()) {
            warnings.add("Auto sizing disabled but auto size columns enabled - configuration conflict");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Create immutable copy of config to prevent runtime changes
     */
    public static ExcelConfig makeImmutable(ExcelConfig config) {
        ValidationResult validation = validate(config);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Cannot make immutable copy of invalid config: " + 
                                             validation.getErrors());
        }
        
        return ExcelConfig.builder()
            .batchSize(config.getBatchSize())
            .memoryThreshold(config.getMemoryThresholdMB())
            .dateFormat(config.getDateFormat())
            .dateTimeFormat(config.getDateTimeFormat())
            .delimiter(config.getDelimiter())
            .parallelProcessing(config.isParallelProcessing())
            .threadPoolSize(config.getThreadPoolSize())
            .strictValidation(config.isStrictValidation())
            .failOnFirstError(config.isFailOnFirstError())
            .enableProgressTracking(config.isEnableProgressTracking())
            .enableMemoryMonitoring(config.isEnableMemoryMonitoring())
            .progressReportInterval(config.getProgressReportInterval())
            .useStreamingParser(config.isUseStreamingParser())
            .maxErrorsBeforeAbort(config.getMaxErrorsBeforeAbort())
            .enableDataTypeCache(config.isEnableDataTypeCache())
            .enableReflectionCache(config.isEnableReflectionCache())
            .cellCountThresholdForSXSSF(config.getCellCountThresholdForSXSSF())
            .sxssfRowAccessWindowSize(config.getSxssfRowAccessWindowSize())
            .maxCellsForXSSF(config.getMaxCellsForXSSF())
            .forceStreamingMode(config.isForceStreamingMode())
            .preferCSVForLargeData(config.isPreferCSVForLargeData())
            .csvThreshold(config.getCsvThreshold())
            .allowXLSFormat(config.isAllowXLSFormat())
            .maxRowsForXLS(config.getMaxRowsForXLS())
            .maxColsForXLS(config.getMaxColsForXLS())
            .enableRangeValidation(config.isEnableRangeValidation())
            .minValue(config.getMinValue())
            .maxValue(config.getMaxValue())
            .startRow(config.getStartRow())
            .autoSizeColumns(config.isAutoSizeColumns())
            .disableAutoSizing(config.isDisableAutoSizing())
            .useSharedStrings(config.isUseSharedStrings())
            .compressOutput(config.isCompressOutput())
            .flushInterval(config.getFlushInterval())
            .enableCellStyleOptimization(config.isEnableCellStyleOptimization())
            .minimizeMemoryFootprint(config.isMinimizeMemoryFootprint())
            .jobId(config.getJobId())
            .build();
    }
    
    /**
     * Get recommended configuration for given file size
     */
    public static ExcelConfig getRecommendedConfig(long estimatedRows, String environment) {
        ExcelConfig.Builder builder = ExcelConfig.builder();
        
        // Base configuration based on file size
        if (estimatedRows < 10000) {
            // Small file
            builder.batchSize(1000)
                   .memoryThreshold(100)
                   .useStreamingParser(false)
                   .enableProgressTracking(false);
        } else if (estimatedRows < 100000) {
            // Medium file
            builder.batchSize(5000)
                   .memoryThreshold(200)
                   .useStreamingParser(false)
                   .enableProgressTracking(true)
                   .progressReportInterval(10000);
        } else if (estimatedRows < 1000000) {
            // Large file
            builder.batchSize(10000)
                   .memoryThreshold(500)
                   .useStreamingParser(true)
                   .forceStreamingMode(true)
                   .enableProgressTracking(true)
                   .progressReportInterval(50000);
        } else {
            // Extra large file
            builder.batchSize(50000)
                   .memoryThreshold(1000)
                   .useStreamingParser(true)
                   .forceStreamingMode(true)
                   .enableProgressTracking(true)
                   .progressReportInterval(100000)
                   .minimizeMemoryFootprint(true);
        }
        
        // Environment-specific adjustments
        switch (environment.toLowerCase()) {
            case "dev":
            case "development":
                builder.strictValidation(true)
                       .failOnFirstError(true)
                       .maxErrorsBeforeAbort(10);
                break;
            case "staging":
                builder.strictValidation(true)
                       .failOnFirstError(false)
                       .maxErrorsBeforeAbort(100);
                break;
            case "prod":
            case "production":
                builder.strictValidation(false)
                       .failOnFirstError(false)
                       .maxErrorsBeforeAbort(500)
                       .enableReflectionCache(true)
                       .enableDataTypeCache(true);
                break;
            default:
                // Default to production settings
                builder.strictValidation(false)
                       .failOnFirstError(false)
                       .maxErrorsBeforeAbort(100);
        }
        
        return builder.build();
    }
}