package com.learnmore.application.utils.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Validator for data type conversion and validation
 */
public class DataTypeValidator implements ValidationRule {
    
    private final Class<?> expectedType;
    private final String dateFormat;
    private final DateTimeFormatter dateFormatter;
    
    public DataTypeValidator(Class<?> expectedType) {
        this(expectedType, "yyyy-MM-dd");
    }
    
    public DataTypeValidator(Class<?> expectedType, String dateFormat) {
        this.expectedType = expectedType;
        this.dateFormat = dateFormat;
        this.dateFormatter = DateTimeFormatter.ofPattern(dateFormat);
    }
    
    @Override
    public ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber) {
        if (value == null) {
            return ValidationResult.success(); // Null values are handled by RequiredFieldValidator
        }
        
        String stringValue = value.toString().trim();
        if (stringValue.isEmpty()) {
            return ValidationResult.success(); // Empty values are considered valid for optional fields
        }
        
        try {
            if (!canConvertToType(stringValue, expectedType)) {
                return ValidationResult.failure(
                    "Cannot convert value '" + stringValue + "' to type " + expectedType.getSimpleName(),
                    fieldName, rowNumber, columnNumber, stringValue, getRuleName()
                );
            }
        } catch (Exception e) {
            return ValidationResult.failure(
                "Type conversion failed for value '" + stringValue + "' to type " + expectedType.getSimpleName() + ": " + e.getMessage(),
                fieldName, rowNumber, columnNumber, stringValue, getRuleName()
            );
        }
        
        return ValidationResult.success();
    }
    
    private boolean canConvertToType(String value, Class<?> type) {
        try {
            if (type == String.class) {
                return true;
            } else if (type == Integer.class || type == int.class) {
                Integer.parseInt(value);
                return true;
            } else if (type == Long.class || type == long.class) {
                Long.parseLong(value);
                return true;
            } else if (type == Double.class || type == double.class) {
                Double.parseDouble(value);
                return true;
            } else if (type == Float.class || type == float.class) {
                Float.parseFloat(value);
                return true;
            } else if (type == Boolean.class || type == boolean.class) {
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ||
                       "1".equals(value) || "0".equals(value) ||
                       "yes".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value);
            } else if (type == BigDecimal.class) {
                new BigDecimal(value);
                return true;
            } else if (type == LocalDate.class) {
                LocalDate.parse(value, dateFormatter);
                return true;
            } else if (type == LocalDateTime.class) {
                LocalDateTime.parse(value, dateFormatter);
                return true;
            } else if (type == Date.class) {
                LocalDate.parse(value, dateFormatter);
                return true;
            } else if (type.isEnum()) {
                // Check if enum constant exists by attempting to find it
                Object[] enumConstants = type.getEnumConstants();
                for (Object enumConstant : enumConstants) {
                    if (enumConstant.toString().equalsIgnoreCase(value)) {
                        return true;
                    }
                }
                return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getRuleName() {
        return "DATA_TYPE";
    }
    
    @Override
    public String getDescription() {
        return "Value must be convertible to " + expectedType.getSimpleName();
    }
    
    public Class<?> getExpectedType() {
        return expectedType;
    }
    
    public String getDateFormat() {
        return dateFormat;
    }
}