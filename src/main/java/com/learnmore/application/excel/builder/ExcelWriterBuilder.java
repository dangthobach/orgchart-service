package com.learnmore.application.excel.builder;

import com.learnmore.application.excel.service.ExcelWritingService;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;

import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for Excel writing operations
 *
 * Provides a clean, readable API for configuring and executing Excel writes.
 * Hides the complexity of ExcelConfig and provides sensible defaults.
 *
 * Features:
 * - Fluent method chaining for easy configuration
 * - Sensible defaults for common use cases
 * - Type-safe API with generics
 * - Clear terminal operations (write, writeToBytes)
 * - Strategy control (XSSF, SXSSF, CSV, Template, Styled)
 *
 * Example usage:
 * <pre>
 * // Simple write
 * excelFacade.writer(users)
 *     .write("output.xlsx");
 *
 * // With styling
 * excelFacade.writer(users)
 *     .withStyling()
 *     .disableAutoSizing()
 *     .write("report.xlsx");
 *
 * // With template
 * excelFacade.writer(users)
 *     .withTemplate("template.xlsx")
 *     .startAt(5, 2)
 *     .write("filled-template.xlsx");
 *
 * // Force CSV for large data
 * excelFacade.writer(largeDataset)
 *     .forceCSV()
 *     .write("big-export.xlsx");
 * </pre>
 *
 * @param <T> The type of objects to write to Excel
 */
public class ExcelWriterBuilder<T> {

    private final ExcelWritingService writingService;
    private final List<T> data;
    private final ExcelConfig.Builder configBuilder;
    private int rowStart = 0;
    private int columnStart = 0;

    /**
     * Public constructor (used by ExcelFacade.writer())
     *
     * @param writingService Writing service
     * @param data Data to write
     */
    public ExcelWriterBuilder(ExcelWritingService writingService, List<T> data) {
        this.writingService = writingService;
        this.data = data;
        this.configBuilder = ExcelConfig.builder();
    }

    // ========== Strategy Selection ==========

    /**
     * Force XSSF (standard workbook) format
     *
     * Best for small files (< 50K records) where memory is not a concern.
     * Provides best compatibility and feature support.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> forceXSSF() {
        configBuilder.forceStreamingMode(false);
        configBuilder.preferCSVForLargeData(false);
        return this;
    }

    /**
     * Force SXSSF (streaming workbook) format
     *
     * Best for medium to large files (50K - 2M records).
     * Memory efficient with streaming write.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> forceSXSSF() {
        configBuilder.forceStreamingMode(true);
        configBuilder.preferCSVForLargeData(false);
        return this;
    }

    /**
     * Force CSV format
     *
     * Best for very large files (2M+ records).
     * 10x+ faster than Excel formats, minimal memory.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> forceCSV() {
        configBuilder.preferCSVForLargeData(true);
        return this;
    }

    /**
     * Use template file for writing
     *
     * Template preserves formatting, formulas, and styles.
     *
     * @param templatePath Path to template file
     * @return This builder
     */
    public ExcelWriterBuilder<T> withTemplate(String templatePath) {
        configBuilder.templatePath(templatePath);
        return this;
    }

    /**
     * Enable professional styling
     *
     * Adds header formatting, borders, colors, auto-sizing.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> withStyling() {
        configBuilder.styleTemplate(new Object()); // Marker for styled write
        return this;
    }

    /**
     * Write to multiple sheets
     *
     * Each sheet has its own name and data.
     * Note: Requires different API - use ExcelFacade.writeMultiSheet() instead.
     *
     * @param sheetNames Sheet names
     * @return This builder
     */
    public ExcelWriterBuilder<T> withSheets(String... sheetNames) {
        configBuilder.sheetNames(Arrays.asList(sheetNames));
        return this;
    }

    // ========== Performance Tuning ==========

    /**
     * Disable auto-sizing of columns
     *
     * Major performance improvement for large files.
     * Columns will have default width.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> disableAutoSizing() {
        configBuilder.disableAutoSizing(true);
        return this;
    }

    /**
     * Enable auto-sizing of columns (default)
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> enableAutoSizing() {
        configBuilder.disableAutoSizing(false);
        return this;
    }

    /**
     * Disable output compression
     *
     * Slightly faster but larger file size.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> disableCompression() {
        configBuilder.compressOutput(false);
        return this;
    }

    /**
     * Set SXSSF window size
     *
     * Number of rows kept in memory before flushing to disk.
     *
     * @param windowSize Window size (default 1000)
     * @return This builder
     */
    public ExcelWriterBuilder<T> windowSize(int windowSize) {
        configBuilder.sxssfRowAccessWindowSize(windowSize);
        return this;
    }

    /**
     * Set flush interval for SXSSF
     *
     * @param interval Flush every N records
     * @return This builder
     */
    public ExcelWriterBuilder<T> flushInterval(int interval) {
        configBuilder.flushInterval(interval);
        return this;
    }

    // ========== Positioning ==========

    /**
     * Set starting position for data
     *
     * @param row Starting row (0-based)
     * @param column Starting column (0-based)
     * @return This builder
     */
    public ExcelWriterBuilder<T> startAt(int row, int column) {
        this.rowStart = row;
        this.columnStart = column;
        return this;
    }

    /**
     * Set starting row (column defaults to 0)
     *
     * @param row Starting row (0-based)
     * @return This builder
     */
    public ExcelWriterBuilder<T> startAtRow(int row) {
        this.rowStart = row;
        return this;
    }

    /**
     * Set starting column (row defaults to 0)
     *
     * @param column Starting column (0-based)
     * @return This builder
     */
    public ExcelWriterBuilder<T> startAtColumn(int column) {
        this.columnStart = column;
        return this;
    }

    // ========== Formatting ==========

    /**
     * Set date format
     *
     * @param format Date format (e.g., "yyyy-MM-dd")
     * @return This builder
     */
    public ExcelWriterBuilder<T> dateFormat(String format) {
        configBuilder.dateFormat(format);
        return this;
    }

    /**
     * Set date-time format
     *
     * @param format Date-time format (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return This builder
     */
    public ExcelWriterBuilder<T> dateTimeFormat(String format) {
        configBuilder.dateTimeFormat(format);
        return this;
    }

    // ========== Cell Styling ==========

    /**
     * Enable cell style optimization
     *
     * Reuses cell styles to reduce memory.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> optimizeCellStyles() {
        configBuilder.enableCellStyleOptimization(true);
        return this;
    }

    /**
     * Minimize memory footprint
     *
     * Aggressive memory optimizations.
     *
     * @return This builder
     */
    public ExcelWriterBuilder<T> minimizeMemory() {
        configBuilder.minimizeMemoryFootprint(true);
        return this;
    }

    // ========== Metadata ==========

    /**
     * Set job ID for tracking
     *
     * @param jobId Job ID
     * @return This builder
     */
    public ExcelWriterBuilder<T> jobId(String jobId) {
        configBuilder.jobId(jobId);
        return this;
    }

    // ========== Terminal Operations ==========

    /**
     * Write to file
     *
     * Uses automatic strategy selection based on data size and configuration.
     *
     * @param fileName Output file name
     * @throws ExcelProcessException if writing fails
     */
    public void write(String fileName) throws ExcelProcessException {
        ExcelConfig config = configBuilder.build();

        if (rowStart != 0 || columnStart != 0) {
            // Use position-aware write
            writingService.writeWithPosition(fileName, data, rowStart, columnStart, config);
        } else {
            // Use standard write
            writingService.writeWithConfig(fileName, data, config);
        }
    }

    /**
     * Write to byte array (in-memory)
     *
     * WARNING: Only use for small files (< 50K records).
     *
     * @return Excel file as byte array
     * @throws ExcelProcessException if writing fails
     */
    public byte[] writeToBytes() throws ExcelProcessException {
        if (data.size() > 50_000) {
            throw new ExcelProcessException(
                "Writing " + data.size() + " records to bytes may cause memory issues. " +
                "Use write(fileName) instead for large datasets."
            );
        }

        return writingService.writeToBytes(data);
    }

    /**
     * Write to file with custom configuration
     *
     * For advanced use cases where you need direct access to ExcelConfig.
     *
     * @param fileName Output file name
     * @param config Custom Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    public void writeWithCustomConfig(String fileName, ExcelConfig config) throws ExcelProcessException {
        writingService.writeWithPosition(fileName, data, rowStart, columnStart, config);
    }

    /**
     * Build ExcelConfig without executing write
     *
     * Useful for inspection or passing to other methods.
     *
     * @return Built ExcelConfig
     */
    public ExcelConfig buildConfig() {
        return configBuilder.build();
    }

    /**
     * Get data being written
     *
     * @return Data list
     */
    public List<T> getData() {
        return data;
    }

    /**
     * Get starting row
     *
     * @return Starting row index
     */
    public int getRowStart() {
        return rowStart;
    }

    /**
     * Get starting column
     *
     * @return Starting column index
     */
    public int getColumnStart() {
        return columnStart;
    }
}
