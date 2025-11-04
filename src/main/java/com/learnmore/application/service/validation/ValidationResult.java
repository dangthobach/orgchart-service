package com.learnmore.application.service.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validation
 * Contains list of errors if any
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    @Builder.Default
    private boolean valid = true;

    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();

    /**
     * Create a successful validation result
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .valid(true)
                .build();
    }

    /**
     * Create a failed validation result with single error
     */
    public static ValidationResult failure(String errorType, String field, String value, String message) {
        ValidationError error = ValidationError.builder()
                .errorType(errorType)
                .errorField(field)
                .errorValue(value)
                .errorMessage(message)
                .build();

        return ValidationResult.builder()
                .valid(false)
                .errors(List.of(error))
                .build();
    }

    /**
     * Create a failed validation result with multiple errors
     */
    public static ValidationResult failure(List<ValidationError> errors) {
        return ValidationResult.builder()
                .valid(false)
                .errors(errors)
                .build();
    }

    /**
     * Add an error to this result
     */
    public void addError(ValidationError error) {
        this.valid = false;
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
    }

    /**
     * Add an error with simple parameters
     */
    public void addError(String errorType, String field, String value, String message) {
        addError(ValidationError.builder()
                .errorType(errorType)
                .errorField(field)
                .errorValue(value)
                .errorMessage(message)
                .build());
    }

    /**
     * Merge another result into this one
     */
    public void merge(ValidationResult other) {
        if (other != null && !other.isValid()) {
            this.valid = false;
            if (this.errors == null) {
                this.errors = new ArrayList<>();
            }
            this.errors.addAll(other.getErrors());
        }
    }

    /**
     * Get error count
     */
    public int getErrorCount() {
        return errors != null ? errors.size() : 0;
    }

    /**
     * Check if has errors
     */
    public boolean hasErrors() {
        return !valid && errors != null && !errors.isEmpty();
    }
}
