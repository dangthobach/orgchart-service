package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Enhanced validator for checking duplicate values with instance-level support
 * Memory-efficient tracking for large datasets
 */
@Slf4j
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
                
                // Check if this column needs uniqueness validation
                if (uniqueColumns.contains(columnName) || uniqueColumns.contains(field.getName())) {
                    field.setAccessible(true);
                    
                    try {
                        Object value = field.get(instance);
                        if (value != null) {
                            String stringValue = value.toString().trim();
                            if (!stringValue.isEmpty()) {
                                ConcurrentHashMap<String, Integer> valueMap = columnValueMap.get(columnName);
                                if (valueMap == null) {
                                    valueMap = new ConcurrentHashMap<>();
                                    columnValueMap.put(columnName, valueMap);
                                }
                                
                                Integer previousRow = valueMap.put(stringValue, rowNumber);
                                if (previousRow != null) {
                                    throw new ValidationException(
                                        String.format("Duplicate value '%s' in column '%s' at row %d. Previously seen at row %d", 
                                            stringValue, columnName, rowNumber, previousRow)
                                    );
                                }
                            }
                        }
                        
                    } catch (IllegalAccessException e) {
                        log.error("Failed to access field '{}' for duplicate validation: {}", columnName, e.getMessage());
                        throw new ValidationException(
                            String.format("Failed to validate duplicate for field '%s' at row %d", columnName, rowNumber)
                        );
                    }
                }
            }
        }
        
        log.debug("Duplicate validation passed for row {}", rowNumber);
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