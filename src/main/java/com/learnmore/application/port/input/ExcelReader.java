package com.learnmore.application.port.input;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Port (Interface) for reading Excel files
 *
 * This interface defines the contract for Excel reading operations.
 * It follows the Hexagonal Architecture pattern where this is the INPUT PORT.
 *
 * Implementations can use different strategies (SAX streaming, parallel processing, etc.)
 * without affecting the client code.
 *
 * @param <T> The type of objects to read from Excel
 */
public interface ExcelReader<T> {

    /**
     * Read Excel file and process in batches using streaming
     *
     * This is the RECOMMENDED method for large files (1M+ records).
     * Uses true streaming with no memory accumulation.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    TrueStreamingSAXProcessor.ProcessingResult read(
        InputStream inputStream,
        Class<T> beanClass,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException;

    /**
     * Read Excel file and return all results in memory
     *
     * WARNING: Only use for small files (< 100K records).
     * For large files, use read() with batch processing.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    List<T> readAll(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException;

    /**
     * Read Excel file with custom configuration
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    TrueStreamingSAXProcessor.ProcessingResult readWithConfig(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException;
}
