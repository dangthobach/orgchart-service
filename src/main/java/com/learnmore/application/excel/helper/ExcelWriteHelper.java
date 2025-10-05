package com.learnmore.application.excel.helper;

import com.learnmore.application.utils.cache.ReflectionCache;
import com.learnmore.application.utils.config.ExcelConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Helper class for low-level Excel writing operations
 *
 * This class encapsulates all Apache POI operations for Excel writing.
 * It's responsible for:
 * - Creating workbooks (XSSF and SXSSF)
 * - Writing headers and data rows
 * - Cell styling and formatting
 * - Byte array and file output
 *
 * ARCHITECTURE PRINCIPLE: Single Responsibility
 * - ExcelWritingService: Coordination and strategy selection
 * - ExcelWriteHelper: Low-level POI operations
 * - WriteStrategy implementations: Business logic
 *
 * Benefits:
 * - Easier testing: Can test POI logic in isolation
 * - Better maintainability: Changes to POI don't affect service layer
 * - Reusability: Can be used by multiple strategies
 *
 * @since Phase 2 refactoring
 */
@Slf4j
@Component
public class ExcelWriteHelper {

    private final ReflectionCache reflectionCache = ReflectionCache.getInstance();

    // ========== XSSF (Standard Workbook) Operations ==========

    /**
     * Write data to byte array using XSSF (standard workbook)
     *
     * Best for small to medium files (< 100K records).
     * Entire workbook kept in memory.
     *
     * @param data Data to write
     * @param config Excel configuration
     * @param <T> Type of objects to write
     * @return Excel file as byte array
     * @throws Exception if writing fails
     */
    public <T> byte[] writeToBytesXSSF(List<T> data, ExcelConfig config) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data list cannot be null or empty");
        }

        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());

            // Write header
            writeHeader(workbook, sheet, columnNames, 0);

            // Write data rows
            writeDataRows(sheet, data, columnNames, excelFields, 1, 0);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Write data to file using XSSF (standard workbook)
     *
     * @param fileName Output file name
     * @param data Data to write
     * @param rowStart Starting row index
     * @param columnStart Starting column index
     * @param config Excel configuration
     * @param <T> Type of objects to write
     * @throws Exception if writing fails
     */
    public <T> void writeToFileXSSF(String fileName, List<T> data, int rowStart, int columnStart, ExcelConfig config) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data list cannot be null or empty");
        }

        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {

            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());

            // Write header
            writeHeader(workbook, sheet, columnNames, rowStart, columnStart);

            // Write data rows
            writeDataRows(sheet, data, columnNames, excelFields, rowStart + 1, columnStart);

            workbook.write(fos);
        }
    }

    // ========== SXSSF (Streaming Workbook) Operations ==========

    /**
     * Write data to byte array using SXSSF (streaming workbook)
     *
     * Best for large files (100K - 2M records).
     * Only window of rows kept in memory.
     *
     * @param data Data to write
     * @param config Excel configuration
     * @param windowSize SXSSF window size
     * @param <T> Type of objects to write
     * @return Excel file as byte array
     * @throws Exception if writing fails
     */
    public <T> byte[] writeToBytesSXSSF(List<T> data, ExcelConfig config, int windowSize) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data list cannot be null or empty");
        }

        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());

            // Write header
            writeHeader(workbook, sheet, columnNames, 0);

            // Write data rows
            writeDataRows(sheet, data, columnNames, excelFields, 1, 0);

            workbook.write(out);
            workbook.dispose(); // Clean up temporary files
            return out.toByteArray();
        }
    }

    /**
     * Write data to file using SXSSF (streaming workbook)
     *
     * @param fileName Output file name
     * @param data Data to write
     * @param rowStart Starting row index
     * @param columnStart Starting column index
     * @param config Excel configuration
     * @param windowSize SXSSF window size
     * @param <T> Type of objects to write
     * @throws Exception if writing fails
     */
    public <T> void writeToFileSXSSF(String fileName, List<T> data, int rowStart, int columnStart, ExcelConfig config, int windowSize) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data list cannot be null or empty");
        }

        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize);
             FileOutputStream fos = new FileOutputStream(fileName)) {

            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());

            // Write header
            writeHeader(workbook, sheet, columnNames, rowStart, columnStart);

            // Write data rows
            writeDataRows(sheet, data, columnNames, excelFields, rowStart + 1, columnStart);

            workbook.write(fos);
            workbook.dispose(); // Clean up temporary files
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Write header row with styling
     *
     * @param workbook Workbook for style creation
     * @param sheet Sheet to write to
     * @param columnNames Column names
     * @param rowStart Starting row index
     */
    private void writeHeader(Workbook workbook, Sheet sheet, List<String> columnNames, int rowStart) {
        writeHeader(workbook, sheet, columnNames, rowStart, 0);
    }

    /**
     * Write header row with styling at specific position
     *
     * @param workbook Workbook for style creation
     * @param sheet Sheet to write to
     * @param columnNames Column names
     * @param rowStart Starting row index
     * @param columnStart Starting column index
     */
    private void writeHeader(Workbook workbook, Sheet sheet, List<String> columnNames, int rowStart, int columnStart) {
        Row headerRow = sheet.createRow(rowStart);
        CellStyle headerStyle = createHeaderStyle(workbook);

        for (int i = 0; i < columnNames.size(); i++) {
            Cell cell = headerRow.createCell(columnStart + i);
            cell.setCellValue(columnNames.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Write data rows
     *
     * @param sheet Sheet to write to
     * @param data Data to write
     * @param columnNames Column names (order)
     * @param excelFields Field mapping
     * @param rowStart Starting row index
     * @param columnStart Starting column index
     * @param <T> Type of objects to write
     * @throws IllegalAccessException if field access fails
     */
    private <T> void writeDataRows(Sheet sheet, List<T> data, List<String> columnNames,
                                    ConcurrentMap<String, Field> excelFields,
                                    int rowStart, int columnStart) throws IllegalAccessException {
        int currentRow = rowStart;
        for (T item : data) {
            Row row = sheet.createRow(currentRow++);
            writeRowData(row, item, columnNames, excelFields, columnStart);
        }
    }

    /**
     * Write single row data
     *
     * @param row Row to write to
     * @param item Data item
     * @param columnNames Column names
     * @param excelFields Field mapping
     * @param columnStart Starting column index
     * @throws IllegalAccessException if field access fails
     */
    private void writeRowData(Row row, Object item, List<String> columnNames,
                              ConcurrentMap<String, Field> excelFields,
                              int columnStart) throws IllegalAccessException {
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            Field field = excelFields.get(columnName);
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(item);
                Cell cell = row.createCell(columnStart + i);
                setCellValue(cell, value);
            }
        }
    }

    /**
     * Set cell value with type handling
     *
     * @param cell Cell to set value for
     * @param value Value to set
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
     * Create header cell style
     *
     * @param workbook Workbook for style creation
     * @return Header cell style (bold font)
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
