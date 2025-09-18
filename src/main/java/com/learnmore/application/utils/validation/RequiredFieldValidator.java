package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Enhanced validator for required fields with instance-level and field-level validation
 * Supports both SAX parsing and traditional validation
 */
@Slf4j
public class RequiredFieldValidator implements ValidationRule {
    
    private final boolean allowEmptyString;
    private final Set<String> requiredFields;
    
    public RequiredFieldValidator() {
        this(false);
    }
    
    public RequiredFieldValidator(boolean allowEmptyString) {
        this.allowEmptyString = allowEmptyString;
        this.requiredFields = Set.of();
    }
    
    public RequiredFieldValidator(Set<String> requiredFields) {
        this.allowEmptyString = false;
        this.requiredFields = requiredFields != null ? requiredFields : Set.of();
    }
    
    @Override
    public ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber) {
        if (value == null) {
            return ValidationResult.failure(
                "Field '" + fieldName + "' is required but was null",
                fieldName, rowNumber, columnNumber, "null", getRuleName()
            );
        }
        
        if (!allowEmptyString && value instanceof String && ((String) value).trim().isEmpty()) {
            return ValidationResult.failure(
                "Field '" + fieldName + "' is required but was empty",
                fieldName, rowNumber, columnNumber, (String) value, getRuleName()
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Instance-level validation for SAX processing
     */
    public void validate(Object instance, int rowNumber) throws ValidationException {
        if (instance == null) {
            throw new ValidationException("Instance cannot be null at row " + rowNumber);
        }
        
        Class<?> clazz = instance.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                String columnName = annotation.name();
                
                // Check if field is required
                if (requiredFields.contains(columnName) || requiredFields.contains(field.getName())) {
                    field.setAccessible(true);
                    
                    try {
                        Object value = field.get(instance);
                        
                        if (isFieldEmpty(value)) {
                            throw new ValidationException(
                                String.format("Required field '%s' is empty at row %d", columnName, rowNumber)
                            );
                        }
                        
                    } catch (IllegalAccessException e) {
                        log.error("Failed to access field '{}' for validation: {}", columnName, e.getMessage());
                        throw new ValidationException(
                            String.format("Failed to validate required field '%s' at row %d", columnName, rowNumber)
                        );
                    }
                }
            }
        }
        
        log.debug("Required field validation passed for row {}", rowNumber);
    }
    
    /**
     * Check if field value is considered empty
     */
    private boolean isFieldEmpty(Object value) {
        if (value == null) {
            return true;
        }
        
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        
        if (value instanceof Number) {
            return false; // Numbers are never considered empty if not null
        }
        
        return false;
    }
    
    @Override
    public String getRuleName() {
        return "REQUIRED_FIELD";
    }
    
    @Override
    public String getDescription() {
        return "Field must not be null" + (allowEmptyString ? "" : " or empty");
    }
}