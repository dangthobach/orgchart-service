package com.learnmore.application.utils.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lỗi validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
    
    /**
     * Mã lỗi
     */
    private String code;
    
    /**
     * Thông báo lỗi
     */
    private String message;
    
    /**
     * Số dòng (1-based)
     */
    private int rowNumber;
    
    /**
     * Số cột (0-based)
     */
    private int columnNumber;
    
    /**
     * Giá trị cell gây lỗi
     */
    private String cellValue;
    
    /**
     * Loại validation
     */
    private String validationType;
    
    /**
     * Tạo lỗi đơn giản
     */
    public static ValidationError of(String code, String message, int rowNumber, int columnNumber) {
        return ValidationError.builder()
                .code(code)
                .message(message)
                .rowNumber(rowNumber)
                .columnNumber(columnNumber)
                .build();
    }
    
    /**
     * Tạo lỗi với giá trị cell
     */
    public static ValidationError of(String code, String message, int rowNumber, int columnNumber, String cellValue) {
        return ValidationError.builder()
                .code(code)
                .message(message)
                .rowNumber(rowNumber)
                .columnNumber(columnNumber)
                .cellValue(cellValue)
                .build();
    }
    
    /**
     * Tạo lỗi đầy đủ
     */
    public static ValidationError of(String code, String message, int rowNumber, int columnNumber, 
                                   String cellValue, String validationType) {
        return ValidationError.builder()
                .code(code)
                .message(message)
                .rowNumber(rowNumber)
                .columnNumber(columnNumber)
                .cellValue(cellValue)
                .validationType(validationType)
                .build();
    }
}
