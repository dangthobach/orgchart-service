package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Validator for numeric range validation
 */
@Slf4j
public class NumericRangeValidator implements ValidationRule {
    
    private final Double minValue;
    private final Double maxValue;
    
    public NumericRangeValidator(Double minValue, Double maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }
    
    @Override
    public ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber) {
        if (value == null) {
            return ValidationResult.success();
        }
        
        Double numericValue = null;
        
        try {
            if (value instanceof Number) {
                numericValue = ((Number) value).doubleValue();
            } else if (value instanceof String) {
                String strValue = ((String) value).trim();
                if (!strValue.isEmpty()) {
                    numericValue = Double.parseDouble(strValue);
                }
            }
        } catch (NumberFormatException e) {
            return ValidationResult.success(); // Let DataTypeValidator handle non-numeric values
        }
        
        if (numericValue != null) {
            if (minValue != null && numericValue < minValue) {
                return ValidationResult.failure(
                    String.format("Value %.2f is below minimum allowed value %.2f", numericValue, minValue),
                    fieldName, rowNumber, columnNumber, value.toString(), getRuleName()
                );
            }
            
            if (maxValue != null && numericValue > maxValue) {
                return ValidationResult.failure(
                    String.format("Value %.2f exceeds maximum allowed value %.2f", numericValue, maxValue),
                    fieldName, rowNumber, columnNumber, value.toString(), getRuleName()
                );
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Instance-level validation for SAX processing
     */
    public void validate(Object instance, int rowNumber) throws ValidationException {
        // Range validation is typically field-specific, but can be implemented if needed
        log.debug("Numeric range validation for row {}", rowNumber);
    }
    
    @Override
    public String getRuleName() {
        return "NUMERIC_RANGE";
    }
    
    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder("Numeric values must be");
        if (minValue != null && maxValue != null) {
            desc.append(String.format(" between %.2f and %.2f", minValue, maxValue));
        } else if (minValue != null) {
            desc.append(String.format(" >= %.2f", minValue));
        } else if (maxValue != null) {
            desc.append(String.format(" <= %.2f", maxValue));
        }
        return desc.toString();
    }
}