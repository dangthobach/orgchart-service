package com.learnmore.application.service.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single validation error
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    private String errorType;
    private String errorField;
    private String errorValue;
    private String errorMessage;
    private String errorCode;

    /**
     * Create error for required field
     */
    public static ValidationError requiredField(String field) {
        return ValidationError.builder()
                .errorType("REQUIRED_MISSING")
                .errorField(field)
                .errorMessage("Trường bắt buộc không được để trống")
                .errorCode("VAL001")
                .build();
    }

    /**
     * Create error for invalid date format
     */
    public static ValidationError invalidDateFormat(String field, String value) {
        return ValidationError.builder()
                .errorType("INVALID_DATE")
                .errorField(field)
                .errorValue(value)
                .errorMessage("Định dạng ngày không hợp lệ (phải là yyyy-MM-dd)")
                .errorCode("VAL002")
                .build();
    }

    /**
     * Create error for invalid enum value
     */
    public static ValidationError invalidEnumValue(String field, String value, String allowedValues) {
        return ValidationError.builder()
                .errorType("INVALID_ENUM")
                .errorField(field)
                .errorValue(value)
                .errorMessage(String.format("Giá trị không hợp lệ. Các giá trị cho phép: %s", allowedValues))
                .errorCode("VAL003")
                .build();
    }

    /**
     * Create error for duplicate record
     */
    public static ValidationError duplicateRecord(String businessKey, String location) {
        return ValidationError.builder()
                .errorType(location.equals("file") ? "DUP_IN_FILE" : "DUP_IN_DB")
                .errorField("business_key")
                .errorValue(businessKey)
                .errorMessage(String.format("Dữ liệu trùng lặp %s", location.equals("file") ? "trong file" : "với database"))
                .errorCode("VAL004")
                .build();
    }

    /**
     * Create error for reference not found
     */
    public static ValidationError referenceNotFound(String field, String value, String masterTable) {
        return ValidationError.builder()
                .errorType("REF_NOT_FOUND")
                .errorField(field)
                .errorValue(value)
                .errorMessage(String.format("Giá trị không tồn tại trong bảng %s", masterTable))
                .errorCode("VAL005")
                .build();
    }

    /**
     * Create error for business rule violation
     */
    public static ValidationError businessRuleViolation(String field, String value, String message) {
        return ValidationError.builder()
                .errorType("BUSINESS_RULE")
                .errorField(field)
                .errorValue(value)
                .errorMessage(message)
                .errorCode("VAL006")
                .build();
    }

    /**
     * Create error for invalid pattern
     */
    public static ValidationError invalidPattern(String field, String value, String pattern) {
        return ValidationError.builder()
                .errorType("INVALID_PATTERN")
                .errorField(field)
                .errorValue(value)
                .errorMessage(String.format("Giá trị không khớp với pattern: %s", pattern))
                .errorCode("VAL007")
                .build();
    }
}
