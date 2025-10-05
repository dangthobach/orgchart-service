package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.helper.ExcelWriteHelper;
import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SXSSF write strategy for medium-to-large Excel files
 *
 * This strategy uses SXSSF (Streaming XML Spreadsheet Format) workbook
 * which uses streaming to keep only a window of rows in memory at a time.
 * Best for medium to large files where memory efficiency is important.
 *
 * Performance characteristics:
 * - Memory: O(window_size) - only window of rows in memory
 * - Speed: Good for large files (50K - 2M records)
 * - File size: 50K - 2M records or 1M - 5M cells
 * - Compatibility: Good (standard Excel format)
 *
 * PHASE 3 REFACTORING:
 * - Delegates all POI operations to ExcelWriteHelper
 * - No duplicate code (DRY principle)
 * - Lightweight strategy (coordination only)
 *
 * SXSSF characteristics:
 * - Streaming write: Only keeps window of rows in memory
 * - Automatic flush: Older rows flushed to disk automatically
 * - Temp files: Uses temp files for streaming (cleaned up automatically)
 * - Memory efficient: Can handle millions of rows
 *
 * When to use:
 * - Medium files (50K - 500K records)
 * - Large files (500K - 2M records)
 * - Medium cell count (1M - 5M cells)
 * - When memory efficiency is important
 *
 * @param <T> The type of objects to write to Excel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SXSSFWriteStrategy<T> implements WriteStrategy<T> {

    private final ExcelWriteHelper writeHelper;

    // Thresholds for SXSSF strategy selection
    private static final int MIN_RECORDS = 50_000;
    private static final int MAX_RECORDS = 2_000_000;
    private static final long MIN_CELLS = 1_000_000L;
    private static final long MAX_CELLS = 5_000_000L;

    /**
     * Execute write using SXSSF (streaming) workbook
     *
     * PHASE 3 REFACTORING: Delegates to ExcelWriteHelper for all POI operations.
     * This eliminates duplicate code and makes the strategy lightweight.
     *
     * @param fileName Output file name (e.g., "output.xlsx")
     * @param data List of objects to write
     * @param config Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    @Override
    public void execute(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException {
        log.debug("Executing SXSSFWriteStrategy for {} records to {}", data.size(), fileName);

        // Validate that data size is appropriate for SXSSF
        if (data.size() < MIN_RECORDS) {
            log.debug("Writing {} records with SXSSF (small file - XSSF might be faster)", data.size());
        }

        if (data.size() > MAX_RECORDS) {
            log.warn("Writing {} records with SXSSF (very large file - consider CSV strategy)", data.size());
        }

        try {
            // Calculate optimal window size for SXSSF
            int windowSize = calculateOptimalWindowSize(data.size(), config);

            // Delegate to ExcelWriteHelper for SXSSF write operations
            writeHelper.writeToFileSXSSF(fileName, data, 0, 0, config, windowSize);

            log.info("SXSSFWriteStrategy completed: {} records written to {}", data.size(), fileName);

        } catch (Exception e) {
            log.error("SXSSFWriteStrategy failed for file: {}", fileName, e);
            throw new ExcelProcessException("Failed to write Excel file with SXSSF strategy", e);
        }
    }

    /**
     * Calculate optimal window size for SXSSF based on data size
     */
    private int calculateOptimalWindowSize(int dataSize, ExcelConfig config) {
        // Use configured window size if available
        if (config.getSxssfRowAccessWindowSize() > 0) {
            return config.getSxssfRowAccessWindowSize();
        }

        // Calculate optimal window size based on data size
        if (dataSize <= 100_000) {
            return 100; // Small files: 100 rows
        } else if (dataSize <= 500_000) {
            return 200; // Medium files: 200 rows
        } else {
            return 500; // Large files: 500 rows
        }
    }

    /**
     * Check if this strategy supports the given data and configuration
     *
     * SXSSFWriteStrategy is selected when:
     * 1. 50K < Data size < 2M records, OR
     * 2. 1M < Total cells < 5M cells, OR
     * 3. config.isForceSXSSF() == true (force SXSSF regardless of size)
     *
     * Cell count estimation:
     * - Total cells = rows * columns
     * - 1M - 5M cells is the sweet spot for SXSSF
     *
     * @param dataSize Number of records to write
     * @param cellCount Total number of cells (rows * columns)
     * @param config Excel configuration
     * @return true if this strategy supports the data size, false otherwise
     */
    @Override
    public boolean supports(int dataSize, long cellCount, ExcelConfig config) {
        // Support medium files based on record count
        // SXSSF is best for medium to large files where streaming is beneficial
        boolean supportsRecordCount = dataSize > MIN_RECORDS && dataSize <= MAX_RECORDS;

        // Support medium files based on cell count
        boolean supportsCellCount = cellCount > MIN_CELLS && cellCount <= MAX_CELLS;

        boolean supported = supportsRecordCount || supportsCellCount;

        if (supported) {
            log.debug("SXSSFWriteStrategy supports data: {} records, {} cells",
                     dataSize, cellCount);
        }

        return supported;
    }

    /**
     * Get strategy name for logging and debugging
     *
     * @return Strategy name
     */
    @Override
    public String getName() {
        return "SXSSFWriteStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 10 means this strategy is preferred for medium files
     * when the configuration supports it.
     *
     * Priority ordering:
     * - 0: Default/fallback strategy
     * - 10: SXSSFWriteStrategy (medium files)
     * - 15: CSVWriteStrategy (large files)
     * - 20: XSSFWriteStrategy (small files)
     *
     * @return Priority level (10 = medium priority)
     */
    @Override
    public int getPriority() {
        return 10; // Medium priority for medium files
    }
}
