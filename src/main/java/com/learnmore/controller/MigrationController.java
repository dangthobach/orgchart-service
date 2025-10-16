package com.learnmore.controller;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.application.service.migration.MigrationOrchestrationService;
import com.learnmore.application.service.EnhancedExcelTemplateValidationService;
import com.learnmore.application.utils.validation.TemplateValidationResult;
import com.learnmore.application.utils.exception.ExcelProcessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller cho Excel Migration API
 * Cung cấp các endpoint để thực hiện migration Excel data
 */
@RestController
@RequestMapping("/migration")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Excel Migration", description = "APIs for Excel data migration to master tables")
public class MigrationController {
    
    private final MigrationOrchestrationService migrationOrchestrationService;
    private final EnhancedExcelTemplateValidationService enhancedExcelTemplateValidationService;
    
    /**
     * Upload và thực hiện migration Excel file (synchronous)
     */
    @PostMapping(value = "/excel/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and migrate Excel file", 
               description = "Upload Excel file and perform full migration process synchronously")
    public ResponseEntity<MigrationResultDTO> uploadAndMigrateExcel(
            @Parameter(description = "Excel file to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "User who initiated the migration") @RequestParam(defaultValue = "system") String createdBy,
            @Parameter(description = "Maximum number of rows allowed (0 = no limit)") @RequestParam(defaultValue = "0") int maxRows) {
        
        log.info("Received Excel upload request: filename={}, size={}, createdBy={}, maxRows={}", 
                file.getOriginalFilename(), file.getSize(), createdBy, maxRows);
        
        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage("File is empty")
                            .build());
        }
        
        if (!isValidExcelFile(file)) {
            return ResponseEntity.badRequest()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage("Invalid file format. Only .xlsx and .xls files are supported")
                            .build());
        }
        
        try {
            // Perform migration
            MigrationResultDTO result = migrationOrchestrationService.performFullMigration(
                    file.getInputStream(), 
                    file.getOriginalFilename(), 
                    createdBy,
                    maxRows
            );
            
            if (result.isFailed()) {
                return ResponseEntity.badRequest().body(result);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage("Failed to read uploaded file: " + e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage("Migration failed: " + e.getMessage())
                            .build());
        }
    }
    
    /**
     * Upload và thực hiện migration Excel file (asynchronous)
     */
    @PostMapping(value = "/excel/upload-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and migrate Excel file asynchronously", 
               description = "Upload Excel file and start migration process asynchronously")
    public ResponseEntity<Map<String, Object>> uploadAndMigrateExcelAsync(
            @Parameter(description = "Excel file to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "User who initiated the migration") @RequestParam(defaultValue = "system") String createdBy,
            @Parameter(description = "Maximum number of rows allowed (0 = no limit)") @RequestParam(defaultValue = "0") int maxRows) {
        
        log.info("Received async Excel upload request: filename={}, size={}, createdBy={}, maxRows={}", 
                file.getOriginalFilename(), file.getSize(), createdBy, maxRows);
        
        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }
        
        if (!isValidExcelFile(file)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid file format. Only .xlsx and .xls files are supported"));
        }

        // Enforce file size limit: 10MB
        final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
        if (file.getSize() > MAX_SIZE_BYTES) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "File size exceeds 10MB limit",
                            "sizeBytes", file.getSize()
                    ));
        }
        
        try {
            // Fast dimension-based validations (fail fast if possible)
            // - Ensure there is at least one data row (excluding header)
            // - Ensure number of data rows does not exceed 1000
            // Use a dedicated stream for early validation (will be consumed)
            try (var earlyStream = file.getInputStream()) {
                int dataRows = com.learnmore.application.utils.validation.ExcelDimensionValidator
                        .validateRowCount(earlyStream, 1000, /*startRow (header rows)*/ 1);
                if (dataRows == 0) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Tập không có dữ liệu"));
                }
            } catch (com.learnmore.application.utils.exception.ExcelProcessException e) {
                log.warn("Early validation failed: {}", e.getMessage());
                return ResponseEntity.badRequest()
                        .body(Map.of("error", e.getMessage()));
            }

            // Template validation based on @ExcelColumn (fail fast if invalid)
            try (var validationStream = file.getInputStream()) {
                TemplateValidationResult templateResult = enhancedExcelTemplateValidationService
                        .validateMigrationExcel(validationStream);
                if (!templateResult.isValid()) {
                    String firstError = (templateResult.getErrors() != null && !templateResult.getErrors().isEmpty())
                            ? templateResult.getErrors().get(0).getMessage()
                            : "Template không hợp lệ";
                    throw new ExcelProcessException("File không đúng template: " + firstError);
                }
            }

            // Start async migration
            migrationOrchestrationService.performFullMigrationAsync(
                    file.getInputStream(), 
                    file.getOriginalFilename(), 
                    createdBy,
                    maxRows
            );
            
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "message", "Migration started successfully",
                            "filename", file.getOriginalFilename(),
                            "status", "PROCESSING",
                            "note", "Use /status endpoint to check progress"
                    ));
            
        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to read uploaded file: " + e.getMessage()));
        } catch (ExcelProcessException e) {
            // Fast-fail template validation or processing exception
            log.warn("Template validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to start async migration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start migration: " + e.getMessage()));
        }
    }
    
    /**
     * Get migration job status and statistics
     */
    @GetMapping("/job/{jobId}/status")
    @Operation(summary = "Get migration job status", 
               description = "Get detailed status and statistics for a migration job")
    public ResponseEntity<Map<String, Object>> getJobStatus(
            @Parameter(description = "Migration job ID") @PathVariable @NotBlank String jobId) {
        
        try {
            Map<String, Object> status = migrationOrchestrationService.getJobStatistics(jobId);
            return ResponseEntity.ok(status);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to get job status for jobId: {}, Error: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get job status: " + e.getMessage()));
        }
    }
    
    /**
     * Manual phase execution endpoints (for debugging/testing)
     */
    @PostMapping("/excel/ingest-only")
    @Operation(summary = "Perform ingest phase only", description = "For debugging purposes")
    public ResponseEntity<MigrationResultDTO> performIngestOnly(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "system") String createdBy) {
        
        if (!isValidExcelFile(file)) {
            return ResponseEntity.badRequest()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage("Invalid file format")
                            .build());
        }
        
        try {
            MigrationResultDTO result = migrationOrchestrationService.performIngestOnly(
                    file.getInputStream(), file.getOriginalFilename(), createdBy);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build());
        }
    }
    
    @PostMapping("/job/{jobId}/validate")
    @Operation(summary = "Perform validation phase only", description = "For debugging purposes")
    public ResponseEntity<MigrationResultDTO> performValidationOnly(
            @PathVariable @NotBlank String jobId) {
        
        try {
            MigrationResultDTO result = migrationOrchestrationService.performValidationOnly(jobId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build());
        }
    }
    
    @PostMapping("/job/{jobId}/apply")
    @Operation(summary = "Perform apply phase only", description = "For debugging purposes")
    public ResponseEntity<MigrationResultDTO> performApplyOnly(
            @PathVariable @NotBlank String jobId) {
        
        try {
            MigrationResultDTO result = migrationOrchestrationService.performApplyOnly(jobId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build());
        }
    }
    
    @PostMapping("/job/{jobId}/reconcile")
    @Operation(summary = "Perform reconciliation phase only", description = "For debugging purposes")
    public ResponseEntity<MigrationResultDTO> performReconciliationOnly(
            @PathVariable @NotBlank String jobId) {
        
        try {
            MigrationResultDTO result = migrationOrchestrationService.performReconciliationOnly(jobId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(MigrationResultDTO.builder()
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build());
        }
    }
    
    /**
     * System monitoring endpoints
     */
    @GetMapping("/system/metrics")
    @Operation(summary = "Get system metrics", description = "Get current system performance metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        try {
            Map<String, Object> metrics = migrationOrchestrationService.getSystemMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Failed to get system metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get system metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Cleanup endpoints
     */
    @DeleteMapping("/job/{jobId}/cleanup")
    @Operation(summary = "Cleanup staging data", description = "Cleanup staging data for completed job")
    public ResponseEntity<Map<String, Object>> cleanupStagingData(
            @PathVariable @NotBlank String jobId,
            @RequestParam(defaultValue = "true") boolean keepErrors) {
        
        try {
            migrationOrchestrationService.cleanupStagingData(jobId, keepErrors);
            return ResponseEntity.ok(Map.of(
                    "message", "Staging data cleaned up successfully",
                    "jobId", jobId,
                    "errorsKept", keepErrors
            ));
        } catch (Exception e) {
            log.error("Failed to cleanup staging data for jobId: {}, Error: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to cleanup staging data: " + e.getMessage()));
        }
    }
    
    /**
     * Validate if uploaded file is a valid Excel file
     */
    private boolean isValidExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        
        String contentType = file.getContentType();
        boolean validContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType) ||
                                   "application/vnd.ms-excel".equals(contentType);
        
        boolean validExtension = filename.toLowerCase().endsWith(".xlsx") || 
                                 filename.toLowerCase().endsWith(".xls");
        
        return validContentType || validExtension;
    }
}
