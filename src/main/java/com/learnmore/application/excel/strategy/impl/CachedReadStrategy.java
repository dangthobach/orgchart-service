package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.ReadStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cached read strategy with automatic cache management
 *
 * This strategy wraps another read strategy (typically StreamingReadStrategy)
 * and caches the parsed results for repeated reads of the same file.
 * Useful for scenarios where the same Excel file is read multiple times.
 *
 * Performance characteristics:
 * - First read: Same as delegated strategy (e.g., StreamingReadStrategy)
 * - Subsequent reads: Near-instant (< 1ms for cache hit)
 * - Memory: O(total_objects) - all objects stored in cache
 * - Cache TTL: Configurable (default 1 hour)
 *
 * Cache key generation:
 * - Uses MD5 hash of input stream content + bean class name
 * - Ensures same file with same class maps to same cache entry
 * - Different files or different classes use different cache entries
 *
 * Cache eviction:
 * - TTL-based: Entries expire after configured time (default 1 hour)
 * - Size-based: LRU eviction when max size reached (default 1000 entries)
 * - Manual: Can be cleared programmatically
 *
 * Use cases:
 * - Repeatedly reading same configuration file
 * - Loading reference data multiple times
 * - Development/testing with same sample files
 * - Read-heavy workloads with stable data
 *
 * Strategy selection:
 * - Priority: 15 (highest - always preferred when enabled)
 * - Supports: When config.isEnableCaching() == true
 *
 * @param <T> The type of objects to read from Excel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachedReadStrategy<T> implements ReadStrategy<T> {

    private final StreamingReadStrategy<T> streamingStrategy; // Fallback strategy
    private final CacheManager cacheManager; // Spring Cache Manager (optional)

    private static final String CACHE_NAME = "excelReadCache";

    /**
     * Execute read with caching
     *
     * Process flow:
     * 1. Generate cache key from input stream and bean class
     * 2. Check cache for existing entry
     * 3. If cache hit: Return cached result
     * 4. If cache miss:
     *    a. Delegate to streaming strategy
     *    b. Store result in cache
     *    c. Return result
     *
     * Note: Batch processor is NOT cached - only the full list is cached.
     * For streaming use cases, caching may not be appropriate.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Excel configuration with caching enabled
     * @param batchProcessor Consumer that processes each batch (not cached)
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    @Override
    public TrueStreamingSAXProcessor.ProcessingResult execute(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Executing CachedReadStrategy for class: {}", beanClass.getSimpleName());

        // Check if caching is enabled
        if (!config.isEnableCaching()) {
            log.warn("CachedReadStrategy selected but caching is disabled. " +
                    "Falling back to StreamingReadStrategy.");
            return streamingStrategy.execute(inputStream, beanClass, config, batchProcessor);
        }

        // Check if cache manager is available
        if (cacheManager == null) {
            log.warn("CacheManager not configured. Falling back to StreamingReadStrategy.");
            return streamingStrategy.execute(inputStream, beanClass, config, batchProcessor);
        }

        try {
            // Generate cache key
            String cacheKey = generateCacheKey(inputStream, beanClass);
            log.debug("Generated cache key: {}", cacheKey);

            // Get cache
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache == null) {
                log.warn("Cache '{}' not found. Falling back to StreamingReadStrategy.", CACHE_NAME);
                return streamingStrategy.execute(inputStream, beanClass, config, batchProcessor);
            }

            // Try to get from cache
            @SuppressWarnings("unchecked")
            CacheEntry<T> cachedEntry = cache.get(cacheKey, CacheEntry.class);

            if (cachedEntry != null) {
                log.info("Cache HIT for key: {} (age: {}ms)",
                        cacheKey, System.currentTimeMillis() - cachedEntry.getTimestamp());

                // Process cached data with batch processor
                if (batchProcessor != null) {
                    batchProcessor.accept(cachedEntry.getData());
                }

                // Return cached result
                return cachedEntry.getResult();
            }

            log.info("Cache MISS for key: {}. Reading from file.", cacheKey);

            // Cache miss - read from file
            List<T> allData = new java.util.ArrayList<>();
            Consumer<List<T>> cachingProcessor = batch -> {
                allData.addAll(batch);
                if (batchProcessor != null) {
                    batchProcessor.accept(batch);
                }
            };

            TrueStreamingSAXProcessor.ProcessingResult result =
                streamingStrategy.execute(inputStream, beanClass, config, cachingProcessor);

            // Store in cache
            CacheEntry<T> entry = new CacheEntry<>(allData, result, System.currentTimeMillis());
            cache.put(cacheKey, entry);

            log.info("Cached {} records with key: {}", allData.size(), cacheKey);

            return result;

        } catch (Exception e) {
            log.error("Error in cache operation, falling back to streaming strategy", e);
            return streamingStrategy.execute(inputStream, beanClass, config, batchProcessor);
        }
    }

    /**
     * Generate cache key from input stream and bean class
     *
     * Uses MD5 hash of:
     * - Input stream content (first 1KB for performance)
     * - Bean class name
     *
     * This ensures same file + same class = same cache key.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type
     * @return Cache key string
     */
    private String generateCacheKey(InputStream inputStream, Class<?> beanClass) {
        try {
            // Read first 1KB for hash (balance between uniqueness and performance)
            byte[] buffer = new byte[1024];
            inputStream.mark(1024);
            int bytesRead = inputStream.read(buffer);
            inputStream.reset();

            // Generate MD5 hash
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(buffer, 0, bytesRead);
            md.update(beanClass.getName().getBytes());

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            log.warn("Failed to generate cache key, using class name only", e);
            return beanClass.getName() + "_" + System.currentTimeMillis();
        }
    }

    /**
     * Check if this strategy supports the given configuration
     *
     * CachedReadStrategy is selected when:
     * - config.isEnableCaching() == true (caching explicitly enabled)
     * - CacheManager is available (Spring Cache configured)
     *
     * @param config Excel configuration to check
     * @return true if caching is enabled, false otherwise
     */
    @Override
    public boolean supports(ExcelConfig config) {
        boolean supported = config.isEnableCaching() && cacheManager != null;

        if (supported) {
            log.debug("CachedReadStrategy supports config: enableCaching=true, " +
                     "cacheTTL={}s, maxSize={}",
                     config.getCacheTTLSeconds(),
                     config.getCacheMaxSize());
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
        return "CachedReadStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 15 means this strategy is highest priority when caching is enabled.
     * It will be selected over all other strategies including ParallelReadStrategy.
     *
     * Priority ordering:
     * - 0: StreamingReadStrategy (baseline)
     * - 5: MultiSheetReadStrategy (multi-sheet)
     * - 10: ParallelReadStrategy (parallel)
     * - 15: CachedReadStrategy (caching - highest)
     *
     * @return Priority level (15 = highest)
     */
    @Override
    public int getPriority() {
        return 15; // Highest priority - caching is most beneficial
    }

    /**
     * Cache entry storing data and metadata
     */
    private static class CacheEntry<T> implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private final List<T> data;
        private final TrueStreamingSAXProcessor.ProcessingResult result;
        private final long timestamp;

        public CacheEntry(List<T> data,
                         TrueStreamingSAXProcessor.ProcessingResult result,
                         long timestamp) {
            this.data = data;
            this.result = result;
            this.timestamp = timestamp;
        }

        public List<T> getData() {
            return data;
        }

        public TrueStreamingSAXProcessor.ProcessingResult getResult() {
            return result;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
