package com.learnmore.application.excel.strategy;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;

import java.util.List;

/**
 * Strategy interface for writing Excel files
 *
 * Implementations provide different approaches to writing Excel:
 * - XSSF for small files (< 1M cells)
 * - SXSSF streaming for medium files (1M - 5M cells)
 * - CSV for very large files (> 5M cells)
 *
 * This follows the Strategy Pattern, allowing runtime selection
 * of the optimal writing approach based on data size.
 */
public interface WriteStrategy<T> {

    /**
     * Execute the write strategy
     *
     * NOTE: Implementation should delegate to existing optimized code in ExcelUtil.
     * Do NOT reimplement the write logic - just call the existing methods.
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param config Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    void execute(
        String fileName,
        List<T> data,
        ExcelConfig config
    ) throws ExcelProcessException;

    /**
     * Check if this strategy supports the given data size
     *
     * Used by the strategy selector to choose the optimal strategy.
     *
     * @param dataSize Number of records
     * @param cellCount Total number of cells (rows × columns)
     * @param config Excel configuration
     * @return true if this strategy can handle the data size
     */
    boolean supports(int dataSize, long cellCount, ExcelConfig config);

    /**
     * Get the name of this strategy for logging
     *
     * @return Strategy name (e.g., "XSSF_STANDARD", "SXSSF_STREAMING", "CSV")
     */
    String getName();

    /**
     * Get the priority of this strategy (higher = more preferred)
     *
     * Used when multiple strategies support the same data size.
     * The strategy selector will choose the one with highest priority.
     *
     * @return Priority value (default: 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Estimate the processing time for this strategy
     *
     * Used for logging and performance recommendations.
     *
     * @param dataSize Number of records
     * @return Estimated time in milliseconds
     */
    default long estimateProcessingTime(int dataSize) {
        // Default estimation: 1000 records per second
        return dataSize;
    }
}
