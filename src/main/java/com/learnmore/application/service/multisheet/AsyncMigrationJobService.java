package com.learnmore.application.service.multisheet;

import com.learnmore.infrastructure.persistence.entity.MigrationJobSheetEntity;
import com.learnmore.infrastructure.repository.MigrationJobSheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Async service for non-blocking migration job execution
 * 
 * Provides:
 * - Non-blocking job submission (@Async)
 * - Job cancellation support
 * - Progress tracking
 * - Graceful error handling
 * 
 * Usage:
 * 1. Controller calls processAsync() and returns immediately (HTTP 202 Accepted)
 * 2. Client polls /progress endpoint to track status
 * 3. Client can cancel job via /cancel endpoint
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncMigrationJobService {

    private final MultiSheetProcessor multiSheetProcessor;
    private final MigrationJobSheetRepository jobSheetRepository;

    // Track running jobs for cancellation support
    private final Map<String, CompletableFuture<MultiSheetProcessor.MultiSheetProcessResult>> runningJobs 
        = new ConcurrentHashMap<>();

    /**
     * Process migration job asynchronously
     * 
     * Runs in background thread pool (migrationExecutor)
     * Returns CompletableFuture for chaining/cancellation
     * 
     * @param jobId Unique job identifier
     * @param filePath Absolute path to Excel file
     * @return CompletableFuture with result (completes after 10-30 minutes)
     */
    @Async("migrationExecutor")
    public CompletableFuture<MultiSheetProcessor.MultiSheetProcessResult> processAsync(
            String jobId, String filePath) {
        
        log.info("🚀 [ASYNC] Starting migration job: {}", jobId);
        
        CompletableFuture<MultiSheetProcessor.MultiSheetProcessResult> future = new CompletableFuture<>();
        
        // Track this job
        runningJobs.put(jobId, future);
        
        try {
            // Update overall job status to STARTED
            updateOverallJobStatus(jobId, "STARTED", null);
            
            // Process all sheets (this is the long-running blocking operation)
            // It's OK to block here because we're in an async thread
            MultiSheetProcessor.MultiSheetProcessResult result = 
                multiSheetProcessor.processAllSheets(jobId, filePath);
            
            // Update overall job status based on result
            if (result.isAllSuccess()) {
                updateOverallJobStatus(jobId, "COMPLETED", null);
                log.info("✅ [ASYNC] Job completed successfully: {}", jobId);
            } else {
                updateOverallJobStatus(jobId, "COMPLETED_WITH_ERRORS", 
                    String.format("%d/%d sheets failed", result.getFailedSheets(), result.getTotalSheets()));
                log.warn("⚠️ [ASYNC] Job completed with errors: {} ({} failed sheets)", 
                         jobId, result.getFailedSheets());
            }
            
            // Complete the future
            future.complete(result);
            
        } catch (Exception e) {
            log.error("❌ [ASYNC] Job failed: {}", jobId, e);
            
            // Update overall job status to FAILED
            updateOverallJobStatus(jobId, "FAILED", e.getMessage());
            
            // Complete exceptionally
            future.completeExceptionally(e);
            
        } finally {
            // Clean up tracking
            runningJobs.remove(jobId);
            log.info("🧹 [ASYNC] Job removed from tracking: {}", jobId);
        }
        
        return future;
    }

    /**
     * Cancel a running job
     * 
     * Attempts to interrupt the running CompletableFuture
     * Note: Actual processing cancellation depends on MultiSheetProcessor implementation
     * 
     * @param jobId Job to cancel
     * @return true if job was found and cancellation was attempted
     */
    public boolean cancelJob(String jobId) {
        log.info("🛑 [ASYNC] Cancellation requested for job: {}", jobId);
        
        CompletableFuture<MultiSheetProcessor.MultiSheetProcessResult> future = runningJobs.get(jobId);
        
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            
            if (cancelled) {
                updateOverallJobStatus(jobId, "CANCELLED", "Cancelled by user");
                log.info("✅ [ASYNC] Job cancelled successfully: {}", jobId);
            } else {
                log.warn("⚠️ [ASYNC] Job cancellation failed (already completing?): {}", jobId);
            }
            
            return cancelled;
        }
        
        log.warn("⚠️ [ASYNC] Job not found or already completed: {}", jobId);
        return false;
    }

    /**
     * Check if job is currently running
     * 
     * @param jobId Job to check
     * @return true if job is tracked and not done
     */
    public boolean isJobRunning(String jobId) {
        CompletableFuture<?> future = runningJobs.get(jobId);
        return future != null && !future.isDone();
    }

    /**
     * Get current number of running jobs
     * 
     * @return Count of active jobs
     */
    public int getRunningJobCount() {
        return (int) runningJobs.values().stream()
                .filter(f -> !f.isDone())
                .count();
    }

    /**
     * Update overall job status
     * 
     * Sets a common status message in the first sheet's entity
     * (We use the first sheet as a proxy for overall job status)
     * 
     * Alternative: Create a separate migration_job table for overall status
     */
    private void updateOverallJobStatus(String jobId, String status, String errorMessage) {
        List<MigrationJobSheetEntity> sheets = jobSheetRepository.findByJobIdOrderBySheetOrder(jobId);
        
        if (sheets.isEmpty()) {
            log.warn("⚠️ [ASYNC] No sheets found for job: {}", jobId);
            return;
        }
        
        // Update first sheet with overall status marker
        MigrationJobSheetEntity firstSheet = sheets.get(0);
        
        // We'll use errorMessage field to store overall job status
        // Format: "OVERALL_STATUS: {status} | {details}"
        String statusMessage = "OVERALL_STATUS: " + status;
        if (errorMessage != null) {
            statusMessage += " | " + errorMessage;
        }
        
        // Store in first sheet's error_message field
        // (This is a workaround - ideally create migration_job table)
        if ("STARTED".equals(status)) {
            firstSheet.setErrorMessage(statusMessage);
        } else {
            // Append to existing error message
            String existing = firstSheet.getErrorMessage();
            if (existing != null && !existing.startsWith("OVERALL_STATUS:")) {
                statusMessage = statusMessage + " | SHEET_ERROR: " + existing;
            }
            firstSheet.setErrorMessage(statusMessage);
        }
        
        jobSheetRepository.save(firstSheet);
        
        log.debug("📝 [ASYNC] Overall job status updated: {} -> {}", jobId, status);
    }

    /**
     * Extract overall job status from sheets
     * 
     * Reads the OVERALL_STATUS marker from first sheet's error_message
     * 
     * @param jobId Job to check
     * @return Overall status (PENDING, STARTED, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, CANCELLED)
     */
    public String getOverallJobStatus(String jobId) {
        List<MigrationJobSheetEntity> sheets = jobSheetRepository.findByJobIdOrderBySheetOrder(jobId);
        
        if (sheets.isEmpty()) {
            return "NOT_FOUND";
        }
        
        MigrationJobSheetEntity firstSheet = sheets.get(0);
        String errorMessage = firstSheet.getErrorMessage();
        
        if (errorMessage != null && errorMessage.startsWith("OVERALL_STATUS:")) {
            // Extract status from "OVERALL_STATUS: {status} | {details}"
            String[] parts = errorMessage.split("\\|");
            if (parts.length > 0) {
                String statusPart = parts[0].replace("OVERALL_STATUS:", "").trim();
                return statusPart;
            }
        }
        
        // Fallback: Infer from sheet statuses
        boolean allCompleted = sheets.stream().allMatch(s -> "COMPLETED".equals(s.getStatus()));
        boolean anyFailed = sheets.stream().anyMatch(s -> "FAILED".equals(s.getStatus()));
        boolean anyInProgress = sheets.stream().anyMatch(MigrationJobSheetEntity::isInProgress);
        
        if (anyInProgress) {
            return "STARTED";
        } else if (allCompleted && !anyFailed) {
            return "COMPLETED";
        } else if (anyFailed) {
            return "COMPLETED_WITH_ERRORS";
        } else {
            return "PENDING";
        }
    }
}
