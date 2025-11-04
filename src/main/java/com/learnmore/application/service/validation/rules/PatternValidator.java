package com.learnmore.application.service.validation.rules;

import com.learnmore.application.service.validation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates fields match regex patterns
 * E.g., ma_thung must be ^[A-Z0-9_]+$
 */
@Component
@Slf4j
public class PatternValidator<T> implements ValidationRule<T> {

    private final Map<String, Pattern> fieldPatterns;

    public PatternValidator() {
        this.fieldPatterns = new HashMap<>();
    }

    public PatternValidator(Map<String, String> patternStrings) {
        this.fieldPatterns = new HashMap<>();
        patternStrings.forEach((field, patternStr) -> {
            fieldPatterns.put(field, Pattern.compile(patternStr));
        });
    }

    @Override
    public ValidationResult validate(T data, ValidationContext context) {
        ValidationResult result = ValidationResult.success();

        if (data == null) {
            return result;
        }

        Class<?> clazz = data.getClass();

        for (Map.Entry<String, Pattern> entry : fieldPatterns.entrySet()) {
            String fieldName = entry.getKey();
            Pattern pattern = entry.getValue();

            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(data);

                if (value != null) {
                    String strValue = value.toString();
                    if (!strValue.isEmpty() && !pattern.matcher(strValue).matches()) {
                        result.addError(ValidationError.invalidPattern(
                                fieldName,
                                strValue,
                                pattern.pattern()
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
        return "pattern";
    }

    @Override
    public String getDescription() {
        return "Validate fields match regex patterns";
    }

    @Override
    public int getPriority() {
        return 40;
    }

    /**
     * Common patterns
     */
    public static class Patterns {
        public static final String MA_THUNG = "^[A-Z0-9_]+$";
        public static final String DATE_YYYY_MM_DD = "^\\d{4}-\\d{2}-\\d{2}$";
        public static final String POSITIVE_INTEGER = "^\\d+$";
    }

    /**
     * Create validator for ma_thung field
     */
    public static <T> PatternValidator<T> forMaThung() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("maThung", Patterns.MA_THUNG);
        return new PatternValidator<>(patterns);
    }
}
