package com.learnmore.application.excel;

import com.learnmore.application.excel.builder.ExcelReaderBuilder;
import com.learnmore.application.excel.builder.ExcelWriterBuilder;
import com.learnmore.application.excel.service.ExcelReadingService;
import com.learnmore.application.excel.service.ExcelWritingService;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simplified facade for Excel operations
 *
 * This facade provides a clean, easy-to-use API for reading and writing Excel files.
 * It hides the complexity of the underlying services and provides sensible defaults.
 *
 * RECOMMENDED: Use this facade for most Excel operations instead of ExcelUtil directly.
 *
 * Features:
 * - Simple API for beginners
 * - Automatic strategy selection for best performance
 * - Dependency injection friendly
 * - Easy to test (can mock dependencies)
 *
 * IMPORTANT: This facade delegates to ExcelReadingService and ExcelWritingService,
 * which in turn delegate to the existing optimized ExcelUtil methods.
 * ZERO performance impact - same speed as calling ExcelUtil directly.
 *
 * Example usage:
 * <pre>
 * // Simple reading
 * List<User> users = excelFacade.readExcel(inputStream, User.class);
 *
 * // Batch processing
 * excelFacade.readExcel(inputStream, User.class, batch -> {
 *     userRepository.saveAll(batch);
 * });
 *
 * // Simple writing
 * excelFacade.writeExcel("output.xlsx", users);
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelFacade {

    private final ExcelReadingService<Object> readingService;
    private final ExcelWritingService<Object> writingService;

    // ========== BUILDER API (Phase 2 - Priority 2) ==========

    /**
     * Create fluent reader builder for type-safe configuration
     *
     * Provides clean, readable API with method chaining.
     *
     * Example:
     * <pre>
     * List<User> users = excelFacade.reader(User.class)
     *     .batchSize(10000)
     *     .parallel()
     *     .withProgressTracking()
     *     .read(inputStream);
     * </pre>
     *
     * @param beanClass Class type to map Excel rows to
     * @param <T> Type of objects to read
     * @return ExcelReaderBuilder for fluent configuration
     */
    public <T> ExcelReaderBuilder<T> reader(Class<T> beanClass) {
        @SuppressWarnings("unchecked")
        ExcelReadingService<T> typedService = (ExcelReadingService<T>) readingService;
        return new ExcelReaderBuilder<>(typedService, beanClass);
    }

    /**
     * Create fluent writer builder for type-safe configuration
     *
     * Provides clean, readable API with method chaining.
     *
     * Example:
     * <pre>
     * excelFacade.writer(users)
     *     .withStyling()
     *     .disableAutoSizing()
     *     .write("report.xlsx");
     * </pre>
     *
     * @param data List of objects to write
     * @param <T> Type of objects to write
     * @return ExcelWriterBuilder for fluent configuration
     */
    public <T> ExcelWriterBuilder<T> writer(List<T> data) {
        @SuppressWarnings("unchecked")
        ExcelWritingService<T> typedService = (ExcelWritingService<T>) writingService;
        return new ExcelWriterBuilder<>(typedService, data);
    }

    // ========== READING API ==========

    /**
     * Read Excel file and return all results
     *
     * Simple method for reading small Excel files (< 100K records).
     * For large files, use readExcel() with batch processing.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param <T> Type of objects to read
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public <T> List<T> readExcel(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        log.debug("Reading Excel file for class: {}", beanClass.getSimpleName());

        @SuppressWarnings("unchecked")
        ExcelReadingService<T> typedService = (ExcelReadingService<T>) readingService;

        return typedService.readAll(inputStream, beanClass);
    }

    /**
     * Read Excel file and process in batches
     *
     * RECOMMENDED method for large files (100K+ records).
     * Uses true streaming with no memory accumulation.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param batchProcessor Consumer that processes each batch
     * @param <T> Type of objects to read
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public <T> TrueStreamingSAXProcessor.ProcessingResult readExcel(
        InputStream inputStream,
        Class<T> beanClass,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading Excel file with batch processing for class: {}", beanClass.getSimpleName());

        @SuppressWarnings("unchecked")
        ExcelReadingService<T> typedService = (ExcelReadingService<T>) readingService;

        return typedService.read(inputStream, beanClass, batchProcessor);
    }

    /**
     * Read Excel file with custom configuration
     *
     * Advanced method for fine-tuning performance and behavior.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param batchProcessor Consumer that processes each batch
     * @param <T> Type of objects to read
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public <T> TrueStreamingSAXProcessor.ProcessingResult readExcelWithConfig(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading Excel file with custom config for class: {}", beanClass.getSimpleName());

        @SuppressWarnings("unchecked")
        ExcelReadingService<T> typedService = (ExcelReadingService<T>) readingService;

        return typedService.readWithConfig(inputStream, beanClass, config, batchProcessor);
    }

    // ========== WRITING API ==========

    /**
     * Write data to Excel file
     *
     * Simple method for writing Excel files.
     * Automatically selects the best strategy based on data size.
     *
     * @param fileName Output file name (e.g., "output.xlsx")
     * @param data List of objects to write
     * @param <T> Type of objects to write
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeExcel(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing {} records to Excel file: {}", data.size(), fileName);

        @SuppressWarnings("unchecked")
        ExcelWritingService<T> typedService = (ExcelWritingService<T>) writingService;

        typedService.write(fileName, data);
    }

    /**
     * Write data to Excel bytes (in-memory)
     *
     * WARNING: Only use for small files (< 50K records).
     *
     * @param data List of objects to write
     * @param <T> Type of objects to write
     * @return Excel file as byte array
     * @throws ExcelProcessException if writing fails
     */
    public <T> byte[] writeExcelToBytes(List<T> data) throws ExcelProcessException {
        log.debug("Writing {} records to Excel bytes", data.size());

        @SuppressWarnings("unchecked")
        ExcelWritingService<T> typedService = (ExcelWritingService<T>) writingService;

        return typedService.writeToBytes(data);
    }

    /**
     * Write data to Excel file with custom configuration
     *
     * Advanced method for fine-tuning performance and behavior.
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param config Custom Excel configuration
     * @param <T> Type of objects to write
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeExcelWithConfig(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException {
        log.debug("Writing {} records to Excel file with custom config: {}", data.size(), fileName);

        @SuppressWarnings("unchecked")
        ExcelWritingService<T> typedService = (ExcelWritingService<T>) writingService;

        typedService.writeWithConfig(fileName, data, config);
    }

    // ========== CONVENIENCE METHODS ==========

    /**
     * Read small Excel file (< 50K records)
     *
     * Optimized configuration for small files.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param <T> Type of objects to read
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public <T> List<T> readSmallFile(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        log.debug("Reading small Excel file for class: {}", beanClass.getSimpleName());

        @SuppressWarnings("unchecked")
        ExcelReadingService<T> typedService = (ExcelReadingService<T>) readingService;

        return typedService.readSmallFile(inputStream, beanClass);
    }

    /**
     * Read large Excel file (500K - 2M records)
     *
     * Optimized configuration for large files with batch processing.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param batchProcessor Consumer that processes each batch
     * @param <T> Type of objects to read
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public <T> TrueStreamingSAXProcessor.ProcessingResult readLargeFile(
        InputStream inputStream,
        Class<T> beanClass,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading large Excel file for class: {}", beanClass.getSimpleName());

        @SuppressWarnings("unchecked")
        ExcelReadingService<T> typedService = (ExcelReadingService<T>) readingService;

        return typedService.readLargeFile(inputStream, beanClass, batchProcessor);
    }

    /**
     * Write small Excel file (< 50K records)
     *
     * Uses XSSF (standard) workbook for best compatibility.
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param <T> Type of objects to write
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeSmallFile(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing small Excel file: {} records to {}", data.size(), fileName);

        @SuppressWarnings("unchecked")
        ExcelWritingService<T> typedService = (ExcelWritingService<T>) writingService;

        typedService.writeSmallFile(fileName, data);
    }

    /**
     * Write large Excel file (500K - 2M records)
     *
     * Uses SXSSF streaming workbook for memory efficiency.
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param <T> Type of objects to write
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeLargeFile(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing large Excel file: {} records to {}", data.size(), fileName);

        @SuppressWarnings("unchecked")
        ExcelWritingService<T> typedService = (ExcelWritingService<T>) writingService;

        typedService.writeLargeFile(fileName, data);
    }
}
