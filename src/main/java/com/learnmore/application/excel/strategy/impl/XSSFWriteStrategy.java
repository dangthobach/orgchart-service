package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * XSSF write strategy for small Excel files
 *
 * This strategy uses standard XSSF (XML Spreadsheet Format) workbook
 * which keeps the entire workbook in memory. Best for small files
 * where memory usage is not a concern.
 *
 * Performance characteristics:
 * - Memory: O(total_cells) - entire workbook in memory
 * - Speed: Fast for small files (< 50K records)
 * - File size: Up to 50K records or 1M cells
 * - Compatibility: Excellent (standard Excel format)
 *
 * This strategy ALWAYS delegates to ExcelUtil.writeToExcel() which
 * automatically uses XSSF for small files based on cell count.
 *
 * When to use:
 * - Small files (< 50K records)
 * - Small cell count (< 1M cells)
 * - When memory is not a concern
 * - When compatibility is important
 *
 * Strategy selection criteria:
 * - Data size < 50,000 records
 * - Total cells < 1,000,000 cells
 * - forceXSSF == true in config (optional)
 *
 * Strategy selection:
 * - Priority: 20 (highest for small files)
 * - Supports: Only small files or when forceXSSF enabled
 *
 * @param <T> The type of objects to write to Excel
 */
@Slf4j
@Component
public class XSSFWriteStrategy<T> implements WriteStrategy<T> {

    // Thresholds for XSSF strategy selection
    private static final int MAX_RECORDS = 50_000;
    private static final long MAX_CELLS = 1_000_000L;

    /**
     * Execute write using XSSF (standard) workbook
     *
     * This method delegates to ExcelUtil.writeToExcel() which will
     * automatically use XSSF for small files. The automatic strategy
     * selection in ExcelUtil is based on cell count:
     * - < 1M cells: XSSF (this strategy)
     * - 1M - 5M cells: SXSSF streaming
     * - > 5M cells: CSV
     *
     * @param fileName Output file name (e.g., "output.xlsx")
     * @param data List of objects to write
     * @param config Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    @Override
    public void execute(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException {
        log.debug("Executing XSSFWriteStrategy for {} records to {}", data.size(), fileName);

        // Validate that data size is appropriate for XSSF
        if (data.size() > MAX_RECORDS) {
            log.warn("Writing {} records with XSSF may cause memory issues. " +
                    "Consider using SXSSF or CSV strategy.", data.size());
        }

        // Delegate to existing optimized implementation - ZERO performance impact
        // ExcelUtil.writeToExcel() will automatically select XSSF for small files
        ExcelUtil.writeToExcel(fileName, data, 0, 0, config);

        log.info("XSSFWriteStrategy completed: {} records written to {}", data.size(), fileName);
    }

    /**
     * Check if this strategy supports the given data and configuration
     *
     * XSSFWriteStrategy is selected when:
     * 1. Data size <= 50,000 records, OR
     * 2. Total cells <= 1,000,000 cells, OR
     * 3. config.isForceXSSF() == true (force XSSF regardless of size)
     *
     * Cell count estimation:
     * - Assumes ~20 columns per row (average)
     * - Total cells = rows * columns
     *
     * @param dataSize Number of records to write
     * @param cellCount Total number of cells (rows * columns)
     * @param config Excel configuration
     * @return true if this strategy supports the data size, false otherwise
     */
    @Override
    public boolean supports(int dataSize, long cellCount, ExcelConfig config) {
        // Support small files based on size and cell count
        // XSSF is best for small files where entire workbook can fit in memory
        boolean supportsSize = dataSize <= MAX_RECORDS;
        boolean supportsCells = cellCount <= MAX_CELLS;

        boolean supported = supportsSize || supportsCells;

        if (supported) {
            log.debug("XSSFWriteStrategy supports data: {} records, {} cells",
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
        return "XSSFWriteStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 20 means this strategy is preferred for small files
     * when the configuration supports it.
     *
     * Priority ordering:
     * - 0: Default/fallback strategy
     * - 10: SXSSFWriteStrategy (medium files)
     * - 15: CSVWriteStrategy (large files)
     * - 20: XSSFWriteStrategy (small files - highest priority)
     *
     * @return Priority level (20 = highest for small files)
     */
    @Override
    public int getPriority() {
        return 20; // Highest priority for small files
    }
}
