package com.learnmore.application.utils.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Memory monitoring system for Excel processing operations
 * Tracks memory usage, GC activity, and provides early warning for memory issues
 */
public class MemoryMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitor.class);
    
    private final MemoryMXBean memoryBean;
    private final Runtime runtime;
    private final ScheduledExecutorService scheduler;
    
    // Configuration
    private final long memoryThresholdBytes;
    private final long heapThresholdBytes;
    private final long monitoringIntervalMs;
    private final Consumer<MemoryAlert> alertCallback;
    
    // Statistics
    private final AtomicLong maxHeapUsed = new AtomicLong(0);
    private final AtomicLong maxNonHeapUsed = new AtomicLong(0);
    private final AtomicLong totalGCTime = new AtomicLong(0);
    private final AtomicLong totalGCCount = new AtomicLong(0);
    
    // State
    private volatile boolean isMonitoring = false;
    private volatile MemoryStatus lastStatus = MemoryStatus.NORMAL;
    
    public MemoryMonitor(long memoryThresholdMB) {
        this(memoryThresholdMB, 5000, null); // Default 5 second monitoring interval
    }
    
    public MemoryMonitor(long memoryThresholdMB, long monitoringIntervalMs, Consumer<MemoryAlert> alertCallback) {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.runtime = Runtime.getRuntime();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MemoryMonitor");
            t.setDaemon(true);
            return t;
        });
        
        this.memoryThresholdBytes = memoryThresholdMB * 1024 * 1024;
        this.heapThresholdBytes = (long) (runtime.maxMemory() * 0.8); // 80% of max heap
        this.monitoringIntervalMs = monitoringIntervalMs;
        this.alertCallback = alertCallback;
        
        initializeGCStats();
    }
    
    /**
     * Start memory monitoring
     */
    public void startMonitoring() {
        if (isMonitoring) {
            logger.warn("Memory monitoring is already active");
            return;
        }
        
        isMonitoring = true;
        
        scheduler.scheduleAtFixedRate(this::checkMemoryStatus, 
            0, monitoringIntervalMs, TimeUnit.MILLISECONDS);
        
        logger.info("Memory monitoring started with threshold: {} MB, interval: {} ms", 
            memoryThresholdBytes / (1024 * 1024), monitoringIntervalMs);
    }
    
    /**
     * Stop memory monitoring
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        isMonitoring = false;
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Memory monitoring stopped");
    }
    
    /**
     * Check current memory status and trigger alerts if needed
     */
    private void checkMemoryStatus() {
        try {
            MemoryReport report = generateReport();
            MemoryStatus currentStatus = determineStatus(report);
            
            // Update peak usage
            maxHeapUsed.set(Math.max(maxHeapUsed.get(), report.getHeapUsed()));
            maxNonHeapUsed.set(Math.max(maxNonHeapUsed.get(), report.getNonHeapUsed()));
            
            // Check for status changes
            if (currentStatus != lastStatus) {
                handleStatusChange(lastStatus, currentStatus, report);
                lastStatus = currentStatus;
            }
            
            // Log periodic status (only if not normal or every 10 checks)
            if (currentStatus != MemoryStatus.NORMAL || 
                System.currentTimeMillis() % (monitoringIntervalMs * 10) < monitoringIntervalMs) {
                logger.debug("Memory status: {} - {}", currentStatus, report);
            }
            
        } catch (Exception e) {
            logger.error("Error during memory monitoring: {}", e.getMessage());
        }
    }
    
    /**
     * Generate current memory report
     */
    public MemoryReport generateReport() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax();
        long heapCommitted = heapUsage.getCommitted();
        
        long nonHeapUsed = nonHeapUsage.getUsed();
        long nonHeapMax = nonHeapUsage.getMax();
        long nonHeapCommitted = nonHeapUsage.getCommitted();
        
        // Runtime memory info
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // GC statistics
        updateGCStats();
        long gcTime = totalGCTime.get();
        long gcCount = totalGCCount.get();
        
        double heapUsagePercentage = heapMax > 0 ? (double) heapUsed / heapMax * 100.0 : 0.0;
        double totalUsagePercentage = maxMemory > 0 ? (double) usedMemory / maxMemory * 100.0 : 0.0;
        
        return new MemoryReport(
            heapUsed, heapMax, heapCommitted, heapUsagePercentage,
            nonHeapUsed, nonHeapMax, nonHeapCommitted,
            usedMemory, totalMemory, maxMemory, totalUsagePercentage,
            gcTime, gcCount
        );
    }
    
    /**
     * Determine memory status based on current usage
     */
    private MemoryStatus determineStatus(MemoryReport report) {
        long heapUsed = report.getHeapUsed();
        double heapPercentage = report.getHeapUsagePercentage();
        double totalPercentage = report.getTotalUsagePercentage();
        
        // Critical: Very high memory usage
        if (heapUsed > heapThresholdBytes * 0.95 || heapPercentage > 95.0 || totalPercentage > 95.0) {
            return MemoryStatus.CRITICAL;
        }
        
        // Warning: High memory usage
        if (heapUsed > heapThresholdBytes || heapPercentage > 80.0 || totalPercentage > 80.0) {
            return MemoryStatus.WARNING;
        }
        
        // Elevated: Above normal threshold
        if (heapUsed > memoryThresholdBytes || heapPercentage > 60.0 || totalPercentage > 60.0) {
            return MemoryStatus.ELEVATED;
        }
        
        return MemoryStatus.NORMAL;
    }
    
    /**
     * Handle memory status changes
     */
    private void handleStatusChange(MemoryStatus oldStatus, MemoryStatus newStatus, MemoryReport report) {
        logger.info("Memory status changed: {} -> {} - {}", oldStatus, newStatus, report);
        
        MemoryAlert alert = new MemoryAlert(oldStatus, newStatus, report, System.currentTimeMillis());
        
        // Trigger callback if provided
        if (alertCallback != null) {
            try {
                alertCallback.accept(alert);
            } catch (Exception e) {
                logger.error("Error in memory alert callback: {}", e.getMessage());
            }
        }
        
        // Automatic actions based on status
        switch (newStatus) {
            case NORMAL:
                // No action needed
                break;
            case ELEVATED:
                logger.info("Memory usage is elevated. Monitoring closely.");
                break;
            case WARNING:
                logger.warn("Memory usage is high. Consider optimizing batch size or processing strategy.");
                break;
            case CRITICAL:
                logger.error("Memory usage is critical! Processing may fail. Suggesting garbage collection.");
                System.gc(); // Force garbage collection
                break;
        }
    }
    
    /**
     * Initialize GC statistics
     */
    private void initializeGCStats() {
        updateGCStats();
        // Initialize baseline GC stats
    }
    
    /**
     * Update GC statistics from MX beans
     */
    private void updateGCStats() {
        long totalTime = 0;
        long totalCount = 0;
        
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = gcBean.getCollectionTime();
            long count = gcBean.getCollectionCount();
            
            if (time >= 0) totalTime += time;
            if (count >= 0) totalCount += count;
        }
        
        totalGCTime.set(totalTime);
        totalGCCount.set(totalCount);
    }
    
    /**
     * Force garbage collection and return memory freed
     */
    public long forceGarbageCollection() {
        MemoryReport beforeGC = generateReport();
        
        logger.info("Forcing garbage collection. Memory before GC: {} MB used", 
            beforeGC.getHeapUsed() / (1024 * 1024));
        
        System.gc();
        
        // Wait a bit for GC to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        MemoryReport afterGC = generateReport();
        long memoryFreed = beforeGC.getHeapUsed() - afterGC.getHeapUsed();
        
        logger.info("Garbage collection completed. Memory after GC: {} MB used, freed: {} MB", 
            afterGC.getHeapUsed() / (1024 * 1024), memoryFreed / (1024 * 1024));
        
        return memoryFreed;
    }
    
    // Getters
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    public MemoryStatus getCurrentStatus() {
        return lastStatus;
    }
    
    public long getMaxHeapUsed() {
        return maxHeapUsed.get();
    }
    
    public long getMaxNonHeapUsed() {
        return maxNonHeapUsed.get();
    }
    
    /**
     * Memory status levels
     */
    public enum MemoryStatus {
        NORMAL("Normal"),
        ELEVATED("Elevated"),
        WARNING("Warning"),
        CRITICAL("Critical");
        
        private final String description;
        
        MemoryStatus(String description) {
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
     * Memory alert data structure
     */
    public static class MemoryAlert {
        private final MemoryStatus oldStatus;
        private final MemoryStatus newStatus;
        private final MemoryReport memoryReport;
        private final long timestamp;
        
        public MemoryAlert(MemoryStatus oldStatus, MemoryStatus newStatus, 
                          MemoryReport memoryReport, long timestamp) {
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
            this.memoryReport = memoryReport;
            this.timestamp = timestamp;
        }
        
        public MemoryStatus getOldStatus() { return oldStatus; }
        public MemoryStatus getNewStatus() { return newStatus; }
        public MemoryReport getMemoryReport() { return memoryReport; }
        public long getTimestamp() { return timestamp; }
        
        public boolean isEscalation() {
            return newStatus.ordinal() > oldStatus.ordinal();
        }
        
        public boolean isImprovement() {
            return newStatus.ordinal() < oldStatus.ordinal();
        }
        
        @Override
        public String toString() {
            return String.format("MemoryAlert{%s -> %s, %s, timestamp=%d}",
                oldStatus, newStatus, memoryReport, timestamp);
        }
    }
    
    /**
     * Memory report data structure
     */
    public static class MemoryReport {
        private final long heapUsed;
        private final long heapMax;
        private final long heapCommitted;
        private final double heapUsagePercentage;
        
        private final long nonHeapUsed;
        private final long nonHeapMax;
        private final long nonHeapCommitted;
        
        private final long totalUsed;
        private final long totalMemory;
        private final long maxMemory;
        private final double totalUsagePercentage;
        
        private final long gcTime;
        private final long gcCount;
        
        public MemoryReport(long heapUsed, long heapMax, long heapCommitted, double heapUsagePercentage,
                           long nonHeapUsed, long nonHeapMax, long nonHeapCommitted,
                           long totalUsed, long totalMemory, long maxMemory, double totalUsagePercentage,
                           long gcTime, long gcCount) {
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.heapCommitted = heapCommitted;
            this.heapUsagePercentage = heapUsagePercentage;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
            this.nonHeapCommitted = nonHeapCommitted;
            this.totalUsed = totalUsed;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
            this.totalUsagePercentage = totalUsagePercentage;
            this.gcTime = gcTime;
            this.gcCount = gcCount;
        }
        
        // Getters
        public long getHeapUsed() { return heapUsed; }
        public long getHeapMax() { return heapMax; }
        public long getHeapCommitted() { return heapCommitted; }
        public double getHeapUsagePercentage() { return heapUsagePercentage; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getNonHeapMax() { return nonHeapMax; }
        public long getNonHeapCommitted() { return nonHeapCommitted; }
        public long getTotalUsed() { return totalUsed; }
        public long getTotalMemory() { return totalMemory; }
        public long getMaxMemory() { return maxMemory; }
        public double getTotalUsagePercentage() { return totalUsagePercentage; }
        public long getGcTime() { return gcTime; }
        public long getGcCount() { return gcCount; }
        
        @Override
        public String toString() {
            return String.format("MemoryReport{heap=%dMB/%.1f%%, nonHeap=%dMB, total=%dMB/%.1f%%, gc=%dms/%d}",
                heapUsed / (1024 * 1024), heapUsagePercentage,
                nonHeapUsed / (1024 * 1024),
                totalUsed / (1024 * 1024), totalUsagePercentage,
                gcTime, gcCount);
        }
    }
}