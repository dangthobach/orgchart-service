package com.learnmore.application.service.validation.rules;

import com.learnmore.application.service.validation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Validates enum fields have allowed values
 */
@Component
@Slf4j
public class EnumValueValidator<T> implements ValidationRule<T> {

    private final Map<String, Set<String>> fieldAllowedValues;

    public EnumValueValidator() {
        this.fieldAllowedValues = new HashMap<>();
    }

    public EnumValueValidator(Map<String, Set<String>> fieldAllowedValues) {
        this.fieldAllowedValues = fieldAllowedValues;
    }

    @Override
    public ValidationResult validate(T data, ValidationContext context) {
        ValidationResult result = ValidationResult.success();

        if (data == null) {
            return result;
        }

        Class<?> clazz = data.getClass();

        for (Map.Entry<String, Set<String>> entry : fieldAllowedValues.entrySet()) {
            String fieldName = entry.getKey();
            Set<String> allowedValues = entry.getValue();

            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(data);

                if (value != null) {
                    String strValue = value.toString();
                    if (!strValue.isEmpty() && !allowedValues.contains(strValue)) {
                        result.addError(ValidationError.invalidEnumValue(
                                fieldName,
                                strValue,
                                String.join(", ", allowedValues)
                        ));
                    }
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
        return "enum_values";
    }

    @Override
    public String getDescription() {
        return "Validate enum fields have allowed values";
    }

    @Override
    public int getPriority() {
        return 30;
    }

    /**
     * Create validator with specific enum rules
     */
    public static <T> EnumValueValidator<T> withRules(Map<String, Set<String>> rules) {
        return new EnumValueValidator<>(rules);
    }
}
