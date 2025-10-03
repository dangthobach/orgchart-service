package com.learnmore.application.port.input;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;

import java.util.List;

/**
 * Port (Interface) for writing Excel files
 *
 * This interface defines the contract for Excel writing operations.
 * It follows the Hexagonal Architecture pattern where this is the INPUT PORT.
 *
 * Implementations automatically select optimal write strategy based on data size:
 * - XSSF for small files (< 1M cells)
 * - SXSSF streaming for medium files (1M - 5M cells)
 * - CSV for very large files (> 5M cells)
 *
 * @param <T> The type of objects to write to Excel
 */
public interface ExcelWriter<T> {

    /**
     * Write data to Excel file with automatic strategy selection
     *
     * The implementation will automatically choose the best write strategy
     * based on data size and configuration.
     *
     * @param fileName Output file name (e.g., "output.xlsx")
     * @param data List of objects to write
     * @throws ExcelProcessException if writing fails
     */
    void write(String fileName, List<T> data) throws ExcelProcessException;

    /**
     * Write data to Excel bytes (in-memory)
     *
     * WARNING: Only use for small files (< 50K records).
     * For large files, use write() to file directly.
     *
     * @param data List of objects to write
     * @return Excel file as byte array
     * @throws ExcelProcessException if writing fails
     */
    byte[] writeToBytes(List<T> data) throws ExcelProcessException;

    /**
     * Write data to Excel file with custom configuration
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param config Custom Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    void writeWithConfig(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException;

    /**
     * Write data to Excel file with custom start positions
     *
     * @param fileName Output file name
     * @param data List of objects to write
     * @param rowStart Starting row index (0-based)
     * @param columnStart Starting column index (0-based)
     * @param config Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    void writeWithPosition(
        String fileName,
        List<T> data,
        int rowStart,
        int columnStart,
        ExcelConfig config
    ) throws ExcelProcessException;
}
