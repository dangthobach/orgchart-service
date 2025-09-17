package com.learnmore.application.utils.exception;

import java.util.List;
import java.util.ArrayList;

/**
 * Exception thrown when validation rules are violated
 */
public class ValidationException extends ExcelProcessException {
    
    private final List<ValidationError> validationErrors;
    
    public ValidationException(String message) {
        super(message);
        this.validationErrors = new ArrayList<>();
    }
    
    public ValidationException(String message, List<ValidationError> validationErrors) {
        super(formatMessage(message, validationErrors));
        this.validationErrors = new ArrayList<>(validationErrors);
    }
    
    public ValidationException(String message, String operation, int rowNumber, int columnNumber, String cellValue) {
        super(message, operation, rowNumber, columnNumber, cellValue);
        this.validationErrors = new ArrayList<>();
    }
    
    private static String formatMessage(String message, List<ValidationError> validationErrors) {
        StringBuilder sb = new StringBuilder(message);
        if (validationErrors != null && !validationErrors.isEmpty()) {
            sb.append(" [Errors: ").append(validationErrors.size()).append("]");
            sb.append("\nValidation Details:");
            for (ValidationError error : validationErrors) {
                sb.append("\n  - Row ").append(error.getRowNumber() + 1)
                  .append(", Column ").append(error.getColumnNumber() + 1)
                  .append(": ").append(error.getMessage());
            }
        }
        return sb.toString();
    }
    
    public List<ValidationError> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }
    
    public void addValidationError(ValidationError error) {
        this.validationErrors.add(error);
    }
    
    public boolean hasErrors() {
        return !validationErrors.isEmpty();
    }
    
    public static class ValidationError {
        private final String message;
        private final String fieldName;
        private final int rowNumber;
        private final int columnNumber;
        private final String cellValue;
        private final String violatedRule;
        
        public ValidationError(String message, String fieldName, int rowNumber, int columnNumber, String cellValue, String violatedRule) {
            this.message = message;
            this.fieldName = fieldName;
            this.rowNumber = rowNumber;
            this.columnNumber = columnNumber;
            this.cellValue = cellValue;
            this.violatedRule = violatedRule;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public int getRowNumber() {
            return rowNumber;
        }
        
        public int getColumnNumber() {
            return columnNumber;
        }
        
        public String getCellValue() {
            return cellValue;
        }
        
        public String getViolatedRule() {
            return violatedRule;
        }
        
        @Override
        public String toString() {
            return String.format("ValidationError{row=%d, column=%d, field='%s', rule='%s', value='%s', message='%s'}", 
                rowNumber + 1, columnNumber + 1, fieldName, violatedRule, cellValue, message);
        }
    }
}