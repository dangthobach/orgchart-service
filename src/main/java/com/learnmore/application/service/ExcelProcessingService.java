package com.learnmore.application.service;

import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.utils.adapter.ExcelProcessorAdapter;
import com.learnmore.application.utils.adapter.ExcelProcessorAdapter.ExcelProcessingResponse;
import com.learnmore.application.utils.adapter.ExcelProcessorAdapter.ProcessingContext;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.reactive.ReactiveExcelUtil;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Unified Excel Processing Service - Single entry point for all Excel operations
 *
 * This service provides three processing modes:
 * 1. **Synchronous (Traditional)**: Best for small files (< 100K records) or simple use cases
 * 2. **Reactive (WebFlux)**: Best for non-blocking I/O and streaming large datasets
 * 3. **Hybrid (Adaptive)**: Automatically chooses best approach based on file size and context
 *
 * ARCHITECTURE:
 * - All methods delegate to ExcelFacade for consistent behavior
 * - ExcelFacade uses Strategy Pattern for optimal performance
 * - ZERO performance impact: Uses the same optimized implementations
 *
 * MIGRATION STATUS:
 * - ✅ Fully migrated from ExcelUtil to ExcelFacade
 * - ✅ All methods now use dependency injection
 * - ✅ Better testability (can mock dependencies)
 * - ✅ Consistent with Hexagonal Architecture principles
 *
 * @see ExcelFacade
 * @see ExcelProcessorAdapter
 * @see ReactiveExcelUtil
 */
@Service
@RequiredArgsConstructor
public class ExcelProcessingService {

    private final ExcelProcessorAdapter adapter;
    private final ExcelFacade excelFacade;
    private final ReactiveExcelUtil reactiveExcelUtil;

    // ========== SYNCHRONOUS PROCESSING ==========

    /**
     * Process Excel file synchronously and return all results
     *
     * Recommended for:
     * - Small files (< 100K records)
     * - Simple use cases where all data fits in memory
     * - Traditional MVC controllers
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param <T> Type of objects to read
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public <T> List<T> processExcelTraditional(InputStream inputStream, Class<T> beanClass)
            throws ExcelProcessException {
        return excelFacade.readExcel(inputStream, beanClass);
    }

    /**
     * Process Excel file synchronously with custom configuration
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param <T> Type of objects to read
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public <T> List<T> processExcelTraditional(InputStream inputStream,
                                               Class<T> beanClass,
                                               ExcelConfig config)
            throws ExcelProcessException {
        // Note: ExcelFacade.readExcel() doesn't accept config yet
        // For now, use batch processing with config
        List<T> results = new java.util.ArrayList<>();
        excelFacade.readExcelWithConfig(inputStream, beanClass, config, results::addAll);
        return results;
    }

    /**
     * Process Excel file with batch processing and custom processor
     *
     * Recommended for:
     * - Large files (100K - 2M records)
     * - Database batch inserts
     * - Memory-efficient processing
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param batchProcessor Consumer that processes each batch
     * @param <T> Type of objects to read
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public <T> TrueStreamingSAXProcessor.ProcessingResult processExcelBatch(
            InputStream inputStream,
            Class<T> beanClass,
            Consumer<List<T>> batchProcessor)
            throws ExcelProcessException {
        return excelFacade.readExcel(inputStream, beanClass, batchProcessor);
    }

    /**
     * Process Excel file with batch processing and custom configuration
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param batchProcessor Consumer that processes each batch
     * @param <T> Type of objects to read
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public <T> TrueStreamingSAXProcessor.ProcessingResult processExcelBatch(
            InputStream inputStream,
            Class<T> beanClass,
            ExcelConfig config,
            Consumer<List<T>> batchProcessor)
            throws ExcelProcessException {
        return excelFacade.readExcelWithConfig(inputStream, beanClass, config, batchProcessor);
    }

    // ========== REACTIVE PROCESSING ==========

    /**
     * Process Excel file reactively (non-blocking)
     *
     * Recommended for:
     * - WebFlux applications
     * - Non-blocking I/O requirements
     * - Event-driven architectures
     * - Real-time data streaming
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param <T> Type of objects to read
     * @return Flux of records (reactive stream)
     */
    public <T> Flux<T> processExcelReactive(InputStream inputStream, Class<T> beanClass) {
        return reactiveExcelUtil.processExcelReactive(inputStream, beanClass);
    }

    /**
     * Process Excel file reactively with custom configuration
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param <T> Type of objects to read
     * @return Flux of records (reactive stream)
     */
    public <T> Flux<T> processExcelReactive(InputStream inputStream,
                                            Class<T> beanClass,
                                            ExcelConfig config) {
        return reactiveExcelUtil.processExcelReactive(inputStream, beanClass, config);
    }

    // ========== HYBRID PROCESSING ==========

    /**
     * Process Excel file with hybrid approach (adaptive strategy)
     *
     * Automatically selects best processing mode based on:
     * - File size estimation
     * - Available system resources
     * - Processing context (batch, streaming, reactive)
     *
     * Recommended for:
     * - Unknown file sizes
     * - Mixed workloads
     * - Production environments requiring optimal performance
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Custom Excel configuration
     * @param context Processing context (batch, streaming, etc.)
     * @param <T> Type of objects to read
     * @return ExcelProcessingResponse with results and metadata
     * @throws ExcelProcessException if processing fails
     */
    public <T> ExcelProcessingResponse<T> processExcelHybrid(InputStream inputStream,
                                                             Class<T> beanClass,
                                                             ExcelConfig config,
                                                             ProcessingContext context)
            throws ExcelProcessException {
        return adapter.process(inputStream, beanClass, config, context);
    }

    // ========== CONVENIENCE METHODS ==========

    /**
     * Process small Excel file (< 50K records) with optimal configuration
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param <T> Type of objects to read
     * @return List of all records
     * @throws ExcelProcessException if reading fails
     */
    public <T> List<T> processSmallFile(InputStream inputStream, Class<T> beanClass)
            throws ExcelProcessException {
        return excelFacade.readSmallFile(inputStream, beanClass);
    }

    /**
     * Process large Excel file (500K - 2M records) with batch processing
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param batchProcessor Consumer that processes each batch
     * @param <T> Type of objects to read
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    public <T> TrueStreamingSAXProcessor.ProcessingResult processLargeFile(
            InputStream inputStream,
            Class<T> beanClass,
            Consumer<List<T>> batchProcessor)
            throws ExcelProcessException {
        return excelFacade.readLargeFile(inputStream, beanClass, batchProcessor);
    }

    /**
     * Write data to Excel file with automatic strategy selection
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param <T> Type of objects to write
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeExcel(String fileName, List<T> data) throws ExcelProcessException {
        excelFacade.writeExcel(fileName, data);
    }

    /**
     * Write data to Excel bytes (in-memory)
     *
     * WARNING: Only use for small files (< 50K records)
     *
     * @param data List of objects to write
     * @param <T> Type of objects to write
     * @return Excel file as byte array
     * @throws ExcelProcessException if writing fails
     */
    public <T> byte[] writeExcelBytes(List<T> data) throws ExcelProcessException {
        return excelFacade.writeExcelToBytes(data);
    }
}
