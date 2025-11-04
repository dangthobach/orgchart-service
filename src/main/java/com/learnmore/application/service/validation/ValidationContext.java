package com.learnmore.application.service.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Context for validation
 * Contains job info and shared state
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationContext {

    private String jobId;
    private String sheetName;
    private Integer rowNumber;

    // Shared state for validation (e.g., lookup caches)
    @Builder.Default
    private Map<String, Object> sharedState = new HashMap<>();

    /**
     * Put value into shared state
     */
    public void put(String key, Object value) {
        if (sharedState == null) {
            sharedState = new HashMap<>();
        }
        sharedState.put(key, value);
    }

    /**
     * Get value from shared state
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (sharedState == null) {
            return null;
        }
        return (T) sharedState.get(key);
    }

    /**
     * Check if key exists in shared state
     */
    public boolean has(String key) {
        return sharedState != null && sharedState.containsKey(key);
    }

    /**
     * Create context for a specific row
     */
    public static ValidationContext forRow(String jobId, String sheetName, int rowNumber) {
        return ValidationContext.builder()
                .jobId(jobId)
                .sheetName(sheetName)
                .rowNumber(rowNumber)
                .build();
    }
}
