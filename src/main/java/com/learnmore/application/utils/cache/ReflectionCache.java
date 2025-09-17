package com.learnmore.application.utils.cache;

import com.learnmore.application.utils.ExcelColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * High-performance reflection cache for Excel processing
 * Caches Field, Method, Constructor, and type converters to minimize reflection overhead
 */
public class ReflectionCache {
    
    private static final Logger logger = LoggerFactory.getLogger(ReflectionCache.class);
    
    // Singleton instance
    private static volatile ReflectionCache instance;
    private static final Object lock = new Object();
    
    // Cache maps
    private final ConcurrentMap<Class<?>, Constructor<?>> constructorCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, ConcurrentMap<String, Field>> fieldCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, ConcurrentMap<String, Method>> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Function<String, Object>> typeConverterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, ConcurrentMap<String, Field>> excelColumnFieldCache = new ConcurrentHashMap<>();
    
    // Statistics
    private volatile long cacheHits = 0;
    private volatile long cacheMisses = 0;
    
    private ReflectionCache() {
        initializeTypeConverters();
    }
    
    /**
     * Get singleton instance with double-checked locking
     */
    public static ReflectionCache getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ReflectionCache();
                }
            }
        }
        return instance;
    }
    
    /**
     * Get cached constructor for a class
     */
    public <T> Constructor<T> getConstructor(Class<T> clazz) {
        @SuppressWarnings("unchecked")
        Constructor<T> constructor = (Constructor<T>) constructorCache.get(clazz);
        
        if (constructor == null) {
            try {
                constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructorCache.put(clazz, constructor);
                cacheMisses++;
            } catch (NoSuchMethodException e) {
                logger.error("No default constructor found for class: {}", clazz.getName());
                throw new RuntimeException("No default constructor found for class: " + clazz.getName(), e);
            }
        } else {
            cacheHits++;
        }
        
        return constructor;
    }
    
    /**
     * Get cached field by name for a class
     */
    public Field getField(Class<?> clazz, String fieldName) {
        ConcurrentMap<String, Field> classFields = fieldCache.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        
        Field field = classFields.get(fieldName);
        if (field == null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                classFields.put(fieldName, field);
                cacheMisses++;
            } catch (NoSuchFieldException e) {
                // Try to find field in superclasses
                Class<?> superClass = clazz.getSuperclass();
                while (superClass != null && field == null) {
                    try {
                        field = superClass.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        classFields.put(fieldName, field);
                        cacheMisses++;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        superClass = superClass.getSuperclass();
                    }
                }
                
                if (field == null) {
                    logger.warn("Field '{}' not found in class: {}", fieldName, clazz.getName());
                    // Cache null to avoid repeated lookups
                    classFields.put(fieldName, null);
                }
            }
        } else {
            cacheHits++;
        }
        
        return field;
    }
    
    /**
     * Get cached method by name for a class
     */
    public Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String methodKey = methodName + "_" + java.util.Arrays.toString(parameterTypes);
        ConcurrentMap<String, Method> classMethods = methodCache.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        
        Method method = classMethods.get(methodKey);
        if (method == null) {
            try {
                method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                classMethods.put(methodKey, method);
                cacheMisses++;
            } catch (NoSuchMethodException e) {
                // Try to find method in superclasses
                Class<?> superClass = clazz.getSuperclass();
                while (superClass != null && method == null) {
                    try {
                        method = superClass.getDeclaredMethod(methodName, parameterTypes);
                        method.setAccessible(true);
                        classMethods.put(methodKey, method);
                        cacheMisses++;
                        break;
                    } catch (NoSuchMethodException ignored) {
                        superClass = superClass.getSuperclass();
                    }
                }
                
                if (method == null) {
                    logger.warn("Method '{}' not found in class: {}", methodName, clazz.getName());
                    // Cache null to avoid repeated lookups
                    classMethods.put(methodKey, null);
                }
            }
        } else {
            cacheHits++;
        }
        
        return method;
    }
    
    /**
     * Get cached fields with ExcelColumn annotation for a class
     */
    public ConcurrentMap<String, Field> getExcelColumnFields(Class<?> clazz) {
        ConcurrentMap<String, Field> excelFields = excelColumnFieldCache.get(clazz);
        
        if (excelFields == null) {
            excelFields = new ConcurrentHashMap<>();
            
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(ExcelColumn.class)) {
                    ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                    field.setAccessible(true);
                    excelFields.put(annotation.name(), field);
                }
            }
            
            // Also check superclass fields
            Class<?> superClass = clazz.getSuperclass();
            while (superClass != null && superClass != Object.class) {
                Field[] superFields = superClass.getDeclaredFields();
                for (Field field : superFields) {
                    if (field.isAnnotationPresent(ExcelColumn.class)) {
                        ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                        field.setAccessible(true);
                        excelFields.putIfAbsent(annotation.name(), field); // Don't override child class fields
                    }
                }
                superClass = superClass.getSuperclass();
            }
            
            excelColumnFieldCache.put(clazz, excelFields);
            cacheMisses++;
        } else {
            cacheHits++;
        }
        
        return excelFields;
    }
    
    /**
     * Get cached type converter function
     */
    public Function<String, Object> getTypeConverter(Class<?> targetType) {
        String typeKey = targetType.getName();
        Function<String, Object> converter = typeConverterCache.get(typeKey);
        
        if (converter != null) {
            cacheHits++;
        } else {
            cacheMisses++;
        }
        
        return converter;
    }
    
    /**
     * Convert string value to target type using cached converter
     */
    public Object convertValue(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        Function<String, Object> converter = getTypeConverter(targetType);
        if (converter != null) {
            return converter.apply(value.trim());
        }
        
        // Fallback to direct conversion if no cached converter found
        return convertValueDirect(value.trim(), targetType);
    }
    
    private Object convertValueDirect(String value, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return value;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(value);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(value);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(value);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(value);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
            } else if (targetType == BigDecimal.class) {
                return new BigDecimal(value);
            } else if (targetType.isEnum()) {
                Object[] enumConstants = targetType.getEnumConstants();
                for (Object enumConstant : enumConstants) {
                    if (enumConstant.toString().equalsIgnoreCase(value)) {
                        return enumConstant;
                    }
                }
                throw new IllegalArgumentException("No enum constant found for value: " + value);
            }
            
            return value; // Default fallback
        } catch (Exception e) {
            logger.warn("Failed to convert value '{}' to type {}: {}", value, targetType.getSimpleName(), e.getMessage());
            throw new IllegalArgumentException("Cannot convert value '" + value + "' to type " + targetType.getSimpleName(), e);
        }
    }
    
    /**
     * Initialize type converters with common data types
     */
    private void initializeTypeConverters() {
        // String converter
        typeConverterCache.put(String.class.getName(), value -> value);
        
        // Numeric converters
        typeConverterCache.put(Integer.class.getName(), Integer::parseInt);
        typeConverterCache.put(int.class.getName(), Integer::parseInt);
        typeConverterCache.put(Long.class.getName(), Long::parseLong);
        typeConverterCache.put(long.class.getName(), Long::parseLong);
        typeConverterCache.put(Double.class.getName(), Double::parseDouble);
        typeConverterCache.put(double.class.getName(), Double::parseDouble);
        typeConverterCache.put(Float.class.getName(), Float::parseFloat);
        typeConverterCache.put(float.class.getName(), Float::parseFloat);
        typeConverterCache.put(BigDecimal.class.getName(), BigDecimal::new);
        
        // Boolean converter
        Function<String, Object> booleanConverter = value -> 
            Boolean.parseBoolean(value) || "1".equals(value) || "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
        typeConverterCache.put(Boolean.class.getName(), booleanConverter);
        typeConverterCache.put(boolean.class.getName(), booleanConverter);
        
        // Date converters
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        typeConverterCache.put(LocalDate.class.getName(), value -> LocalDate.parse(value, dateFormatter));
        typeConverterCache.put(LocalDateTime.class.getName(), value -> LocalDateTime.parse(value, dateTimeFormatter));
        typeConverterCache.put(Date.class.getName(), value -> 
            java.sql.Date.valueOf(LocalDate.parse(value, dateFormatter)));
    }
    
    /**
     * Add custom type converter
     */
    public void addTypeConverter(Class<?> targetType, Function<String, Object> converter) {
        typeConverterCache.put(targetType.getName(), converter);
    }
    
    /**
     * Clear all caches
     */
    public void clearCaches() {
        constructorCache.clear();
        fieldCache.clear();
        methodCache.clear();
        excelColumnFieldCache.clear();
        // Don't clear type converters as they are static
        
        cacheHits = 0;
        cacheMisses = 0;
        
        logger.info("All reflection caches cleared");
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            cacheHits, 
            cacheMisses, 
            constructorCache.size(),
            fieldCache.size(),
            methodCache.size(),
            excelColumnFieldCache.size(),
            typeConverterCache.size()
        );
    }
    
    /**
     * Cache statistics holder
     */
    public static class CacheStatistics {
        private final long hits;
        private final long misses;
        private final int constructorCacheSize;
        private final int fieldCacheSize;
        private final int methodCacheSize;
        private final int excelColumnCacheSize;
        private final int typeConverterCacheSize;
        
        public CacheStatistics(long hits, long misses, int constructorCacheSize, 
                             int fieldCacheSize, int methodCacheSize, 
                             int excelColumnCacheSize, int typeConverterCacheSize) {
            this.hits = hits;
            this.misses = misses;
            this.constructorCacheSize = constructorCacheSize;
            this.fieldCacheSize = fieldCacheSize;
            this.methodCacheSize = methodCacheSize;
            this.excelColumnCacheSize = excelColumnCacheSize;
            this.typeConverterCacheSize = typeConverterCacheSize;
        }
        
        public long getHits() {
            return hits;
        }
        
        public long getMisses() {
            return misses;
        }
        
        public long getTotal() {
            return hits + misses;
        }
        
        public double getHitRatio() {
            long total = getTotal();
            return total > 0 ? (double) hits / total : 0.0;
        }
        
        public int getConstructorCacheSize() {
            return constructorCacheSize;
        }
        
        public int getFieldCacheSize() {
            return fieldCacheSize;
        }
        
        public int getMethodCacheSize() {
            return methodCacheSize;
        }
        
        public int getExcelColumnCacheSize() {
            return excelColumnCacheSize;
        }
        
        public int getTypeConverterCacheSize() {
            return typeConverterCacheSize;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStatistics{hits=%d, misses=%d, hitRatio=%.2f%%, " +
                    "constructors=%d, fields=%d, methods=%d, excelColumns=%d, typeConverters=%d}",
                    hits, misses, getHitRatio() * 100, 
                    constructorCacheSize, fieldCacheSize, methodCacheSize, 
                    excelColumnCacheSize, typeConverterCacheSize);
        }
    }
}