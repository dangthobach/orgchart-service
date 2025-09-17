package com.learnmore.application.utils.validation;

/**
 * Validator for required fields (non-null and non-empty)
 */
public class RequiredFieldValidator implements ValidationRule {
    
    private final boolean allowEmptyString;
    
    public RequiredFieldValidator() {
        this(false);
    }
    
    public RequiredFieldValidator(boolean allowEmptyString) {
        this.allowEmptyString = allowEmptyString;
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
    
    @Override
    public String getRuleName() {
        return "REQUIRED_FIELD";
    }
    
    @Override
    public String getDescription() {
        return "Field must not be null" + (allowEmptyString ? "" : " or empty");
    }
}