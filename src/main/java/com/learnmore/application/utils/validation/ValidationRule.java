package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.exception.ValidationException;

/**
 * Interface for field validation rules
 */
public interface ValidationRule {
    
    /**
     * Validates a field value
     * @param fieldName The name of the field being validated
     * @param value The value to validate
     * @param rowNumber The row number (0-based)
     * @param columnNumber The column number (0-based)
     * @return ValidationResult containing validation status and error details
     */
    ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber);
    
    /**
     * Gets the rule name for error reporting
     */
    String getRuleName();
    
    /**
     * Gets the rule description
     */
    String getDescription();
    
    /**
     * Result of validation operation
     */
    class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final ValidationException.ValidationError validationError;
        
        private ValidationResult(boolean valid, String errorMessage, ValidationException.ValidationError validationError) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.validationError = validationError;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult failure(String message, String fieldName, int rowNumber, int columnNumber, String cellValue, String violatedRule) {
            ValidationException.ValidationError error = new ValidationException.ValidationError(
                message, fieldName, rowNumber, columnNumber, cellValue, violatedRule);
            return new ValidationResult(false, message, error);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public ValidationException.ValidationError getValidationError() {
            return validationError;
        }
    }
}