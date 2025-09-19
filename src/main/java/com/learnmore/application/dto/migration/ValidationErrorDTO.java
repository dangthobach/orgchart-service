package com.learnmore.application.dto.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO cho lỗi validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorDTO {
    
    private Integer rowNumber;
    private String errorType;
    private String errorField;
    private String errorValue;
    private String errorMessage;
    private String originalData;
}
