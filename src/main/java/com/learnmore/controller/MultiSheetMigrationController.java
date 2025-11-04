package com.learnmore.controller;

import com.learnmore.application.dto.migration.MigrationStartRequest;
import com.learnmore.application.service.multisheet.MultiSheetProcessor;
import com.learnmore.infrastructure.persistence.entity.MigrationJobSheetEntity;
import com.learnmore.infrastructure.repository.MigrationJobSheetRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for multi-sheet migration
 * Provides APIs to start migration and monitor progress per sheet
 */
@RestController
@RequestMapping("/api/migration/multisheet")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Multi-Sheet Migration", description = "APIs for multi-sheet Excel migration with per-sheet monitoring")
public class MultiSheetMigrationController {

    private final MultiSheetProcessor multiSheetProcessor;
    private final MigrationJobSheetRepository jobSheetRepository;

    /**
     * Start multi-sheet migration
     * POST /api/migration/multisheet/start
     *
     * Protected by circuit breaker and rate limiting for resilience
     */
    @PostMapping("/start")
    @Operation(summary = "Start multi-sheet migration",
               description = "Process all enabled sheets in Excel file with parallel processing")
    @CircuitBreaker(name = "multiSheetMigration", fallbackMethod = "startMigrationFallback")
    @RateLimiter(name = "multiSheetMigration")
    public ResponseEntity<Map<String, Object>> startMigration(@Valid @RequestBody MigrationStartRequest request) {
        log.info("Starting multi-sheet migration - JobId: {}, File: {}", request.getJobId(), request.getFilePath());

        try {
            // 1. Validate file exists
            if (!Files.exists(Paths.get(request.getFilePath()))) {
                log.error("File not found: {}", request.getFilePath());
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File not found: " + request.getFilePath()));
            }

            // 2. Check idempotency - prevent duplicate job submission
            List<MigrationJobSheetEntity> existingSheets =
                jobSheetRepository.findByJobIdOrderBySheetOrder(request.getJobId());

            if (!existingSheets.isEmpty()) {
                // Check if job is already completed
                boolean allCompleted = existingSheets.stream()
                        .allMatch(MigrationJobSheetEntity::isCompleted);

                if (allCompleted) {
                    log.info("Job {} already completed, returning existing result", request.getJobId());
                    Map<String, Object> response = new HashMap<>();
                    response.put("jobId", request.getJobId());
                    response.put("message", "Job already completed");
                    response.put("sheets", existingSheets);
                    return ResponseEntity.ok(response);
                } else {
                    // Job is still in progress
                    log.warn("Job {} already in progress", request.getJobId());
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Job already in progress",
                                       "jobId", request.getJobId()));
                }
            }

            // 3. Process all sheets
            MultiSheetProcessor.MultiSheetProcessResult result =
                multiSheetProcessor.processAllSheets(request.getJobId(), request.getFilePath());

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", request.getJobId());
            response.put("success", result.isAllSuccess());
            response.put("totalSheets", result.getTotalSheets());
            response.put("successSheets", result.getSuccessSheets());
            response.put("failedSheets", result.getFailedSheets());
            response.put("totalIngestedRows", result.getTotalIngestedRows());
            response.put("totalValidRows", result.getTotalValidRows());
            response.put("totalErrorRows", result.getTotalErrorRows());
            response.put("totalInsertedRows", result.getTotalInsertedRows());
            response.put("sheetResults", result.getSheetResults());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error starting multi-sheet migration: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("jobId", request.getJobId());
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get all sheets status for a job
     * GET /api/migration/multisheet/{jobId}/sheets
     */
    @GetMapping("/{jobId}/sheets")
    @Operation(summary = "Get all sheets status",
               description = "Returns status of all sheets in the migration job")
    public ResponseEntity<Map<String, Object>> getAllSheets(@PathVariable String jobId) {
        log.info("Getting all sheets for JobId: {}", jobId);

        List<MigrationJobSheetEntity> sheets = jobSheetRepository.findByJobIdOrderBySheetOrder(jobId);

        if (sheets.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("totalSheets", sheets.size());
        response.put("sheets", sheets);

        // Calculate summary
        long completedSheets = sheets.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failedSheets = sheets.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        long inProgressSheets = sheets.stream().filter(MigrationJobSheetEntity::isInProgress).count();

        response.put("completedSheets", completedSheets);
        response.put("failedSheets", failedSheets);
        response.put("inProgressSheets", inProgressSheets);
        response.put("overallProgress", calculateOverallProgress(sheets));

        return ResponseEntity.ok(response);
    }

    /**
     * Get specific sheet status
     * GET /api/migration/multisheet/{jobId}/sheet/{sheetName}
     */
    @GetMapping("/{jobId}/sheet/{sheetName}")
    @Operation(summary = "Get specific sheet status",
               description = "Returns detailed status of a specific sheet")
    public ResponseEntity<Map<String, Object>> getSheetStatus(@PathVariable String jobId,
                                                                @PathVariable String sheetName) {
        log.info("Getting sheet status - JobId: {}, Sheet: {}", jobId, sheetName);

        return jobSheetRepository.findByJobIdAndSheetName(jobId, sheetName)
                .map(sheet -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("jobId", jobId);
                    response.put("sheetName", sheetName);
                    response.put("status", sheet.getStatus());
                    response.put("currentPhase", sheet.getCurrentPhase());
                    response.put("progressPercent", sheet.getProgressPercent());
                    response.put("totalRows", sheet.getTotalRows());
                    response.put("ingestedRows", sheet.getIngestedRows());
                    response.put("validRows", sheet.getValidRows());
                    response.put("errorRows", sheet.getErrorRows());
                    response.put("insertedRows", sheet.getInsertedRows());

                    // Timing info
                    if (sheet.getIngestDurationMs() != null) {
                        response.put("ingestDurationMs", sheet.getIngestDurationMs());
                        response.put("ingestDurationSeconds", sheet.getIngestDurationMs() / 1000.0);
                    }
                    if (sheet.getValidationDurationMs() != null) {
                        response.put("validationDurationMs", sheet.getValidationDurationMs());
                        response.put("validationDurationSeconds", sheet.getValidationDurationMs() / 1000.0);
                    }
                    if (sheet.getInsertionDurationMs() != null) {
                        response.put("insertionDurationMs", sheet.getInsertionDurationMs());
                        response.put("insertionDurationSeconds", sheet.getInsertionDurationMs() / 1000.0);
                    }
                    if (sheet.getTotalDurationMs() != null) {
                        response.put("totalDurationMs", sheet.getTotalDurationMs());
                        response.put("totalDurationSeconds", sheet.getTotalDurationMs() / 1000.0);
                    }

                    // Error info
                    if (sheet.getErrorMessage() != null) {
                        response.put("errorMessage", sheet.getErrorMessage());
                    }

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get overall progress summary
     * GET /api/migration/multisheet/{jobId}/progress
     */
    @GetMapping("/{jobId}/progress")
    @Operation(summary = "Get overall progress",
               description = "Returns aggregated progress across all sheets")
    public ResponseEntity<Map<String, Object>> getOverallProgress(@PathVariable String jobId) {
        log.info("Getting overall progress for JobId: {}", jobId);

        List<MigrationJobSheetEntity> sheets = jobSheetRepository.findByJobIdOrderBySheetOrder(jobId);

        if (sheets.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("totalSheets", sheets.size());

        // Status counts
        long pendingSheets = sheets.stream().filter(s -> "PENDING".equals(s.getStatus())).count();
        long inProgressSheets = sheets.stream().filter(MigrationJobSheetEntity::isInProgress).count();
        long completedSheets = sheets.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failedSheets = sheets.stream().filter(s -> "FAILED".equals(s.getStatus())).count();

        response.put("pendingSheets", pendingSheets);
        response.put("inProgressSheets", inProgressSheets);
        response.put("completedSheets", completedSheets);
        response.put("failedSheets", failedSheets);

        // Overall progress
        double overallProgress = calculateOverallProgress(sheets);
        response.put("overallProgress", overallProgress);

        // Aggregated metrics
        Long totalIngested = sheets.stream()
                .filter(s -> s.getIngestedRows() != null)
                .mapToLong(MigrationJobSheetEntity::getIngestedRows)
                .sum();
        Long totalValid = sheets.stream()
                .filter(s -> s.getValidRows() != null)
                .mapToLong(MigrationJobSheetEntity::getValidRows)
                .sum();
        Long totalErrors = sheets.stream()
                .filter(s -> s.getErrorRows() != null)
                .mapToLong(MigrationJobSheetEntity::getErrorRows)
                .sum();
        Long totalInserted = sheets.stream()
                .filter(s -> s.getInsertedRows() != null)
                .mapToLong(MigrationJobSheetEntity::getInsertedRows)
                .sum();

        response.put("totalIngestedRows", totalIngested);
        response.put("totalValidRows", totalValid);
        response.put("totalErrorRows", totalErrors);
        response.put("totalInsertedRows", totalInserted);

        // Current sheet in progress
        sheets.stream()
                .filter(MigrationJobSheetEntity::isInProgress)
                .findFirst()
                .ifPresent(currentSheet -> {
                    response.put("currentSheet", currentSheet.getSheetName());
                    response.put("currentPhase", currentSheet.getCurrentPhase());
                });

        return ResponseEntity.ok(response);
    }

    /**
     * Get sheets that are currently in progress
     * GET /api/migration/multisheet/{jobId}/in-progress
     */
    @GetMapping("/{jobId}/in-progress")
    @Operation(summary = "Get in-progress sheets",
               description = "Returns sheets that are currently being processed")
    public ResponseEntity<Map<String, Object>> getInProgressSheets(@PathVariable String jobId) {
        log.info("Getting in-progress sheets for JobId: {}", jobId);

        List<MigrationJobSheetEntity> inProgressSheets = jobSheetRepository.findInProgressSheetsByJobId(jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("inProgressCount", inProgressSheets.size());
        response.put("sheets", inProgressSheets);

        return ResponseEntity.ok(response);
    }

    /**
     * Get performance metrics for all sheets
     * GET /api/migration/multisheet/{jobId}/performance
     */
    @GetMapping("/{jobId}/performance")
    @Operation(summary = "Get performance metrics",
               description = "Returns timing and throughput metrics for all sheets")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics(@PathVariable String jobId) {
        log.info("Getting performance metrics for JobId: {}", jobId);

        List<MigrationJobSheetEntity> sheets = jobSheetRepository.findByJobIdOrderBySheetOrder(jobId);

        if (sheets.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> sheetMetrics = sheets.stream()
                .filter(MigrationJobSheetEntity::isCompleted)
                .map(sheet -> {
                    Map<String, Object> metrics = new HashMap<>();
                    metrics.put("sheetName", sheet.getSheetName());

                    // Timing
                    if (sheet.getIngestDurationMs() != null) {
                        metrics.put("ingestDurationMs", sheet.getIngestDurationMs());
                        metrics.put("ingestDurationSeconds", sheet.getIngestDurationMs() / 1000.0);
                    }
                    if (sheet.getValidationDurationMs() != null) {
                        metrics.put("validationDurationMs", sheet.getValidationDurationMs());
                        metrics.put("validationDurationSeconds", sheet.getValidationDurationMs() / 1000.0);
                    }
                    if (sheet.getInsertionDurationMs() != null) {
                        metrics.put("insertionDurationMs", sheet.getInsertionDurationMs());
                        metrics.put("insertionDurationSeconds", sheet.getInsertionDurationMs() / 1000.0);
                    }
                    if (sheet.getTotalDurationMs() != null) {
                        metrics.put("totalDurationMs", sheet.getTotalDurationMs());
                        metrics.put("totalDurationSeconds", sheet.getTotalDurationMs() / 1000.0);
                    }

                    // Throughput
                    if (sheet.getIngestedRows() != null && sheet.getIngestDurationMs() != null && sheet.getIngestDurationMs() > 0) {
                        double rowsPerSecond = (sheet.getIngestedRows() * 1000.0) / sheet.getIngestDurationMs();
                        metrics.put("ingestThroughput", rowsPerSecond);
                    }

                    metrics.put("totalRows", sheet.getTotalRows());
                    metrics.put("validRows", sheet.getValidRows());
                    metrics.put("errorRows", sheet.getErrorRows());

                    return metrics;
                })
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("sheetMetrics", sheetMetrics);

        // Aggregate totals
        long totalDuration = sheets.stream()
                .filter(s -> s.getTotalDurationMs() != null)
                .mapToLong(MigrationJobSheetEntity::getTotalDurationMs)
                .sum();
        response.put("totalDurationMs", totalDuration);
        response.put("totalDurationSeconds", totalDuration / 1000.0);

        return ResponseEntity.ok(response);
    }

    /**
     * Check if all sheets are completed
     * GET /api/migration/multisheet/{jobId}/is-complete
     */
    @GetMapping("/{jobId}/is-complete")
    @Operation(summary = "Check if migration is complete",
               description = "Returns true if all sheets are completed (success or failed)")
    public ResponseEntity<Map<String, Object>> isComplete(@PathVariable String jobId) {
        Boolean isComplete = jobSheetRepository.areAllSheetsCompleted(jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("isComplete", isComplete);

        return ResponseEntity.ok(response);
    }

    /**
     * Calculate overall progress percentage
     */
    private double calculateOverallProgress(List<MigrationJobSheetEntity> sheets) {
        if (sheets.isEmpty()) {
            return 0.0;
        }

        Double avgProgress = jobSheetRepository.getAverageProgressByJobId(sheets.get(0).getJobId());
        return avgProgress != null ? avgProgress : 0.0;
    }

    /**
     * Fallback method for circuit breaker
     * Called when circuit is open or service is unavailable
     */
    private ResponseEntity<Map<String, Object>> startMigrationFallback(MigrationStartRequest request, Throwable t) {
        log.error("Circuit breaker triggered for multi-sheet migration. JobId: {}, Error: {}",
                  request.getJobId(), t.getMessage(), t);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("jobId", request.getJobId());
        errorResponse.put("error", "Service temporarily unavailable. Please try again later.");
        errorResponse.put("circuitBreakerTriggered", true);
        errorResponse.put("details", t.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
}
