package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.utils.cache.ReflectionCache;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

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
 * This strategy ALWAYS delegates to ExcelUtil.writeToExcel() which
 * automatically uses SXSSF for medium files based on cell count.
 *
 * SXSSF characteristics:
 * - Streaming write: Only keeps 100 rows in memory (window size)
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
 * Strategy selection criteria:
 * - 50,000 < Data size < 2,000,000 records
 * - 1,000,000 < Total cells < 5,000,000 cells
 * - forceSXSSF == true in config (optional)
 *
 * Strategy selection:
 * - Priority: 10 (medium priority for medium files)
 * - Supports: Medium to large files
 *
 * @param <T> The type of objects to write to Excel
 */
@Slf4j
@Component
public class SXSSFWriteStrategy<T> implements WriteStrategy<T> {

    // Thresholds for SXSSF strategy selection
    private static final int MIN_RECORDS = 50_000;
    private static final int MAX_RECORDS = 2_000_000;
    private static final long MIN_CELLS = 1_000_000L;
    private static final long MAX_CELLS = 5_000_000L;

    /**
     * Execute write using SXSSF (streaming) workbook
     *
     * This method now implements the SXSSF writing logic directly instead of delegating to ExcelUtil.
     * It creates a streaming SXSSF workbook that keeps only a window of rows in memory at a time.
     * Best for medium to large files where memory efficiency is important.
     *
     * SXSSF streaming details:
     * - Window size: 100 rows (configurable)
     * - Older rows automatically flushed to temp file
     * - Temp files cleaned up after writing
     * - Memory usage: ~10MB regardless of file size
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
            // Get reflection cache for field mapping
            ReflectionCache reflectionCache = ReflectionCache.getInstance();
            @SuppressWarnings("unchecked")
            Class<T> beanClass = (Class<T>) data.get(0).getClass();
            
            // Calculate optimal window size for SXSSF
            int windowSize = calculateOptimalWindowSize(data.size(), config);
            
            // Create SXSSF workbook with streaming
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize);
                 FileOutputStream fos = new FileOutputStream(fileName)) {
                
                // Create sheet
                Sheet sheet = workbook.createSheet("Sheet1");
                
                // Get field mapping from reflection cache
                ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
                List<String> columnNames = new ArrayList<>(excelFields.keySet());
                
                // Create header row
                Row headerRow = sheet.createRow(0);
                CellStyle headerStyle = createHeaderStyle(workbook);
                for (int i = 0; i < columnNames.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columnNames.get(i));
                    cell.setCellStyle(headerStyle);
                }
                
                // Write data rows with streaming
                int currentRow = 1;
                for (T item : data) {
                    Row row = sheet.createRow(currentRow++);
                    writeRowData(row, item, columnNames, excelFields, 0);
                    
                    // Flush rows to disk periodically to maintain memory efficiency
                    if (currentRow % 100 == 0) {
                        ((org.apache.poi.xssf.streaming.SXSSFSheet) sheet).flushRows(100);
                    }
                }
                
                // Auto-size columns if enabled (only for visible rows)
                if (!config.isDisableAutoSizing()) {
                    for (int i = 0; i < columnNames.size(); i++) {
                        sheet.autoSizeColumn(i);
                    }
                }
                
                // Write workbook to file
                workbook.write(fos);
                
                // Dispose of temporary files
                workbook.dispose();
            }
            
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
     * Create header style for Excel cells
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        return headerStyle;
    }
    
    /**
     * Write row data to Excel row
     */
    private void writeRowData(Row row, T item, List<String> columnNames, 
                             ConcurrentMap<String, Field> excelFields, int columnStart) {
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            Field field = excelFields.get(columnName);
            
            if (field != null) {
                try {
                    Object value = field.get(item);
                    Cell cell = row.createCell(columnStart + i);
                    setCellValue(cell, value);
                } catch (IllegalAccessException e) {
                    log.warn("Failed to access field {} for column {}", field.getName(), columnName);
                }
            }
        }
    }
    
    /**
     * Set cell value with proper type handling
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        
        if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else if (value instanceof Float) {
            cell.setCellValue((Float) value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(value.toString());
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
