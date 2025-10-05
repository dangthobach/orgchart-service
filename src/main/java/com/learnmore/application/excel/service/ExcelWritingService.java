package com.learnmore.application.excel.service;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.excel.strategy.selector.WriteStrategySelector;
// Removed direct dependency on ExcelWriter port to allow method-level generics
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.config.ExcelConfigFactory;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;

import com.learnmore.application.utils.cache.ReflectionCache;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

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
     * Delegates to ExcelUtil.writeToExcelBytes() - ZERO performance impact.
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
                return writeToBytesSXSSF(data, DEFAULT_CONFIG, 2000);
            } else {
                return writeToBytesXSSF(data, DEFAULT_CONFIG);
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
     * Delegates to ExcelUtil.writeToExcel() - ZERO performance impact.
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
            if (useStreaming) {
                writeToFileSXSSF(fileName, data, rowStart, columnStart, config, Math.min(5000, Math.max(200, config.getFlushInterval())));
            } else {
                writeToFileXSSF(fileName, data, rowStart, columnStart, config);
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
    public <T> void writeWithAutoCSV(String fileName, List<T> data) throws ExcelProcessException {
        log.debug("Writing with auto-CSV: {} records to {}", data.size(), fileName);

        ExcelConfig csvConfig = ExcelConfigFactory.createMigrationConfig();

        // Use selector; CSV strategy should handle conversion
        WriteStrategy<T> strategy = writeStrategySelector.selectStrategy(data.size(), csvConfig);
        strategy.execute(fileName, data, csvConfig);

        log.info("Successfully wrote {} records (auto-CSV enabled)", data.size());
    }

    private <T> byte[] writeToBytesXSSF(List<T> data, ExcelConfig config) throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            int currentRow = 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                writeRowData(row, item, columnNames, excelFields, 0);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private <T> byte[] writeToBytesSXSSF(List<T> data, ExcelConfig config, int windowSize) throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            int currentRow = 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                writeRowData(row, item, columnNames, excelFields, 0);
            }
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        }
    }

    private <T> void writeToFileXSSF(String fileName, List<T> data, int rowStart, int columnStart, ExcelConfig config) throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(fileName)) {
            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            Row headerRow = sheet.createRow(rowStart);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            int currentRow = rowStart + 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                writeRowData(row, item, columnNames, excelFields, columnStart);
            }
            workbook.write(fos);
        }
    }

    private <T> void writeToFileSXSSF(String fileName, List<T> data, int rowStart, int columnStart, ExcelConfig config, int windowSize) throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize); FileOutputStream fos = new FileOutputStream(fileName)) {
            Sheet sheet = workbook.createSheet("Sheet1");
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            Row headerRow = sheet.createRow(rowStart);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            int currentRow = rowStart + 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                writeRowData(row, item, columnNames, excelFields, columnStart);
            }
            workbook.write(fos);
            workbook.dispose();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeRowData(Row row, Object item, List<String> columnNames, ConcurrentMap<String, Field> excelFields, int columnStart) throws IllegalAccessException {
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
}
