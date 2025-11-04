package com.learnmore.application.dto.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO để track status của từng step trong validation process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationStepStatus {

    private String jobId;
    private String stepName;
    private String stepDescription;
    private Integer stepNumber;
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED, TIMEOUT
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private Integer affectedRows;
    private String errorMessage;
    private Double progressPercent;

    /**
     * Calculate duration if both start and end time are set
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * Mark step as started
     */
    public void markStarted() {
        this.status = "IN_PROGRESS";
        this.startTime = LocalDateTime.now();
    }

    /**
     * Mark step as completed
     */
    public void markCompleted(int affectedRows) {
        this.status = "COMPLETED";
        this.endTime = LocalDateTime.now();
        this.affectedRows = affectedRows;
        calculateDuration();
    }

    /**
     * Mark step as failed
     */
    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.endTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
        calculateDuration();
    }

    /**
     * Mark step as timeout
     */
    public void markTimeout(String message) {
        this.status = "TIMEOUT";
        this.endTime = LocalDateTime.now();
        this.errorMessage = message;
        calculateDuration();
    }

    /**
     * Check if step is running too long (timeout check)
     */
    public boolean isRunningTooLong(long timeoutMs) {
        if (startTime == null || !"IN_PROGRESS".equals(status)) {
            return false;
        }
        long elapsedMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        return elapsedMs > timeoutMs;
    }

    /**
     * Get elapsed time in milliseconds
     */
    public long getElapsedMs() {
        if (startTime == null) {
            return 0;
        }
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }
}
