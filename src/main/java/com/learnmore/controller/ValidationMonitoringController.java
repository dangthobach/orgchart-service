package com.learnmore.controller;

import com.learnmore.application.dto.migration.ValidationStepStatus;
import com.learnmore.application.service.migration.ValidationStepTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller để monitor validation progress
 * Giúp theo dõi realtime từng step trong quá trình validation
 */
@RestController
@RequestMapping("/api/migration/validation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Validation Monitoring", description = "APIs to monitor validation progress in real-time")
public class ValidationMonitoringController {

    private final ValidationStepTracker stepTracker;

    /**
     * Get all validation steps for a job
     * GET /api/migration/validation/{jobId}/steps
     */
    @GetMapping("/{jobId}/steps")
    @Operation(summary = "Get all validation steps",
               description = "Returns all validation steps with their status for the specified job")
    public ResponseEntity<Map<String, Object>> getValidationSteps(@PathVariable String jobId) {
        log.info("Getting validation steps for JobId: {}", jobId);

        List<ValidationStepStatus> steps = stepTracker.getAllSteps(jobId);

        if (steps.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("steps", steps);
        response.put("totalSteps", steps.size());
        response.put("overallProgress", stepTracker.calculateOverallProgress(jobId));

        return ResponseEntity.ok(response);
    }

    /**
     * Get current validation step being executed
     * GET /api/migration/validation/{jobId}/current
     */
    @GetMapping("/{jobId}/current")
    @Operation(summary = "Get current validation step",
               description = "Returns the current step being executed (IN_PROGRESS status)")
    public ResponseEntity<Map<String, Object>> getCurrentStep(@PathVariable String jobId) {
        log.info("Getting current step for JobId: {}", jobId);

        ValidationStepStatus currentStep = stepTracker.getCurrentStep(jobId);

        if (currentStep == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("message", "No step is currently in progress");
            return ResponseEntity.ok(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("currentStep", currentStep);
        response.put("elapsedMs", currentStep.getElapsedMs());
        response.put("elapsedSeconds", currentStep.getElapsedMs() / 1000.0);

        return ResponseEntity.ok(response);
    }

    /**
     * Get progress summary
     * GET /api/migration/validation/{jobId}/summary
     */
    @GetMapping("/{jobId}/summary")
    @Operation(summary = "Get validation progress summary",
               description = "Returns a summary of validation progress including completion percentage")
    public ResponseEntity<Map<String, Object>> getProgressSummary(@PathVariable String jobId) {
        log.info("Getting progress summary for JobId: {}", jobId);

        List<ValidationStepStatus> steps = stepTracker.getAllSteps(jobId);

        if (steps.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        long completedCount = steps.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failedCount = steps.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        long timeoutCount = steps.stream().filter(s -> "TIMEOUT".equals(s.getStatus())).count();
        long inProgressCount = steps.stream().filter(s -> "IN_PROGRESS".equals(s.getStatus())).count();
        long pendingCount = steps.stream().filter(s -> "PENDING".equals(s.getStatus())).count();

        ValidationStepStatus currentStep = stepTracker.getCurrentStep(jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("overallProgress", stepTracker.calculateOverallProgress(jobId));
        response.put("totalSteps", steps.size());
        response.put("completedSteps", completedCount);
        response.put("failedSteps", failedCount);
        response.put("timeoutSteps", timeoutCount);
        response.put("inProgressSteps", inProgressCount);
        response.put("pendingSteps", pendingCount);

        if (currentStep != null) {
            response.put("currentStepNumber", currentStep.getStepNumber());
            response.put("currentStepName", currentStep.getStepName());
            response.put("currentStepDescription", currentStep.getStepDescription());
            response.put("currentStepElapsedMs", currentStep.getElapsedMs());
            response.put("currentStepElapsedSeconds", currentStep.getElapsedMs() / 1000.0);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed report
     * GET /api/migration/validation/{jobId}/report
     */
    @GetMapping("/{jobId}/report")
    @Operation(summary = "Get detailed validation report",
               description = "Returns a detailed text report of all validation steps")
    public ResponseEntity<Map<String, Object>> getDetailedReport(@PathVariable String jobId) {
        log.info("Getting detailed report for JobId: {}", jobId);

        String report = stepTracker.getDetailedReport(jobId);

        if (report.contains("No tracking data")) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("report", report);
        response.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Check for timeouts
     * POST /api/migration/validation/{jobId}/check-timeout
     */
    @PostMapping("/{jobId}/check-timeout")
    @Operation(summary = "Check for step timeouts",
               description = "Manually trigger a timeout check for all in-progress steps")
    public ResponseEntity<Map<String, Object>> checkTimeouts(@PathVariable String jobId) {
        log.info("Checking timeouts for JobId: {}", jobId);

        stepTracker.checkTimeouts(jobId);

        List<ValidationStepStatus> steps = stepTracker.getAllSteps(jobId);
        long timeoutCount = steps.stream().filter(s -> "TIMEOUT".equals(s.getStatus())).count();

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("timeoutSteps", timeoutCount);
        response.put("message", timeoutCount > 0 ?
            "Found " + timeoutCount + " step(s) that exceeded timeout" :
            "No timeouts detected");

        return ResponseEntity.ok(response);
    }

    /**
     * Get step execution times
     * GET /api/migration/validation/{jobId}/performance
     */
    @GetMapping("/{jobId}/performance")
    @Operation(summary = "Get step performance metrics",
               description = "Returns execution time and affected rows for each completed step")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics(@PathVariable String jobId) {
        log.info("Getting performance metrics for JobId: {}", jobId);

        List<ValidationStepStatus> steps = stepTracker.getAllSteps(jobId);

        if (steps.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> performanceData = steps.stream()
            .filter(s -> "COMPLETED".equals(s.getStatus()))
            .map(step -> {
                Map<String, Object> data = new HashMap<>();
                data.put("stepNumber", step.getStepNumber());
                data.put("stepName", step.getStepName());
                data.put("durationMs", step.getDurationMs());
                data.put("durationSeconds", step.getDurationMs() / 1000.0);
                data.put("affectedRows", step.getAffectedRows());
                if (step.getAffectedRows() != null && step.getAffectedRows() > 0 && step.getDurationMs() != null) {
                    data.put("rowsPerSecond", (step.getAffectedRows() * 1000.0) / step.getDurationMs());
                }
                return data;
            })
            .toList();

        long totalDuration = steps.stream()
            .filter(s -> s.getDurationMs() != null)
            .mapToLong(ValidationStepStatus::getDurationMs)
            .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("performanceData", performanceData);
        response.put("totalDurationMs", totalDuration);
        response.put("totalDurationSeconds", totalDuration / 1000.0);

        return ResponseEntity.ok(response);
    }

    /**
     * Get status of specific step
     * GET /api/migration/validation/{jobId}/step/{stepName}
     */
    @GetMapping("/{jobId}/step/{stepName}")
    @Operation(summary = "Get specific step status",
               description = "Returns status details for a specific validation step")
    public ResponseEntity<ValidationStepStatus> getStepStatus(
            @PathVariable String jobId,
            @PathVariable String stepName) {

        log.info("Getting step status for JobId: {}, Step: {}", jobId, stepName);

        List<ValidationStepStatus> steps = stepTracker.getAllSteps(jobId);
        ValidationStepStatus step = steps.stream()
            .filter(s -> s.getStepName().equals(stepName))
            .findFirst()
            .orElse(null);

        if (step == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(step);
    }
}
