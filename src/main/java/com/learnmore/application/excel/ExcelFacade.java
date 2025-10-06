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
import java.util.Map;

/**
 * Unified facade for Excel operations with automatic strategy selection
 *
 * This facade provides a clean, easy-to-use API for reading and writing Excel files.
 * It hides the complexity of the underlying services and provides sensible defaults.
 *
 * RECOMMENDED: Use this facade as the primary entry point for all Excel operations.
 *
 * Features:
 * - Simple API for beginners and advanced users
 * - Automatic strategy selection for optimal performance
 * - Dependency injection friendly (Spring managed)
 * - Easy to test (can mock dependencies)
 * - Strategy Pattern for extensibility
 *
 * Architecture:
 * - ExcelFacade → ExcelReadingService/ExcelWritingService
 * - Services → Strategy Selector → Concrete Strategies
 * - Strategies → TrueStreamingSAXProcessor (read) or ExcelWriteHelper (write)
 *
 * Performance:
 * - Small files (< 50K): XSSF standard format
 * - Medium files (50K - 2M): SXSSF streaming format
 * - Large files (> 2M): CSV format (10x faster)
 * - True streaming: SAX-based processing with zero memory accumulation
 *
 * Example usage:
 * <pre>
 * // Simple reading
 * List<User> users = excelFacade.readExcel(inputStream, User.class);
 *
 * // Batch processing (recommended for large files)
 * excelFacade.readExcel(inputStream, User.class, batch -> {
 *     userRepository.saveAll(batch);
 * });
 *
 * // Simple writing (auto-selects XSSF/SXSSF/CSV)
 * excelFacade.writeExcel("output.xlsx", users);
 *
 * // Fluent API
 * excelFacade.reader(User.class)
 *     .batchSize(10000)
 *     .parallel()
 *     .read(inputStream);
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelFacade {

    private final ExcelReadingService readingService;
    private final ExcelWritingService writingService;

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
        return new ExcelReaderBuilder<>(readingService, beanClass);
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
        return new ExcelWriterBuilder<>(writingService, data);
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

        return readingService.readAll(inputStream, beanClass);
    }

    /**
     * Read Excel file and return all results with custom configuration
     *
     * Use when you need to control behavior via ExcelConfig but still want a simple List<T> result.
     * WARNING: Only for small files (< 100K records) as this accumulates in memory.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param <T> Type of objects to read
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public <T> List<T> readExcel(InputStream inputStream, Class<T> beanClass, ExcelConfig config) throws ExcelProcessException {
        log.debug("Reading Excel file with custom config for class: {}", beanClass.getSimpleName());

        return readingService.readAll(inputStream, beanClass, config);
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

        return readingService.read(inputStream, beanClass, batchProcessor);
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

        return readingService.readWithConfig(inputStream, beanClass, config, batchProcessor);
    }

    /**
     * Read multi-sheet Excel file using true streaming and return per-sheet results
     *
     * @param inputStream Excel file input stream
     * @param sheetClassMap Map of sheet name to target bean class
     * @param sheetProcessors Map of sheet name to batch processor
     * @param config Custom Excel configuration
     * @return Map of sheet name to ProcessingResult
     * @throws ExcelProcessException if reading fails
     */
    public Map<String, TrueStreamingSAXProcessor.ProcessingResult> readMultiSheet(
        InputStream inputStream,
        Map<String, Class<?>> sheetClassMap,
        Map<String, Consumer<List<?>>> sheetProcessors,
        ExcelConfig config
    ) throws ExcelProcessException {
        try {
            com.learnmore.application.utils.sax.TrueStreamingMultiSheetProcessor processor =
                new com.learnmore.application.utils.sax.TrueStreamingMultiSheetProcessor(sheetClassMap, sheetProcessors, config);
            return processor.processTrueStreaming(inputStream);
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to read multi-sheet Excel", e);
        }
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

        writingService.write(fileName, data);
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

        return writingService.writeToBytes(data);
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

        writingService.writeWithConfig(fileName, data, config);
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

        return readingService.readSmallFile(inputStream, beanClass);
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

        return readingService.readLargeFile(inputStream, beanClass, batchProcessor);
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

        writingService.writeSmallFile(fileName, data);
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

        writingService.writeLargeFile(fileName, data);
    }
}
