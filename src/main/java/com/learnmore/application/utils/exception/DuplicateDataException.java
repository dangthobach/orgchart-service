package com.learnmore.application.utils.exception;

import java.util.List;
import java.util.Set;

/**
 * Exception thrown when duplicate data is found in columns that should be unique
 */
public class DuplicateDataException extends ValidationException {
    
    private final Set<String> duplicateColumns;
    private final List<DuplicateEntry> duplicateEntries;
    
    public DuplicateDataException(String message, Set<String> duplicateColumns, List<DuplicateEntry> duplicateEntries) {
        super(formatMessage(message, duplicateColumns, duplicateEntries));
        this.duplicateColumns = duplicateColumns;
        this.duplicateEntries = duplicateEntries;
    }
    
    private static String formatMessage(String message, Set<String> duplicateColumns, List<DuplicateEntry> duplicateEntries) {
        StringBuilder sb = new StringBuilder(message);
        if (duplicateColumns != null && !duplicateColumns.isEmpty()) {
            sb.append(" [Duplicate Columns: ").append(String.join(", ", duplicateColumns)).append("]");
        }
        if (duplicateEntries != null && !duplicateEntries.isEmpty()) {
            sb.append(" [Total Duplicates: ").append(duplicateEntries.size()).append("]");
            sb.append("\nDuplicate Details:");
            for (DuplicateEntry entry : duplicateEntries) {
                sb.append("\n  - Value '").append(entry.getValue())
                  .append("' in column '").append(entry.getColumnName())
                  .append("' found at rows: ").append(entry.getRowNumbers());
            }
        }
        return sb.toString();
    }
    
    public Set<String> getDuplicateColumns() {
        return duplicateColumns;
    }
    
    public List<DuplicateEntry> getDuplicateEntries() {
        return duplicateEntries;
    }
    
    public static class DuplicateEntry {
        private final String columnName;
        private final String value;
        private final List<Integer> rowNumbers;
        
        public DuplicateEntry(String columnName, String value, List<Integer> rowNumbers) {
            this.columnName = columnName;
            this.value = value;
            this.rowNumbers = rowNumbers;
        }
        
        public String getColumnName() {
            return columnName;
        }
        
        public String getValue() {
            return value;
        }
        
        public List<Integer> getRowNumbers() {
            return rowNumbers;
        }
        
        @Override
        public String toString() {
            return String.format("DuplicateEntry{column='%s', value='%s', rows=%s}", 
                columnName, value, rowNumbers);
        }
    }
}