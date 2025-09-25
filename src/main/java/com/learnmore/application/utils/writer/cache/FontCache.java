package com.learnmore.application.utils.writer.cache;

import com.learnmore.application.utils.config.ExcelConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance cache for Excel Font objects
 * Reduces Font creation overhead and memory usage
 */
@Slf4j
public class FontCache {
    
    private final ExcelConfig config;
    private final ConcurrentHashMap<String, Font> fontCache;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong creationTime = new AtomicLong(0);
    
    // Pre-defined font keys
    private static final String DEFAULT_FONT = "DEFAULT";
    private static final String HEADER_FONT = "HEADER";
    private static final String BOLD_FONT = "BOLD";
    private static final String ITALIC_FONT = "ITALIC";
    private static final String TITLE_FONT = "TITLE";
    
    public FontCache(ExcelConfig config) {
        this.config = config;
        this.fontCache = new ConcurrentHashMap<>();
        log.debug("FontCache initialized");
    }
    
    /**
     * Get default font
     */
    public Font getDefaultFont(Workbook workbook) {
        return getOrCreateFont(DEFAULT_FONT, workbook, this::createDefaultFont);
    }
    
    /**
     * Get header font
     */
    public Font getHeaderFont(Workbook workbook) {
        return getOrCreateFont(HEADER_FONT, workbook, this::createHeaderFont);
    }
    
    /**
     * Get bold font
     */
    public Font getBoldFont(Workbook workbook) {
        return getOrCreateFont(BOLD_FONT, workbook, this::createBoldFont);
    }
    
    /**
     * Get italic font
     */
    public Font getItalicFont(Workbook workbook) {
        return getOrCreateFont(ITALIC_FONT, workbook, this::createItalicFont);
    }
    
    /**
     * Get title font
     */
    public Font getTitleFont(Workbook workbook) {
        return getOrCreateFont(TITLE_FONT, workbook, this::createTitleFont);
    }
    
    /**
     * Get custom font with specific properties
     */
    public Font getCustomFont(Workbook workbook, String fontName, short size, boolean bold, boolean italic) {
        String fontKey = String.format("CUSTOM_%s_%d_%s_%s", fontName, size, bold, italic);
        return getOrCreateFont(fontKey, workbook, wb -> createCustomFont(wb, fontName, size, bold, italic));
    }
    
    /**
     * Generic method to get or create fonts with caching
     */
    private Font getOrCreateFont(String fontKey, Workbook workbook, FontCreator creator) {
        Font font = fontCache.get(fontKey);
        
        if (font != null) {
            cacheHits.incrementAndGet();
            return font;
        }
        
        // Cache miss - create new font
        cacheMisses.incrementAndGet();
        long startTime = System.nanoTime();
        
        try {
            font = creator.create(workbook);
            fontCache.put(fontKey, font);
            
            long duration = System.nanoTime() - startTime;
            creationTime.addAndGet(duration);
            
            log.debug("Created and cached font: {} in {} ns", fontKey, duration);
            return font;
            
        } catch (Exception e) {
            log.error("Failed to create font: {}", fontKey, e);
            return workbook.createFont(); // Return basic font as fallback
        }
    }
    
    // Font creation methods
    
    private Font createDefaultFont(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        return font;
    }
    
    private Font createHeaderFont(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        return font;
    }
    
    private Font createBoldFont(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        return font;
    }
    
    private Font createItalicFont(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setItalic(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        return font;
    }
    
    private Font createTitleFont(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);
        font.setColor(IndexedColors.BLUE.getIndex());
        return font;
    }
    
    private Font createCustomFont(Workbook workbook, String fontName, short size, boolean bold, boolean italic) {
        Font font = workbook.createFont();
        font.setFontName(fontName);
        font.setFontHeightInPoints(size);
        font.setBold(bold);
        font.setItalic(italic);
        font.setColor(IndexedColors.BLACK.getIndex());
        return font;
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
            fontCache.size(),
            avgCreationTime,
            creationTime.get() / 1_000_000.0
        );
    }
    
    /**
     * Clear cache
     */
    public void clearCache() {
        fontCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        creationTime.set(0);
        log.info("FontCache cleared");
    }
    
    /**
     * Get cache size
     */
    public int getCacheSize() {
        return fontCache.size();
    }
    
    /**
     * Get cache hit rate
     */
    public double getHitRate() {
        long totalAccess = cacheHits.get() + cacheMisses.get();
        return totalAccess > 0 ? (double) cacheHits.get() / totalAccess : 0.0;
    }
    
    // Functional interface for font creation
    @FunctionalInterface
    private interface FontCreator {
        Font create(Workbook workbook);
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
                "FontCacheStats{hits=%d, misses=%d, hitRate=%.2f%%, size=%d, avgCreation=%.3fms}",
                hits, misses, hitRate * 100, cacheSize, avgCreationTimeMs
            );
        }
    }
}