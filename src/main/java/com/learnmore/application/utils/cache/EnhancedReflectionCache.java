package com.learnmore.application.utils.cache;

import com.learnmore.application.utils.ExcelColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced caching strategy with high-performance concurrent maps
 * Optimized for field reflection and column mapping with built-in Java
 */
public class EnhancedReflectionCache {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedReflectionCache.class);
    
    private static volatile EnhancedReflectionCache instance;
    
    // Field reflection cache with concurrent access
    private final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();
    
    // Column mapping cache for different Excel structures
    private final Map<String, Map<Integer, String>> columnMappingCache = new ConcurrentHashMap<>();
    
    // Class metadata cache
    private final Map<Class<?>, ClassMetadata> classMetadataCache = new ConcurrentHashMap<>();
    
    // Performance statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheLoadTime = new AtomicLong(0);
    
    private EnhancedReflectionCache() {
        logger.info("Enhanced reflection cache initialized with ConcurrentHashMap");
    }
    
    public static EnhancedReflectionCache getInstance() {
        if (instance == null) {
            synchronized (EnhancedReflectionCache.class) {
                if (instance == null) {
                    instance = new EnhancedReflectionCache();
                }
            }
        }
        return instance;
    }
    
    /**
     * Get Excel column fields for a class with automatic caching
     */
    public Map<String, Field> getExcelColumnFields(Class<?> clazz) {
        Map<String, Field> fields = fieldCache.get(clazz);
        if (fields == null) {
            long startTime = System.nanoTime();
            fields = buildFieldMap(clazz);
            fieldCache.put(clazz, fields);
            long loadTime = System.nanoTime() - startTime;
            
            cacheMisses.incrementAndGet();
            cacheLoadTime.addAndGet(loadTime);
        } else {
            cacheHits.incrementAndGet();
        }
        
        return fields;
    }
    
    /**
     * Get column mapping with caching
     */
    public Map<Integer, String> getColumnMapping(String mappingKey) {
        Map<Integer, String> mapping = columnMappingCache.get(mappingKey);
        if (mapping == null) {
            mapping = buildColumnMapping(mappingKey);
            columnMappingCache.put(mappingKey, mapping);
        }
        return mapping;
    }
    
    /**
     * Get class metadata with caching
     */
    public ClassMetadata getClassMetadata(Class<?> clazz) {
        ClassMetadata metadata = classMetadataCache.get(clazz);
        if (metadata == null) {
            metadata = buildClassMetadata(clazz);
            classMetadataCache.put(clazz, metadata);
        }
        return metadata;
    }
    
    /**
     * Build field mapping for Excel columns
     */
    private Map<String, Field> buildFieldMap(Class<?> clazz) {
        logger.debug("Building field map for class: {}", clazz.getName());
        
        Map<String, Field> fieldMap = new ConcurrentHashMap<>();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            if (field.isAnnotationPresent(ExcelColumn.class)) {
                ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                String columnName = annotation.name().isEmpty() ? field.getName() : annotation.name();
                
                field.setAccessible(true);
                fieldMap.put(columnName, field);
                
                logger.trace("Mapped Excel column '{}' to field '{}'", columnName, field.getName());
            }
        }
        
        logger.debug("Built field map with {} columns for class: {}", fieldMap.size(), clazz.getName());
        return fieldMap;
    }
    
    /**
     * Build column mapping from Excel header structure
     */
    private Map<Integer, String> buildColumnMapping(String mappingKey) {
        logger.debug("Building column mapping for key: {}", mappingKey);
        
        // This is a placeholder - in real implementation, this would parse
        // the Excel header structure and map column indices to column names
        Map<Integer, String> mapping = new ConcurrentHashMap<>();
        
        // Example mapping logic would go here
        // For now, return empty mapping
        return mapping;
    }
    
    /**
     * Build class metadata including field count, validation info, etc.
     */
    private ClassMetadata buildClassMetadata(Class<?> clazz) {
        logger.debug("Building class metadata for: {}", clazz.getName());
        
        Map<String, Field> fields = buildFieldMap(clazz);
        int fieldCount = fields.size();
        boolean hasValidation = fields.values().stream()
                .anyMatch(field -> field.getAnnotations().length > 1); // Has more than just @ExcelColumn
        
        return new ClassMetadata(clazz.getName(), fieldCount, hasValidation, fields.keySet());
    }
    
    /**
     * Get comprehensive cache statistics
     */
    public CacheStatistics getStatistics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = hits + misses > 0 ? (double) hits / (hits + misses) : 0.0;
        
        return new CacheStatistics(
                hits, misses, hitRate, fieldCache.size(),
                0L, 0L, 0.0, columnMappingCache.size(),
                0L, 0L, 0.0, classMetadataCache.size());
    }
    
    /**
     * Preload cache for commonly used classes
     */
    public void preloadCache(Class<?>... classes) {
        logger.info("Preloading cache for {} classes", classes.length);
        
        for (Class<?> clazz : classes) {
            try {
                getExcelColumnFields(clazz);
                getClassMetadata(clazz);
                logger.debug("Preloaded cache for class: {}", clazz.getName());
            } catch (Exception e) {
                logger.warn("Failed to preload cache for class: {}", clazz.getName(), e);
            }
        }
        
        logger.info("Cache preloading completed");
    }
    
    /**
     * Refresh cache for specific class
     */
    public void refreshCache(Class<?> clazz) {
        fieldCache.remove(clazz);
        classMetadataCache.remove(clazz);
        // Rebuild cache
        getExcelColumnFields(clazz);
        getClassMetadata(clazz);
        logger.debug("Refreshed cache for class: {}", clazz.getName());
    }
    
    /**
     * Clear all caches
     */
    public void clearCaches() {
        fieldCache.clear();
        columnMappingCache.clear();
        classMetadataCache.clear();
        
        cacheHits.set(0);
        cacheMisses.set(0);
        cacheLoadTime.set(0);
        
        logger.info("All enhanced caches cleared");
    }
    
    /**
     * Class metadata for optimization
     */
    public static class ClassMetadata {
        private final String className;
        private final int fieldCount;
        private final boolean hasValidation;
        private final java.util.Set<String> columnNames;
        
        public ClassMetadata(String className, int fieldCount, boolean hasValidation, 
                           java.util.Set<String> columnNames) {
            this.className = className;
            this.fieldCount = fieldCount;
            this.hasValidation = hasValidation;
            this.columnNames = columnNames;
        }
        
        public String getClassName() { return className; }
        public int getFieldCount() { return fieldCount; }
        public boolean hasValidation() { return hasValidation; }
        public java.util.Set<String> getColumnNames() { return columnNames; }
    }
    
    /**
     * Cache performance statistics
     */
    public static class CacheStatistics {
        private final long fieldCacheHits;
        private final long fieldCacheMisses;
        private final double fieldCacheHitRate;
        private final long fieldCacheSize;
        
        private final long columnCacheHits;
        private final long columnCacheMisses;
        private final double columnCacheHitRate;
        private final long columnCacheSize;
        
        private final long metadataCacheHits;
        private final long metadataCacheMisses;
        private final double metadataCacheHitRate;
        private final long metadataCacheSize;
        
        public CacheStatistics(long fieldCacheHits, long fieldCacheMisses, double fieldCacheHitRate, long fieldCacheSize,
                             long columnCacheHits, long columnCacheMisses, double columnCacheHitRate, long columnCacheSize,
                             long metadataCacheHits, long metadataCacheMisses, double metadataCacheHitRate, long metadataCacheSize) {
            this.fieldCacheHits = fieldCacheHits;
            this.fieldCacheMisses = fieldCacheMisses;
            this.fieldCacheHitRate = fieldCacheHitRate;
            this.fieldCacheSize = fieldCacheSize;
            this.columnCacheHits = columnCacheHits;
            this.columnCacheMisses = columnCacheMisses;
            this.columnCacheHitRate = columnCacheHitRate;
            this.columnCacheSize = columnCacheSize;
            this.metadataCacheHits = metadataCacheHits;
            this.metadataCacheMisses = metadataCacheMisses;
            this.metadataCacheHitRate = metadataCacheHitRate;
            this.metadataCacheSize = metadataCacheSize;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Enhanced Cache Statistics:\n" +
                "Field Cache: %d hits, %d misses (%.2f%% hit rate, %d entries)\n" +
                "Column Cache: %d hits, %d misses (%.2f%% hit rate, %d entries)\n" +
                "Metadata Cache: %d hits, %d misses (%.2f%% hit rate, %d entries)",
                fieldCacheHits, fieldCacheMisses, fieldCacheHitRate * 100, fieldCacheSize,
                columnCacheHits, columnCacheMisses, columnCacheHitRate * 100, columnCacheSize,
                metadataCacheHits, metadataCacheMisses, metadataCacheHitRate * 100, metadataCacheSize);
        }
        
        // Getters
        public long getFieldCacheHits() { return fieldCacheHits; }
        public long getFieldCacheMisses() { return fieldCacheMisses; }
        public double getFieldCacheHitRate() { return fieldCacheHitRate; }
        public long getFieldCacheSize() { return fieldCacheSize; }
        public long getColumnCacheHits() { return columnCacheHits; }
        public long getColumnCacheMisses() { return columnCacheMisses; }
        public double getColumnCacheHitRate() { return columnCacheHitRate; }
        public long getColumnCacheSize() { return columnCacheSize; }
        public long getMetadataCacheHits() { return metadataCacheHits; }
        public long getMetadataCacheMisses() { return metadataCacheMisses; }
        public double getMetadataCacheHitRate() { return metadataCacheHitRate; }
        public long getMetadataCacheSize() { return metadataCacheSize; }
    }
}