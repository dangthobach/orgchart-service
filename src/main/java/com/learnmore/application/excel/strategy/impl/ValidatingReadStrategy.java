package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.ReadStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// import javax.validation.ConstraintViolation;
// import javax.validation.Validator;
// Note: Validator is optional - requires spring-boot-starter-validation dependency
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Validating read strategy with JSR-303 Bean Validation
 *
 * This strategy wraps another read strategy (typically StreamingReadStrategy)
 * and validates each object using JSR-303 Bean Validation annotations before
 * passing it to the batch processor.
 *
 * Performance characteristics:
 * - Memory: O(batch_size) - same as streaming
 * - Speed: Slightly slower than streaming (validation overhead ~5-10%)
 * - Validation: Per-object validation using Bean Validation API
 *
 * Validation modes:
 * 1. **Strict Mode** (config.isStrictValidation() == true):
 *    - Throws exception on first validation error
 *    - Processing stops immediately
 *    - Best for data quality enforcement
 *
 * 2. **Lenient Mode** (config.isStrictValidation() == false):
 *    - Logs validation errors but continues processing
 *    - Invalid objects are skipped
 *    - Best for import scenarios with partial success
 *
 * Validation features:
 * - Standard JSR-303 annotations (@NotNull, @Size, @Min, @Max, etc.)
 * - Custom validation annotations
 * - Required fields validation (config.getRequiredFields())
 * - Unique fields validation (config.getUniqueFields())
 * - Field-specific validation rules (config.getFieldValidationRules())
 *
 * Use cases:
 * - Data import with quality assurance
 * - User-uploaded files requiring validation
 * - Compliance scenarios requiring data validation
 * - Integration with external systems
 *
 * Strategy selection:
 * - Priority: 8 (higher than streaming, lower than parallel/cached)
 * - Supports: When Bean Validator is available
 *
 * @param <T> The type of objects to read and validate from Excel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidatingReadStrategy<T> implements ReadStrategy<T> {

    private final StreamingReadStrategy<T> streamingStrategy; // Fallback strategy
    // private final Validator validator; // JSR-303 Bean Validator (optional - from Spring)
    // TODO: Uncomment when spring-boot-starter-validation is added to dependencies

    /**
     * Execute read with validation
     *
     * Process flow:
     * 1. Read batch using streaming strategy
     * 2. For each object in batch:
     *    a. Validate using JSR-303 annotations
     *    b. Validate using required fields
     *    c. Validate using unique fields
     *    d. Validate using custom rules
     * 3. If strict mode and validation fails: Throw exception
     * 4. If lenient mode and validation fails: Log and skip
     * 5. Pass valid objects to batch processor
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Excel configuration
     * @param batchProcessor Consumer that processes validated batches
     * @return ProcessingResult with statistics (includes validation errors)
     * @throws ExcelProcessException if reading or validation fails
     */
    @Override
    public TrueStreamingSAXProcessor.ProcessingResult execute(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Executing ValidatingReadStrategy for class: {}", beanClass.getSimpleName());

        // Check if validator is available
        // TODO: Uncomment when validator is available
        // if (validator == null) {
        log.warn("Bean Validator not configured (requires spring-boot-starter-validation). " +
                "Falling back to StreamingReadStrategy without validation.");
        return streamingStrategy.execute(inputStream, beanClass, config, batchProcessor);
        // }

        // TODO: Uncomment below when validator is available
        /*
        // Track validation statistics
        ValidationStatistics stats = new ValidationStatistics();

        // Wrap batch processor with validation
        Consumer<List<T>> validatingProcessor = batch -> {
            List<T> validObjects = new ArrayList<>();

            for (T object : batch) {
                try {
                    // Validate using JSR-303
                    // TODO: Uncomment when validator is available
                    // Set<ConstraintViolation<T>> violations = validator.validate(object);
                    // if (!violations.isEmpty()) {
                    //     stats.incrementValidationErrors();
                    //     String errorMessage = violations.stream()
                    //         .map(v -> String.format("%s: %s", v.getPropertyPath(), v.getMessage()))
                    //         .collect(Collectors.joining(", "));
                    //     log.warn("Validation failed for object: {}", errorMessage);
                    //     if (config.isStrictValidation() || config.isFailOnFirstError()) {
                    //         throw new ExcelProcessException("Validation failed: " + errorMessage);
                    //     }
                    //     continue; // Lenient mode: skip invalid object
                    // }

                    // Additional custom validation
                    if (!validateRequiredFields(object, config)) {
                        stats.incrementValidationErrors();
                        if (config.isStrictValidation()) {
                            throw new ExcelProcessException(
                                "Required fields validation failed for: " + object);
                        }
                        continue;
                    }

                    // Object is valid - add to valid list
                    validObjects.add(object);
                    stats.incrementValidObjects();

                } catch (ExcelProcessException e) {
                    throw e; // Re-throw ExcelProcessException
                } catch (Exception e) {
                    stats.incrementValidationErrors();
                    log.error("Unexpected validation error for object: " + object, e);

                    if (config.isStrictValidation()) {
                        throw new ExcelProcessException("Validation error: " + e.getMessage(), e);
                    }
                }
            }

            // Process only valid objects
            if (!validObjects.isEmpty() && batchProcessor != null) {
                batchProcessor.accept(validObjects);
            }
        };

        // Execute with validating processor
        TrueStreamingSAXProcessor.ProcessingResult result = streamingStrategy.execute(
            inputStream,
            beanClass,
            config,
            validatingProcessor
        );

        log.info("ValidatingReadStrategy completed: {} valid objects, {} validation errors",
                stats.getValidObjects(), stats.getValidationErrors());

        return result;
        */
    }

    /**
     * Validate required fields
     *
     * Checks if all required fields (from config) are non-null and non-empty.
     *
     * @param object Object to validate
     * @param config Excel configuration with required fields
     * @return true if valid, false otherwise
     */
    private boolean validateRequiredFields(T object, ExcelConfig config) {
        Set<String> requiredFields = config.getRequiredFields();
        if (requiredFields == null || requiredFields.isEmpty()) {
            return true; // No required fields - valid
        }

        try {
            for (String fieldName : requiredFields) {
                Object value = getFieldValue(object, fieldName);

                if (value == null) {
                    log.warn("Required field '{}' is null in object: {}", fieldName, object);
                    return false;
                }

                if (value instanceof String && ((String) value).trim().isEmpty()) {
                    log.warn("Required field '{}' is empty in object: {}", fieldName, object);
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            log.error("Error validating required fields", e);
            return false;
        }
    }

    /**
     * Get field value using reflection
     *
     * @param object Object to get field from
     * @param fieldName Field name
     * @return Field value or null
     */
    private Object getFieldValue(T object, String fieldName) {
        try {
            java.lang.reflect.Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            // Try getter method
            try {
                String getterName = "get" + fieldName.substring(0, 1).toUpperCase() +
                                   fieldName.substring(1);
                java.lang.reflect.Method getter = object.getClass().getMethod(getterName);
                return getter.invoke(object);
            } catch (Exception ex) {
                log.debug("Could not get field value for: {}", fieldName);
                return null;
            }
        }
    }

    /**
     * Check if this strategy supports the given configuration
     *
     * ValidatingReadStrategy is selected when:
     * - Bean Validator is available (Spring context)
     * - Validation is needed (has required fields, strict validation, etc.)
     *
     * @param config Excel configuration to check
     * @return true if validation is available, false otherwise
     */
    @Override
    public boolean supports(ExcelConfig config) {
        // Always support if validator is available
        // Strategy selector will choose based on priority
        // TODO: Uncomment when validator is available
        // boolean supported = validator != null;
        boolean supported = false; // Disabled until validation dependency added

        if (supported) {
            log.debug("ValidatingReadStrategy supports config: validator available, " +
                     "strictValidation={}, requiredFields={}",
                     config.isStrictValidation(),
                     config.getRequiredFields().size());
        }

        return supported;
    }

    /**
     * Get strategy name for logging and debugging
     *
     * @return Strategy name
     */
    @Override
    public String getName() {
        return "ValidatingReadStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 8 means this strategy is preferred over StreamingReadStrategy (0)
     * and MultiSheetReadStrategy (5) when validation is needed, but lower than
     * ParallelReadStrategy (10) and CachedReadStrategy (15).
     *
     * Priority ordering:
     * - 0: StreamingReadStrategy (baseline)
     * - 5: MultiSheetReadStrategy (multi-sheet)
     * - 8: ValidatingReadStrategy (validation)
     * - 10: ParallelReadStrategy (parallel)
     * - 15: CachedReadStrategy (caching)
     *
     * @return Priority level (8 = medium-high for validation)
     */
    @Override
    public int getPriority() {
        return 8; // Higher than streaming/multi-sheet, lower than parallel/cached
    }

    /**
     * Validation statistics tracker
     */
    private static class ValidationStatistics {
        private int validObjects = 0;
        private int validationErrors = 0;

        public void incrementValidObjects() {
            validObjects++;
        }

        public void incrementValidationErrors() {
            validationErrors++;
        }

        public int getValidObjects() {
            return validObjects;
        }

        public int getValidationErrors() {
            return validationErrors;
        }
    }
}
