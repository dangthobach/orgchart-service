package com.learnmore.application.excel.builder;

import com.learnmore.application.excel.service.ExcelReadingService;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for Excel reading operations
 *
 * Provides a clean, readable API for configuring and executing Excel reads.
 * Hides the complexity of ExcelConfig and provides sensible defaults.
 *
 * Features:
 * - Fluent method chaining for easy configuration
 * - Sensible defaults for common use cases
 * - Type-safe API with generics
 * - Clear terminal operations (read, readStream, readAll)
 *
 * Example usage:
 * <pre>
 * // Simple read
 * List<User> users = excelFacade.reader(User.class)
 *     .read(inputStream);
 *
 * // Batch processing with configuration
 * excelFacade.reader(User.class)
 *     .batchSize(10000)
 *     .parallel()
 *     .withProgressTracking()
 *     .readStream(inputStream, batch -> {
 *         userRepository.saveAll(batch);
 *     });
 *
 * // With validation
 * List<User> users = excelFacade.reader(User.class)
 *     .withValidation("name", "email")
 *     .strictValidation()
 *     .read(inputStream);
 * </pre>
 *
 * @param <T> The type of objects to read from Excel
 */
public class ExcelReaderBuilder<T> {

    private final ExcelReadingService<T> readingService;
    private final Class<T> beanClass;
    private final ExcelConfig.Builder configBuilder;

    /**
     * Public constructor (used by ExcelFacade.reader())
     *
     * @param readingService Reading service
     * @param beanClass Bean class type
     */
    public ExcelReaderBuilder(ExcelReadingService<T> readingService, Class<T> beanClass) {
        this.readingService = readingService;
        this.beanClass = beanClass;
        this.configBuilder = ExcelConfig.builder();
    }

    // ========== Configuration Methods ==========

    /**
     * Set batch size for processing
     *
     * @param size Batch size (default 1000)
     * @return This builder
     */
    public ExcelReaderBuilder<T> batchSize(int size) {
        configBuilder.batchSize(size);
        return this;
    }

    /**
     * Enable parallel processing for multi-core systems
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> parallel() {
        configBuilder.parallelProcessing(true);
        return this;
    }

    /**
     * Disable parallel processing (default)
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> sequential() {
        configBuilder.parallelProcessing(false);
        return this;
    }

    /**
     * Enable progress tracking with default interval
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> withProgressTracking() {
        configBuilder.enableProgressTracking(true);
        return this;
    }

    /**
     * Enable progress tracking with custom interval
     *
     * @param interval Report progress every N records
     * @return This builder
     */
    public ExcelReaderBuilder<T> withProgressTracking(long interval) {
        configBuilder.enableProgressTracking(true);
        configBuilder.progressReportInterval(interval);
        return this;
    }

    /**
     * Enable memory monitoring
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> withMemoryMonitoring() {
        configBuilder.enableMemoryMonitoring(true);
        return this;
    }

    /**
     * Set memory threshold in MB
     *
     * @param thresholdMB Memory threshold
     * @return This builder
     */
    public ExcelReaderBuilder<T> memoryThreshold(long thresholdMB) {
        configBuilder.memoryThreshold(thresholdMB);
        return this;
    }

    /**
     * Enable caching for repeated reads
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> withCaching() {
        configBuilder.enableCaching(true);
        return this;
    }

    /**
     * Enable caching with custom TTL
     *
     * @param ttlSeconds Cache TTL in seconds
     * @return This builder
     */
    public ExcelReaderBuilder<T> withCaching(long ttlSeconds) {
        configBuilder.enableCaching(true);
        configBuilder.cacheTTLSeconds(ttlSeconds);
        return this;
    }

    /**
     * Read all sheets instead of just first sheet
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> readAllSheets() {
        configBuilder.readAllSheets(true);
        return this;
    }

    /**
     * Read specific sheets by name
     *
     * @param sheetNames Sheet names to read
     * @return This builder
     */
    public ExcelReaderBuilder<T> readSheets(String... sheetNames) {
        configBuilder.sheetNames(Arrays.asList(sheetNames));
        return this;
    }

    /**
     * Add required field validation
     *
     * @param fieldNames Required field names
     * @return This builder
     */
    public ExcelReaderBuilder<T> withValidation(String... fieldNames) {
        configBuilder.requiredFields(fieldNames);
        return this;
    }

    /**
     * Add unique field validation
     *
     * @param fieldNames Unique field names
     * @return This builder
     */
    public ExcelReaderBuilder<T> withUniqueFields(String... fieldNames) {
        configBuilder.uniqueFields(fieldNames);
        return this;
    }

    /**
     * Enable strict validation (fail on first error)
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> strictValidation() {
        configBuilder.strictValidation(true);
        configBuilder.failOnFirstError(true);
        return this;
    }

    /**
     * Enable lenient validation (log errors but continue)
     *
     * @return This builder
     */
    public ExcelReaderBuilder<T> lenientValidation() {
        configBuilder.strictValidation(false);
        configBuilder.failOnFirstError(false);
        return this;
    }

    /**
     * Set custom date format
     *
     * @param format Date format (e.g., "yyyy-MM-dd")
     * @return This builder
     */
    public ExcelReaderBuilder<T> dateFormat(String format) {
        configBuilder.dateFormat(format);
        return this;
    }

    /**
     * Set custom date-time format
     *
     * @param format Date-time format (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return This builder
     */
    public ExcelReaderBuilder<T> dateTimeFormat(String format) {
        configBuilder.dateTimeFormat(format);
        return this;
    }

    /**
     * Set starting row (0-based)
     *
     * @param row Starting row index
     * @return This builder
     */
    public ExcelReaderBuilder<T> startRow(int row) {
        configBuilder.startRow(row);
        return this;
    }

    /**
     * Set job ID for tracking
     *
     * @param jobId Job ID
     * @return This builder
     */
    public ExcelReaderBuilder<T> jobId(String jobId) {
        configBuilder.jobId(jobId);
        return this;
    }

    // ========== Terminal Operations ==========

    /**
     * Read all records into memory
     *
     * Best for small files (< 100K records).
     *
     * @param inputStream Excel file input stream
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public List<T> read(InputStream inputStream) throws ExcelProcessException {
        ExcelConfig config = configBuilder.build();
        return readingService.readAll(inputStream, beanClass);
    }

    /**
     * Read and process in batches (streaming)
     *
     * Best for large files (100K+ records).
     * Memory efficient - only one batch in memory at a time.
     *
     * @param inputStream Excel file input stream
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public TrueStreamingSAXProcessor.ProcessingResult readStream(
        InputStream inputStream,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        ExcelConfig config = configBuilder.build();
        return readingService.readWithConfig(inputStream, beanClass, config, batchProcessor);
    }

    /**
     * Read all records (alias for read)
     *
     * @param inputStream Excel file input stream
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public List<T> readAll(InputStream inputStream) throws ExcelProcessException {
        return read(inputStream);
    }

    /**
     * Read with custom configuration
     *
     * For advanced use cases where you need direct access to ExcelConfig.
     *
     * @param inputStream Excel file input stream
     * @param config Custom Excel configuration
     * @param batchProcessor Batch processor
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public TrueStreamingSAXProcessor.ProcessingResult readWithCustomConfig(
        InputStream inputStream,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        return readingService.readWithConfig(inputStream, beanClass, config, batchProcessor);
    }

    /**
     * Build ExcelConfig without executing read
     *
     * Useful for inspection or passing to other methods.
     *
     * @return Built ExcelConfig
     */
    public ExcelConfig buildConfig() {
        return configBuilder.build();
    }
}
