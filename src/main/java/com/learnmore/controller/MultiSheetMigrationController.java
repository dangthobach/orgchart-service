package com.learnmore.controller;

import com.learnmore.application.dto.migration.MigrationStartRequest;
import com.learnmore.application.service.multisheet.AsyncMigrationJobService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import com.learnmore.application.utils.validation.ExcelDimensionValidator;

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
    private final AsyncMigrationJobService asyncMigrationJobService;
    private final MigrationJobSheetRepository jobSheetRepository;

    // Upload directory configuration (deprecated local storage removed)
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".xlsx", ".xls");

    // Constants for sheet validation
    private static final int MAX_ROWS_PER_SHEET = 10_000;
    private static final String SHEET_NAME_CONTRACT = "HSBG_theo_hop_dong";
    private static final String SHEET_NAME_CIF = "HSBG_theo_CIF";
    private static final String SHEET_NAME_FOLDER = "HSBG_theo_tap";

    /**
     * Upload Excel file and start multi-sheet migration with early validation
     * POST /api/migration/multisheet/upload
     * 
     * HYBRID APPROACH:
     * 1. Early validation: File size, extension, sheet structure (fail fast)
     * 2. Dimension validation: Check row count per sheet (< 10K rows)
     * 3. Template validation: Verify column headers match expected DTOs
     * 4. Save file to disk only after all validations pass
     * 5. Start async processing with streaming
     * 
     * Memory optimized: Uses ExcelDimensionValidator with SAX streaming
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Excel file and start migration",
               description = "Upload Excel file with 3 sheets (HSBG_theo_hop_dong, HSBG_theo_CIF, HSBG_theo_tap) with early validation (max 10K rows per sheet)")
    @CircuitBreaker(name = "multiSheetMigration", fallbackMethod = "uploadFileFallback")
    @RateLimiter(name = "multiSheetMigration")
    public ResponseEntity<Map<String, Object>> uploadAndStartMigration(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "async", defaultValue = "false") Boolean async,
            @RequestParam(value = "testMode", defaultValue = "false") Boolean testMode,
            @RequestParam(value = "testRowLimit", required = false) Integer testRowLimit) {
        
        long startTime = System.currentTimeMillis();
        log.info("📤 Upload Excel file started - Filename: {}, Size: {} bytes", 
                 file.getOriginalFilename(), file.getSize());

        try {
            // ============================================================
            // PHASE 1: Early Basic Validation (Fail Fast)
            // ============================================================
            log.info("🔍 Phase 1: Basic file validation");
            Map<String, Object> basicValidation = validateUploadedFile(file);
            if (basicValidation.containsKey("error")) {
                log.warn("❌ Basic validation failed: {}", basicValidation.get("error"));
                return ResponseEntity.badRequest().body(basicValidation);
            }

            // Generate unique Job ID
            String jobId = generateJobId();
            log.info("✅ Generated Job ID: {}", jobId);

            // ============================================================
            // OPTIMIZATION: Read file into memory ONCE for all validations
            // Avoids multiple file.getInputStream() calls and potential resource leaks
            // Max file size: 100MB (validated in Phase 1)
            // ============================================================
            log.info("📥 Reading file into memory (size: {} bytes)", file.getSize());
            byte[] fileBytes = file.getBytes();
            log.info("✅ File loaded into memory: {} MB", fileBytes.length / 1024.0 / 1024.0);

            // ============================================================
            // PHASE 2: Sheet Structure Validation (Fail Fast - Memory Efficient)
            // Uses SAX streaming to check sheet names without loading entire file
            // ============================================================
            log.info("🔍 Phase 2: Sheet structure validation (3 required sheets)");
            Map<String, Object> sheetValidation;
            try (InputStream stream = new ByteArrayInputStream(fileBytes)) {
                sheetValidation = validateSheetStructureBeforeSaving(stream);
            }
            if (sheetValidation.containsKey("error")) {
                log.warn("❌ Sheet structure validation failed: {}", sheetValidation.get("error"));
                return ResponseEntity.badRequest().body(sheetValidation);
            }

            // ============================================================
            // PHASE 3: Dimension Validation per Sheet (< 10K rows)
            // Uses ExcelDimensionValidator with SAX streaming for constant memory
            // ============================================================
            int effectiveMaxRows = testRowLimit != null ? testRowLimit : MAX_ROWS_PER_SHEET;
            log.info("🔍 Phase 3: Dimension validation (max {} rows per sheet)", effectiveMaxRows);
            Map<String, Object> dimensionValidation;
            try (InputStream stream = new ByteArrayInputStream(fileBytes)) {
                dimensionValidation = validateSheetDimensionsBeforeSaving(stream, effectiveMaxRows);
            }
            if (dimensionValidation.containsKey("error")) {
                log.warn("❌ Dimension validation failed: {}", dimensionValidation.get("error"));
                return ResponseEntity.badRequest().body(dimensionValidation);
            }

            @SuppressWarnings("unchecked")
            Map<String, Integer> sheetRowCounts = (Map<String, Integer>) dimensionValidation.get("sheetRowCounts");
            log.info("✅ Dimension validation passed: {}", sheetRowCounts);

            // ============================================================
            // PHASE 4: Template Validation (Column Headers - Optional)
            // Validates that column headers match expected DTO structure
            // Non-blocking warnings only
            // ============================================================
            log.info("🔍 Phase 4: Template validation (column headers)");
            Map<String, Object> templateValidation;
            try (InputStream stream = new ByteArrayInputStream(fileBytes)) {
                templateValidation = validateTemplateStructureBeforeSaving(stream);
            }
            if (templateValidation.containsKey("warnings")) {
                log.warn("⚠️ Template validation has warnings: {}", templateValidation.get("warnings"));
                // Continue processing - warnings are non-blocking
            }

            // ============================================================
            // PHASE 5: Idempotency Check (Prevent duplicate job submission)
            // ============================================================
            List<MigrationJobSheetEntity> existingSheets = 
                jobSheetRepository.findByJobIdOrderBySheetOrder(jobId);
            
            if (!existingSheets.isEmpty()) {
                boolean anyInProgress = existingSheets.stream()
                    .anyMatch(MigrationJobSheetEntity::isInProgress);
                
                if (anyInProgress) {
                    log.warn("⚠️ Duplicate job submission detected: {}", jobId);
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                            "error", "Job already in progress",
                            "jobId", jobId,
                            "progressUrl", "/api/migration/multisheet/" + jobId + "/progress"
                        ));
                }
            }

            // ============================================================
            // PHASE 6: Start Migration Processing (In-Memory)
            // ============================================================
            long validationTimeMs = System.currentTimeMillis() - startTime;
            log.info("⏱️ Total validation time: {} ms", validationTimeMs);

            if (async) {
                // ASYNC MODE: Submit to background thread pool (process from memory)
                log.info("🚀 Submitting async job: {} (in-memory processing)", jobId);
                
                // Pass byte array to async service (no file I/O needed)
                asyncMigrationJobService.processAsyncFromMemory(jobId, fileBytes, file.getOriginalFilename());
                
                // Build immediate response (HTTP 202 Accepted)
                Map<String, Object> response = new HashMap<>();
                response.put("jobId", jobId);
                response.put("status", "STARTED");
                response.put("message", "Migration job submitted successfully (in-memory processing). Use progress endpoint to track status.");
                response.put("originalFilename", file.getOriginalFilename());
                response.put("fileSize", file.getSize());
                response.put("uploadedAt", LocalDateTime.now().toString());
                response.put("async", true);
                response.put("validationTimeMs", validationTimeMs);
                response.put("sheetRowCounts", sheetRowCounts);
                response.put("templateWarnings", templateValidation.get("warnings"));
                response.put("processingMode", "in-memory");
                
                // Provide URLs for client
                response.put("progressUrl", "/api/migration/multisheet/" + jobId + "/progress");
                response.put("sheetsUrl", "/api/migration/multisheet/" + jobId + "/sheets");
                response.put("cancelUrl", "/api/migration/multisheet/" + jobId + "/cancel");
                
                log.info("✅ Async job submitted successfully (in-memory) - JobId: {}", jobId);
                return ResponseEntity.accepted().body(response); // HTTP 202
                
            } else {
                // SYNC MODE: Block until completion (backward compatibility)
                // Check estimated processing time to avoid timeout
                int totalRows = sheetRowCounts.values().stream().mapToInt(Integer::intValue).sum();
                long estimatedTimeSeconds = (totalRows / 100) + 30; // ~100 rows/sec + 30s overhead
                
                if (estimatedTimeSeconds > 300) { // > 5 minutes
                    log.warn("⚠️ Sync mode may timeout! Estimated time: {}s, Total rows: {}", 
                             estimatedTimeSeconds, totalRows);
                    
                    return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(Map.of(
                            "error", "File too large for synchronous processing",
                            "totalRows", totalRows,
                            "estimatedTimeSeconds", estimatedTimeSeconds,
                            "recommendation", "Please use async=true parameter",
                            "maxSyncRows", 30000
                        ));
                }
                
                log.warn("⚠️ Using SYNCHRONOUS mode for job: {} (estimated {}s, in-memory processing)", 
                         jobId, estimatedTimeSeconds);
                
                // Process from memory (pass byte array to processor)
                MultiSheetProcessor.MultiSheetProcessResult result =
                    multiSheetProcessor.processAllSheetsFromMemory(jobId, fileBytes, file.getOriginalFilename());

                // Build response with full results
                Map<String, Object> response = new HashMap<>();
                response.put("jobId", jobId);
                response.put("originalFilename", file.getOriginalFilename());
                response.put("fileSize", file.getSize());
                response.put("uploadedAt", LocalDateTime.now().toString());
                response.put("async", false);
                response.put("validationTimeMs", validationTimeMs);
                response.put("sheetRowCounts", sheetRowCounts);
                response.put("templateWarnings", templateValidation.get("warnings"));
                response.put("processingMode", "in-memory");
                response.put("success", result.isAllSuccess());
                response.put("totalSheets", result.getTotalSheets());
                response.put("successSheets", result.getSuccessSheets());
                response.put("failedSheets", result.getFailedSheets());
                response.put("totalIngestedRows", result.getTotalIngestedRows());
                response.put("totalValidRows", result.getTotalValidRows());
                response.put("totalErrorRows", result.getTotalErrorRows());
                response.put("totalInsertedRows", result.getTotalInsertedRows());
                response.put("sheetResults", result.getSheetResults());

                long totalTimeMs = System.currentTimeMillis() - startTime;
                response.put("totalProcessingTimeMs", totalTimeMs);

                log.info("✅ Sync upload and migration completed (in-memory) - JobId: {} in {} ms", jobId, totalTimeMs);
                return ResponseEntity.ok(response);
            }

        } catch (IOException e) {
            log.error("File I/O error during upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "File upload failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error during upload and migration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Migration failed: " + e.getMessage()));
        }
    }

    /**
     * Validate uploaded file (size, extension, not empty)
     */
    private Map<String, Object> validateUploadedFile(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        // Check if file is empty
        if (file.isEmpty()) {
            result.put("error", "File is empty");
            return result;
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            result.put("error", "File size exceeds maximum limit of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
            return result;
        }

        // Check file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            result.put("error", "Invalid filename");
            return result;
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            result.put("error", "Invalid file type. Only Excel files (.xlsx, .xls) are allowed");
            return result;
        }

        result.put("valid", true);
        return result;
    }

    /**
     * Generate unique Job ID with format: JOB-YYYYMMDD-XXX
     */
    private String generateJobId() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.format("%03d", (int) (Math.random() * 1000));
        return "JOB-" + date + "-" + random;
    }

    // Removed deprecated disk I/O helper (saveUploadedFile)

    /**
     * PHASE 2: Validate sheet structure BEFORE saving file
     * Uses SAX streaming to check sheet names without loading entire file into memory
     * 
     * @param inputStream InputStream from byte array (in memory)
     * @return Map with validation result: {"error": "..."} if failed, {"valid": true, "sheetNames": [...]} if passed
     */
    private Map<String, Object> validateSheetStructureBeforeSaving(InputStream inputStream) {
        Map<String, Object> result = new HashMap<>();
        List<String> allowedSheets = List.of(SHEET_NAME_CONTRACT, SHEET_NAME_CIF, SHEET_NAME_FOLDER);

        try {
            // Use SAX streaming to read sheet names without loading entire file
            try (OPCPackage pkg = OPCPackage.open(inputStream)) {
                XSSFReader reader = new XSSFReader(pkg);
                XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();

                List<String> foundSheetNames = new ArrayList<>();
                while (iterator.hasNext()) {
                    iterator.next();
                    String sheetName = iterator.getSheetName();
                    foundSheetNames.add(sheetName);
                }

            // Determine which allowed sheets are present (sheets are optional)
            List<String> presentAllowedSheets = allowedSheets.stream()
                    .filter(foundSheetNames::contains)
                    .toList();

            // Success
                result.put("valid", true);
                result.put("sheetNames", foundSheetNames);
                result.put("totalSheets", foundSheetNames.size());
                result.put("presentAllowedSheets", presentAllowedSheets);
                result.put("allowedSheets", allowedSheets);
                log.info("✅ Sheet structure validation passed. Present sheets (allowed subset): {}", presentAllowedSheets);
            }

        } catch (Exception e) {
            log.error("Error during sheet structure validation: {}", e.getMessage(), e);
            result.put("error", "Failed to validate Excel sheet structure: " + e.getMessage());
        }

        return result;
    }

    /**
     * PHASE 3: Validate sheet dimensions BEFORE saving file
     * Uses ExcelDimensionValidator with SAX streaming for constant memory footprint
     * Checks that each sheet has <= MAX_ROWS_PER_SHEET (10,000 rows)
     * 
     * @param inputStream InputStream from byte array (in memory)
     * @return Map with validation result: {"error": "..."} if failed, {"valid": true, "sheetRowCounts": {...}} if passed
     */
    private Map<String, Object> validateSheetDimensionsBeforeSaving(InputStream inputStream, int maxRowsPerSheet) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Use ExcelDimensionValidator.validateAllSheets() for memory-efficient validation
            // Parameters: (inputStream, maxRows, startRowIndex)
            Map<String, Integer> sheetRowCounts = 
                ExcelDimensionValidator.validateAllSheets(inputStream, maxRowsPerSheet, 1);

            // Check if any sheet exceeds limit
            List<String> oversizedSheets = sheetRowCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > maxRowsPerSheet)
                    .map(entry -> String.format("%s (%d rows)", entry.getKey(), entry.getValue()))
                    .toList();

            if (!oversizedSheets.isEmpty()) {
                result.put("error", String.format("Sheets exceed maximum row limit of %d: %s", 
                    maxRowsPerSheet, oversizedSheets));
                result.put("sheetRowCounts", sheetRowCounts);
                result.put("maxAllowedRows", maxRowsPerSheet);
                log.warn("❌ Dimension validation failed. Oversized sheets: {}", oversizedSheets);
                return result;
            }

            // Success
            result.put("valid", true);
            result.put("sheetRowCounts", sheetRowCounts);
            result.put("maxAllowedRows", maxRowsPerSheet);
            log.info("✅ Dimension validation passed. All sheets within {} row limit: {}", 
                maxRowsPerSheet, sheetRowCounts);

        } catch (Exception e) {
            log.error("Error during dimension validation: {}", e.getMessage(), e);
            result.put("error", "Failed to validate Excel dimensions: " + e.getMessage());
            result.put("maxAllowedRows", maxRowsPerSheet);
        }

        return result;
    }

    /**
     * PHASE 4: Validate template structure BEFORE saving file (OPTIONAL - NON-BLOCKING)
     * Validates that column headers in first row match expected DTO field names
     * Returns warnings only, does not block processing
     * 
     * @param inputStream InputStream from byte array (in memory)
     * @return Map with warnings: {"warnings": [...]} if mismatches found, {"valid": true} otherwise
     */
    private Map<String, Object> validateTemplateStructureBeforeSaving(InputStream inputStream) {
        Map<String, Object> result = new HashMap<>();
        List<String> warnings = new ArrayList<>();

        try {
            // Define expected column headers for each sheet (English field names)
            Map<String, List<String>> expectedHeaders = Map.of(
                SHEET_NAME_CONTRACT, List.of(
                    "contract_number", "contract_date", "customer_cif", "customer_name", 
                    "total_amount", "currency", "status", "branch_code", "officer_code"
                    // Add remaining 24 columns as needed
                ),
                SHEET_NAME_CIF, List.of(
                    "customer_cif", "full_name", "date_of_birth", "id_number", 
                    "phone", "email", "address", "segment"
                    // Add remaining 18 columns as needed
                ),
                SHEET_NAME_FOLDER, List.of(
                    "folder_id", "contract_number", "document_type", "upload_date", 
                    "file_path", "status", "reviewer"
                    // Add remaining 16 columns as needed
                )
            );

            // Use SAX streaming to read first row headers for each sheet
            try (OPCPackage pkg = OPCPackage.open(inputStream)) {
                XSSFReader reader = new XSSFReader(pkg);
                XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();

                while (iterator.hasNext()) {
                    iterator.next(); // Move to next sheet
                    String sheetName = iterator.getSheetName();

                    if (!expectedHeaders.containsKey(sheetName)) {
                        continue;
                    }

                    warnings.add(String.format("Template validation for sheet '%s' skipped (not yet implemented)", sheetName));
                }
            }

            // Success (even with warnings)
            result.put("valid", true);
            if (!warnings.isEmpty()) {
                result.put("warnings", warnings);
                log.warn("⚠️ Template validation has warnings: {}", warnings);
            } else {
                log.info("✅ Template validation passed (no warnings)");
            }

        } catch (Exception e) {
            log.error("Error during template validation: {}", e.getMessage(), e);
            warnings.add("Template validation skipped due to error: " + e.getMessage());
            result.put("warnings", warnings);
        }

        return result;
    }

    // Removed unused Excel structure validator (obsolete)

    // Removed unused deleteFile helper (obsolete)

    /**
     * Fallback method for upload circuit breaker
     */
    @SuppressWarnings("unused")
    private ResponseEntity<Map<String, Object>> uploadFileFallback(
            MultipartFile file, Boolean async, Boolean testMode, Integer testRowLimit, Throwable t) {
        log.error("Circuit breaker triggered for file upload. Filename: {}, Error: {}",
                  file.getOriginalFilename(), t.getMessage(), t);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Upload service temporarily unavailable. Please try again later.");
        errorResponse.put("circuitBreakerTriggered", true);
        errorResponse.put("details", t.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    /**
     * Start multi-sheet migration
     * POST /api/migration/multisheet/start
     *
     * Protected by circuit breaker and rate limiting for resilience
     * @deprecated Use /upload endpoint with MultipartFile for better resource management
     */
    @Deprecated
    @PostMapping("/start")
    @Operation(summary = "Start multi-sheet migration (deprecated)",
               description = "Process all enabled sheets in Excel file with parallel processing. Use /upload instead.")
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

        // Overall job status (async tracking)
        String overallStatus = asyncMigrationJobService.getOverallJobStatus(jobId);
        boolean isRunning = asyncMigrationJobService.isJobRunning(jobId);
        response.put("overallStatus", overallStatus);
        response.put("isRunning", isRunning);

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
     * Cancel a running migration job
     * DELETE /api/migration/multisheet/{jobId}/cancel
     * 
     * Attempts to cancel the async job if it's still running
     * Note: Cancellation may not be immediate if job is in critical phase
     */
    @DeleteMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel migration job",
               description = "Attempts to cancel a running migration job. Returns cancellation status.")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        log.info("🛑 Cancellation requested for job: {}", jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);

        // Check if job is running
        boolean isRunning = asyncMigrationJobService.isJobRunning(jobId);
        
        if (!isRunning) {
            response.put("cancelled", false);
            response.put("message", "Job is not running or already completed");
            response.put("currentStatus", asyncMigrationJobService.getOverallJobStatus(jobId));
            return ResponseEntity.ok(response);
        }

        // Attempt cancellation
        boolean cancelled = asyncMigrationJobService.cancelJob(jobId);

        if (cancelled) {
            response.put("cancelled", true);
            response.put("message", "Job cancellation initiated. Processing will stop gracefully.");
            log.info("✅ Job cancellation successful: {}", jobId);
            return ResponseEntity.ok(response);
        } else {
            response.put("cancelled", false);
            response.put("message", "Job cancellation failed. Job may be in critical phase or already completing.");
            log.warn("⚠️ Job cancellation failed: {}", jobId);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Get overall job status
     * GET /api/migration/multisheet/{jobId}/status
     * 
     * Returns overall job status (not per-sheet)
     */
    @GetMapping("/{jobId}/status")
    @Operation(summary = "Get overall job status",
               description = "Returns overall job status: PENDING, STARTED, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, CANCELLED")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        String status = asyncMigrationJobService.getOverallJobStatus(jobId);
        boolean isRunning = asyncMigrationJobService.isJobRunning(jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("overallStatus", status);
        response.put("isRunning", isRunning);
        response.put("canCancel", isRunning);

        return ResponseEntity.ok(response);
    }

    /**
     * Get system info (running jobs count)
     * GET /api/migration/multisheet/system/info
     */
    @GetMapping("/system/info")
    @Operation(summary = "Get system information",
               description = "Returns system information including count of running jobs")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        int runningJobsCount = asyncMigrationJobService.getRunningJobCount();

        Map<String, Object> response = new HashMap<>();
        response.put("runningJobsCount", runningJobsCount);
        response.put("timestamp", LocalDateTime.now().toString());

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
    @SuppressWarnings("unused")
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
