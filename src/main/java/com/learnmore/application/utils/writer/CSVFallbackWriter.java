package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CSV fallback writer for ultra-fast data-only export
 * Best for scenarios where formatting is not required
 */
@Slf4j
public class CSVFallbackWriter<T> implements ExcelWriter<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final Field[] fields;
    private final String[] columnHeaders;
    
    // Performance tracking
    private final AtomicLong recordsWritten = new AtomicLong(0);
    private long startTime;
    private long endTime;
    
    public CSVFallbackWriter(Class<T> beanClass, ExcelConfig config) throws ExcelProcessException {
        this.beanClass = beanClass;
        this.config = config;
        this.fields = getAccessibleFields();
        this.columnHeaders = extractColumnHeaders();
        
        log.info("CSVFallbackWriter initialized for {} with {} fields", 
                beanClass.getSimpleName(), fields.length);
    }
    
    @Override
    public WritingResult write(List<T> data, OutputStream outputStream) throws ExcelProcessException {
        startTime = System.currentTimeMillis();
        
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            
            // Write headers
            writeHeaders(writer);
            
            // Write data
            writeDataRows(writer, data);
            
            writer.flush();
            
            endTime = System.currentTimeMillis();
            
            return buildWritingResult();
            
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to write CSV data", e);
        }
    }
    
    @Override
    public WritingResult getStatistics() {
        return buildWritingResult();
    }
    
    @Override
    public String getStrategyName() {
        return "CSVFallbackWriter";
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
    
    private void writeHeaders(PrintWriter writer) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < columnHeaders.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(escapeCSVValue(columnHeaders[i]));
        }
        
        writer.println(sb.toString());
    }
    
    private void writeDataRows(PrintWriter writer, List<T> data) throws ExcelProcessException {
        for (int i = 0; i < data.size(); i++) {
            T record = data.get(i);
            writeRecord(writer, record);
            recordsWritten.incrementAndGet();
            
            // Progress reporting
            if (config.isEnableProgressTracking() && 
                (i + 1) % config.getProgressReportInterval() == 0) {
                log.info("Progress: {}/{} records written", i + 1, data.size());
            }
        }
    }
    
    private void writeRecord(PrintWriter writer, T record) throws ExcelProcessException {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            
            try {
                if (i > 0) sb.append(",");
                
                Object value = field.get(record);
                String csvValue = formatValue(value, field.getType());
                sb.append(escapeCSVValue(csvValue));
                
            } catch (IllegalAccessException e) {
                throw new ExcelProcessException(
                    String.format("Failed to access field %s for record", field.getName()), e);
            }
        }
        
        writer.println(sb.toString());
    }
    
    private String formatValue(Object value, Class<?> fieldType) {
        if (value == null) {
            return "";
        }
        
        if (fieldType == LocalDateTime.class) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else if (fieldType == Date.class) {
            return value.toString();
        } else {
            return value.toString();
        }
    }
    
    private String escapeCSVValue(String value) {
        if (value == null) return "";
        
        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
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