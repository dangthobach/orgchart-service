package com.learnmore.application.utils.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Enhanced type converter with support for complex data types
 * Provides high-performance conversion with caching and error handling
 */
public class TypeConverter {
    
    private static final Logger logger = LoggerFactory.getLogger(TypeConverter.class);
    
    // Singleton instance
    private static volatile TypeConverter instance;
    private static final Object lock = new Object();
    
    // Conversion cache for performance
    private final ConcurrentMap<String, Object> conversionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Function<String, Object>> converterMap = new ConcurrentHashMap<>();
    
    // Date formatters - Enhanced with more patterns
    private final DateTimeFormatter[] dateFormatters = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MM-yyyy"),       // Month-Year format
        DateTimeFormatter.ofPattern("M/d/yyyy"),      // Single digit month/day
        DateTimeFormatter.ofPattern("MM/d/yyyy"),     // Single digit day
        DateTimeFormatter.ofPattern("M/dd/yyyy"),    // Single digit month
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM-yyyy HH:mm:ss"), // Month-Year with time
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/d/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("M/dd/yyyy HH:mm:ss")
    };
    
    // Statistics
    private volatile long conversionCount = 0;
    private volatile long cacheHits = 0;
    private volatile long conversionErrors = 0;
    
    private TypeConverter() {
        initializeConverters();
    }
    
    /**
     * Get singleton instance
     */
    public static TypeConverter getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new TypeConverter();
                }
            }
        }
        return instance;
    }
    
    /**
     * Convert string value to target type
     */
    public <T> T convert(String value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        
        conversionCount++;
        
        // Check cache first
        String cacheKey = value + "_" + targetType.getName();
        Object cachedResult = conversionCache.get(cacheKey);
        if (cachedResult != null) {
            cacheHits++;
            return targetType.cast(cachedResult);
        }
        
        try {
            T result = performConversion(value.trim(), targetType);
            
            // Cache successful conversions (limit cache size)
            if (conversionCache.size() < 10000) {
                conversionCache.put(cacheKey, result);
            }
            
            return result;
            
        } catch (Exception e) {
            conversionErrors++;
            logger.warn("Failed to convert '{}' to {}: {}", value, targetType.getSimpleName(), e.getMessage());
            throw new IllegalArgumentException("Cannot convert '" + value + "' to " + targetType.getSimpleName(), e);
        }
    }
    
    /**
     * Perform the actual conversion
     */
    @SuppressWarnings("unchecked")
    private <T> T performConversion(String value, Class<T> targetType) {
        if (value.isEmpty()) {
            return null;
        }
        
        // Use registered converter if available
        Function<String, Object> converter = converterMap.get(targetType);
        if (converter != null) {
            return (T) converter.apply(value);
        }
        
        // Handle primitive wrapper classes
        if (targetType == String.class) {
            return (T) value;
        } else if (targetType == Integer.class || targetType == int.class) {
            return (T) Integer.valueOf(parseInteger(value));
        } else if (targetType == Long.class || targetType == long.class) {
            return (T) Long.valueOf(parseLong(value));
        } else if (targetType == Double.class || targetType == double.class) {
            return (T) Double.valueOf(parseDouble(value));
        } else if (targetType == Float.class || targetType == float.class) {
            return (T) Float.valueOf(parseFloat(value));
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return (T) Boolean.valueOf(parseBoolean(value));
        } else if (targetType == BigDecimal.class) {
            return (T) parseBigDecimal(value);
        } else if (targetType == LocalDate.class) {
            return (T) parseLocalDate(value);
        } else if (targetType == LocalDateTime.class) {
            return (T) parseLocalDateTime(value);
        } else if (targetType == Date.class) {
            return (T) parseDate(value);
        } else if (targetType.isEnum()) {
            return (T) parseEnum(value, targetType);
        }
        
        throw new IllegalArgumentException("Unsupported target type: " + targetType);
    }
    
    /**
     * Parse integer with enhanced error handling
     */
    private Integer parseInteger(String value) {
        try {
            // Handle decimal values by truncating
            if (value.contains(".")) {
                double doubleValue = Double.parseDouble(value);
                return (int) doubleValue;
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer format: " + value);
        }
    }
    
    /**
     * Parse long with enhanced error handling
     */
    private Long parseLong(String value) {
        try {
            // Handle decimal values by truncating
            if (value.contains(".")) {
                double doubleValue = Double.parseDouble(value);
                return (long) doubleValue;
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long format: " + value);
        }
    }
    
    /**
     * Parse double with enhanced error handling
     */
    private Double parseDouble(String value) {
        try {
            // Handle percentage values
            if (value.endsWith("%")) {
                return Double.parseDouble(value.substring(0, value.length() - 1)) / 100.0;
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double format: " + value);
        }
    }
    
    /**
     * Parse float with enhanced error handling
     */
    private Float parseFloat(String value) {
        try {
            // Handle percentage values
            if (value.endsWith("%")) {
                return Float.parseFloat(value.substring(0, value.length() - 1)) / 100.0f;
            }
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float format: " + value);
        }
    }
    
    /**
     * Parse boolean with multiple format support
     */
    private Boolean parseBoolean(String value) {
        String lowerValue = value.toLowerCase();
        
        // True values
        if ("true".equals(lowerValue) || "yes".equals(lowerValue) || "y".equals(lowerValue) || 
            "1".equals(lowerValue) || "on".equals(lowerValue) || "enabled".equals(lowerValue)) {
            return Boolean.TRUE;
        }
        
        // False values
        if ("false".equals(lowerValue) || "no".equals(lowerValue) || "n".equals(lowerValue) || 
            "0".equals(lowerValue) || "off".equals(lowerValue) || "disabled".equals(lowerValue)) {
            return Boolean.FALSE;
        }
        
        throw new IllegalArgumentException("Invalid boolean format: " + value);
    }
    
    /**
     * Parse BigDecimal with enhanced precision handling
     */
    private BigDecimal parseBigDecimal(String value) {
        try {
            // Handle percentage values
            if (value.endsWith("%")) {
                BigDecimal percentage = new BigDecimal(value.substring(0, value.length() - 1));
                return percentage.divide(new BigDecimal("100"));
            }
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid BigDecimal format: " + value);
        }
    }
    
    /**
     * Parse LocalDate with multiple format support
     */
    private LocalDate parseLocalDate(String value) {
        for (DateTimeFormatter formatter : dateFormatters) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        
        throw new IllegalArgumentException("Invalid date format: " + value);
    }
    
    /**
     * Parse LocalDateTime with multiple format support
     */
    private LocalDateTime parseLocalDateTime(String value) {
        for (DateTimeFormatter formatter : dateFormatters) {
            try {
                // Try parsing as LocalDateTime first
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try parsing as LocalDate and convert to LocalDateTime at start of day
                try {
                    LocalDate date = LocalDate.parse(value, formatter);
                    return date.atStartOfDay();
                } catch (DateTimeParseException alsoIgnored) {
                    // Try next formatter
                }
            }
        }
        
        throw new IllegalArgumentException("Invalid datetime format: " + value);
    }
    
    /**
     * Parse Date with multiple format support
     */
    private Date parseDate(String value) {
        LocalDate localDate = parseLocalDate(value);
        return java.sql.Date.valueOf(localDate);
    }
    
    /**
     * Parse enum value with case-insensitive matching
     */
    @SuppressWarnings("rawtypes")
    private Object parseEnum(String value, Class<?> enumClass) {
        Object[] enumConstants = enumClass.getEnumConstants();
        
        // Try exact match first
        for (Object enumConstant : enumConstants) {
            if (enumConstant.toString().equals(value)) {
                return enumConstant;
            }
        }
        
        // Try case-insensitive match
        for (Object enumConstant : enumConstants) {
            if (enumConstant.toString().equalsIgnoreCase(value)) {
                return enumConstant;
            }
        }
        
        // Try matching against enum name
        for (Object enumConstant : enumConstants) {
            if (((Enum) enumConstant).name().equalsIgnoreCase(value)) {
                return enumConstant;
            }
        }
        
        throw new IllegalArgumentException("Invalid enum value '" + value + "' for type " + enumClass.getSimpleName());
    }
    
    /**
     * Initialize default converters
     */
    private void initializeConverters() {
        // Add any custom converters here
        logger.debug("Type converters initialized");
    }
    
    /**
     * Register custom converter for a type
     */
    public <T> void registerConverter(Class<T> targetType, Function<String, T> converter) {
        converterMap.put(targetType, value -> converter.apply(value));
        logger.info("Registered custom converter for type: {}", targetType.getSimpleName());
    }
    
    /**
     * Check if a value can be converted to target type
     */
    public boolean canConvert(String value, Class<?> targetType) {
        if (value == null) {
            return true; // null can be converted to any type
        }
        
        try {
            convert(value, targetType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Clear conversion cache
     */
    public void clearCache() {
        conversionCache.clear();
        logger.info("Type conversion cache cleared");
    }
    
    /**
     * Get conversion statistics
     */
    public ConversionStatistics getStatistics() {
        return new ConversionStatistics(
            conversionCount, 
            cacheHits, 
            conversionErrors,
            conversionCache.size(),
            converterMap.size()
        );
    }
    
    /**
     * Conversion statistics holder
     */
    public static class ConversionStatistics {
        private final long totalConversions;
        private final long cacheHits;
        private final long errors;
        private final int cacheSize;
        private final int registeredConverters;
        
        public ConversionStatistics(long totalConversions, long cacheHits, long errors, 
                                  int cacheSize, int registeredConverters) {
            this.totalConversions = totalConversions;
            this.cacheHits = cacheHits;
            this.errors = errors;
            this.cacheSize = cacheSize;
            this.registeredConverters = registeredConverters;
        }
        
        public long getTotalConversions() {
            return totalConversions;
        }
        
        public long getCacheHits() {
            return cacheHits;
        }
        
        public long getErrors() {
            return errors;
        }
        
        public int getCacheSize() {
            return cacheSize;
        }
        
        public int getRegisteredConverters() {
            return registeredConverters;
        }
        
        public double getCacheHitRatio() {
            return totalConversions > 0 ? (double) cacheHits / totalConversions : 0.0;
        }
        
        public double getErrorRate() {
            return totalConversions > 0 ? (double) errors / totalConversions : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ConversionStatistics{conversions=%d, cacheHits=%d (%.2f%%), " +
                    "errors=%d (%.2f%%), cacheSize=%d, converters=%d}",
                    totalConversions, cacheHits, getCacheHitRatio() * 100,
                    errors, getErrorRate() * 100, cacheSize, registeredConverters);
        }
    }
}