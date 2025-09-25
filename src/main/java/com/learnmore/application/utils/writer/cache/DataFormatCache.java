package com.learnmore.application.utils.writer.cache;

import com.learnmore.application.utils.config.ExcelConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance cache for Excel DataFormat objects
 * Reduces DataFormat lookup and creation overhead
 */
@Slf4j
public class DataFormatCache {
    
    private final ConcurrentHashMap<String, Short> formatCache;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong creationTime = new AtomicLong(0);
    
    // Pre-defined format strings
    public static final String GENERAL = "General";
    public static final String INTEGER = "#,##0";
    public static final String DECIMAL = "#,##0.00";
    public static final String CURRENCY = "\"$\"#,##0.00";
    public static final String PERCENTAGE = "0.00%";
    public static final String DATE = "yyyy-mm-dd";
    public static final String DATETIME = "yyyy-mm-dd hh:mm:ss";
    public static final String TIME = "hh:mm:ss";
    public static final String TEXT = "@";
    public static final String SCIENTIFIC = "0.00E+00";
    public static final String FRACTION = "# ?/?";
    
    public DataFormatCache(ExcelConfig config) {
        this.formatCache = new ConcurrentHashMap<>();
        log.debug("DataFormatCache initialized");
    }
    
    /**
     * Get format index for format string
     */
    public short getFormat(Workbook workbook, String formatString) {
        if (formatString == null || formatString.trim().isEmpty()) {
            formatString = GENERAL;
        }
        
        Short formatIndex = formatCache.get(formatString);
        
        if (formatIndex != null) {
            cacheHits.incrementAndGet();
            return formatIndex;
        }
        
        // Cache miss - create new format
        cacheMisses.incrementAndGet();
        long startTime = System.nanoTime();
        
        try {
            DataFormat dataFormat = workbook.createDataFormat();
            formatIndex = dataFormat.getFormat(formatString);
            formatCache.put(formatString, formatIndex);
            
            long duration = System.nanoTime() - startTime;
            creationTime.addAndGet(duration);
            
            log.debug("Created and cached format: '{}' -> {} in {} ns", formatString, formatIndex, duration);
            return formatIndex;
            
        } catch (Exception e) {
            log.error("Failed to create format: '{}'", formatString, e);
            return 0; // Return General format as fallback
        }
    }
    
    /**
     * Get pre-defined formats
     */
    public short getGeneralFormat(Workbook workbook) {
        return getFormat(workbook, GENERAL);
    }
    
    public short getIntegerFormat(Workbook workbook) {
        return getFormat(workbook, INTEGER);
    }
    
    public short getDecimalFormat(Workbook workbook) {
        return getFormat(workbook, DECIMAL);
    }
    
    public short getCurrencyFormat(Workbook workbook) {
        return getFormat(workbook, CURRENCY);
    }
    
    public short getPercentageFormat(Workbook workbook) {
        return getFormat(workbook, PERCENTAGE);
    }
    
    public short getDateFormat(Workbook workbook) {
        return getFormat(workbook, DATE);
    }
    
    public short getDateTimeFormat(Workbook workbook) {
        return getFormat(workbook, DATETIME);
    }
    
    public short getTimeFormat(Workbook workbook) {
        return getFormat(workbook, TIME);
    }
    
    public short getTextFormat(Workbook workbook) {
        return getFormat(workbook, TEXT);
    }
    
    public short getScientificFormat(Workbook workbook) {
        return getFormat(workbook, SCIENTIFIC);
    }
    
    public short getFractionFormat(Workbook workbook) {
        return getFormat(workbook, FRACTION);
    }
    
    /**
     * Get format based on data type
     */
    public short getFormatForType(Workbook workbook, Class<?> dataType) {
        if (dataType == null) {
            return getGeneralFormat(workbook);
        }
        
        if (dataType == String.class) {
            return getTextFormat(workbook);
        } else if (dataType == Integer.class || dataType == int.class ||
                   dataType == Long.class || dataType == long.class) {
            return getIntegerFormat(workbook);
        } else if (dataType == Double.class || dataType == double.class ||
                   dataType == Float.class || dataType == float.class) {
            return getDecimalFormat(workbook);
        } else if (dataType == java.util.Date.class || dataType == java.time.LocalDate.class) {
            return getDateFormat(workbook);
        } else if (dataType == java.time.LocalDateTime.class || dataType == java.sql.Timestamp.class) {
            return getDateTimeFormat(workbook);
        } else if (dataType == java.time.LocalTime.class || dataType == java.sql.Time.class) {
            return getTimeFormat(workbook);
        } else if (dataType == Boolean.class || dataType == boolean.class) {
            return getGeneralFormat(workbook);
        } else {
            return getTextFormat(workbook);
        }
    }
    
    /**
     * Get custom numeric format with specific decimal places
     */
    public short getNumericFormat(Workbook workbook, int decimalPlaces) {
        String formatString;
        if (decimalPlaces == 0) {
            formatString = "#,##0";
        } else {
            StringBuilder sb = new StringBuilder("#,##0.");
            for (int i = 0; i < decimalPlaces; i++) {
                sb.append("0");
            }
            formatString = sb.toString();
        }
        return getFormat(workbook, formatString);
    }
    
    /**
     * Get custom currency format with specific symbol
     */
    public short getCurrencyFormat(Workbook workbook, String currencySymbol) {
        String formatString = "\"" + currencySymbol + "\"#,##0.00";
        return getFormat(workbook, formatString);
    }
    
    /**
     * Get custom date format
     */
    public short getCustomDateFormat(Workbook workbook, String pattern) {
        return getFormat(workbook, pattern);
    }
    
    /**
     * Check if format string is numeric
     */
    public boolean isNumericFormat(String formatString) {
        if (formatString == null) return false;
        
        return formatString.contains("#") || formatString.contains("0") || 
               formatString.contains("%") || formatString.contains("E");
    }
    
    /**
     * Check if format string is date/time
     */
    public boolean isDateTimeFormat(String formatString) {
        if (formatString == null) return false;
        
        return formatString.contains("yyyy") || formatString.contains("mm") || 
               formatString.contains("dd") || formatString.contains("hh") ||
               formatString.contains("ss");
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        long totalAccess = cacheHits.get() + cacheMisses.get();
        double hitRate = totalAccess > 0 ? (double) cacheHits.get() / totalAccess : 0.0;
        double avgCreationTime = cacheMisses.get() > 0 ? 
            (double) creationTime.get() / cacheMisses.get() / 1_000_000.0 : 0.0;
        
        return new CacheStatistics(
            cacheHits.get(),
            cacheMisses.get(),
            hitRate,
            formatCache.size(),
            avgCreationTime,
            creationTime.get() / 1_000_000.0
        );
    }
    
    /**
     * Clear cache
     */
    public void clearCache() {
        formatCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        creationTime.set(0);
        log.info("DataFormatCache cleared");
    }
    
    /**
     * Get cache size
     */
    public int getCacheSize() {
        return formatCache.size();
    }
    
    /**
     * Get cache hit rate
     */
    public double getHitRate() {
        long totalAccess = cacheHits.get() + cacheMisses.get();
        return totalAccess > 0 ? (double) cacheHits.get() / totalAccess : 0.0;
    }
    
    /**
     * Get all cached format strings
     */
    public java.util.Set<String> getCachedFormats() {
        return new java.util.HashSet<>(formatCache.keySet());
    }
    
    // Statistics class
    public static class CacheStatistics {
        private final long hits;
        private final long misses;
        private final double hitRate;
        private final int cacheSize;
        private final double avgCreationTimeMs;
        private final double totalCreationTimeMs;
        
        public CacheStatistics(long hits, long misses, double hitRate, int cacheSize, 
                             double avgCreationTimeMs, double totalCreationTimeMs) {
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
            this.cacheSize = cacheSize;
            this.avgCreationTimeMs = avgCreationTimeMs;
            this.totalCreationTimeMs = totalCreationTimeMs;
        }
        
        // Getters
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public double getHitRate() { return hitRate; }
        public int getCacheSize() { return cacheSize; }
        public double getAvgCreationTimeMs() { return avgCreationTimeMs; }
        public double getTotalCreationTimeMs() { return totalCreationTimeMs; }
        
        @Override
        public String toString() {
            return String.format(
                "DataFormatCacheStats{hits=%d, misses=%d, hitRate=%.2f%%, size=%d, avgCreation=%.3fms}",
                hits, misses, hitRate * 100, cacheSize, avgCreationTimeMs
            );
        }
    }
}