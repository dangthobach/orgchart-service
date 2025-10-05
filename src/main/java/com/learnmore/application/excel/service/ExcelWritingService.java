package com.learnmore.application.excel.service;

import com.learnmore.application.excel.helper.ExcelWriteHelper;
import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.excel.strategy.selector.WriteStrategySelector;
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
public class ExcelWritingService {

    // Strategy selector for automatic strategy selection
    private final WriteStrategySelector writeStrategySelector;

    // Helper for low-level POI operations (Phase 2 refactoring)
    private final ExcelWriteHelper writeHelper;

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
    public <T> void write(String fileName, List<T> data) throws ExcelProcessException {
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
     * Phase 2: Now delegates to ExcelWriteHelper instead of internal methods.
     *
     * @param data List of objects to write
     * @return Excel file as byte array
     * @throws ExcelProcessException if writing fails
     */
    public <T> byte[] writeToBytes(List<T> data) throws ExcelProcessException {
        log.debug("Writing {} records to Excel bytes", data.size());

        if (data.size() > 50_000) {
            log.warn("Writing {} records to bytes may cause memory issues. Consider writing to file.", data.size());
        }
        try {
            boolean useStreaming = data.size() > 50_000;
            if (useStreaming) {
                return writeHelper.writeToBytesSXSSF(data, DEFAULT_CONFIG, 2000);
            } else {
                return writeHelper.writeToBytesXSSF(data, DEFAULT_CONFIG);
            }
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to generate Excel bytes", e);
        }
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
    public <T> void writeWithConfig(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException {
        log.debug("Writing {} records to Excel file with custom config: {}", data.size(), fileName);

        // Phase 2: Use strategy selector for automatic optimization based on config
        WriteStrategy<T> strategy = writeStrategySelector.selectStrategy(data.size(), config);
        strategy.execute(fileName, data, config);

        log.info("Successfully wrote {} records to {}", data.size(), fileName);
    }

    /**
     * Write data to Excel file with custom start positions
     *
     * NOTE: This method is ACTIVELY USED by ExcelWriterBuilder for position-aware writes.
     * Phase 2: Now delegates to ExcelWriteHelper instead of internal POI code.
     *
     * TODO (PHASE 3): Add executeWithPosition() to WriteStrategy interface for full consistency.
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param rowStart Starting row index (0-based)
     * @param columnStart Starting column index (0-based)
     * @param config Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeWithPosition(
        String fileName,
        List<T> data,
        int rowStart,
        int columnStart,
        ExcelConfig config
    ) throws ExcelProcessException {
        log.debug("Writing {} records to Excel file starting at row={}, col={}: {}",
                 data.size(), rowStart, columnStart, fileName);
        try {
            if (data == null || data.isEmpty()) {
                throw new ExcelProcessException("Data list cannot be null or empty");
            }
            boolean useStreaming = data.size() > 50_000;
            int windowSize = Math.min(5000, Math.max(200, config.getFlushInterval()));

            if (useStreaming) {
                writeHelper.writeToFileSXSSF(fileName, data, rowStart, columnStart, config, windowSize);
            } else {
                writeHelper.writeToFileXSSF(fileName, data, rowStart, columnStart, config);
            }
            log.info("Successfully wrote {} records to {}", data.size(), fileName);
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to write Excel with position", e);
        }
    }

    /**
     * Write data to Excel file optimized for small files
     *
     * Uses XSSF (standard) workbook for best compatibility.
     *
     * NOTE: This is a convenience method accessed via ExcelFacade.writeSmallFile()
     * It's kept in the public API for explicit small-file optimization.
     * Direct usage is rare - most callers use write() with automatic strategy selection.
     *
     * @param fileName Output file name
     * @param data List of objects to write (< 50K records recommended)
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeSmallFile(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing small file: {} records to {}", data.size(), fileName);

        ExcelConfig smallFileConfig = ExcelConfigFactory.createSmallFileConfig();

        // Use strategy selector path
        WriteStrategy<T> strategy = writeStrategySelector.selectStrategy(data.size(), smallFileConfig);
        strategy.execute(fileName, data, smallFileConfig);

        log.info("Successfully wrote small file: {} records to {}", data.size(), fileName);
    }

    /**
     * Write data to Excel file optimized for large files
     *
     * Uses SXSSF streaming workbook for memory efficiency.
     *
     * NOTE: This is a convenience method accessed via ExcelFacade.writeLargeFile()
     * It's kept in the public API for explicit large-file optimization.
     * Direct usage is rare - most callers use write() with automatic strategy selection.
     *
     * @param fileName Output file name
     * @param data List of objects to write (500K - 2M records)
     * @throws ExcelProcessException if writing fails
     */
    public <T> void writeLargeFile(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing large file: {} records to {}", data.size(), fileName);

        ExcelConfig largeFileConfig = ExcelConfigFactory.createLargeFileConfig();

        // Use strategy selector path
        WriteStrategy<T> strategy = writeStrategySelector.selectStrategy(data.size(), largeFileConfig);
        strategy.execute(fileName, data, largeFileConfig);

        log.info("Successfully wrote large file: {} records to {}", data.size(), fileName);
    }
}
