package com.learnmore.application.excel.monitoring;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight memory monitor for Excel processing jobs.
 */
@Slf4j
@Component
public class MemoryMonitor {

    private final Runtime runtime = Runtime.getRuntime();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final Map<String, MemorySnapshot> snapshots = new ConcurrentHashMap<>();

    public void startMonitoring(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return;
        }
        MemorySnapshot snapshot = new MemorySnapshot();
        snapshot.setJobId(jobId);
        snapshot.setStartTime(System.currentTimeMillis());
        snapshot.setStartMemoryMB(getUsedMemoryMB());
        snapshots.put(jobId, snapshot);
        log.debug("[Memory] Start monitoring job={} start={}MB", jobId, snapshot.getStartMemoryMB());
    }

    public boolean isThresholdExceeded(String jobId, long thresholdMB) {
        if (jobId == null || jobId.isEmpty()) {
            return false;
        }
        MemorySnapshot snapshot = snapshots.get(jobId);
        long currentMB = getUsedMemoryMB();
        if (snapshot != null && currentMB > snapshot.getPeakMemoryMB()) {
            snapshot.setPeakMemoryMB(currentMB);
        }
        long maxMB = runtime.maxMemory() / (1024 * 1024);
        double usagePct = maxMB > 0 ? (double) currentMB / maxMB * 100.0 : 0.0;
        if (usagePct >= 80.0) {
            log.warn("[Memory] High usage job={} used={}MB max={}MB ({}%)", jobId, currentMB, maxMB, String.format("%.1f", usagePct));
            return true;
        }
        if (thresholdMB > 0 && currentMB > thresholdMB) {
            log.warn("[Memory] Threshold exceeded job={} used={}MB threshold={}MB", jobId, currentMB, thresholdMB);
            return true;
        }
        return false;
    }

    public MemorySummary stopMonitoring(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return null;
        }
        MemorySnapshot snapshot = snapshots.remove(jobId);
        if (snapshot == null) {
            return null;
        }
        long endMB = getUsedMemoryMB();
        MemorySummary summary = new MemorySummary();
        summary.setJobId(jobId);
        summary.setStartMemoryMB(snapshot.getStartMemoryMB());
        summary.setPeakMemoryMB(snapshot.getPeakMemoryMB());
        summary.setEndMemoryMB(endMB);
        summary.setDurationMs(System.currentTimeMillis() - snapshot.getStartTime());
        summary.setMemoryIncreaseMB(endMB - snapshot.getStartMemoryMB());
        log.debug("[Memory] Summary job={} start={}MB peak={}MB end={}MB +{}MB", jobId, summary.getStartMemoryMB(), summary.getPeakMemoryMB(), summary.getEndMemoryMB(), summary.getMemoryIncreaseMB());
        return summary;
    }

    public long getUsedMemoryMB() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    public MemoryInfo getMemoryInfo() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        MemoryInfo info = new MemoryInfo();
        info.setHeapUsedMB(heap.getUsed() / (1024 * 1024));
        info.setHeapMaxMB(heap.getMax() / (1024 * 1024));
        info.setNonHeapUsedMB(nonHeap.getUsed() / (1024 * 1024));
        info.setNonHeapMaxMB(nonHeap.getMax() / (1024 * 1024));
        return info;
    }

    @Data
    public static class MemorySummary {
        private String jobId;
        private long startMemoryMB;
        private long peakMemoryMB;
        private long endMemoryMB;
        private long memoryIncreaseMB;
        private long durationMs;
    }

    @Data
    public static class MemoryInfo {
        private long heapUsedMB;
        private long heapMaxMB;
        private long nonHeapUsedMB;
        private long nonHeapMaxMB;
    }

    @Data
    private static class MemorySnapshot {
        private String jobId;
        private long startTime;
        private long startMemoryMB;
        private long peakMemoryMB;
    }
}


