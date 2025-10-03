package com.learnmore.application.excel.service;

import com.learnmore.application.excel.strategy.ReadStrategy;
import com.learnmore.application.excel.strategy.selector.ReadStrategySelector;
import com.learnmore.application.port.input.ExcelReader;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.config.ExcelConfigFactory;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service for reading Excel files with automatic strategy selection
 *
 * This service implements the ExcelReader port and provides a clean API
 * for reading Excel files. It follows Hexagonal Architecture principles
 * with Strategy Pattern for automatic optimization.
 *
 * Strategy Selection (Phase 2):
 * - Uses ReadStrategySelector to automatically choose the best strategy
 * - StreamingReadStrategy: Default for all file sizes
 * - ParallelReadStrategy: When parallelProcessing enabled
 *
 * IMPORTANT: All strategies delegate to the existing ExcelUtil methods
 * to preserve the optimized performance for millions of records.
 * ZERO performance impact from refactoring.
 *
 * @param <T> The type of objects to read from Excel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelReadingService<T> implements ExcelReader<T> {

    // Strategy selector for automatic strategy selection (Phase 2)
    private final ReadStrategySelector readStrategySelector;

    // Default configuration optimized for streaming
    private static final ExcelConfig DEFAULT_CONFIG = ExcelConfigFactory.createLargeFileConfig();

    /**
     * Read Excel file and process in batches using automatic strategy selection
     *
     * Phase 2: Now uses ReadStrategySelector to automatically choose the best strategy:
     * - StreamingReadStrategy: Default for all file sizes
     * - ParallelReadStrategy: When config.parallelProcessing enabled
     *
     * All strategies delegate to ExcelUtil - ZERO performance impact.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    @Override
    public TrueStreamingSAXProcessor.ProcessingResult read(
        InputStream inputStream,
        Class<T> beanClass,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading Excel file for class: {}", beanClass.getSimpleName());

        // Phase 2: Use strategy selector for automatic optimization
        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(DEFAULT_CONFIG);
        return strategy.execute(inputStream, beanClass, DEFAULT_CONFIG, batchProcessor);
    }

    /**
     * Read Excel file and return all results in memory
     *
     * WARNING: Only use for small files (< 100K records).
     * Delegates to ExcelUtil.processExcel() - ZERO performance impact.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    @Override
    public List<T> readAll(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        log.debug("Reading all records from Excel for class: {}", beanClass.getSimpleName());

        // Collect all results in memory
        List<T> results = new ArrayList<>();
        Consumer<List<T>> collector = results::addAll;

        // Delegate to existing optimized implementation
        ExcelUtil.processExcelTrueStreaming(inputStream, beanClass, DEFAULT_CONFIG, collector);

        log.info("Read {} records from Excel", results.size());
        return results;
    }

    /**
     * Read Excel file with custom configuration and automatic strategy selection
     *
     * Phase 2: Uses ReadStrategySelector to choose optimal strategy based on config.
     * All strategies delegate to ExcelUtil - ZERO performance impact.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    @Override
    public TrueStreamingSAXProcessor.ProcessingResult readWithConfig(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading Excel file with custom config for class: {}", beanClass.getSimpleName());

        // Phase 2: Use strategy selector for automatic optimization based on config
        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(config);
        return strategy.execute(inputStream, beanClass, config, batchProcessor);
    }

    /**
     * Read Excel file with default small file configuration
     *
     * Optimized for files < 50K records.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public List<T> readSmallFile(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        log.debug("Reading small Excel file for class: {}", beanClass.getSimpleName());

        ExcelConfig smallFileConfig = ExcelConfigFactory.createSmallFileConfig();
        List<T> results = new ArrayList<>();

        // Delegate to existing optimized implementation
        ExcelUtil.processExcelTrueStreaming(inputStream, beanClass, smallFileConfig, results::addAll);

        return results;
    }

    /**
     * Read Excel file with large file configuration
     *
     * Optimized for files 500K - 2M records.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public TrueStreamingSAXProcessor.ProcessingResult readLargeFile(
        InputStream inputStream,
        Class<T> beanClass,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading large Excel file for class: {}", beanClass.getSimpleName());

        ExcelConfig largeFileConfig = ExcelConfigFactory.createLargeFileConfig();

        // Delegate to existing optimized implementation
        return ExcelUtil.processExcelTrueStreaming(inputStream, beanClass, largeFileConfig, batchProcessor);
    }
}
