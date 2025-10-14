package com.learnmore.application.service;

import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.utils.validation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced Excel Template Validation Service
 * Tận dụng ExcelFacade và @ExcelColumn annotation để validation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedExcelTemplateValidationService {
    
    private final ExcelFacade excelFacade;
    private final Map<Class<?>, ExcelReflectionTemplateValidator> validatorCache = new HashMap<>();
    
    /**
     * Validate file Excel theo class có @ExcelColumn annotations
     */
    public TemplateValidationResult validateExcel(InputStream inputStream, Class<?> targetClass) {
        log.info("Validating Excel file with reflection-based template for class: {}", targetClass.getSimpleName());
        
        ExcelReflectionTemplateValidator validator = getOrCreateValidator(targetClass);
        return validator.validate(inputStream);
    }
    
    /**
     * Validate file Excel cho User class
     */
    public TemplateValidationResult validateUserExcel(InputStream inputStream) {
        try {
            Class<?> userClass = Class.forName("com.learnmore.application.dto.User");
            return validateExcel(inputStream, userClass);
        } catch (ClassNotFoundException e) {
            log.error("User class not found", e);
            return TemplateValidationResult.failure(
                java.util.List.of(ValidationError.of(
                    "CLASS_NOT_FOUND", 
                    "User class không tồn tại",
                    0, 0, "CLASS", "ClassValidation"
                )),
                null
            );
        }
    }
    
    /**
     * Validate file Excel cho ExcelRowDTO class (migration)
     */
    public TemplateValidationResult validateMigrationExcel(InputStream inputStream) {
        try {
            Class<?> migrationClass = Class.forName("com.learnmore.application.dto.migration.ExcelRowDTO");
            return validateExcel(inputStream, migrationClass);
        } catch (ClassNotFoundException e) {
            log.error("ExcelRowDTO class not found", e);
            return TemplateValidationResult.failure(
                java.util.List.of(ValidationError.of(
                    "CLASS_NOT_FOUND", 
                    "ExcelRowDTO class không tồn tại",
                    0, 0, "CLASS", "ClassValidation"
                )),
                null
            );
        }
    }
    
    /**
     * Validate file Excel và đọc dữ liệu nếu hợp lệ
     */
    public <T> ExcelValidationAndReadResult<T> validateAndReadExcel(InputStream inputStream, Class<T> targetClass) {
        log.info("Validating and reading Excel file for class: {}", targetClass.getSimpleName());
        
        // 1. Validate template trước
        TemplateValidationResult validationResult = validateExcel(inputStream, targetClass);
        
        if (!validationResult.isValid()) {
            return ExcelValidationAndReadResult.<T>builder()
                    .valid(false)
                    .validationResult(validationResult)
                    .data(null)
                    .build();
        }
        
        // 2. Nếu hợp lệ, đọc dữ liệu bằng ExcelFacade
        try {
            // Reset stream để đọc lại
            inputStream.reset();
            
            java.util.List<T> data = excelFacade.readExcel(inputStream, targetClass);
            
            return ExcelValidationAndReadResult.<T>builder()
                    .valid(true)
                    .validationResult(validationResult)
                    .data(data)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error reading Excel data after validation", e);
            return ExcelValidationAndReadResult.<T>builder()
                    .valid(false)
                    .validationResult(TemplateValidationResult.failure(
                        java.util.List.of(ValidationError.of(
                            "READ_ERROR", 
                            "Lỗi khi đọc dữ liệu: " + e.getMessage(),
                            0, 0, "DATA", "ReadError"
                        )),
                        validationResult.getTemplateDefinition()
                    ))
                    .data(null)
                    .build();
        }
    }
    
    /**
     * Validate file Excel và đọc dữ liệu theo batch nếu hợp lệ
     */
    public <T> ExcelValidationAndBatchReadResult<T> validateAndReadExcelBatch(
            InputStream inputStream, 
            Class<T> targetClass,
            java.util.function.Consumer<java.util.List<T>> batchProcessor) {
        
        log.info("Validating and reading Excel file in batches for class: {}", targetClass.getSimpleName());
        
        // 1. Validate template trước
        TemplateValidationResult validationResult = validateExcel(inputStream, targetClass);
        
        if (!validationResult.isValid()) {
            return ExcelValidationAndBatchReadResult.<T>builder()
                    .valid(false)
                    .validationResult(validationResult)
                    .processingResult(null)
                    .build();
        }
        
        // 2. Nếu hợp lệ, đọc dữ liệu theo batch bằng ExcelFacade
        try {
            // Reset stream để đọc lại
            inputStream.reset();
            
            com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult processingResult = 
                excelFacade.readExcel(inputStream, targetClass, batchProcessor);
            
            return ExcelValidationAndBatchReadResult.<T>builder()
                    .valid(true)
                    .validationResult(validationResult)
                    .processingResult(processingResult)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error reading Excel data in batches after validation", e);
            return ExcelValidationAndBatchReadResult.<T>builder()
                    .valid(false)
                    .validationResult(TemplateValidationResult.failure(
                        java.util.List.of(ValidationError.of(
                            "BATCH_READ_ERROR", 
                            "Lỗi khi đọc dữ liệu theo batch: " + e.getMessage(),
                            0, 0, "DATA", "BatchReadError"
                        )),
                        validationResult.getTemplateDefinition()
                    ))
                    .processingResult(null)
                    .build();
        }
    }
    
    /**
     * Kiểm tra nhanh header có hợp lệ không
     */
    public boolean isHeaderValid(InputStream inputStream, Class<?> targetClass) {
        try {
            TemplateValidationResult result = validateExcel(inputStream, targetClass);
            // Chỉ kiểm tra lỗi liên quan đến header
            return result.getErrors().stream()
                    .noneMatch(error -> error.getValidationType().contains("Header"));
        } catch (Exception e) {
            log.error("Error during header validation", e);
            return false;
        }
    }
    
    /**
     * Kiểm tra nhanh cấu trúc có hợp lệ không
     */
    public boolean isStructureValid(InputStream inputStream, Class<?> targetClass) {
        try {
            TemplateValidationResult result = validateExcel(inputStream, targetClass);
            // Chỉ kiểm tra lỗi liên quan đến cấu trúc
            return result.getErrors().stream()
                    .noneMatch(error -> error.getValidationType().contains("Structure") || 
                                      error.getValidationType().contains("Sheet"));
        } catch (Exception e) {
            log.error("Error during structure validation", e);
            return false;
        }
    }
    
    /**
     * Lấy thông tin template từ class
     */
    public TemplateInfo getTemplateInfo(Class<?> targetClass) {
        ExcelReflectionTemplateValidator validator = getOrCreateValidator(targetClass);
        ExcelTemplateDefinition templateDefinition = validator.buildTemplateDefinitionFromReflection();
        
        return TemplateInfo.builder()
                .templateName(templateDefinition.getTemplateName())
                .description(templateDefinition.getDescription())
                .version(templateDefinition.getVersion())
                .requiredColumnCount(templateDefinition.getRequiredColumns().size())
                .optionalColumnCount(templateDefinition.getOptionalColumns().size())
                .totalColumnCount(templateDefinition.getAllColumns().size())
                .minDataRows(templateDefinition.getMinDataRows())
                .maxDataRows(templateDefinition.getMaxDataRows())
                .build();
    }
    
    /**
     * Lấy danh sách class có sẵn
     */
    public Map<String, String> getAvailableClasses() {
        Map<String, String> classes = new HashMap<>();
        classes.put("User", "com.learnmore.application.dto.User");
        classes.put("ExcelRowDTO", "com.learnmore.application.dto.migration.ExcelRowDTO");
        return classes;
    }
    
    /**
     * Get or create validator từ cache
     */
    private ExcelReflectionTemplateValidator getOrCreateValidator(Class<?> targetClass) {
        return validatorCache.computeIfAbsent(targetClass, ExcelReflectionTemplateValidator::new);
    }
    
    /**
     * Thông tin template
     */
    @lombok.Data
    @lombok.Builder
    public static class TemplateInfo {
        private String templateName;
        private String description;
        private String version;
        private int requiredColumnCount;
        private int optionalColumnCount;
        private int totalColumnCount;
        private int minDataRows;
        private int maxDataRows;
    }
    
    /**
     * Kết quả validation và đọc dữ liệu
     */
    @lombok.Data
    @lombok.Builder
    public static class ExcelValidationAndReadResult<T> {
        private boolean valid;
        private TemplateValidationResult validationResult;
        private java.util.List<T> data;
    }
    
    /**
     * Kết quả validation và đọc dữ liệu theo batch
     */
    @lombok.Data
    @lombok.Builder
    public static class ExcelValidationAndBatchReadResult<T> {
        private boolean valid;
        private TemplateValidationResult validationResult;
        private com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult processingResult;
    }
}
