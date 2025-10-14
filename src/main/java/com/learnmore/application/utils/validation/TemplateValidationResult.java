package com.learnmore.application.utils.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kết quả validation template Excel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateValidationResult {
    
    /**
     * File có hợp lệ theo template không
     */
    private boolean valid;
    
    /**
     * Danh sách lỗi
     */
    private List<ValidationError> errors;
    
    /**
     * Danh sách cảnh báo
     */
    private List<ValidationWarning> warnings;
    
    /**
     * Template definition được sử dụng
     */
    private ExcelTemplateDefinition templateDefinition;
    
    /**
     * Tổng số lỗi
     */
    public int getErrorCount() {
        return errors != null ? errors.size() : 0;
    }
    
    /**
     * Tổng số cảnh báo
     */
    public int getWarningCount() {
        return warnings != null ? warnings.size() : 0;
    }
    
    /**
     * Có lỗi không
     */
    public boolean hasErrors() {
        return getErrorCount() > 0;
    }
    
    /**
     * Có cảnh báo không
     */
    public boolean hasWarnings() {
        return getWarningCount() > 0;
    }
    
    /**
     * Tạo kết quả thành công
     */
    public static TemplateValidationResult success(ExcelTemplateDefinition templateDefinition) {
        return TemplateValidationResult.builder()
                .valid(true)
                .errors(java.util.List.of())
                .warnings(java.util.List.of())
                .templateDefinition(templateDefinition)
                .build();
    }
    
    /**
     * Tạo kết quả thất bại
     */
    public static TemplateValidationResult failure(List<ValidationError> errors, 
                                                   ExcelTemplateDefinition templateDefinition) {
        return TemplateValidationResult.builder()
                .valid(false)
                .errors(errors)
                .warnings(java.util.List.of())
                .templateDefinition(templateDefinition)
                .build();
    }
    
    /**
     * Tạo kết quả với cảnh báo
     */
    public static TemplateValidationResult withWarnings(boolean valid, 
                                                        List<ValidationError> errors,
                                                        List<ValidationWarning> warnings,
                                                        ExcelTemplateDefinition templateDefinition) {
        return TemplateValidationResult.builder()
                .valid(valid)
                .errors(errors)
                .warnings(warnings)
                .templateDefinition(templateDefinition)
                .build();
    }
}
