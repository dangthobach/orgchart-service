package com.learnmore.application.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {

    String name();
    String numberFormat() default "General";
    
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
}