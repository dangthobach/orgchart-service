package com.learnmore.application.utils.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Simplified real-time monitoring and metrics collection for Excel processing
 * Built-in metrics without external dependencies for production monitoring
 */
@Component
public class SimpleExcelProcessingMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleExcelProcessingMetrics.class);
    
    // Counters for tracking processing volumes
    private final AtomicLong processedRecordsCounter = new AtomicLong(0);
    private final AtomicLong processedFilesCounter = new AtomicLong(0);
    private final AtomicLong processingErrorsCounter = new AtomicLong(0);
    private final AtomicLong validationErrorsCounter = new AtomicLong(0);
    
    // Real-time gauges
    private final AtomicLong activeProcessingTasks = new AtomicLong(0);
    private final AtomicLong currentMemoryUsage = new AtomicLong(0);
    private final AtomicLong recordsPerSecond = new AtomicLong(0);
    
    // Performance tracking
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong totalBatchCount = new AtomicLong(0);
    private final AtomicLong totalBatchSize = new AtomicLong(0);
    private final AtomicLong totalFileSize = new AtomicLong(0);
    
    // Error tracking by type
    private final Map<String, AtomicLong> errorsByType = new ConcurrentHashMap<>();
    
    // Processing start times for active tasks
    private final Map<String, Long> activeTaskStartTimes = new ConcurrentHashMap<>();
    
    public SimpleExcelProcessingMetrics() {
        logger.info("Excel processing metrics initialized with built-in counters");
    }
    
    // =======================================================================================
    // PROCESSING METRICS TRACKING
    // =======================================================================================
    
    /**
     * Record the start of a processing task
     */
    public String startProcessingTimer() {
        String taskId = "task-" + System.nanoTime();
        activeTaskStartTimes.put(taskId, System.currentTimeMillis());
        activeProcessingTasks.incrementAndGet();
        
        logger.debug("Started processing task: {}", taskId);
        return taskId;
    }
    
    /**
     * Record the completion of a processing task
     */
    public void endProcessingTimer(String taskId, long recordsProcessed) {
        Long startTime = activeTaskStartTimes.remove(taskId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            totalProcessingTimeMs.addAndGet(duration);
            
            processedRecordsCounter.addAndGet(recordsProcessed);
            processedFilesCounter.incrementAndGet();
            activeProcessingTasks.decrementAndGet();
            
            // Calculate and update records per second
            if (duration > 0) {
                long rps = recordsProcessed * 1000 / duration;
                recordsPerSecond.set(rps);
            }
            
            logger.debug("Processing task {} completed: {} records in {}ms", taskId, recordsProcessed, duration);
        }
    }
    
    /**
     * Record database operation timing
     */
    public void recordDatabaseOperation(long durationMs, int recordCount) {
        // Could add database-specific metrics here
        logger.debug("Database operation: {} records in {}ms", recordCount, durationMs);
    }
    
    /**
     * Record validation timing and results
     */
    public void recordValidation(long durationMs, int validRecords, int invalidRecords) {
        if (invalidRecords > 0) {
            validationErrorsCounter.addAndGet(invalidRecords);
        }
        
        logger.debug("Validation completed: {}/{} valid records in {}ms", 
                validRecords, validRecords + invalidRecords, durationMs);
    }
    
    /**
     * Record processing error
     */
    public void recordProcessingError(String errorType, Exception exception) {
        processingErrorsCounter.incrementAndGet();
        
        // Track errors by type
        errorsByType.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        
        logger.warn("Processing error recorded: {} - {}", errorType, exception.getMessage());
    }
    
    /**
     * Record batch processing metrics
     */
    public void recordBatchProcessing(int batchSize, long processingTimeMs) {
        totalBatchCount.incrementAndGet();
        totalBatchSize.addAndGet(batchSize);
        
        if (processingTimeMs > 0) {
            double batchRecordsPerSecond = batchSize * 1000.0 / processingTimeMs;
            
            // Alert if batch processing is too slow
            if (batchRecordsPerSecond < 500) {
                logger.warn("Slow batch processing detected: {:.2f} records/sec for batch size {}", 
                        batchRecordsPerSecond, batchSize);
            }
        }
    }
    
    /**
     * Record file size for analysis
     */
    public void recordFileSize(long fileSizeBytes) {
        totalFileSize.addAndGet(fileSizeBytes);
    }
    
    /**
     * Update memory usage gauge
     */
    public void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        currentMemoryUsage.set(usedMemory);
        
        // Alert if memory usage is high
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        if (memoryUsagePercent > 80) {
            logger.warn("High memory usage detected: {:.1f}% ({} MB)", 
                    memoryUsagePercent, usedMemory / 1024 / 1024);
        }
    }
    
    // =======================================================================================
    // REAL-TIME MONITORING HELPERS
    // =======================================================================================
    
    /**
     * Get current processing statistics for dashboards
     */
    public ProcessingStatistics getCurrentStatistics() {
        double avgProcessingTimeMs = processedFilesCounter.get() > 0 ? 
                (double) totalProcessingTimeMs.get() / processedFilesCounter.get() : 0;
        
        double avgBatchSize = totalBatchCount.get() > 0 ? 
                (double) totalBatchSize.get() / totalBatchCount.get() : 0;
        
        double avgFileSizeBytes = processedFilesCounter.get() > 0 ? 
                (double) totalFileSize.get() / processedFilesCounter.get() : 0;
        
        return new ProcessingStatistics(
                processedRecordsCounter.get(),
                processedFilesCounter.get(),
                processingErrorsCounter.get(),
                validationErrorsCounter.get(),
                activeProcessingTasks.get(),
                recordsPerSecond.get(),
                currentMemoryUsage.get(),
                avgProcessingTimeMs,
                avgBatchSize,
                avgFileSizeBytes,
                new ConcurrentHashMap<>(errorsByType));
    }
    
    /**
     * Reset all metrics (useful for testing or periodic resets)
     */
    public void resetMetrics() {
        processedRecordsCounter.set(0);
        processedFilesCounter.set(0);
        processingErrorsCounter.set(0);
        validationErrorsCounter.set(0);
        activeProcessingTasks.set(0);
        recordsPerSecond.set(0);
        currentMemoryUsage.set(0);
        totalProcessingTimeMs.set(0);
        totalBatchCount.set(0);
        totalBatchSize.set(0);
        totalFileSize.set(0);
        errorsByType.clear();
        activeTaskStartTimes.clear();
        
        logger.info("All Excel processing metrics reset");
    }
    
    // =======================================================================================
    // PERFORMANCE ALERTING
    // =======================================================================================
    
    /**
     * Check performance thresholds and log alerts
     */
    public void checkPerformanceThresholds() {
        ProcessingStatistics stats = getCurrentStatistics();
        
        // Check processing rate
        if (stats.getRecordsPerSecond() > 0 && stats.getRecordsPerSecond() < 1000) {
            logger.warn("LOW PROCESSING RATE ALERT: {} records/sec", stats.getRecordsPerSecond());
        }
        
        // Check error rate
        double errorRate = stats.getTotalRecords() > 0 ? 
                (double) stats.getTotalErrors() / stats.getTotalRecords() * 100 : 0;
        if (errorRate > 5.0) {
            logger.warn("HIGH ERROR RATE ALERT: {:.2f}%", errorRate);
        }
        
        // Check active tasks
        if (stats.getActiveTasks() > 10) {
            logger.warn("HIGH CONCURRENT PROCESSING ALERT: {} active tasks", stats.getActiveTasks());
        }
        
        // Check memory usage
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (double) stats.getMemoryUsageBytes() / maxMemory * 100;
        if (memoryUsagePercent > 85) {
            logger.warn("HIGH MEMORY USAGE ALERT: {:.1f}%", memoryUsagePercent);
        }
    }
    
    /**
     * Log comprehensive metrics report
     */
    public void logMetricsReport() {
        ProcessingStatistics stats = getCurrentStatistics();
        
        logger.info("=== EXCEL PROCESSING METRICS REPORT ===");
        logger.info("Total records processed: {}", stats.getTotalRecords());
        logger.info("Total files processed: {}", stats.getTotalFiles());
        logger.info("Total errors: {} (validation: {})", stats.getTotalErrors(), stats.getValidationErrors());
        logger.info("Active tasks: {}", stats.getActiveTasks());
        logger.info("Current processing rate: {} records/sec", stats.getRecordsPerSecond());
        logger.info("Memory usage: {:.1f} MB", stats.getMemoryUsageBytes() / 1024.0 / 1024.0);
        logger.info("Average processing time: {:.2f}ms", stats.getAvgProcessingTimeMs());
        logger.info("Average batch size: {:.1f}", stats.getAvgBatchSize());
        logger.info("Average file size: {:.1f} KB", stats.getAvgFileSizeBytes() / 1024.0);
        
        // Log errors by type
        if (!stats.getErrorsByType().isEmpty()) {
            logger.info("Errors by type:");
            stats.getErrorsByType().forEach((type, count) -> 
                    logger.info("  {}: {}", type, count.get()));
        }
        
        logger.info("==========================================");
    }
    
    /**
     * Processing statistics for monitoring dashboards
     */
    public static class ProcessingStatistics {
        private final long totalRecords;
        private final long totalFiles;
        private final long totalErrors;
        private final long validationErrors;
        private final long activeTasks;
        private final long recordsPerSecond;
        private final long memoryUsageBytes;
        private final double avgProcessingTimeMs;
        private final double avgBatchSize;
        private final double avgFileSizeBytes;
        private final Map<String, AtomicLong> errorsByType;
        
        public ProcessingStatistics(long totalRecords, long totalFiles, long totalErrors, 
                                  long validationErrors, long activeTasks, long recordsPerSecond,
                                  long memoryUsageBytes, double avgProcessingTimeMs, 
                                  double avgBatchSize, double avgFileSizeBytes,
                                  Map<String, AtomicLong> errorsByType) {
            this.totalRecords = totalRecords;
            this.totalFiles = totalFiles;
            this.totalErrors = totalErrors;
            this.validationErrors = validationErrors;
            this.activeTasks = activeTasks;
            this.recordsPerSecond = recordsPerSecond;
            this.memoryUsageBytes = memoryUsageBytes;
            this.avgProcessingTimeMs = avgProcessingTimeMs;
            this.avgBatchSize = avgBatchSize;
            this.avgFileSizeBytes = avgFileSizeBytes;
            this.errorsByType = errorsByType;
        }
        
        // Getters
        public long getTotalRecords() { return totalRecords; }
        public long getTotalFiles() { return totalFiles; }
        public long getTotalErrors() { return totalErrors; }
        public long getValidationErrors() { return validationErrors; }
        public long getActiveTasks() { return activeTasks; }
        public long getRecordsPerSecond() { return recordsPerSecond; }
        public long getMemoryUsageBytes() { return memoryUsageBytes; }
        public double getAvgProcessingTimeMs() { return avgProcessingTimeMs; }
        public double getAvgBatchSize() { return avgBatchSize; }
        public double getAvgFileSizeBytes() { return avgFileSizeBytes; }
        public Map<String, AtomicLong> getErrorsByType() { return errorsByType; }
        
        @Override
        public String toString() {
            return String.format(
                "Excel Processing Real-time Statistics:\n" +
                "  Total records processed: %d\n" +
                "  Total files processed: %d\n" +
                "  Total errors: %d\n" +
                "  Validation errors: %d\n" +
                "  Active tasks: %d\n" +
                "  Current rate: %d records/sec\n" +
                "  Memory usage: %.1f MB\n" +
                "  Avg processing time: %.2fms\n" +
                "  Avg batch size: %.1f\n" +
                "  Avg file size: %.1f KB",
                totalRecords, totalFiles, totalErrors, validationErrors,
                activeTasks, recordsPerSecond, memoryUsageBytes / 1024.0 / 1024.0,
                avgProcessingTimeMs, avgBatchSize, avgFileSizeBytes / 1024.0);
        }
    }
}