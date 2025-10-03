package com.learnmore.application.excel.strategy;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;

import java.io.InputStream;
import java.util.function.Consumer;
import java.util.List;

/**
 * Strategy interface for reading Excel files
 *
 * Implementations provide different approaches to reading Excel:
 * - Streaming (SAX-based) for large files
 * - Parallel processing for multi-core systems
 * - Standard reading for small files
 *
 * This follows the Strategy Pattern, allowing runtime selection
 * of the optimal reading approach based on file size and configuration.
 */
public interface ReadStrategy<T> {

    /**
     * Execute the read strategy
     *
     * NOTE: Implementation should delegate to existing optimized code in ExcelUtil.
     * Do NOT reimplement the read logic - just call the existing methods.
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Excel configuration
     * @param batchProcessor Consumer that processes each batch
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    TrueStreamingSAXProcessor.ProcessingResult execute(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException;

    /**
     * Check if this strategy supports the given configuration
     *
     * Used by the strategy selector to choose the optimal strategy.
     *
     * @param config Excel configuration
     * @return true if this strategy can handle the configuration
     */
    boolean supports(ExcelConfig config);

    /**
     * Get the name of this strategy for logging
     *
     * @return Strategy name (e.g., "STREAMING_SAX", "PARALLEL_BATCH")
     */
    String getName();

    /**
     * Get the priority of this strategy (higher = more preferred)
     *
     * Used when multiple strategies support the same configuration.
     * The strategy selector will choose the one with highest priority.
     *
     * @return Priority value (default: 0)
     */
    default int getPriority() {
        return 0;
    }
}
