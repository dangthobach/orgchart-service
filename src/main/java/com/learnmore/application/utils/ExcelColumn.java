package com.learnmore.application.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {

    String name();
    String numberFormat() default "General";

    /**
     * Column index (0-based)
     */
    int index() default -1;

    /**
     * Date format for date columns
     */
    String dateFormat() default "";

    /**
     * Cell format type - determines how to process the cell value
     * GENERAL: Auto-detect based on cell type (default)
     * TEXT: Treat as text (preserve leading zeros, scientific notation as string)
     * NUMBER: Treat as numeric value
     * DATE: Treat as date (parse Excel serial date)
     * IDENTIFIER: Treat as identifier (CMND, phone, etc. - normalize scientific notation)
     */
    CellFormatType cellFormat() default CellFormatType.GENERAL;

    // ========== VALIDATION ATTRIBUTES ==========

    /**
     * Có bắt buộc không
     */
    boolean required() default false;
    
    /**
     * Độ dài tối đa
     */
    int maxLength() default -1;
    
    /**
     * Pattern regex để validate
     */
    String pattern() default "";
    
    /**
     * Kiểu dữ liệu
     */
    ColumnType dataType() default ColumnType.STRING;
    
    /**
     * Mô tả cột
     */
    String description() default "";
    
    /**
     * Ví dụ giá trị
     */
    String example() default "";
    
    /**
     * Vị trí cột (A, B, C, ...)
     */
    String position() default "";
    
    /**
     * Các kiểu dữ liệu được hỗ trợ
     */
    enum ColumnType {
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
     * Cell format type for processing cell values
     */
    enum CellFormatType {
        /**
         * Auto-detect based on cell type and field type
         */
        GENERAL,
        
        /**
         * Treat as text - preserve leading zeros, scientific notation as string
         * Use for: CMND, phone numbers, account numbers, etc.
         */
        TEXT,
        
        /**
         * Treat as numeric value
         */
        NUMBER,
        
        /**
         * Treat as date - parse Excel serial date number
         */
        DATE,
        
        /**
         * Treat as identifier - normalize scientific notation, preserve format
         * Use for: CMND, CCCD, passport, tax code, etc.
         */
        IDENTIFIER
    }
}