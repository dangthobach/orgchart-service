package com.learnmore.application.utils.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cảnh báo validation (không làm fail validation nhưng cần lưu ý)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationWarning {
    
    /**
     * Mã cảnh báo
     */
    private String code;
    
    /**
     * Thông báo cảnh báo
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
     * Giá trị cell gây cảnh báo
     */
    private String cellValue;
    
    /**
     * Loại validation
     */
    private String validationType;
    
    /**
     * Mức độ nghiêm trọng
     */
    @Builder.Default
    private WarningSeverity severity = WarningSeverity.MEDIUM;
    
    /**
     * Mức độ nghiêm trọng của cảnh báo
     */
    public enum WarningSeverity {
        LOW,    // Chỉ là gợi ý
        MEDIUM, // Nên xem xét
        HIGH    // Nên sửa
    }
    
    /**
     * Tạo cảnh báo đơn giản
     */
    public static ValidationWarning of(String code, String message, int rowNumber, int columnNumber) {
        return ValidationWarning.builder()
                .code(code)
                .message(message)
                .rowNumber(rowNumber)
                .columnNumber(columnNumber)
                .build();
    }
    
    /**
     * Tạo cảnh báo với giá trị cell
     */
    public static ValidationWarning of(String code, String message, int rowNumber, int columnNumber, String cellValue) {
        return ValidationWarning.builder()
                .code(code)
                .message(message)
                .rowNumber(rowNumber)
                .columnNumber(columnNumber)
                .cellValue(cellValue)
                .build();
    }
    
    /**
     * Tạo cảnh báo đầy đủ
     */
    public static ValidationWarning of(String code, String message, int rowNumber, int columnNumber, 
                                      String cellValue, String validationType) {
        return ValidationWarning.builder()
                .code(code)
                .message(message)
                .rowNumber(rowNumber)
                .columnNumber(columnNumber)
                .cellValue(cellValue)
                .validationType(validationType)
                .build();
    }
}
