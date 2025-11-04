package com.learnmore.application.service.validation.rules;

import com.learnmore.application.service.validation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates required fields are not null or empty
 * Generic validator that works with any DTO
 */
@Component
@Slf4j
public class RequiredFieldsValidator<T> implements ValidationRule<T> {

    private final List<String> requiredFields;

    public RequiredFieldsValidator() {
        this.requiredFields = new ArrayList<>();
    }

    public RequiredFieldsValidator(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    @Override
    public ValidationResult validate(T data, ValidationContext context) {
        ValidationResult result = ValidationResult.success();

        if (data == null) {
            result.addError("REQUIRED_MISSING", "object", null, "Dữ liệu không được null");
            return result;
        }

        Class<?> clazz = data.getClass();

        for (String fieldName : requiredFields) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(data);

                if (value == null) {
                    result.addError(ValidationError.requiredField(fieldName));
                } else if (value instanceof String && ((String) value).trim().isEmpty()) {
                    result.addError(ValidationError.requiredField(fieldName));
                }

            } catch (NoSuchFieldException e) {
                log.warn("Field '{}' not found in class {}", fieldName, clazz.getSimpleName());
            } catch (IllegalAccessException e) {
                log.error("Cannot access field '{}'", fieldName, e);
            }
        }

        return result;
    }

    @Override
    public String getRuleName() {
        return "required_fields";
    }

    @Override
    public String getDescription() {
        return "Validate required fields are not null or empty";
    }

    @Override
    public int getPriority() {
        return 10; // High priority - check required fields first
    }

    /**
     * Create validator with specific required fields
     */
    public static <T> RequiredFieldsValidator<T> withFields(List<String> fields) {
        return new RequiredFieldsValidator<>(fields);
    }
}
