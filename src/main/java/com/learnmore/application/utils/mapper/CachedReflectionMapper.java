package com.learnmore.application.utils.mapper;

import com.learnmore.application.utils.ExcelColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached Reflection Mapper for performance comparison with MethodHandle approach
 * 
 * Performance Strategy:
 * - Cache Method objects once during initialization
 * - Use Method.invoke() for field access
 * - Minimize reflection overhead through caching
 * - Thread-safe implementation with ConcurrentHashMap
 * 
 * @param <T> The bean class type
 */
public class CachedReflectionMapper<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(CachedReflectionMapper.class);
    
    // Thread-safe cache for mappers by class
    private static final Map<Class<?>, CachedReflectionMapper<?>> MAPPER_CACHE = new ConcurrentHashMap<>();
    
    private final Class<T> beanClass;
    private final List<FieldAccessor> fieldAccessors;
    private final Map<String, FieldAccessor> nameToAccessorMap;
    
    // Performance tracking
    private volatile long totalMappingTime = 0;
    private volatile long totalMappedRecords = 0;
    private volatile long cacheHits = 0;
    private volatile long cacheMisses = 0;
    
    /**
     * Field accessor containing cached getter and setter methods
     */
    private static class FieldAccessor {
        final String fieldName;
        final String columnName;
        final Class<?> fieldType;
        final Method getter;
        final Method setter;
        
        // Performance metrics per field
        volatile long accessCount = 0;
        volatile long totalAccessTime = 0;
        volatile long getterInvocations = 0;
        volatile long setterInvocations = 0;
        
        FieldAccessor(String fieldName, String columnName, Class<?> fieldType, 
                     Method getter, Method setter) {
            this.fieldName = fieldName;
            this.columnName = columnName;
            this.fieldType = fieldType;
            this.getter = getter;
            this.setter = setter;
            
            // Ensure methods are accessible
            if (getter != null) {
                getter.setAccessible(true);
            }
            if (setter != null) {
                setter.setAccessible(true);
            }
        }
        
        /**
         * Get field value using cached getter method
         */
        public Object getValue(Object instance) {
            if (getter == null) {
                throw new RuntimeException("No getter available for field: " + fieldName);
            }
            
            long startTime = System.nanoTime();
            try {
                Object result = getter.invoke(instance);
                getterInvocations++;
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get value for field: " + fieldName, e);
            } finally {
                long duration = System.nanoTime() - startTime;
                accessCount++;
                totalAccessTime += duration;
            }
        }
        
        /**
         * Set field value using cached setter method
         */
        public void setValue(Object instance, Object value) {
            if (setter == null) {
                throw new RuntimeException("No setter available for field: " + fieldName);
            }
            
            long startTime = System.nanoTime();
            try {
                setter.invoke(instance, value);
                setterInvocations++;
            } catch (Exception e) {
                throw new RuntimeException("Failed to set value for field: " + fieldName, e);
            } finally {
                long duration = System.nanoTime() - startTime;
                accessCount++;
                totalAccessTime += duration;
            }
        }
        
        /**
         * Get average access time in nanoseconds
         */
        public double getAverageAccessTimeNs() {
            return accessCount > 0 ? (double) totalAccessTime / accessCount : 0.0;
        }
        
        /**
         * Get field performance stats
         */
        public FieldStats getStats() {
            return new FieldStats(
                fieldName, 
                accessCount, 
                getterInvocations, 
                setterInvocations,
                getAverageAccessTimeNs()
            );
        }
    }
    
    /**
     * Field performance statistics
     */
    public static class FieldStats {
        public final String fieldName;
        public final long totalAccess;
        public final long getterCalls;
        public final long setterCalls;
        public final double avgAccessTimeNs;
        
        FieldStats(String fieldName, long totalAccess, long getterCalls, 
                  long setterCalls, double avgAccessTimeNs) {
            this.fieldName = fieldName;
            this.totalAccess = totalAccess;
            this.getterCalls = getterCalls;
            this.setterCalls = setterCalls;
            this.avgAccessTimeNs = avgAccessTimeNs;
        }
        
        @Override
        public String toString() {
            return String.format(
                "FieldStats{field='%s', access=%d, get=%d, set=%d, avgTime=%.2fns}",
                fieldName, totalAccess, getterCalls, setterCalls, avgAccessTimeNs
            );
        }
    }
    
    /**
     * Private constructor - use factory method
     */
    private CachedReflectionMapper(Class<T> beanClass) {
        this.beanClass = beanClass;
        this.fieldAccessors = new ArrayList<>();
        this.nameToAccessorMap = new HashMap<>();
        
        initializeFieldAccessors();
        
        logger.info("Initialized CachedReflectionMapper for {} with {} fields", 
                   beanClass.getSimpleName(), fieldAccessors.size());
    }
    
    /**
     * Factory method with caching
     */
    @SuppressWarnings("unchecked")
    public static <T> CachedReflectionMapper<T> of(Class<T> beanClass) {
        return (CachedReflectionMapper<T>) MAPPER_CACHE.computeIfAbsent(beanClass, 
            clazz -> {
                logger.debug("Creating new CachedReflectionMapper for {}", clazz.getSimpleName());
                return new CachedReflectionMapper<>(clazz);
            });
    }
    
    /**
     * Initialize field accessors with cached reflection methods
     */
    private void initializeFieldAccessors() {
        Field[] fields = beanClass.getDeclaredFields();
        
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation == null) continue;
            
            String fieldName = field.getName();
            String columnName = annotation.name();
            if (columnName.isEmpty()) {
                columnName = fieldName;
            }
            
            Class<?> fieldType = field.getType();
            
            try {
                // Find and cache getter method
                Method getter = findGetter(field);
                Method setter = findSetter(field);
                
                if (getter == null && setter == null) {
                    logger.warn("No getter or setter found for field: {}", fieldName);
                    continue;
                }
                
                // Create field accessor
                FieldAccessor accessor = new FieldAccessor(
                    fieldName, columnName, fieldType, getter, setter
                );
                
                fieldAccessors.add(accessor);
                nameToAccessorMap.put(columnName, accessor);
                nameToAccessorMap.put(fieldName, accessor);
                
                logger.debug("Cached accessor for field '{}' -> column '{}' (getter: {}, setter: {})", 
                           fieldName, columnName, getter != null, setter != null);
                
            } catch (Exception e) {
                logger.error("Failed to create accessor for field: {}", fieldName, e);
            }
        }
        
        // Sort by column name for consistent ordering
        fieldAccessors.sort(Comparator.comparing(a -> a.columnName));
        
        logger.info("Successfully cached {} field accessors for {}", 
                   fieldAccessors.size(), beanClass.getSimpleName());
    }
    
    /**
     * Find getter method for field
     */
    private Method findGetter(Field field) {
        String fieldName = field.getName();
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        
        try {
            return beanClass.getMethod(getterName);
        } catch (NoSuchMethodException e) {
            // Try boolean getter
            if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                String booleanGetterName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                try {
                    return beanClass.getMethod(booleanGetterName);
                } catch (NoSuchMethodException ex) {
                    logger.debug("No standard getter found for field: {}", fieldName);
                    return null;
                }
            }
            return null;
        }
    }
    
    /**
     * Find setter method for field
     */
    private Method findSetter(Field field) {
        String fieldName = field.getName();
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        
        try {
            return beanClass.getMethod(setterName, field.getType());
        } catch (NoSuchMethodException e) {
            logger.debug("No standard setter found for field: {}", fieldName);
            return null;
        }
    }
    
    /**
     * Get field value from bean instance
     */
    public Object getFieldValue(T instance, String columnName) {
        FieldAccessor accessor = nameToAccessorMap.get(columnName);
        if (accessor == null) {
            cacheMisses++;
            throw new IllegalArgumentException("No mapping found for column: " + columnName);
        }
        
        cacheHits++;
        long startTime = System.nanoTime();
        try {
            return accessor.getValue(instance);
        } finally {
            long duration = System.nanoTime() - startTime;
            totalMappingTime += duration;
            totalMappedRecords++;
        }
    }
    
    /**
     * Set field value to bean instance
     */
    public void setFieldValue(T instance, String columnName, Object value) {
        FieldAccessor accessor = nameToAccessorMap.get(columnName);
        if (accessor == null) {
            cacheMisses++;
            throw new IllegalArgumentException("No mapping found for column: " + columnName);
        }
        
        cacheHits++;
        long startTime = System.nanoTime();
        try {
            // Type conversion if needed
            Object convertedValue = convertValue(value, accessor.fieldType);
            accessor.setValue(instance, convertedValue);
        } finally {
            long duration = System.nanoTime() - startTime;
            totalMappingTime += duration;
            totalMappedRecords++;
        }
    }
    
    /**
     * Convert value to target field type
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        
        // String conversions
        if (targetType == String.class) {
            return value.toString();
        }
        
        String stringValue = value.toString().trim();
        if (stringValue.isEmpty()) return null;
        
        // Numeric conversions
        try {
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(stringValue);
            }
            if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(stringValue);
            }
            if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(stringValue);
            }
            if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(stringValue);
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(stringValue);
            }
        } catch (NumberFormatException e) {
            logger.debug("Failed to convert '{}' to {}", stringValue, targetType.getSimpleName());
            return null;
        }
        
        return value; // Return as-is if no conversion available
    }
    
    /**
     * Get all field accessors
     */
    public List<FieldAccessor> getFieldAccessors() {
        return Collections.unmodifiableList(fieldAccessors);
    }
    
    /**
     * Get column names in order
     */
    public List<String> getColumnNames() {
        return fieldAccessors.stream()
            .map(accessor -> accessor.columnName)
            .toList();
    }
    
    /**
     * Create new instance of the bean class
     */
    public T createInstance() {
        try {
            return beanClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + beanClass.getSimpleName(), e);
        }
    }
    
    /**
     * Convert bean to array of values
     */
    public Object[] beanToArray(T instance) {
        return fieldAccessors.stream()
            .map(accessor -> accessor.getValue(instance))
            .toArray();
    }
    
    /**
     * Convert array of values to bean
     */
    public T arrayToBean(Object[] values) {
        T instance = createInstance();
        
        for (int i = 0; i < Math.min(values.length, fieldAccessors.size()); i++) {
            FieldAccessor accessor = fieldAccessors.get(i);
            Object value = values[i];
            
            if (value != null) {
                accessor.setValue(instance, value);
            }
        }
        
        return instance;
    }
    
    /**
     * Get performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        double avgMappingTimeNs = totalMappedRecords > 0 ? 
            (double) totalMappingTime / totalMappedRecords : 0.0;
        
        double cacheHitRatio = (cacheHits + cacheMisses) > 0 ? 
            (double) cacheHits / (cacheHits + cacheMisses) : 0.0;
        
        List<FieldStats> fieldStats = fieldAccessors.stream()
            .map(FieldAccessor::getStats)
            .toList();
        
        return new PerformanceStats(
            totalMappedRecords,
            totalMappingTime,
            avgMappingTimeNs,
            cacheHits,
            cacheMisses,
            cacheHitRatio,
            fieldAccessors.size(),
            fieldStats
        );
    }
    
    /**
     * Performance statistics
     */
    public static class PerformanceStats {
        public final long totalMappedRecords;
        public final long totalMappingTimeNs;
        public final double avgMappingTimeNs;
        public final long cacheHits;
        public final long cacheMisses;
        public final double cacheHitRatio;
        public final int fieldCount;
        public final List<FieldStats> fieldStats;
        
        PerformanceStats(long totalMappedRecords, long totalMappingTimeNs, 
                        double avgMappingTimeNs, long cacheHits, long cacheMisses,
                        double cacheHitRatio, int fieldCount, List<FieldStats> fieldStats) {
            this.totalMappedRecords = totalMappedRecords;
            this.totalMappingTimeNs = totalMappingTimeNs;
            this.avgMappingTimeNs = avgMappingTimeNs;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheHitRatio = cacheHitRatio;
            this.fieldCount = fieldCount;
            this.fieldStats = fieldStats;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CachedReflectionStats{records=%d, avgTime=%.2fns, cacheHit=%.1f%%, fields=%d}",
                totalMappedRecords, avgMappingTimeNs, cacheHitRatio * 100, fieldCount
            );
        }
    }
    
    /**
     * Clear all cached mappers
     */
    public static void clearCache() {
        MAPPER_CACHE.clear();
        logger.info("Cleared CachedReflectionMapper cache");
    }
    
    /**
     * Get cache statistics
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cached_classes", MAPPER_CACHE.size());
        stats.put("cached_class_names", 
            MAPPER_CACHE.keySet().stream().map(Class::getSimpleName).toList());
        return stats;
    }
}