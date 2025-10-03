package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.ReadStrategy;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Default streaming read strategy using SAX processing
 *
 * This is the default, always-available strategy for reading Excel files.
 * It uses the optimized TrueStreamingSAXProcessor which can handle files
 * of any size from small to very large (1M+ records).
 *
 * Performance characteristics:
 * - Memory: O(batch_size) - only one batch in memory at a time
 * - Speed: ~50,000-100,000 records/sec (single-threaded)
 * - File size: Unlimited (tested up to 2M records)
 *
 * This strategy ALWAYS delegates to the existing optimized ExcelUtil.processExcelTrueStreaming()
 * to ensure ZERO performance impact from the refactoring.
 *
 * When to use:
 * - Default choice for all file sizes
 * - Always safe and reliable
 * - Best for files 100K+ records
 * - Works with any configuration
 *
 * Strategy selection:
 * - Priority: 0 (default baseline)
 * - Supports: ALL configurations (fallback strategy)
 *
 * @param <T> The type of objects to read from Excel
 */
@Slf4j
@Component
public class StreamingReadStrategy<T> implements ReadStrategy<T> {

    /**
     * Execute streaming read using SAX processing
     *
     * This method delegates directly to ExcelUtil.processExcelTrueStreaming()
     * which provides the optimized implementation with:
     * - True streaming SAX parsing (no DOM)
     * - MethodHandle optimization (5x faster than reflection)
     * - Batch processing with configurable batch size
     * - Memory monitoring and progress tracking
     * - Zero memory accumulation
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Excel configuration (batch size, progress tracking, etc.)
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics (records, time, speed)
     * @throws ExcelProcessException if reading fails
     */
    @Override
    public TrueStreamingSAXProcessor.ProcessingResult execute(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Executing StreamingReadStrategy for class: {}", beanClass.getSimpleName());

        // Delegate to existing optimized implementation - ZERO performance impact
        // This preserves all optimizations:
        // - SAX streaming parsing
        // - MethodHandle optimization
        // - Batch processing
        // - Memory monitoring
        // - Progress tracking
        TrueStreamingSAXProcessor.ProcessingResult result = ExcelUtil.processExcelTrueStreaming(
            inputStream,
            beanClass,
            config,
            batchProcessor
        );

        log.info("StreamingReadStrategy completed: {} records in {} ms ({} rec/sec)",
                result.getProcessedRecords(),
                result.getProcessingTimeMs(),
                result.getRecordsPerSecond());

        return result;
    }

    /**
     * Check if this strategy supports the given configuration
     *
     * StreamingReadStrategy is the universal fallback strategy that
     * ALWAYS supports any configuration. It's designed to handle:
     * - Small files (< 50K records)
     * - Medium files (50K - 500K records)
     * - Large files (500K - 2M records)
     * - Any batch size
     * - Any config settings
     *
     * @param config Excel configuration to check
     * @return Always returns true (universal fallback)
     */
    @Override
    public boolean supports(ExcelConfig config) {
        // Always return true - this is the universal fallback strategy
        // It works with any configuration and any file size
        return true;
    }

    /**
     * Get strategy name for logging and debugging
     *
     * @return Strategy name
     */
    @Override
    public String getName() {
        return "StreamingReadStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 0 means this is the baseline/default strategy.
     * Other strategies with higher priority will be selected first
     * if they support the configuration.
     *
     * Priority ordering:
     * - 0: StreamingReadStrategy (default baseline)
     * - 10: ParallelReadStrategy (if parallel processing enabled)
     * - 20: CustomStrategy (future extensions)
     *
     * @return Priority level (0 = baseline)
     */
    @Override
    public int getPriority() {
        return 0; // Baseline priority - always available fallback
    }
}
