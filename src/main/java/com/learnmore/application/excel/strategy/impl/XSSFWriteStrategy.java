package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.utils.cache.ReflectionCache;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

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
     * This method now implements the XSSF writing logic directly instead of delegating to ExcelUtil.
     * It creates a standard XSSF workbook and writes all data to memory before saving to file.
     * Best for small files where memory usage is not a concern.
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

        try {
            // Get reflection cache for field mapping
            ReflectionCache reflectionCache = ReflectionCache.getInstance();
            @SuppressWarnings("unchecked")
            Class<T> beanClass = (Class<T>) data.get(0).getClass();
            
            // Create XSSF workbook
            try (XSSFWorkbook workbook = new XSSFWorkbook();
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
                
                // Write data rows
                int currentRow = 1;
                for (T item : data) {
                    Row row = sheet.createRow(currentRow++);
                    writeRowData(row, item, columnNames, excelFields, 0);
                }
                
                // Auto-size columns if enabled
                if (!config.isDisableAutoSizing()) {
                    for (int i = 0; i < columnNames.size(); i++) {
                        sheet.autoSizeColumn(i);
                    }
                }
                
                // Write workbook to file
                workbook.write(fos);
            }
            
            log.info("XSSFWriteStrategy completed: {} records written to {}", data.size(), fileName);
            
        } catch (Exception e) {
            log.error("XSSFWriteStrategy failed for file: {}", fileName, e);
            throw new ExcelProcessException("Failed to write Excel file with XSSF strategy", e);
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
