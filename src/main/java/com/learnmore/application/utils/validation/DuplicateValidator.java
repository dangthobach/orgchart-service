package com.learnmore.application.utils.validation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Validator for checking duplicate values in specified columns
 */
public class DuplicateValidator implements ValidationRule {
    
    private final Set<String> uniqueColumns;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> columnValueMap;
    
    public DuplicateValidator(Set<String> uniqueColumns) {
        this.uniqueColumns = uniqueColumns;
        this.columnValueMap = new ConcurrentHashMap<>();
        
        // Initialize maps for each unique column
        for (String column : uniqueColumns) {
            columnValueMap.put(column, new ConcurrentHashMap<>());
        }
    }
    
    @Override
    public ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber) {
        if (!uniqueColumns.contains(fieldName) || value == null) {
            return ValidationResult.success();
        }
        
        String stringValue = value.toString().trim();
        if (stringValue.isEmpty()) {
            return ValidationResult.success(); // Empty values are not considered for duplication
        }
        
        ConcurrentHashMap<String, Integer> valueMap = columnValueMap.get(fieldName);
        Integer previousRow = valueMap.put(stringValue, rowNumber);
        
        if (previousRow != null) {
            return ValidationResult.failure(
                "Duplicate value '" + stringValue + "' found in column '" + fieldName + "'. Previously seen at row " + (previousRow + 1),
                fieldName, rowNumber, columnNumber, stringValue, getRuleName()
            );
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public String getRuleName() {
        return "UNIQUE_VALUE";
    }
    
    @Override
    public String getDescription() {
        return "Values in specified columns must be unique";
    }
    
    /**
     * Reset the validator for a new validation session
     */
    public void reset() {
        for (ConcurrentHashMap<String, Integer> valueMap : columnValueMap.values()) {
            valueMap.clear();
        }
    }
    
    /**
     * Get the columns that are checked for uniqueness
     */
    public Set<String> getUniqueColumns() {
        return uniqueColumns;
    }
}