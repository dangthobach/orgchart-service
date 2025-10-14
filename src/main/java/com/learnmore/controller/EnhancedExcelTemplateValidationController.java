package com.learnmore.controller;

import com.learnmore.application.service.EnhancedExcelTemplateValidationService;
import com.learnmore.application.utils.validation.TemplateValidationResult;
import com.learnmore.application.utils.validation.ValidationError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Enhanced Controller để validate Excel file theo @ExcelColumn annotations
 * Tận dụng ExcelFacade và reflection để validation
 */
@Slf4j
@RestController
@RequestMapping("/api/excel/enhanced-template")
@RequiredArgsConstructor
public class EnhancedExcelTemplateValidationController {
    
    private final EnhancedExcelTemplateValidationService enhancedValidationService;
    
    /**
     * Validate file Excel theo class có @ExcelColumn annotations
     */
    @PostMapping("/validate")
    public ResponseEntity<TemplateValidationResult> validateExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("className") String className) {
        
        log.info("Validating Excel file: {} with class: {}", file.getOriginalFilename(), className);
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    TemplateValidationResult.failure(
                        java.util.List.of(ValidationError.of(
                            "EMPTY_FILE", 
                            "File Excel không được để trống",
                            0, 0, "FILE", "FileValidation"
                        )),
                        null
                    )
                );
            }
            
            if (!isExcelFile(file)) {
                return ResponseEntity.badRequest().body(
                    TemplateValidationResult.failure(
                        java.util.List.of(ValidationError.of(
                            "INVALID_FILE_TYPE", 
                            "File phải có định dạng Excel (.xlsx, .xls)",
                            0, 0, "FILE", "FileValidation"
                        )),
                        null
                    )
                );
            }
            
            Class<?> targetClass = Class.forName(className);
            TemplateValidationResult result = enhancedValidationService.validateExcel(
                file.getInputStream(), targetClass);
            
            return ResponseEntity.ok(result);
            
        } catch (ClassNotFoundException e) {
            log.error("Class not found: {}", className, e);
            return ResponseEntity.badRequest().body(
                TemplateValidationResult.failure(
                    java.util.List.of(ValidationError.of(
                        "CLASS_NOT_FOUND", 
                        "Class không tồn tại: " + className,
                        0, 0, "CLASS", "ClassValidation"
                    )),
                    null
                )
            );
        } catch (IOException e) {
            log.error("Error reading file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                TemplateValidationResult.failure(
                    java.util.List.of(ValidationError.of(
                        "FILE_READ_ERROR", 
                        "Không thể đọc file: " + e.getMessage(),
                        0, 0, "FILE", "FileReadError"
                    )),
                    null
                )
            );
        }
    }
    
    /**
     * Validate file Excel cho User class
     */
    @PostMapping("/validate/user")
    public ResponseEntity<TemplateValidationResult> validateUserExcel(
            @RequestParam("file") MultipartFile file) {
        
        log.info("Validating User Excel file: {}", file.getOriginalFilename());
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    TemplateValidationResult.failure(
                        java.util.List.of(ValidationError.of(
                            "EMPTY_FILE", 
                            "File Excel không được để trống",
                            0, 0, "FILE", "FileValidation"
                        )),
                        null
                    )
                );
            }
            
            TemplateValidationResult result = enhancedValidationService.validateUserExcel(
                file.getInputStream());
            
            return ResponseEntity.ok(result);
            
        } catch (IOException e) {
            log.error("Error reading user file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                TemplateValidationResult.failure(
                    java.util.List.of(ValidationError.of(
                        "FILE_READ_ERROR", 
                        "Không thể đọc file: " + e.getMessage(),
                        0, 0, "FILE", "FileReadError"
                    )),
                    null
                )
            );
        }
    }
    
    /**
     * Validate file Excel cho ExcelRowDTO class (migration)
     */
    @PostMapping("/validate/migration")
    public ResponseEntity<TemplateValidationResult> validateMigrationExcel(
            @RequestParam("file") MultipartFile file) {
        
        log.info("Validating migration Excel file: {}", file.getOriginalFilename());
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    TemplateValidationResult.failure(
                        java.util.List.of(ValidationError.of(
                            "EMPTY_FILE", 
                            "File Excel không được để trống",
                            0, 0, "FILE", "FileValidation"
                        )),
                        null
                    )
                );
            }
            
            TemplateValidationResult result = enhancedValidationService.validateMigrationExcel(
                file.getInputStream());
            
            return ResponseEntity.ok(result);
            
        } catch (IOException e) {
            log.error("Error reading migration file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                TemplateValidationResult.failure(
                    java.util.List.of(ValidationError.of(
                        "FILE_READ_ERROR", 
                        "Không thể đọc file: " + e.getMessage(),
                        0, 0, "FILE", "FileReadError"
                    )),
                    null
                )
            );
        }
    }
    
    /**
     * Validate và đọc dữ liệu Excel
     */
    @PostMapping("/validate-and-read")
    public ResponseEntity<Map<String, Object>> validateAndReadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("className") String className) {
        
        log.info("Validating and reading Excel file: {} with class: {}", 
                file.getOriginalFilename(), className);
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "File Excel không được để trống")
                );
            }
            
            Class<?> targetClass = Class.forName(className);
            EnhancedExcelTemplateValidationService.ExcelValidationAndReadResult<?> result = 
                enhancedValidationService.validateAndReadExcel(file.getInputStream(), targetClass);
            
            if (result.isValid()) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "File Excel hợp lệ và đã đọc thành công",
                    "dataCount", result.getData() != null ? result.getData().size() : 0,
                    "validationResult", result.getValidationResult()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "message", "File Excel không hợp lệ",
                    "validationResult", result.getValidationResult()
                ));
            }
            
        } catch (ClassNotFoundException e) {
            log.error("Class not found: {}", className, e);
            return ResponseEntity.badRequest().body(
                Map.of("error", "Class không tồn tại: " + className)
            );
        } catch (IOException e) {
            log.error("Error reading file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Không thể đọc file: " + e.getMessage())
            );
        }
    }
    
    /**
     * Validate và đọc dữ liệu Excel theo batch
     */
    @PostMapping("/validate-and-read-batch")
    public ResponseEntity<Map<String, Object>> validateAndReadExcelBatch(
            @RequestParam("file") MultipartFile file,
            @RequestParam("className") String className,
            @RequestParam(value = "batchSize", defaultValue = "1000") int batchSize) {
        
        log.info("Validating and reading Excel file in batches: {} with class: {}", 
                file.getOriginalFilename(), className);
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "File Excel không được để trống")
                );
            }
            
            Class<?> targetClass = Class.forName(className);
            
            // Tạo batch processor để đếm số lượng records
            java.util.concurrent.atomic.AtomicInteger recordCount = new java.util.concurrent.atomic.AtomicInteger(0);
            
            // Sử dụng raw type để tránh generic type issues
            @SuppressWarnings("unchecked")
            EnhancedExcelTemplateValidationService.ExcelValidationAndBatchReadResult<Object> result = 
                (EnhancedExcelTemplateValidationService.ExcelValidationAndBatchReadResult<Object>) 
                enhancedValidationService.validateAndReadExcelBatch(
                    file.getInputStream(), targetClass, batch -> {
                        recordCount.addAndGet(batch.size());
                        log.debug("Processed batch of {} records, total: {}", batch.size(), recordCount.get());
                    });
            
            if (result.isValid()) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "File Excel hợp lệ và đã đọc thành công theo batch",
                    "totalRecords", recordCount.get(),
                    "processingResult", result.getProcessingResult(),
                    "validationResult", result.getValidationResult()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "message", "File Excel không hợp lệ",
                    "validationResult", result.getValidationResult()
                ));
            }
            
        } catch (ClassNotFoundException e) {
            log.error("Class not found: {}", className, e);
            return ResponseEntity.badRequest().body(
                Map.of("error", "Class không tồn tại: " + className)
            );
        } catch (IOException e) {
            log.error("Error reading file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Không thể đọc file: " + e.getMessage())
            );
        }
    }
    
    /**
     * Lấy danh sách class có sẵn
     */
    @GetMapping("/classes")
    public ResponseEntity<Map<String, String>> getAvailableClasses() {
        log.info("Getting available classes");
        
        Map<String, String> classes = enhancedValidationService.getAvailableClasses();
        return ResponseEntity.ok(classes);
    }
    
    /**
     * Lấy thông tin chi tiết của class
     */
    @GetMapping("/classes/{className}")
    public ResponseEntity<EnhancedExcelTemplateValidationService.TemplateInfo> getClassInfo(
            @PathVariable String className) {
        
        log.info("Getting class info for: {}", className);
        
        try {
            Class<?> targetClass = Class.forName(className);
            EnhancedExcelTemplateValidationService.TemplateInfo info = 
                enhancedValidationService.getTemplateInfo(targetClass);
            
            return ResponseEntity.ok(info);
            
        } catch (ClassNotFoundException e) {
            log.error("Class not found: {}", className, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Kiểm tra nhanh header có hợp lệ không
     */
    @PostMapping("/validate/header")
    public ResponseEntity<Map<String, Object>> validateHeader(
            @RequestParam("file") MultipartFile file,
            @RequestParam("className") String className) {
        
        log.info("Validating header for file: {} with class: {}", 
                file.getOriginalFilename(), className);
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("valid", false, "message", "File Excel không được để trống")
                );
            }
            
            Class<?> targetClass = Class.forName(className);
            boolean isValid = enhancedValidationService.isHeaderValid(
                file.getInputStream(), targetClass);
            
            return ResponseEntity.ok(Map.of(
                "valid", isValid,
                "message", isValid ? "Header hợp lệ" : "Header không hợp lệ"
            ));
            
        } catch (ClassNotFoundException e) {
            log.error("Class not found: {}", className, e);
            return ResponseEntity.badRequest().body(
                Map.of("valid", false, "message", "Class không tồn tại: " + className)
            );
        } catch (IOException e) {
            log.error("Error reading file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                Map.of("valid", false, "message", "Không thể đọc file: " + e.getMessage())
            );
        }
    }
    
    /**
     * Kiểm tra nhanh cấu trúc có hợp lệ không
     */
    @PostMapping("/validate/structure")
    public ResponseEntity<Map<String, Object>> validateStructure(
            @RequestParam("file") MultipartFile file,
            @RequestParam("className") String className) {
        
        log.info("Validating structure for file: {} with class: {}", 
                file.getOriginalFilename(), className);
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("valid", false, "message", "File Excel không được để trống")
                );
            }
            
            Class<?> targetClass = Class.forName(className);
            boolean isValid = enhancedValidationService.isStructureValid(
                file.getInputStream(), targetClass);
            
            return ResponseEntity.ok(Map.of(
                "valid", isValid,
                "message", isValid ? "Cấu trúc hợp lệ" : "Cấu trúc không hợp lệ"
            ));
            
        } catch (ClassNotFoundException e) {
            log.error("Class not found: {}", className, e);
            return ResponseEntity.badRequest().body(
                Map.of("valid", false, "message", "Class không tồn tại: " + className)
            );
        } catch (IOException e) {
            log.error("Error reading file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                Map.of("valid", false, "message", "Không thể đọc file: " + e.getMessage())
            );
        }
    }
    
    /**
     * Kiểm tra file có phải Excel không
     */
    private boolean isExcelFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) return false;
        
        String extension = filename.toLowerCase();
        return extension.endsWith(".xlsx") || extension.endsWith(".xls");
    }
}
