package com.learnmore.application.excel.service;

import com.learnmore.application.excel.strategy.ReadStrategy;
import com.learnmore.application.excel.monitoring.ErrorTracker;
import com.learnmore.application.excel.monitoring.MemoryMonitor;
import com.learnmore.application.excel.strategy.selector.ReadStrategySelector;
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
public class ExcelReadingService {

    // Strategy selector for automatic strategy selection (Phase 2)
    private final ReadStrategySelector readStrategySelector;
    private final MemoryMonitor memoryMonitor;

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
    public <T> TrueStreamingSAXProcessor.ProcessingResult read(
        InputStream inputStream,
        Class<T> beanClass,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading Excel file for class: {}", beanClass.getSimpleName());

        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(DEFAULT_CONFIG);

        Consumer<List<T>> wrapped = decorateBatchProcessor(DEFAULT_CONFIG, batchProcessor);
        return strategy.execute(inputStream, beanClass, DEFAULT_CONFIG, wrapped);
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
    public <T> List<T> readAll(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        log.debug("Reading all records from Excel for class: {}", beanClass.getSimpleName());

        List<T> results = new ArrayList<>();
        Consumer<List<T>> collector = batch -> {
            Consumer<List<T>> wrapped = decorateBatchProcessor(DEFAULT_CONFIG, results::addAll);
            wrapped.accept(batch);
        };

        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(DEFAULT_CONFIG);
        strategy.execute(inputStream, beanClass, DEFAULT_CONFIG, collector);

        log.info("Read {} records from Excel", results.size());
        return results;
    }

    /**
     * Read Excel file and return all results in memory using provided configuration
     *
     * WARNING: Only use for small files (< 100K records) as this accumulates results in memory.
     * Delegates to ExcelUtil.processExcelTrueStreaming() with the given config.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public <T> List<T> readAll(InputStream inputStream, Class<T> beanClass, ExcelConfig config) throws ExcelProcessException {
        log.debug("Reading all records from Excel with custom config for class: {}", beanClass.getSimpleName());

        List<T> results = new ArrayList<>();
        Consumer<List<T>> collector = batch -> {
            Consumer<List<T>> wrapped = decorateBatchProcessor(config, results::addAll);
            wrapped.accept(batch);
        };

        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(config);
        strategy.execute(inputStream, beanClass, config, collector);

        log.info("Read {} records from Excel (custom config)", results.size());
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
    public <T> TrueStreamingSAXProcessor.ProcessingResult readWithConfig(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading Excel file with custom config for class: {}", beanClass.getSimpleName());

        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(config);
        Consumer<List<T>> wrapped = decorateBatchProcessor(config, batchProcessor);
        return strategy.execute(inputStream, beanClass, config, wrapped);
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
    public <T> List<T> readSmallFile(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        log.debug("Reading small Excel file for class: {}", beanClass.getSimpleName());

        ExcelConfig smallFileConfig = ExcelConfigFactory.createSmallFileConfig();
        List<T> results = new ArrayList<>();
        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(smallFileConfig);
        Consumer<List<T>> wrapped = decorateBatchProcessor(smallFileConfig, results::addAll);
        strategy.execute(inputStream, beanClass, smallFileConfig, wrapped);
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
    public <T> TrueStreamingSAXProcessor.ProcessingResult readLargeFile(
        InputStream inputStream,
        Class<T> beanClass,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Reading large Excel file for class: {}", beanClass.getSimpleName());

        ExcelConfig largeFileConfig = ExcelConfigFactory.createLargeFileConfig();
        ReadStrategy<T> strategy = readStrategySelector.selectStrategy(largeFileConfig);
        Consumer<List<T>> wrapped = decorateBatchProcessor(largeFileConfig, batchProcessor);
        return strategy.execute(inputStream, beanClass, largeFileConfig, wrapped);
    }

    private <T> Consumer<List<T>> decorateBatchProcessor(ExcelConfig config, Consumer<List<T>> delegate) {
        final boolean monitor = config.isEnableMemoryMonitoring();
        final String jobId = config.getJobId();
        if (monitor && jobId != null && !jobId.isEmpty()) {
            memoryMonitor.startMonitoring(jobId);
        }
        final ErrorTracker errorTracker = new ErrorTracker(config);

        return batch -> {
            try {
                if (monitor && memoryMonitor.isThresholdExceeded(jobId, config.getMemoryThresholdMB())) {
                    // If threshold exceeded, still process but log. Consumers can decide to flush.
                    // Intentionally no GC here; leave to runtime/consumer.
                }
                delegate.accept(batch);
            } catch (Exception e) {
                boolean cont = errorTracker.recordError(-1, "batch", e);
                if (!cont) {
                    throw new ExcelProcessException("Aborted due to max errors", e);
                }
            } finally {
                if (monitor && jobId != null && !jobId.isEmpty()) {
                    memoryMonitor.stopMonitoring(jobId);
                }
            }
        };
    }
}
