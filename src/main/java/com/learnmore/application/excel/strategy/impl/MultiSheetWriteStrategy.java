package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Multi-sheet write strategy for Excel files
 *
 * This strategy writes multiple sheets to a single Excel workbook.
 * Each sheet can have different data types and structures. Perfect
 * for complex reports with related data across multiple tabs.
 *
 * Performance characteristics:
 * - Memory: O(sum of all sheet sizes) or O(window_size) with SXSSF
 * - Speed: Good for small to medium workbooks (< 500K total records)
 * - File size: Up to 1M total records across all sheets
 *
 * Sheet handling:
 * - Each sheet has independent data and structure
 * - Sheet names are customizable
 * - Supports different object types per sheet
 * - Automatic workbook type selection (XSSF vs SXSSF)
 *
 * Memory optimization:
 * - Uses SXSSF (streaming) for large workbooks
 * - Uses XSSF (standard) for small workbooks
 * - Automatic strategy based on total data size
 *
 * Use cases:
 * - Multi-tab reports (Summary, Details, Charts)
 * - Related data exports (Users, Orders, Products)
 * - Department-wise data (HR, Finance, IT)
 * - Time-series data (Jan, Feb, Mar sheets)
 * - Hierarchical data (Parent, Child relationships)
 *
 * Strategy selection:
 * - Priority: 18 (higher than standard single-sheet writes)
 * - Supports: When config.getSheetNames() has multiple entries
 *
 * @param <T> The type of objects to write to Excel (can vary per sheet)
 */
@Slf4j
@Component
public class MultiSheetWriteStrategy<T> implements WriteStrategy<T> {

    private static final int SXSSF_THRESHOLD = 100_000; // Use SXSSF above this

    /**
     * Execute multi-sheet write
     *
     * Note: This method signature accepts List<T> but for multi-sheet,
     * data should be structured as Map<String, List<?>> where key is sheet name.
     * This is a design constraint of the WriteStrategy interface.
     *
     * Alternative: Use executeMultiSheet() method directly for type-safe API.
     *
     * @param fileName Output file name
     * @param data List of objects (should be Map wrapper)
     * @param config Excel configuration with sheet names
     * @throws ExcelProcessException if writing fails
     */
    @Override
    public void execute(String fileName, List<T> data, ExcelConfig config) throws ExcelProcessException {
        throw new ExcelProcessException(
            "MultiSheetWriteStrategy requires Map<String, List<?>> data structure. " +
            "Use executeMultiSheet() method instead or wrap data appropriately."
        );
    }

    /**
     * Execute multi-sheet write with proper type-safe API
     *
     * Process flow:
     * 1. Determine workbook type (XSSF vs SXSSF) based on total size
     * 2. Create workbook
     * 3. For each sheet:
     *    a. Create sheet with name
     *    b. Write header row
     *    c. Write data rows
     *    d. Auto-size columns (if enabled)
     * 4. Save workbook to file
     *
     * @param fileName Output file name
     * @param sheetsData Map of sheet name to data list
     * @param config Excel configuration
     * @throws ExcelProcessException if writing fails
     */
    public void executeMultiSheet(String fileName,
                                  Map<String, List<?>> sheetsData,
                                  ExcelConfig config) throws ExcelProcessException {
        log.debug("Executing MultiSheetWriteStrategy for {} sheets", sheetsData.size());

        if (sheetsData.isEmpty()) {
            throw new ExcelProcessException("No sheets data provided for MultiSheetWriteStrategy");
        }

        // Calculate total records across all sheets
        int totalRecords = sheetsData.values().stream()
            .mapToInt(List::size)
            .sum();

        log.debug("Total records across all sheets: {}", totalRecords);

        try {
            // Choose workbook type based on size
            Workbook workbook;
            if (totalRecords > SXSSF_THRESHOLD) {
                log.info("Using SXSSF for large multi-sheet workbook ({} records)", totalRecords);
                workbook = new SXSSFWorkbook(config.getSxssfRowAccessWindowSize());
            } else {
                log.info("Using XSSF for small multi-sheet workbook ({} records)", totalRecords);
                workbook = new XSSFWorkbook();
            }

            // Write each sheet
            for (Map.Entry<String, List<?>> entry : sheetsData.entrySet()) {
                String sheetName = entry.getKey();
                List<?> sheetData = entry.getValue();

                log.debug("Writing sheet '{}' with {} records", sheetName, sheetData.size());

                writeSheet(workbook, sheetName, sheetData, config);
            }

            // Save workbook
            try (FileOutputStream outputStream = new FileOutputStream(fileName)) {
                workbook.write(outputStream);
            }

            // Cleanup SXSSF temp files
            if (workbook instanceof SXSSFWorkbook) {
                ((SXSSFWorkbook) workbook).dispose();
            }

            workbook.close();

            log.info("MultiSheetWriteStrategy completed: {} sheets, {} total records written to {}",
                    sheetsData.size(), totalRecords, fileName);

        } catch (ExcelProcessException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to write multi-sheet workbook: " + e.getMessage(), e);
        }
    }

    /**
     * Write single sheet to workbook
     *
     * @param workbook Excel workbook
     * @param sheetName Sheet name
     * @param data Data to write
     * @param config Excel configuration
     * @throws Exception if writing fails
     */
    private void writeSheet(Workbook workbook,
                           String sheetName,
                           List<?> data,
                           ExcelConfig config) throws Exception {
        if (data.isEmpty()) {
            log.warn("Sheet '{}' has no data, creating empty sheet", sheetName);
            workbook.createSheet(sheetName);
            return;
        }

        // Create sheet
        Sheet sheet = workbook.createSheet(sheetName);

        // Get fields from first object
        Object firstObject = data.get(0);
        java.lang.reflect.Field[] fields = firstObject.getClass().getDeclaredFields();

        // Create header style
        CellStyle headerStyle = createHeaderStyle(workbook);

        // Write header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < fields.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(formatFieldName(fields[i].getName()));
            cell.setCellStyle(headerStyle);
        }

        // Write data rows
        int rowIndex = 1;
        for (Object dataObject : data) {
            Row row = sheet.createRow(rowIndex++);

            for (int i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                Object value = fields[i].get(dataObject);

                Cell cell = row.createCell(i);
                setCellValue(cell, value);
            }
        }

        // Auto-size columns if enabled
        if (!config.isDisableAutoSizing()) {
            for (int i = 0; i < fields.length; i++) {
                sheet.autoSizeColumn(i);

                // Limit column width
                if (sheet.getColumnWidth(i) > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }
        }

        // Freeze header pane
        sheet.createFreezePane(0, 1);
    }

    /**
     * Create header style
     *
     * @param workbook Excel workbook
     * @return Header cell style
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // Font: Bold
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        // Background: Dark blue
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Alignment: Center
        style.setAlignment(HorizontalAlignment.CENTER);

        // Border
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * Set cell value based on type
     *
     * @param cell Excel cell
     * @param value Value to set
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Format field name for header
     *
     * @param fieldName Field name
     * @return Formatted header name
     */
    private String formatFieldName(String fieldName) {
        String result = fieldName.replaceAll("([A-Z])", " $1");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }

    /**
     * Check if this strategy supports the given configuration
     *
     * MultiSheetWriteStrategy is selected when:
     * - config.getSheetNames() has multiple entries (> 1 sheet)
     * - Total data size is appropriate for multi-sheet approach
     *
     * @param dataSize Number of records to write
     * @param cellCount Total number of cells
     * @param config Excel configuration
     * @return true if multi-sheet is configured, false otherwise
     */
    @Override
    public boolean supports(int dataSize, long cellCount, ExcelConfig config) {
        // Check if multiple sheet names are provided
        if (config.getSheetNames() == null || config.getSheetNames().size() <= 1) {
            return false;
        }

        // Check if total size is reasonable for multi-sheet
        if (dataSize > 1_000_000) {
            log.warn("MultiSheetWriteStrategy not recommended for {} total records (> 1M limit)",
                    dataSize);
            return false;
        }

        log.debug("MultiSheetWriteStrategy supports config: {} sheets, {} total records",
                 config.getSheetNames().size(), dataSize);

        return true;
    }

    /**
     * Get strategy name for logging and debugging
     *
     * @return Strategy name
     */
    @Override
    public String getName() {
        return "MultiSheetWriteStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 18 means this strategy is preferred over standard single-sheet
     * writes (10, 15, 20) when multiple sheets are configured.
     *
     * Priority ordering (active strategies only):
     * - 0: Default/fallback strategy
     * - 10: SXSSFWriteStrategy (medium files)
     * - 15: CSVWriteStrategy (large files)
     * - 18: MultiSheetWriteStrategy (multi-sheet - high)
     * - 20: XSSFWriteStrategy (small files)
     *
     * @return Priority level (18 = high for multi-sheet writes)
     */
    @Override
    public int getPriority() {
        return 18; // Higher than standard writes
    }
}
