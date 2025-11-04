package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.ValidationStepStatus;
import com.learnmore.infrastructure.repository.MigrationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component để track và monitor từng step trong validation process
 * Giúp xác định step nào đang chạy, step nào bị treo, và thời gian thực thi
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationStepTracker {

    private final MigrationJobRepository migrationJobRepository;

    // Map để lưu trữ status của các step theo jobId
    private final Map<String, List<ValidationStepStatus>> jobStepsMap = new ConcurrentHashMap<>();

    // Timeout configuration (ms) - có thể config từ application.yml
    private static final long DEFAULT_STEP_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    private static final long MOVE_VALID_RECORDS_TIMEOUT_MS = 15 * 60 * 1000; // 15 minutes for large operations

    /**
     * Initialize tracking cho một job
     */
    public void initializeTracking(String jobId) {
        List<ValidationStepStatus> steps = new ArrayList<>();

        steps.add(createStep(jobId, 1, "VALIDATE_REQUIRED_FIELDS",
                "Validate required fields (mandatory columns)"));
        steps.add(createStep(jobId, 2, "VALIDATE_DATE_FORMATS",
                "Validate date formats and business rules"));
        steps.add(createStep(jobId, 3, "VALIDATE_NUMERIC_FIELDS",
                "Validate numeric fields (positive integers)"));
        steps.add(createStep(jobId, 4, "CHECK_DUPLICATES_IN_FILE",
                "Check duplicates within uploaded file"));
        steps.add(createStep(jobId, 5, "VALIDATE_MASTER_REFERENCES",
                "Validate references with master tables"));
        steps.add(createStep(jobId, 6, "CHECK_DUPLICATES_WITH_DB",
                "Check duplicates with existing database records"));
        steps.add(createStep(jobId, 7, "MOVE_VALID_RECORDS",
                "Move valid records to staging_valid table"));

        jobStepsMap.put(jobId, steps);
        log.info("Initialized validation step tracking for JobId: {} with {} steps", jobId, steps.size());
    }

    /**
     * Create a step status object
     */
    private ValidationStepStatus createStep(String jobId, int stepNumber, String stepName, String description) {
        return ValidationStepStatus.builder()
                .jobId(jobId)
                .stepNumber(stepNumber)
                .stepName(stepName)
                .stepDescription(description)
                .status("PENDING")
                .progressPercent(0.0)
                .build();
    }

    /**
     * Mark step as started
     */
    public void markStepStarted(String jobId, String stepName) {
        ValidationStepStatus step = getStep(jobId, stepName);
        if (step != null) {
            step.markStarted();
            updateMigrationJobProgress(jobId, step);
            log.info("JobId: {} - Step {}/{}: {} STARTED",
                    jobId, step.getStepNumber(), getTotalSteps(jobId), stepName);
        }
    }

    /**
     * Mark step as completed
     */
    public void markStepCompleted(String jobId, String stepName, int affectedRows) {
        ValidationStepStatus step = getStep(jobId, stepName);
        if (step != null) {
            step.markCompleted(affectedRows);
            updateMigrationJobProgress(jobId, step);
            log.info("JobId: {} - Step {}/{}: {} COMPLETED in {}ms (affected {} rows)",
                    jobId, step.getStepNumber(), getTotalSteps(jobId), stepName,
                    step.getDurationMs(), affectedRows);
        }
    }

    /**
     * Mark step as failed
     */
    public void markStepFailed(String jobId, String stepName, String errorMessage) {
        ValidationStepStatus step = getStep(jobId, stepName);
        if (step != null) {
            step.markFailed(errorMessage);
            updateMigrationJobProgress(jobId, step);
            log.error("JobId: {} - Step {}/{}: {} FAILED after {}ms - Error: {}",
                    jobId, step.getStepNumber(), getTotalSteps(jobId), stepName,
                    step.getDurationMs(), errorMessage);
        }
    }

    /**
     * Check timeout for all in-progress steps
     */
    public void checkTimeouts(String jobId) {
        List<ValidationStepStatus> steps = jobStepsMap.get(jobId);
        if (steps == null) {
            return;
        }

        for (ValidationStepStatus step : steps) {
            long timeout = getTimeoutForStep(step.getStepName());
            if (step.isRunningTooLong(timeout)) {
                String message = String.format("Step timeout after %dms (limit: %dms)",
                        step.getElapsedMs(), timeout);
                step.markTimeout(message);
                log.warn("JobId: {} - Step {}: {} TIMEOUT - {}",
                        jobId, step.getStepNumber(), step.getStepName(), message);
            }
        }
    }

    /**
     * Get timeout value for specific step
     */
    private long getTimeoutForStep(String stepName) {
        if ("MOVE_VALID_RECORDS".equals(stepName)) {
            return MOVE_VALID_RECORDS_TIMEOUT_MS;
        }
        return DEFAULT_STEP_TIMEOUT_MS;
    }

    /**
     * Get current step that is in progress
     */
    public ValidationStepStatus getCurrentStep(String jobId) {
        List<ValidationStepStatus> steps = jobStepsMap.get(jobId);
        if (steps == null) {
            return null;
        }
        return steps.stream()
                .filter(s -> "IN_PROGRESS".equals(s.getStatus()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all steps for a job
     */
    public List<ValidationStepStatus> getAllSteps(String jobId) {
        return jobStepsMap.getOrDefault(jobId, new ArrayList<>());
    }

    /**
     * Get step by name
     */
    private ValidationStepStatus getStep(String jobId, String stepName) {
        List<ValidationStepStatus> steps = jobStepsMap.get(jobId);
        if (steps == null) {
            return null;
        }
        return steps.stream()
                .filter(s -> s.getStepName().equals(stepName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get total number of steps
     */
    private int getTotalSteps(String jobId) {
        List<ValidationStepStatus> steps = jobStepsMap.get(jobId);
        return steps != null ? steps.size() : 0;
    }

    /**
     * Calculate overall progress percentage
     */
    public double calculateOverallProgress(String jobId) {
        List<ValidationStepStatus> steps = jobStepsMap.get(jobId);
        if (steps == null || steps.isEmpty()) {
            return 0.0;
        }

        long completedSteps = steps.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()))
                .count();

        return (completedSteps * 100.0) / steps.size();
    }

    /**
     * Update migration job with current progress
     */
    private void updateMigrationJobProgress(String jobId, ValidationStepStatus step) {
        try {
            migrationJobRepository.findByJobId(jobId).ifPresent(job -> {
                double progress = calculateOverallProgress(jobId);
                job.setProgressPercent(progress);
                job.setCurrentPhase(String.format("VALIDATION - Step %d/%d: %s",
                        step.getStepNumber(), getTotalSteps(jobId), step.getStepName()));
                migrationJobRepository.save(job);
            });
        } catch (Exception e) {
            log.warn("Failed to update migration job progress for JobId: {}, Error: {}",
                    jobId, e.getMessage());
        }
    }

    /**
     * Get summary of validation progress
     */
    public String getProgressSummary(String jobId) {
        List<ValidationStepStatus> steps = getAllSteps(jobId);
        if (steps.isEmpty()) {
            return "No tracking data available for JobId: " + jobId;
        }

        ValidationStepStatus currentStep = getCurrentStep(jobId);
        long completedCount = steps.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failedCount = steps.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        long timeoutCount = steps.stream().filter(s -> "TIMEOUT".equals(s.getStatus())).count();

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("JobId: %s - Validation Progress: %.1f%%\n",
                jobId, calculateOverallProgress(jobId)));
        summary.append(String.format("Completed: %d, Failed: %d, Timeout: %d\n",
                completedCount, failedCount, timeoutCount));

        if (currentStep != null) {
            summary.append(String.format("Current Step: %d/%d - %s (running for %dms)\n",
                    currentStep.getStepNumber(), getTotalSteps(jobId),
                    currentStep.getStepName(), currentStep.getElapsedMs()));
        }

        return summary.toString();
    }

    /**
     * Cleanup tracking data for completed jobs
     */
    public void cleanupTracking(String jobId) {
        jobStepsMap.remove(jobId);
        log.debug("Cleaned up validation step tracking for JobId: {}", jobId);
    }

    /**
     * Get detailed report of all steps
     */
    public String getDetailedReport(String jobId) {
        List<ValidationStepStatus> steps = getAllSteps(jobId);
        if (steps.isEmpty()) {
            return "No tracking data available for JobId: " + jobId;
        }

        StringBuilder report = new StringBuilder();
        report.append("=== Validation Step Report for JobId: ").append(jobId).append(" ===\n\n");

        for (ValidationStepStatus step : steps) {
            report.append(String.format("Step %d: %s\n", step.getStepNumber(), step.getStepName()));
            report.append(String.format("  Description: %s\n", step.getStepDescription()));
            report.append(String.format("  Status: %s\n", step.getStatus()));

            if (step.getStartTime() != null) {
                report.append(String.format("  Start Time: %s\n", step.getStartTime()));
            }
            if (step.getEndTime() != null) {
                report.append(String.format("  End Time: %s\n", step.getEndTime()));
            }
            if (step.getDurationMs() != null) {
                report.append(String.format("  Duration: %dms (%.2fs)\n",
                        step.getDurationMs(), step.getDurationMs() / 1000.0));
            }
            if (step.getAffectedRows() != null) {
                report.append(String.format("  Affected Rows: %d\n", step.getAffectedRows()));
            }
            if (step.getErrorMessage() != null) {
                report.append(String.format("  Error: %s\n", step.getErrorMessage()));
            }

            // Show elapsed time for in-progress steps
            if ("IN_PROGRESS".equals(step.getStatus())) {
                report.append(String.format("  Elapsed: %dms (%.2fs)\n",
                        step.getElapsedMs(), step.getElapsedMs() / 1000.0));
            }

            report.append("\n");
        }

        report.append(String.format("Overall Progress: %.1f%%\n", calculateOverallProgress(jobId)));

        return report.toString();
    }
}
