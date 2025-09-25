package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming SXSSF Excel writer for medium datasets
 * Best for 10K-100K records with moderate memory usage
 */
@Slf4j
public class StreamingSXSSFWriter<T> implements ExcelWriter<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final Field[] fields;
    private final String[] columnHeaders;
    
    // Performance tracking
    private final AtomicLong recordsWritten = new AtomicLong(0);
    private long startTime;
    private long endTime;
    
    public StreamingSXSSFWriter(Class<T> beanClass, ExcelConfig config) throws ExcelProcessException {
        this.beanClass = beanClass;
        this.config = config;
        this.fields = getAccessibleFields();
        this.columnHeaders = extractColumnHeaders();
        
        log.info("StreamingSXSSFWriter initialized for {} with {} fields", 
                beanClass.getSimpleName(), fields.length);
    }
    
    @Override
    public WritingResult write(List<T> data, OutputStream outputStream) throws ExcelProcessException {
        startTime = System.currentTimeMillis();
        
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(config.getFlushInterval())) {
            workbook.setCompressTempFiles(config.isCompressOutput());
            
            Sheet sheet = workbook.createSheet("Data");
            
            // Write headers
            createHeaderRow(sheet, workbook);
            
            // Write data
            writeDataRows(sheet, data, workbook);
            
            // Auto-size columns if requested
            if (config.isAutoSizeColumns()) {
                autoSizeColumns(sheet);
            }
            
            // Write to output stream
            workbook.write(outputStream);
            
            endTime = System.currentTimeMillis();
            
            return buildWritingResult();
            
        } catch (IOException e) {
            throw new ExcelProcessException("Failed to write Excel data", e);
        }
    }
    
    @Override
    public WritingResult getStatistics() {
        return buildWritingResult();
    }
    
    @Override
    public String getStrategyName() {
        return "StreamingSXSSFWriter";
    }
    
    // Private implementation methods
    
    private Field[] getAccessibleFields() throws ExcelProcessException {
        List<Field> accessibleFields = new ArrayList<>();
        
        for (Field field : beanClass.getDeclaredFields()) {
            if (shouldIncludeField(field)) {
                field.setAccessible(true);
                accessibleFields.add(field);
            }
        }
        
        if (accessibleFields.isEmpty()) {
            throw new ExcelProcessException("No accessible fields found in class: " + beanClass.getName());
        }
        
        return accessibleFields.toArray(new Field[0]);
    }
    
    private boolean shouldIncludeField(Field field) {
        int modifiers = field.getModifiers();
        return !(java.lang.reflect.Modifier.isStatic(modifiers) || 
                java.lang.reflect.Modifier.isTransient(modifiers) ||
                field.isSynthetic());
    }
    
    private String[] extractColumnHeaders() {
        return Arrays.stream(fields)
                .map(Field::getName)
                .toArray(String[]::new);
    }
    
    private void createHeaderRow(Sheet sheet, Workbook workbook) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        for (int i = 0; i < columnHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
    }
    
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Background color
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Font
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        
        // Borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    private void writeDataRows(Sheet sheet, List<T> data, Workbook workbook) throws ExcelProcessException {
        for (int i = 0; i < data.size(); i++) {
            T record = data.get(i);
            Row row = sheet.createRow(i + 1); // +1 for header row
            
            writeRecord(row, record);
            recordsWritten.incrementAndGet();
            
            // Progress reporting
            if (config.isEnableProgressTracking() && 
                (i + 1) % config.getProgressReportInterval() == 0) {
                log.info("Progress: {}/{} records written", i + 1, data.size());
            }
        }
    }
    
    private void writeRecord(Row row, T record) throws ExcelProcessException {
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            Cell cell = row.createCell(i);
            
            try {
                Object value = field.get(record);
                setCellValue(cell, value, field.getType());
                
            } catch (IllegalAccessException e) {
                throw new ExcelProcessException(
                    String.format("Failed to access field %s for record at row %d", 
                                field.getName(), row.getRowNum()), e);
            }
        }
    }
    
    private void setCellValue(Cell cell, Object value, Class<?> fieldType) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        
        // Set value based on type
        if (fieldType == String.class) {
            cell.setCellValue((String) value);
        } else if (fieldType == Integer.class || fieldType == int.class) {
            cell.setCellValue((Integer) value);
        } else if (fieldType == Long.class || fieldType == long.class) {
            cell.setCellValue((Long) value);
        } else if (fieldType == Double.class || fieldType == double.class) {
            cell.setCellValue((Double) value);
        } else if (fieldType == Float.class || fieldType == float.class) {
            cell.setCellValue((Float) value);
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            cell.setCellValue((Boolean) value);
        } else if (fieldType == Date.class) {
            cell.setCellValue((Date) value);
        } else if (fieldType == LocalDateTime.class) {
            cell.setCellValue(((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            cell.setCellValue(value.toString());
        }
    }
    
    private void autoSizeColumns(Sheet sheet) {
        log.info("Auto-sizing {} columns", columnHeaders.length);
        
        // For SXSSF sheets, need to track columns first
        if (sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet) {
            org.apache.poi.xssf.streaming.SXSSFSheet sxssfSheet = (org.apache.poi.xssf.streaming.SXSSFSheet) sheet;
            sxssfSheet.trackAllColumnsForAutoSizing();
        }
        
        for (int i = 0; i < columnHeaders.length; i++) {
            try {
                sheet.autoSizeColumn(i);
            } catch (Exception e) {
                log.warn("Failed to auto-size column {}: {}", i, e.getMessage());
                // Set a default width if auto-sizing fails
                sheet.setColumnWidth(i, 15 * 256); // 15 characters width
            }
        }
    }
    
    private WritingResult buildWritingResult() {
        long duration = endTime > startTime ? endTime - startTime : 0;
        
        WritingResult result = new WritingResult(getStrategyName());
        result.setTotalRowsWritten(recordsWritten.get());
        result.setTotalCellsWritten(recordsWritten.get() * fields.length);
        result.setProcessingTimeMs(duration);
        result.setSuccess(true);
        
        return result;
    }
}