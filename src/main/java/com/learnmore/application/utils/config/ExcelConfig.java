package com.learnmore.application.utils.config;

import com.learnmore.application.utils.validation.ValidationRule;
import java.util.*;

/**
 * Configuration class for Excel processing operations
 */
public class ExcelConfig {
    
    // Default values
    public static final int DEFAULT_BATCH_SIZE = 1000;
    public static final long DEFAULT_MEMORY_THRESHOLD_MB = 500;
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_DELIMITER = ",";
    public static final boolean DEFAULT_PARALLEL_PROCESSING = true;
    public static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long memoryThresholdMB = DEFAULT_MEMORY_THRESHOLD_MB;
    private String dateFormat = DEFAULT_DATE_FORMAT;
    private String dateTimeFormat = DEFAULT_DATETIME_FORMAT;
    private String delimiter = DEFAULT_DELIMITER;
    private boolean parallelProcessing = DEFAULT_PARALLEL_PROCESSING;
    private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
    private boolean strictValidation = true;
    private boolean failOnFirstError = false;
    private boolean enableProgressTracking = true;
    private boolean enableMemoryMonitoring = true;
    private long progressReportInterval = 10000; // Report every 10k records
    
    // Validation rules
    private Set<String> requiredFields = new HashSet<>();
    private Set<String> uniqueFields = new HashSet<>();
    private Map<String, ValidationRule> fieldValidationRules = new HashMap<>();
    private List<ValidationRule> globalValidationRules = new ArrayList<>();
    
    // Performance tuning - Production optimized values
    private boolean useStreamingParser = true;
    private int maxErrorsBeforeAbort = 500; // Lower for production - fail fast
    private boolean enableDataTypeCache = true;
    private boolean enableReflectionCache = true;
    
    // Memory management - Production thresholds
    private boolean enableMemoryGC = true; // Auto GC when memory threshold reached
    private int memoryCheckInterval = 1000; // Check memory every 1000 records
    
    // Excel Processing Strategy Configuration - Tuned for 1-2M records
    private long cellCountThresholdForSXSSF = 1_500_000L; // 1.5M cells -> SXSSF (lower for safety)
    private int sxssfRowAccessWindowSize = 1000; // Larger window for better performance
    private long maxCellsForXSSF = 1_000_000L; // 1M cells max for XSSF (conservative)
    private boolean forceStreamingMode = true; // Force streaming for production
    private boolean preferCSVForLargeData = true; // Strongly prefer CSV for large data
    private long csvThreshold = 3_000_000L; // Lower threshold for CSV recommendation
    
    // Format constraints
    private boolean allowXLSFormat = false; // Mặc định không cho phép .xls (giới hạn 65k hàng)
    private int maxRowsForXLS = 65_535; // Giới hạn .xls
    private int maxColsForXLS = 256; // Giới hạn cột .xls
    
    // Range validation
    private boolean enableRangeValidation = false;
    private Double minValue = null;
    private Double maxValue = null;
    
    // Processing configuration
    private int startRow = 0; // 0-based index for header row
    
    public ExcelConfig() {
        // Default constructor
    }
    
    // Builder pattern for easy configuration
    public static class Builder {
        private final ExcelConfig config = new ExcelConfig();
        
        public Builder batchSize(int batchSize) {
            config.batchSize = batchSize;
            return this;
        }
        
        public Builder memoryThreshold(long memoryThresholdMB) {
            config.memoryThresholdMB = memoryThresholdMB;
            return this;
        }
        
        public Builder dateFormat(String dateFormat) {
            config.dateFormat = dateFormat;
            return this;
        }
        
        public Builder dateTimeFormat(String dateTimeFormat) {
            config.dateTimeFormat = dateTimeFormat;
            return this;
        }
        
        public Builder delimiter(String delimiter) {
            config.delimiter = delimiter;
            return this;
        }
        
        public Builder parallelProcessing(boolean enabled) {
            config.parallelProcessing = enabled;
            return this;
        }
        
        public Builder threadPoolSize(int size) {
            config.threadPoolSize = size;
            return this;
        }
        
        public Builder strictValidation(boolean enabled) {
            config.strictValidation = enabled;
            return this;
        }
        
        public Builder failOnFirstError(boolean enabled) {
            config.failOnFirstError = enabled;
            return this;
        }
        
        public Builder enableProgressTracking(boolean enabled) {
            config.enableProgressTracking = enabled;
            return this;
        }
        
        public Builder enableMemoryMonitoring(boolean enabled) {
            config.enableMemoryMonitoring = enabled;
            return this;
        }
        
        public Builder progressReportInterval(long interval) {
            config.progressReportInterval = interval;
            return this;
        }
        
        public Builder requiredFields(String... fields) {
            config.requiredFields.addAll(Arrays.asList(fields));
            return this;
        }
        
        public Builder requiredFields(Set<String> fields) {
            config.requiredFields.addAll(fields);
            return this;
        }
        
        public Builder uniqueFields(String... fields) {
            config.uniqueFields.addAll(Arrays.asList(fields));
            return this;
        }
        
        public Builder uniqueFields(Set<String> fields) {
            config.uniqueFields.addAll(fields);
            return this;
        }
        
        public Builder addFieldValidation(String fieldName, ValidationRule rule) {
            config.fieldValidationRules.put(fieldName, rule);
            return this;
        }
        
        public Builder addGlobalValidation(ValidationRule rule) {
            config.globalValidationRules.add(rule);
            return this;
        }
        
        public Builder useStreamingParser(boolean enabled) {
            config.useStreamingParser = enabled;
            return this;
        }
        
        public Builder maxErrorsBeforeAbort(int maxErrors) {
            config.maxErrorsBeforeAbort = maxErrors;
            return this;
        }
        
        public Builder enableDataTypeCache(boolean enabled) {
            config.enableDataTypeCache = enabled;
            return this;
        }
        
        public Builder enableReflectionCache(boolean enabled) {
            config.enableReflectionCache = enabled;
            return this;
        }
        
        // Excel Strategy Configuration
        public Builder cellCountThresholdForSXSSF(long threshold) {
            config.cellCountThresholdForSXSSF = threshold;
            return this;
        }
        
        public Builder sxssfRowAccessWindowSize(int windowSize) {
            config.sxssfRowAccessWindowSize = windowSize;
            return this;
        }
        
        public Builder maxCellsForXSSF(long maxCells) {
            config.maxCellsForXSSF = maxCells;
            return this;
        }
        
        public Builder forceStreamingMode(boolean force) {
            config.forceStreamingMode = force;
            return this;
        }
        
        public Builder preferCSVForLargeData(boolean prefer) {
            config.preferCSVForLargeData = prefer;
            return this;
        }
        
        public Builder csvThreshold(long threshold) {
            config.csvThreshold = threshold;
            return this;
        }
        
        public Builder allowXLSFormat(boolean allow) {
            config.allowXLSFormat = allow;
            return this;
        }
        
        public Builder maxRowsForXLS(int maxRows) {
            config.maxRowsForXLS = maxRows;
            return this;
        }
        
        public Builder maxColsForXLS(int maxCols) {
            config.maxColsForXLS = maxCols;
            return this;
        }
        
        public Builder enableRangeValidation(boolean enabled) {
            config.enableRangeValidation = enabled;
            return this;
        }
        
        public Builder minValue(Double minValue) {
            config.minValue = minValue;
            return this;
        }
        
        public Builder maxValue(Double maxValue) {
            config.maxValue = maxValue;
            return this;
        }
        
        public Builder startRow(int startRow) {
            config.startRow = startRow;
            return this;
        }
        
        public ExcelConfig build() {
            return config;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public int getBatchSize() {
        return batchSize;
    }
    
    public long getMemoryThresholdMB() {
        return memoryThresholdMB;
    }
    
    public String getDateFormat() {
        return dateFormat;
    }
    
    public String getDateTimeFormat() {
        return dateTimeFormat;
    }
    
    public String getDelimiter() {
        return delimiter;
    }
    
    public boolean isParallelProcessing() {
        return parallelProcessing;
    }
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public boolean isStrictValidation() {
        return strictValidation;
    }
    
    public boolean isFailOnFirstError() {
        return failOnFirstError;
    }
    
    public boolean isEnableProgressTracking() {
        return enableProgressTracking;
    }
    
    public boolean isEnableMemoryMonitoring() {
        return enableMemoryMonitoring;
    }
    
    public long getProgressReportInterval() {
        return progressReportInterval;
    }
    
    public Set<String> getRequiredFields() {
        return new HashSet<>(requiredFields);
    }
    
    public Set<String> getUniqueFields() {
        return new HashSet<>(uniqueFields);
    }
    
    public Map<String, ValidationRule> getFieldValidationRules() {
        return new HashMap<>(fieldValidationRules);
    }
    
    public List<ValidationRule> getGlobalValidationRules() {
        return new ArrayList<>(globalValidationRules);
    }
    
    public boolean isUseStreamingParser() {
        return useStreamingParser;
    }
    
    public int getMaxErrorsBeforeAbort() {
        return maxErrorsBeforeAbort;
    }
    
    public boolean isEnableDataTypeCache() {
        return enableDataTypeCache;
    }
    
    public boolean isEnableReflectionCache() {
        return enableReflectionCache;
    }
    
    public boolean isEnableRangeValidation() {
        return enableRangeValidation;
    }
    
    public Double getMinValue() {
        return minValue;
    }
    
    public Double getMaxValue() {
        return maxValue;
    }
    
    public int getStartRow() {
        return startRow;
    }
    
    // Excel Strategy Getters
    public long getCellCountThresholdForSXSSF() {
        return cellCountThresholdForSXSSF;
    }
    
    public int getSxssfRowAccessWindowSize() {
        return sxssfRowAccessWindowSize;
    }
    
    public long getMaxCellsForXSSF() {
        return maxCellsForXSSF;
    }
    
    public boolean isForceStreamingMode() {
        return forceStreamingMode;
    }
    
    public boolean isPreferCSVForLargeData() {
        return preferCSVForLargeData;
    }
    
    public long getCsvThreshold() {
        return csvThreshold;
    }
    
    public boolean isAllowXLSFormat() {
        return allowXLSFormat;
    }
    
    public int getMaxRowsForXLS() {
        return maxRowsForXLS;
    }
    
    public int getMaxColsForXLS() {
        return maxColsForXLS;
    }
    
    // Setters (if needed for dynamic configuration)
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    public void setMemoryThresholdMB(long memoryThresholdMB) {
        this.memoryThresholdMB = memoryThresholdMB;
    }
    
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }
    
    public void setDateTimeFormat(String dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
    }
    
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }
    
    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }
    
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }
    
    public void setStrictValidation(boolean strictValidation) {
        this.strictValidation = strictValidation;
    }
    
    public void setFailOnFirstError(boolean failOnFirstError) {
        this.failOnFirstError = failOnFirstError;
    }
    
    public void setEnableProgressTracking(boolean enableProgressTracking) {
        this.enableProgressTracking = enableProgressTracking;
    }
    
    public void setEnableMemoryMonitoring(boolean enableMemoryMonitoring) {
        this.enableMemoryMonitoring = enableMemoryMonitoring;
    }
    
    public void setProgressReportInterval(long progressReportInterval) {
        this.progressReportInterval = progressReportInterval;
    }
    
    public void setUseStreamingParser(boolean useStreamingParser) {
        this.useStreamingParser = useStreamingParser;
    }
    
    public void setMaxErrorsBeforeAbort(int maxErrorsBeforeAbort) {
        this.maxErrorsBeforeAbort = maxErrorsBeforeAbort;
    }
    
    public void setEnableDataTypeCache(boolean enableDataTypeCache) {
        this.enableDataTypeCache = enableDataTypeCache;
    }
    
    public void setEnableReflectionCache(boolean enableReflectionCache) {
        this.enableReflectionCache = enableReflectionCache;
    }
    
    // Additional methods for dynamic configuration
    public void addFieldValidation(String fieldName, ValidationRule rule) {
        this.fieldValidationRules.put(fieldName, rule);
    }
    
    public void addGlobalValidation(ValidationRule rule) {
        this.globalValidationRules.add(rule);
    }
    
    @Override
    public String toString() {
        return "ExcelConfig{" +
                "batchSize=" + batchSize +
                ", memoryThresholdMB=" + memoryThresholdMB +
                ", dateFormat='" + dateFormat + '\'' +
                ", dateTimeFormat='" + dateTimeFormat + '\'' +
                ", delimiter='" + delimiter + '\'' +
                ", parallelProcessing=" + parallelProcessing +
                ", threadPoolSize=" + threadPoolSize +
                ", strictValidation=" + strictValidation +
                ", failOnFirstError=" + failOnFirstError +
                ", enableProgressTracking=" + enableProgressTracking +
                ", enableMemoryMonitoring=" + enableMemoryMonitoring +
                ", progressReportInterval=" + progressReportInterval +
                ", requiredFields=" + requiredFields +
                ", uniqueFields=" + uniqueFields +
                ", useStreamingParser=" + useStreamingParser +
                ", maxErrorsBeforeAbort=" + maxErrorsBeforeAbort +
                ", enableDataTypeCache=" + enableDataTypeCache +
                ", enableReflectionCache=" + enableReflectionCache +
                '}';
    }
}