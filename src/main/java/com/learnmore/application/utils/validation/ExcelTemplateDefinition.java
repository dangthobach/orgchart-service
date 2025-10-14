package com.learnmore.application.utils.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Định nghĩa template Excel để validation
 * Chứa thông tin về cấu trúc, header, và các quy tắc validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelTemplateDefinition {
    
    /**
     * Tên template
     */
    private String templateName;
    
    /**
     * Mô tả template
     */
    private String description;
    
    /**
     * Version của template
     */
    private String version;
    
    /**
     * Danh sách các cột bắt buộc theo thứ tự
     */
    private List<ColumnDefinition> requiredColumns;
    
    /**
     * Danh sách các cột tùy chọn
     */
    private List<ColumnDefinition> optionalColumns;
    
    /**
     * Số sheet tối thiểu
     */
    @Builder.Default
    private int minSheets = 1;
    
    /**
     * Số sheet tối đa
     */
    @Builder.Default
    private int maxSheets = 1;
    
    /**
     * Tên sheet bắt buộc (nếu có)
     */
    private Set<String> requiredSheetNames;
    
    /**
     * Số dòng header (thường là 1)
     */
    @Builder.Default
    private int headerRowCount = 1;
    
    /**
     * Có cho phép dòng trống không
     */
    @Builder.Default
    private boolean allowEmptyRows = true;
    
    /**
     * Số dòng dữ liệu tối thiểu
     */
    @Builder.Default
    private int minDataRows = 1;
    
    /**
     * Số dòng dữ liệu tối đa
     */
    @Builder.Default
    private int maxDataRows = Integer.MAX_VALUE;
    
    /**
     * Định nghĩa cột Excel
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnDefinition {
        
        /**
         * Tên cột trong Excel
         */
        private String columnName;
        
        /**
         * Vị trí cột (A, B, C, ...)
         */
        private String columnPosition;
        
        /**
         * Kiểu dữ liệu
         */
        private ColumnType dataType;
        
        /**
         * Có bắt buộc không
         */
        private boolean required;
        
        /**
         * Độ dài tối đa
         */
        private Integer maxLength;
        
        /**
         * Pattern regex để validate
         */
        private String pattern;
        
        /**
         * Danh sách giá trị cho phép
         */
        private List<String> allowedValues;
        
        /**
         * Mô tả cột
         */
        private String description;
        
        /**
         * Ví dụ giá trị
         */
        private String example;
    }
    
    /**
     * Các kiểu dữ liệu cột
     */
    public enum ColumnType {
        STRING,
        INTEGER,
        DECIMAL,
        DATE,
        BOOLEAN,
        EMAIL,
        PHONE,
        CUSTOM
    }
    
    /**
     * Tạo template definition từ class DTO
     */
    public static ExcelTemplateDefinition fromClass(Class<?> dtoClass) {
        return ExcelTemplateDefinition.builder()
                .templateName(dtoClass.getSimpleName())
                .description("Template generated from " + dtoClass.getSimpleName())
                .version("1.0")
                .build();
    }
    
    /**
     * Lấy tất cả các cột (required + optional)
     */
    public List<ColumnDefinition> getAllColumns() {
        List<ColumnDefinition> allColumns = new java.util.ArrayList<>();
        if (requiredColumns != null) {
            allColumns.addAll(requiredColumns);
        }
        if (optionalColumns != null) {
            allColumns.addAll(optionalColumns);
        }
        return allColumns;
    }
    
    /**
     * Lấy tên tất cả các cột
     */
    public Set<String> getAllColumnNames() {
        return getAllColumns().stream()
                .map(ColumnDefinition::getColumnName)
                .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Lấy tên các cột bắt buộc
     */
    public Set<String> getRequiredColumnNames() {
        if (requiredColumns == null) {
            return java.util.Set.of();
        }
        return requiredColumns.stream()
                .map(ColumnDefinition::getColumnName)
                .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Kiểm tra cột có tồn tại trong template không
     */
    public boolean hasColumn(String columnName) {
        return getAllColumnNames().contains(columnName);
    }
    
    /**
     * Kiểm tra cột có bắt buộc không
     */
    public boolean isColumnRequired(String columnName) {
        return getRequiredColumnNames().contains(columnName);
    }
    
    /**
     * Lấy định nghĩa cột theo tên
     */
    public ColumnDefinition getColumnDefinition(String columnName) {
        return getAllColumns().stream()
                .filter(col -> col.getColumnName().equals(columnName))
                .findFirst()
                .orElse(null);
    }
}
