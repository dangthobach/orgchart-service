package com.learnmore.application.utils.exception;

/**
 * Base exception for Excel processing operations
 */
public class ExcelProcessException extends RuntimeException {
    
    private final String operation;
    private final int rowNumber;
    private final int columnNumber;
    private final String cellValue;
    
    public ExcelProcessException(String message) {
        super(message);
        this.operation = null;
        this.rowNumber = -1;
        this.columnNumber = -1;
        this.cellValue = null;
    }
    
    public ExcelProcessException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.rowNumber = -1;
        this.columnNumber = -1;
        this.cellValue = null;
    }
    
    public ExcelProcessException(String message, String operation, int rowNumber, int columnNumber, String cellValue) {
        super(formatMessage(message, operation, rowNumber, columnNumber, cellValue));
        this.operation = operation;
        this.rowNumber = rowNumber;
        this.columnNumber = columnNumber;
        this.cellValue = cellValue;
    }
    
    public ExcelProcessException(String message, Throwable cause, String operation, int rowNumber, int columnNumber, String cellValue) {
        super(formatMessage(message, operation, rowNumber, columnNumber, cellValue), cause);
        this.operation = operation;
        this.rowNumber = rowNumber;
        this.columnNumber = columnNumber;
        this.cellValue = cellValue;
    }
    
    private static String formatMessage(String message, String operation, int rowNumber, int columnNumber, String cellValue) {
        StringBuilder sb = new StringBuilder(message);
        if (operation != null) {
            sb.append(" [Operation: ").append(operation).append("]");
        }
        if (rowNumber >= 0) {
            sb.append(" [Row: ").append(rowNumber + 1).append("]"); // Human readable (1-based)
        }
        if (columnNumber >= 0) {
            sb.append(" [Column: ").append(columnNumber + 1).append("]"); // Human readable (1-based)
        }
        if (cellValue != null) {
            sb.append(" [Value: '").append(cellValue).append("']");
        }
        return sb.toString();
    }
    
    public String getOperation() {
        return operation;
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
}