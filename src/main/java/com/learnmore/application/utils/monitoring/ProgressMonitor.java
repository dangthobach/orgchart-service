package com.learnmore.application.utils.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Progress monitoring system for Excel processing operations
 * Provides real-time progress tracking with configurable reporting intervals
 */
public class ProgressMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ProgressMonitor.class);
    
    private final AtomicLong totalRecords = new AtomicLong(0);
    private final AtomicLong processedRecords = new AtomicLong(0);
    private final AtomicLong successfulRecords = new AtomicLong(0);
    private final AtomicLong errorRecords = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(0);
    private final AtomicReference<ProcessingStage> currentStage = new AtomicReference<>(ProcessingStage.INITIALIZING);
    
    private final long reportingInterval;
    private final Consumer<ProgressReport> progressCallback;
    
    private volatile boolean isActive = false;
    private volatile long lastReportTime = 0;
    private volatile long lastReportedRecords = 0;
    
    public ProgressMonitor(long reportingInterval) {
        this(reportingInterval, null);
    }
    
    public ProgressMonitor(long reportingInterval, Consumer<ProgressReport> progressCallback) {
        this.reportingInterval = reportingInterval;
        this.progressCallback = progressCallback;
    }
    
    /**
     * Start monitoring with total expected records
     */
    public void start(long totalRecords) {
        this.totalRecords.set(totalRecords);
        this.processedRecords.set(0);
        this.successfulRecords.set(0);
        this.errorRecords.set(0);
        this.startTime.set(System.currentTimeMillis());
        this.currentStage.set(ProcessingStage.PROCESSING);
        this.isActive = true;
        this.lastReportTime = System.currentTimeMillis();
        this.lastReportedRecords = 0;
        
        logger.info("Progress monitoring started for {} records", totalRecords);
    }
    
    /**
     * Update progress with successful record processing
     */
    public void recordSuccess() {
        if (!isActive) return;
        
        long processed = processedRecords.incrementAndGet();
        successfulRecords.incrementAndGet();
        
        checkAndReport(processed);
    }
    
    /**
     * Update progress with error record processing
     */
    public void recordError() {
        if (!isActive) return;
        
        long processed = processedRecords.incrementAndGet();
        errorRecords.incrementAndGet();
        
        checkAndReport(processed);
    }
    
    /**
     * Update progress with batch processing
     */
    public void recordBatch(int batchSize, int successCount, int errorCount) {
        if (!isActive) return;
        
        long processed = processedRecords.addAndGet(batchSize);
        successfulRecords.addAndGet(successCount);
        errorRecords.addAndGet(errorCount);
        
        checkAndReport(processed);
    }
    
    /**
     * Set current processing stage
     */
    public void setStage(ProcessingStage stage) {
        ProcessingStage oldStage = currentStage.getAndSet(stage);
        if (oldStage != stage) {
            logger.info("Processing stage changed: {} -> {}", oldStage, stage);
        }
    }
    
    /**
     * Check if report should be generated and send it
     */
    private void checkAndReport(long currentProcessed) {
        long currentTime = System.currentTimeMillis();
        
        // Check if we should report based on interval or record count
        boolean shouldReport = false;
        
        if (reportingInterval > 0) {
            // Time-based reporting
            if (currentTime - lastReportTime >= reportingInterval * 1000) {
                shouldReport = true;
            }
        } else {
            // Record count-based reporting
            long intervalRecords = Math.abs(reportingInterval);
            if (currentProcessed - lastReportedRecords >= intervalRecords) {
                shouldReport = true;
            }
        }
        
        // Always report on completion
        if (currentProcessed >= totalRecords.get()) {
            shouldReport = true;
        }
        
        if (shouldReport) {
            ProgressReport report = generateReport();
            reportProgress(report);
            
            lastReportTime = currentTime;
            lastReportedRecords = currentProcessed;
        }
    }
    
    /**
     * Generate current progress report
     */
    public ProgressReport generateReport() {
        long current = processedRecords.get();
        long total = totalRecords.get();
        long successful = successfulRecords.get();
        long errors = errorRecords.get();
        long elapsed = System.currentTimeMillis() - startTime.get();
        
        double progressPercentage = total > 0 ? (double) current * 100.0 / total : 0.0;
        double recordsPerSecond = elapsed > 0 ? (double) current * 1000.0 / elapsed : 0.0;
        
        // Estimate remaining time
        long estimatedRemainingMs = 0;
        if (current > 0 && recordsPerSecond > 0) {
            long remaining = total - current;
            estimatedRemainingMs = (long) (remaining / recordsPerSecond * 1000);
        }
        
        return new ProgressReport(
            current, total, successful, errors,
            progressPercentage, recordsPerSecond,
            elapsed, estimatedRemainingMs,
            currentStage.get()
        );
    }
    
    /**
     * Report progress via callback and logging
     */
    private void reportProgress(ProgressReport report) {
        // Log progress
        logger.info("Progress: {} / {} records ({}%) - Success: {}, Errors: {} - Speed: {} rec/sec - ETA: {}",
            report.getProcessedRecords(), report.getTotalRecords(), 
            String.format("%.2f", report.getProgressPercentage()),
            report.getSuccessfulRecords(), report.getErrorRecords(),
            String.format("%.2f", report.getRecordsPerSecond()),
            formatDuration(report.getEstimatedRemainingMs())
        );
        
        // Call external callback if provided
        if (progressCallback != null) {
            try {
                progressCallback.accept(report);
            } catch (Exception e) {
                logger.warn("Error in progress callback: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Complete monitoring
     */
    public ProgressReport complete() {
        setStage(ProcessingStage.COMPLETED);
        
        ProgressReport finalReport = generateReport();
        reportProgress(finalReport);
        
        isActive = false;
        
        logger.info("Progress monitoring completed. Final stats: {}", finalReport);
        return finalReport;
    }
    
    /**
     * Abort monitoring due to error
     */
    public ProgressReport abort(String reason) {
        setStage(ProcessingStage.ABORTED);
        
        ProgressReport finalReport = generateReport();
        
        logger.error("Progress monitoring aborted: {}. Final stats: {}", reason, finalReport);
        
        isActive = false;
        return finalReport;
    }
    
    /**
     * Format duration in human-readable format
     */
    private String formatDuration(long milliseconds) {
        if (milliseconds < 0) return "Unknown";
        
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    // Getters
    public boolean isActive() {
        return isActive;
    }
    
    public long getTotalRecords() {
        return totalRecords.get();
    }
    
    public long getProcessedRecords() {
        return processedRecords.get();
    }
    
    public long getSuccessfulRecords() {
        return successfulRecords.get();
    }
    
    public long getErrorRecords() {
        return errorRecords.get();
    }
    
    public ProcessingStage getCurrentStage() {
        return currentStage.get();
    }
    
    /**
     * Processing stages
     */
    public enum ProcessingStage {
        INITIALIZING("Initializing"),
        READING_HEADERS("Reading Headers"),
        VALIDATING("Validating Data"),
        PROCESSING("Processing Records"),
        WRITING("Writing Results"),
        COMPLETED("Completed"),
        ABORTED("Aborted");
        
        private final String description;
        
        ProcessingStage(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }
    
    /**
     * Progress report data structure
     */
    public static class ProgressReport {
        private final long processedRecords;
        private final long totalRecords;
        private final long successfulRecords;
        private final long errorRecords;
        private final double progressPercentage;
        private final double recordsPerSecond;
        private final long elapsedTimeMs;
        private final long estimatedRemainingMs;
        private final ProcessingStage stage;
        
        public ProgressReport(long processedRecords, long totalRecords, 
                            long successfulRecords, long errorRecords,
                            double progressPercentage, double recordsPerSecond,
                            long elapsedTimeMs, long estimatedRemainingMs,
                            ProcessingStage stage) {
            this.processedRecords = processedRecords;
            this.totalRecords = totalRecords;
            this.successfulRecords = successfulRecords;
            this.errorRecords = errorRecords;
            this.progressPercentage = progressPercentage;
            this.recordsPerSecond = recordsPerSecond;
            this.elapsedTimeMs = elapsedTimeMs;
            this.estimatedRemainingMs = estimatedRemainingMs;
            this.stage = stage;
        }
        
        // Getters
        public long getProcessedRecords() { return processedRecords; }
        public long getTotalRecords() { return totalRecords; }
        public long getSuccessfulRecords() { return successfulRecords; }
        public long getErrorRecords() { return errorRecords; }
        public double getProgressPercentage() { return progressPercentage; }
        public double getRecordsPerSecond() { return recordsPerSecond; }
        public long getElapsedTimeMs() { return elapsedTimeMs; }
        public long getEstimatedRemainingMs() { return estimatedRemainingMs; }
        public ProcessingStage getStage() { return stage; }
        
        public boolean isCompleted() {
            return processedRecords >= totalRecords || stage == ProcessingStage.COMPLETED;
        }
        
        public double getErrorRate() {
            return processedRecords > 0 ? (double) errorRecords / processedRecords : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ProgressReport{processed=%d/%d (%.2f%%), success=%d, errors=%d, " +
                    "speed=%.2f rec/sec, elapsed=%dms, eta=%dms, stage=%s}",
                    processedRecords, totalRecords, progressPercentage,
                    successfulRecords, errorRecords, recordsPerSecond,
                    elapsedTimeMs, estimatedRemainingMs, stage);
        }
    }
}