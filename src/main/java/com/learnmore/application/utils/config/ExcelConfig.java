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
    // Note: Removed useStreamingParser - TrueStreamingSAXProcessor always uses SAX streaming
    // Note: Removed enableDataTypeCache - TypeConverter singleton always caches internally
    // Note: Removed enableReflectionCache - MethodHandleMapper/ReflectionCache always cache
    // Note: Removed enableMemoryGC - MemoryMonitor automatically triggers GC when CRITICAL
    // Note: Removed memoryCheckInterval - MemoryMonitor uses fixed 5-second interval
    private int maxErrorsBeforeAbort = 500; // Lower for production - fail fast
    
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

    // Row count validation - PERFORMANCE OPTIMIZED
    private int maxRows = 0; // Maximum rows to process (0 = no limit). Validated during streaming.
    
    // Range validation - REMOVED: Use field-specific ValidationRule instead
    // Example: config.addFieldValidation("score", new NumericRangeValidator(0.0, 100.0))
    
    // Processing configuration
    private int startRow = 0; // 0-based index for header row
    private boolean autoSizeColumns = true; // Default auto-size columns for better presentation
    private String jobId; // Job ID for tracking processing tasks
    
    // POI Performance Optimizations - Based on benchmark analysis
    private boolean disableAutoSizing = false; // Major performance impact for large datasets
    private boolean useSharedStrings = true; // Memory vs speed tradeoff (default true for memory efficiency)
    private boolean compressOutput = true; // Enable compression for smaller files (slight performance cost)
    private int flushInterval = 1000; // Periodic flushing interval for SXSSF (records)
    private boolean enableCellStyleOptimization = true; // Reuse cell styles to reduce memory
    private boolean minimizeMemoryFootprint = true; // Aggressive memory optimizations

    // Multi-Sheet Support
    private boolean readAllSheets = false; // Read all sheets or just first
    private List<String> sheetNames; // Specific sheet names to read
    private int sheetCount = 1; // Number of sheets processed (tracked during execution)

    // Caching Support
    private boolean enableCaching = false; // Enable caching of parsed objects
    private long cacheTTLSeconds = 3600; // Cache TTL in seconds (1 hour default)
    private int cacheMaxSize = 1000; // Maximum cache entries

    // Template Support
    private String templatePath; // Path to Excel template file

    // Style Support
    private Object styleTemplate; // Custom style template (can be any type)

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

        // REMOVED: useStreamingParser - TrueStreamingSAXProcessor always uses SAX streaming
        // REMOVED: enableDataTypeCache - TypeConverter always caches (singleton)
        // REMOVED: enableReflectionCache - MethodHandleMapper always caches

        public Builder maxErrorsBeforeAbort(int maxErrors) {
            config.maxErrorsBeforeAbort = maxErrors;
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

        public Builder maxRows(int maxRows) {
            config.maxRows = maxRows;
            return this;
        }

        // REMOVED: enableRangeValidation, minValue, maxValue
        // Use field-specific validation instead:
        // config.addFieldValidation("fieldName", new NumericRangeValidator(min, max))
        
        public Builder startRow(int startRow) {
            config.startRow = startRow;
            return this;
        }
        
        public Builder autoSizeColumns(boolean autoSize) {
            config.autoSizeColumns = autoSize;
            return this;
        }
        
        // POI Performance Optimizations - Based on benchmark findings
        public Builder disableAutoSizing(boolean disabled) {
            config.disableAutoSizing = disabled;
            return this;
        }
        
        public Builder useSharedStrings(boolean enabled) {
            config.useSharedStrings = enabled;
            return this;
        }
        
        public Builder compressOutput(boolean enabled) {
            config.compressOutput = enabled;
            return this;
        }
        
        public Builder flushInterval(int interval) {
            config.flushInterval = interval;
            return this;
        }
        
        public Builder enableCellStyleOptimization(boolean enabled) {
            config.enableCellStyleOptimization = enabled;
            return this;
        }
        
        public Builder minimizeMemoryFootprint(boolean enabled) {
            config.minimizeMemoryFootprint = enabled;
            return this;
        }
        
        public Builder jobId(String jobId) {
            config.jobId = jobId;
            return this;
        }

        // Multi-Sheet Support Builder Methods
        public Builder readAllSheets(boolean readAllSheets) {
            config.readAllSheets = readAllSheets;
            return this;
        }

        public Builder sheetNames(List<String> sheetNames) {
            config.sheetNames = sheetNames;
            return this;
        }

        public Builder sheetCount(int sheetCount) {
            config.sheetCount = sheetCount;
            return this;
        }

        // Caching Support Builder Methods
        public Builder enableCaching(boolean enableCaching) {
            config.enableCaching = enableCaching;
            return this;
        }

        public Builder cacheTTLSeconds(long cacheTTLSeconds) {
            config.cacheTTLSeconds = cacheTTLSeconds;
            return this;
        }

        public Builder cacheMaxSize(int cacheMaxSize) {
            config.cacheMaxSize = cacheMaxSize;
            return this;
        }

        // Template Support Builder Method
        public Builder templatePath(String templatePath) {
            config.templatePath = templatePath;
            return this;
        }

        // Style Support Builder Method
        public Builder styleTemplate(Object styleTemplate) {
            config.styleTemplate = styleTemplate;
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

    // REMOVED getters: isUseStreamingParser, isEnableDataTypeCache, isEnableReflectionCache
    // REMOVED getters: isEnableRangeValidation, getMinValue, getMaxValue
    // Reason: Caching is always enabled internally, range validation moved to ValidationRule

    public int getMaxErrorsBeforeAbort() {
        return maxErrorsBeforeAbort;
    }
    
    public int getStartRow() {
        return startRow;
    }
    
    public boolean isAutoSizeColumns() {
        return autoSizeColumns;
    }
    
    // POI Performance Optimization Getters - Based on benchmark analysis
    public boolean isDisableAutoSizing() {
        return disableAutoSizing;
    }
    
    public boolean isUseSharedStrings() {
        return useSharedStrings;
    }
    
    public boolean isCompressOutput() {
        return compressOutput;
    }
    
    public int getFlushInterval() {
        return flushInterval;
    }
    
    public boolean isEnableCellStyleOptimization() {
        return enableCellStyleOptimization;
    }
    
    public boolean isMinimizeMemoryFootprint() {
        return minimizeMemoryFootprint;
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

    public int getMaxRows() {
        return maxRows;
    }

    public String getJobId() {
        return jobId;
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
    
    public void setAutoSizeColumns(boolean autoSizeColumns) {
        this.autoSizeColumns = autoSizeColumns;
    }

    // REMOVED setters: setUseStreamingParser, setEnableDataTypeCache, setEnableReflectionCache
    // Reason: These fields have been removed from configuration

    public void setMaxErrorsBeforeAbort(int maxErrorsBeforeAbort) {
        this.maxErrorsBeforeAbort = maxErrorsBeforeAbort;
    }
    
    // Additional methods for dynamic configuration
    public void addFieldValidation(String fieldName, ValidationRule rule) {
        this.fieldValidationRules.put(fieldName, rule);
    }
    
    public void addGlobalValidation(ValidationRule rule) {
        this.globalValidationRules.add(rule);
    }

    // Multi-Sheet Support Getters
    public boolean isReadAllSheets() {
        return readAllSheets;
    }

    public List<String> getSheetNames() {
        return sheetNames;
    }

    public int getSheetCount() {
        return sheetCount;
    }

    // Caching Support Getters
    public boolean isEnableCaching() {
        return enableCaching;
    }

    public long getCacheTTLSeconds() {
        return cacheTTLSeconds;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    // Template Support Getter
    public String getTemplatePath() {
        return templatePath;
    }

    // Style Support Getter
    public Object getStyleTemplate() {
        return styleTemplate;
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
                ", maxErrorsBeforeAbort=" + maxErrorsBeforeAbort +
                // Removed: useStreamingParser, enableDataTypeCache, enableReflectionCache (always enabled)
                '}';
    }
}