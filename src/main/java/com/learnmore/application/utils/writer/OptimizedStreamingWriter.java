package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.writer.cache.CellStyleCache;
import com.learnmore.application.utils.writer.cache.FontCache;
import com.learnmore.application.utils.writer.cache.DataFormatCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance Excel writer using MethodHandle optimization and advanced caching
 * Target: 300K+ records/second (6x improvement over basic reflection)
 */
@Slf4j
public class OptimizedStreamingWriter<T> implements ExcelWriter<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final FieldAccessor[] fieldAccessors;
    private final String[] columnHeaders;
    
    // Caching components
    private final CellStyleCache styleCache;
    private final FontCache fontCache;
    private final DataFormatCache formatCache;
    
    // Performance tracking
    private final AtomicLong recordsWritten = new AtomicLong(0);
    private final AtomicLong totalWriteTime = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private long startTime;
    private long endTime;
    
    // Memory tracking
    private final Runtime runtime = Runtime.getRuntime();
    private long initialMemory;
    private long peakMemory;
    private long finalMemory;
    
    public OptimizedStreamingWriter(Class<T> beanClass, ExcelConfig config) throws ExcelProcessException {
        this.beanClass = beanClass;
        this.config = config;
        this.fieldAccessors = buildFieldAccessors();
        this.columnHeaders = extractColumnHeaders();
        
        // Initialize caches
        this.styleCache = new CellStyleCache(config);
        this.fontCache = new FontCache(config);
        this.formatCache = new DataFormatCache(config);
        
        log.info("OptimizedStreamingWriter initialized for {} with {} fields", 
                beanClass.getSimpleName(), fieldAccessors.length);
    }
    
    @Override
    public WritingResult write(List<T> data, OutputStream outputStream) throws ExcelProcessException {
        startTime = System.currentTimeMillis();
        initialMemory = runtime.totalMemory() - runtime.freeMemory();
        peakMemory = initialMemory;
        
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(config.getFlushInterval())) {
            // Configure workbook for performance
            workbook.setCompressTempFiles(config.isCompressOutput());
            
            Sheet sheet = workbook.createSheet("Data");
            
            // Write headers
            writeHeaders(sheet);
            
            // Write data in batches
            writeBatchedData(sheet, data);
            
            // Auto-size columns if requested (expensive operation)
            if (config.isAutoSizeColumns()) {
                autoSizeColumns(sheet);
            }
            
            // Write to output stream
            workbook.write(outputStream);
            
            endTime = System.currentTimeMillis();
            finalMemory = runtime.totalMemory() - runtime.freeMemory();
            
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
        return "OptimizedStreamingWriter";
    }
    
    // Private implementation methods
    
    private FieldAccessor[] buildFieldAccessors() throws ExcelProcessException {
        Field[] fields = getDeclaredFields();
        List<FieldAccessor> accessors = new ArrayList<>();
        
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        
        for (Field field : fields) {
            if (shouldIncludeField(field)) {
                try {
                    field.setAccessible(true);
                    MethodHandle getter = lookup.unreflectGetter(field);
                    
                    FieldAccessor accessor = new FieldAccessor(
                        field.getName(),
                        field.getType(),
                        getter,
                        getColumnHeader(field),
                        getColumnWidth(field),
                        getDataFormat(field)
                    );
                    
                    accessors.add(accessor);
                    
                } catch (IllegalAccessException e) {
                    log.warn("Cannot access field: {}", field.getName(), e);
                }
            }
        }
        
        if (accessors.isEmpty()) {
            throw new ExcelProcessException("No accessible fields found in class: " + beanClass.getName());
        }
        
        return accessors.toArray(new FieldAccessor[0]);
    }
    
    private Field[] getDeclaredFields() {
        List<Field> allFields = new ArrayList<>();
        Class<?> currentClass = beanClass;
        
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            allFields.addAll(Arrays.asList(fields));
            currentClass = currentClass.getSuperclass();
        }
        
        return allFields.toArray(new Field[0]);
    }
    
    private boolean shouldIncludeField(Field field) {
        // Skip static, transient, and synthetic fields
        int modifiers = field.getModifiers();
        if (java.lang.reflect.Modifier.isStatic(modifiers) || 
            java.lang.reflect.Modifier.isTransient(modifiers) ||
            field.isSynthetic()) {
            return false;
        }
        
        // Check for exclusion annotations (can be extended)
        return !field.isAnnotationPresent(java.beans.Transient.class);
    }
    
    private String getColumnHeader(Field field) {
        // Can be extended to read from annotations
        return field.getName();
    }
    
    private int getColumnWidth(Field field) {
        // Default column width, can be extended with annotations
        return 15;
    }
    
    private String getDataFormat(Field field) {
        Class<?> type = field.getType();
        
        if (type == Date.class || type == LocalDateTime.class) {
            return "yyyy-mm-dd hh:mm:ss";
        } else if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            if (type == Double.class || type == double.class || 
                type == Float.class || type == float.class) {
                return "#,##0.00";
            } else {
                return "#,##0";
            }
        }
        
        return "General";
    }
    
    private String[] extractColumnHeaders() {
        return Arrays.stream(fieldAccessors)
                .map(FieldAccessor::getHeaderName)
                .toArray(String[]::new);
    }
    
    private void writeHeaders(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = styleCache.getHeaderStyle(sheet.getWorkbook());
        
        for (int i = 0; i < columnHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnHeaders[i]);
            cell.setCellStyle(headerStyle);
            
            // Set column width
            sheet.setColumnWidth(i, fieldAccessors[i].getColumnWidth() * 256);
        }
    }
    
    private void writeBatchedData(Sheet sheet, List<T> data) throws ExcelProcessException {
        int batchSize = config.getBatchSize();
        int totalBatches = (data.size() + batchSize - 1) / batchSize;
        
        log.info("Writing {} records in {} batches of size {}", data.size(), totalBatches, batchSize);
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, data.size());
            
            List<T> batch = data.subList(startIndex, endIndex);
            writeBatch(sheet, batch, startIndex + 1); // +1 for header row
            
            // Update peak memory usage
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            peakMemory = Math.max(peakMemory, currentMemory);
            
            // Progress reporting
            if (config.isEnableProgressTracking() && 
                (batchIndex + 1) % (config.getProgressReportInterval() / batchSize) == 0) {
                log.info("Progress: {}/{} batches completed", batchIndex + 1, totalBatches);
            }
        }
    }
    
    private void writeBatch(Sheet sheet, List<T> batch, int startRowIndex) throws ExcelProcessException {
        long batchStartTime = System.nanoTime();
        
        for (int i = 0; i < batch.size(); i++) {
            T record = batch.get(i);
            Row row = sheet.createRow(startRowIndex + i);
            
            writeRecord(row, record);
            recordsWritten.incrementAndGet();
        }
        
        long batchTime = System.nanoTime() - batchStartTime;
        totalWriteTime.addAndGet(batchTime);
    }
    
    private void writeRecord(Row row, T record) throws ExcelProcessException {
        for (int i = 0; i < fieldAccessors.length; i++) {
            FieldAccessor accessor = fieldAccessors[i];
            Cell cell = row.createCell(i);
            
            try {
                Object value = accessor.getValue(record);
                setCellValue(cell, value, accessor);
                
            } catch (Throwable e) {
                throw new ExcelProcessException(
                    String.format("Failed to write field %s for record at row %d", 
                                accessor.getFieldName(), row.getRowNum()), e);
            }
        }
    }
    
    private void setCellValue(Cell cell, Object value, FieldAccessor accessor) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        
        Class<?> type = accessor.getFieldType();
        
        // Set value based on type
        if (type == String.class) {
            cell.setCellValue((String) value);
        } else if (type == Integer.class || type == int.class) {
            cell.setCellValue((Integer) value);
        } else if (type == Long.class || type == long.class) {
            cell.setCellValue((Long) value);
        } else if (type == Double.class || type == double.class) {
            cell.setCellValue((Double) value);
        } else if (type == Float.class || type == float.class) {
            cell.setCellValue((Float) value);
        } else if (type == Boolean.class || type == boolean.class) {
            cell.setCellValue((Boolean) value);
        } else if (type == Date.class) {
            cell.setCellValue((Date) value);
        } else if (type == LocalDateTime.class) {
            cell.setCellValue(((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            cell.setCellValue(value.toString());
        }
        
        // Apply cell style and format
        applyCellStyle(cell, accessor);
    }
    
    private void applyCellStyle(Cell cell, FieldAccessor accessor) {
        String formatKey = accessor.getDataFormat();
        CellStyle style = styleCache.getDataStyle(cell.getSheet().getWorkbook(), formatKey);
        
        if (style != null) {
            cell.setCellStyle(style);
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
    }
    
    private void autoSizeColumns(Sheet sheet) {
        log.info("Auto-sizing {} columns (this may take time for large datasets)", columnHeaders.length);
        
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
        result.setTotalCellsWritten(recordsWritten.get() * fieldAccessors.length);
        result.setProcessingTimeMs(duration);
        result.setPeakMemoryUsageMB((peakMemory - initialMemory) / (1024 * 1024));
        result.setSuccess(true);
        
        return result;
    }
    

    

    
    // Inner class for field access optimization
    private static class FieldAccessor {
        private final String fieldName;
        private final Class<?> fieldType;
        private final MethodHandle getter;
        private final String headerName;
        private final int columnWidth;
        private final String dataFormat;
        
        public FieldAccessor(String fieldName, Class<?> fieldType, MethodHandle getter, 
                           String headerName, int columnWidth, String dataFormat) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.getter = getter;
            this.headerName = headerName;
            this.columnWidth = columnWidth;
            this.dataFormat = dataFormat;
        }
        
        public Object getValue(Object instance) throws Throwable {
            return getter.invoke(instance);
        }
        
        // Getters
        public String getFieldName() { return fieldName; }
        public Class<?> getFieldType() { return fieldType; }
        public String getHeaderName() { return headerName; }
        public int getColumnWidth() { return columnWidth; }
        public String getDataFormat() { return dataFormat; }
    }
}