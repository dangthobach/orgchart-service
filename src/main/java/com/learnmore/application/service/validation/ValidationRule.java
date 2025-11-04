package com.learnmore.application.service.validation;

/**
 * Interface for validation rules
 * Each rule validates a specific aspect of data
 */
public interface ValidationRule<T> {

    /**
     * Validate a single record
     * @param data The data to validate
     * @param context Validation context with job info
     * @return ValidationResult with errors if any
     */
    ValidationResult validate(T data, ValidationContext context);

    /**
     * Get rule name
     */
    String getRuleName();

    /**
     * Get rule description
     */
    String getDescription();

    /**
     * Check if rule is enabled
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Priority for rule execution (lower = higher priority)
     */
    default int getPriority() {
        return 100;
    }
}
