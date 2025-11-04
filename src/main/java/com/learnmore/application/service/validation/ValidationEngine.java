package com.learnmore.application.service.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Validation engine that orchestrates multiple validation rules
 * Executes rules in priority order and aggregates results
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationEngine<T> {

    /**
     * Validate a single record with multiple rules
     */
    public ValidationResult validate(T data, List<ValidationRule<T>> rules, ValidationContext context) {
        ValidationResult aggregatedResult = ValidationResult.success();

        if (data == null) {
            aggregatedResult.addError("INVALID_DATA", "object", null, "Dữ liệu không được null");
            return aggregatedResult;
        }

        // Sort rules by priority
        List<ValidationRule<T>> sortedRules = rules.stream()
                .filter(ValidationRule::isEnabled)
                .sorted(Comparator.comparingInt(ValidationRule::getPriority))
                .toList();

        log.debug("Validating with {} rules for row {}", sortedRules.size(), context.getRowNumber());

        // Execute each rule
        for (ValidationRule<T> rule : sortedRules) {
            try {
                long startTime = System.currentTimeMillis();
                ValidationResult result = rule.validate(data, context);
                long duration = System.currentTimeMillis() - startTime;

                if (duration > 100) {
                    log.warn("Rule '{}' took {}ms for row {}", rule.getRuleName(), duration, context.getRowNumber());
                }

                // Merge results
                aggregatedResult.merge(result);

            } catch (Exception e) {
                log.error("Error executing rule '{}' for row {}: {}",
                         rule.getRuleName(), context.getRowNumber(), e.getMessage(), e);
                aggregatedResult.addError(
                        "VALIDATION_ERROR",
                        rule.getRuleName(),
                        null,
                        "Lỗi khi thực thi validation rule: " + e.getMessage()
                );
            }
        }

        return aggregatedResult;
    }

    /**
     * Validate a batch of records
     */
    public List<ValidationResult> validateBatch(List<T> dataList,
                                                 List<ValidationRule<T>> rules,
                                                 String jobId,
                                                 String sheetName) {
        List<ValidationResult> results = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            T data = dataList.get(i);
            ValidationContext context = ValidationContext.forRow(jobId, sheetName, i + 1);
            ValidationResult result = validate(data, rules, context);
            results.add(result);
        }

        return results;
    }

    /**
     * Validate batch and return only errors
     */
    public List<ValidationResultWithRow> validateBatchWithErrors(List<T> dataList,
                                                                   List<ValidationRule<T>> rules,
                                                                   String jobId,
                                                                   String sheetName) {
        List<ValidationResultWithRow> errors = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            T data = dataList.get(i);
            ValidationContext context = ValidationContext.forRow(jobId, sheetName, i + 1);
            ValidationResult result = validate(data, rules, context);

            if (!result.isValid()) {
                errors.add(new ValidationResultWithRow(i + 1, data, result));
            }
        }

        return errors;
    }

    /**
     * Get validation statistics for a batch
     */
    public ValidationStatistics getStatistics(List<ValidationResult> results) {
        long totalRecords = results.size();
        long validRecords = results.stream().filter(ValidationResult::isValid).count();
        long errorRecords = totalRecords - validRecords;
        long totalErrors = results.stream()
                .mapToLong(ValidationResult::getErrorCount)
                .sum();

        return ValidationStatistics.builder()
                .totalRecords(totalRecords)
                .validRecords(validRecords)
                .errorRecords(errorRecords)
                .totalErrors(totalErrors)
                .build();
    }

    /**
     * Helper class to hold validation result with row info
     */
    public static class ValidationResultWithRow {
        public final int rowNumber;
        public final Object data;
        public final ValidationResult result;

        public ValidationResultWithRow(int rowNumber, Object data, ValidationResult result) {
            this.rowNumber = rowNumber;
            this.data = data;
            this.result = result;
        }
    }

    /**
     * Validation statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class ValidationStatistics {
        private long totalRecords;
        private long validRecords;
        private long errorRecords;
        private long totalErrors;

        public double getErrorRate() {
            return totalRecords > 0 ? (errorRecords * 100.0 / totalRecords) : 0;
        }
    }
}
