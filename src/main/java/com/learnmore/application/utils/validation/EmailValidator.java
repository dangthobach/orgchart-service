package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

/**
 * Email validation validator
 * Validates email format using regex pattern
 */
@Slf4j
public class EmailValidator implements ValidationRule {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    @Override
    public ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber) {
        if (value == null) {
            return ValidationResult.success();
        }
        
        String email = value.toString().trim();
        if (email.isEmpty()) {
            return ValidationResult.success();
        }
        
        // Only validate if field name suggests it's an email field
        if (isEmailField(fieldName)) {
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                return ValidationResult.failure(
                    String.format("Invalid email format: '%s'", email),
                    fieldName, rowNumber, columnNumber, email, getRuleName()
                );
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Instance-level validation for SAX processing
     */
    public void validate(Object instance, int rowNumber) throws ValidationException {
        if (instance == null) {
            return;
        }
        
        Class<?> clazz = instance.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                String columnName = annotation.name();
                
                // Check if this is an email field
                if (isEmailField(columnName) || isEmailField(field.getName())) {
                    field.setAccessible(true);
                    
                    try {
                        Object value = field.get(instance);
                        if (value != null) {
                            String email = value.toString().trim();
                            if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
                                throw new ValidationException(
                                    String.format("Invalid email format '%s' in field '%s' at row %d", 
                                        email, columnName, rowNumber)
                                );
                            }
                        }
                        
                    } catch (IllegalAccessException e) {
                        log.error("Failed to access email field '{}' for validation: {}", columnName, e.getMessage());
                        throw new ValidationException(
                            String.format("Failed to validate email field '%s' at row %d", columnName, rowNumber)
                        );
                    }
                }
            }
        }
        
        log.debug("Email validation passed for row {}", rowNumber);
    }
    
    /**
     * Check if field name suggests it's an email field
     */
    private boolean isEmailField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        
        String lowerCase = fieldName.toLowerCase();
        return lowerCase.contains("email") || lowerCase.contains("mail") || 
               lowerCase.equals("e-mail") || lowerCase.equals("e_mail");
    }
    
    @Override
    public String getRuleName() {
        return "EMAIL_FORMAT";
    }
    
    @Override
    public String getDescription() {
        return "Email fields must have valid email format";
    }
}