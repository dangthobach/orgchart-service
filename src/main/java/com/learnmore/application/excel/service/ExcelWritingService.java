package com.learnmore.application.excel.service;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.excel.strategy.selector.WriteStrategySelector;
import com.learnmore.application.port.input.ExcelWriter;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.config.ExcelConfigFactory;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for writing Excel files with automatic strategy selection
 *
 * This service implements the ExcelWriter port and provides a clean API
 * for writing Excel files. It follows Hexagonal Architecture principles
 * with Strategy Pattern for automatic optimization.
 *
 * Strategy Selection (Phase 2):
 * - Uses WriteStrategySelector to automatically choose the best strategy
 * - XSSFWriteStrategy: Small files (< 50K records, < 1M cells)
 * - SXSSFWriteStrategy: Medium files (50K - 2M records, 1M - 5M cells)
 * - CSVWriteStrategy: Large files (> 2M records, > 5M cells)
 *
 * IMPORTANT: All strategies delegate to the existing ExcelUtil methods
 * to preserve the optimized performance and automatic strategy selection.
 * ZERO performance impact from refactoring.
 *
 * @param <T> The type of objects to write to Excel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelWritingService<T> implements ExcelWriter<T> {

    // Strategy selector for automatic strategy selection (Phase 2)
    private final WriteStrategySelector writeStrategySelector;

    // Default configuration optimized for writing
    private static final ExcelConfig DEFAULT_CONFIG = ExcelConfigFactory.createProductionConfig();

    /**
     * Write data to Excel file with automatic strategy selection
     *
     * Phase 2: Now uses WriteStrategySelector to automatically choose the best strategy:
     * - XSSFWriteStrategy: Small files (< 50K records)
     * - SXSSFWriteStrategy: Medium files (50K - 2M records)
     * - CSVWriteStrategy: Large files (> 2M records)
     *
     * All strategies delegate to ExcelUtil - ZERO performance impact.
     *
     * @param fileName Output file name (e.g., "output.xlsx")
     * @param data List of objects to write
     * @throws ExcelProcessException if writing fails
     */
    @Override
    public void write(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing {} records to Excel file: {}", data.size(), fileName);

        // Phase 2: Use strategy selector for automatic optimization
        WriteStrategy<T> strategy = writeStrategySelector.selectStrategy(data.size(), DEFAULT_CONFIG);
        strategy.execute(fileName, data, DEFAULT_CONFIG);

        log.info("Successfully wrote {} records to {}", data.size(), fileName);
    }

    /**
     * Write data to Excel bytes (in-memory)
     *
     * WARNING: Only use for small files (< 50K records).
     * Delegates to ExcelUtil.writeToExcelBytes() - ZERO performance impact.
     *
     * @param data List of objects to write
     * @return Excel file as byte array
     * @throws ExcelProcessException if writing fails
     */
    @Override
    public byte[] writeToBytes(List<T> data) throws ExcelProcessException {
        log.debug("Writing {} records to Excel bytes", data.size());

        if (data.size() > 50_000) {
            log.warn("Writing {} records to bytes may cause memory issues. Consider writing to file.", data.size());
        }

        // Delegate to existing optimized implementation
        byte[] result = ExcelUtil.writeToExcelBytes(data, 0, 0, DEFAULT_CONFIG);

        log.info("Successfully wrote {} records to bytes ({} KB)", data.size(), result.length / 1024);
        return result;
    }

    /**
     * Write data to Excel file with custom configuration and automatic strategy selection
     *
     * Phase 2: Uses WriteStrategySelector to choose optimal strategy based on data size and config.
     * All strategies delegate to ExcelUtil - ZERO performance impact.
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param config Custom Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    @Override
    public void writeWithConfig(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException {
        log.debug("Writing {} records to Excel file with custom config: {}", data.size(), fileName);

        // Phase 2: Use strategy selector for automatic optimization based on config
        WriteStrategy<T> strategy = writeStrategySelector.selectStrategy(data.size(), config);
        strategy.execute(fileName, data, config);

        log.info("Successfully wrote {} records to {}", data.size(), fileName);
    }

    /**
     * Write data to Excel file with custom start positions
     *
     * Delegates to ExcelUtil.writeToExcel() - ZERO performance impact.
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param rowStart Starting row index (0-based)
     * @param columnStart Starting column index (0-based)
     * @param config Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    @Override
    public void writeWithPosition(
        String fileName,
        List<T> data,
        int rowStart,
        int columnStart,
        ExcelConfig config
    ) throws ExcelProcessException {
        log.debug("Writing {} records to Excel file starting at row={}, col={}: {}",
                 data.size(), rowStart, columnStart, fileName);

        // Delegate to existing optimized implementation
        ExcelUtil.writeToExcel(fileName, data, rowStart, columnStart, config);

        log.info("Successfully wrote {} records to {}", data.size(), fileName);
    }

    /**
     * Write data to Excel file optimized for small files
     *
     * Uses XSSF (standard) workbook for best compatibility.
     *
     * @param fileName Output file name
     * @param data List of objects to write (< 50K records recommended)
     * @throws ExcelProcessException if writing fails
     */
    public void writeSmallFile(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing small file: {} records to {}", data.size(), fileName);

        ExcelConfig smallFileConfig = ExcelConfigFactory.createSmallFileConfig();

        // Delegate to existing optimized implementation
        ExcelUtil.writeToExcel(fileName, data, 0, 0, smallFileConfig);

        log.info("Successfully wrote small file: {} records to {}", data.size(), fileName);
    }

    /**
     * Write data to Excel file optimized for large files
     *
     * Uses SXSSF streaming workbook for memory efficiency.
     *
     * @param fileName Output file name
     * @param data List of objects to write (500K - 2M records)
     * @throws ExcelProcessException if writing fails
     */
    public void writeLargeFile(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing large file: {} records to {}", data.size(), fileName);

        ExcelConfig largeFileConfig = ExcelConfigFactory.createLargeFileConfig();

        // Delegate to existing optimized implementation
        ExcelUtil.writeToExcel(fileName, data, 0, 0, largeFileConfig);

        log.info("Successfully wrote large file: {} records to {}", data.size(), fileName);
    }

    /**
     * Write data with automatic CSV conversion for very large files
     *
     * If data size exceeds threshold, automatically converts to CSV
     * for 10x+ performance improvement.
     *
     * @param fileName Output file name (may be changed to .csv)
     * @param data List of objects to write
     * @throws ExcelProcessException if writing fails
     */
    public void writeWithAutoCSV(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing with auto-CSV: {} records to {}", data.size(), fileName);

        ExcelConfig csvConfig = ExcelConfigFactory.createMigrationConfig();

        // Delegate to existing optimized implementation
        // ExcelUtil will automatically convert to CSV if needed
        ExcelUtil.writeToExcel(fileName, data, 0, 0, csvConfig);

        log.info("Successfully wrote {} records (auto-CSV enabled)", data.size());
    }
}
