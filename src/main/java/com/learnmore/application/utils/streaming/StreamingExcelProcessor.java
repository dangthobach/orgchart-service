package com.learnmore.application.utils.streaming;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.exception.ValidationException;
import com.learnmore.application.utils.validation.ValidationRule;
import com.learnmore.application.utils.ExcelColumn;

import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * High-performance streaming Excel processor for large datasets
 * Optimized for processing millions of records with memory efficiency
 */
public class StreamingExcelProcessor<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamingExcelProcessor.class);
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final ExecutorService executorService;
    private final Map<String, Field> fieldByColumnName;
    
    // Progress tracking - Using AtomicLong for thread-safe operations
    @Getter
    private final AtomicLong processedRecords = new AtomicLong(0);
    @Getter
    private volatile long totalRecords = 0;
    private volatile boolean isProcessing = false;
    
    // Error tracking
    private final List<ValidationException.ValidationError> validationErrors = 
        Collections.synchronizedList(new ArrayList<>());
    
    // Memory monitoring
    private final Runtime runtime = Runtime.getRuntime();
    @Getter
    private volatile long peakMemoryUsage = 0;
    
    public StreamingExcelProcessor(Class<T> beanClass, ExcelConfig config) {
        this.beanClass = beanClass;
        this.config = config;
        this.executorService = config.isParallelProcessing() 
            ? Executors.newFixedThreadPool(config.getThreadPoolSize())
            : Executors.newSingleThreadExecutor();
        this.fieldByColumnName = buildFieldMapping();
    }
    
    /**
     * Process Excel file with streaming and batch processing
     */
    public ProcessingResult processExcel(InputStream inputStream, Consumer<List<T>> batchProcessor) 
            throws ExcelProcessException {
        
        isProcessing = true;
        processedRecords.set(0);
        validationErrors.clear();
        
        long startTime = System.currentTimeMillis();
        
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            totalRecords = sheet.getLastRowNum();
            
            if (totalRecords == 0) {
                return new ProcessingResult(0, 0, Collections.emptyList(), 0);
            }
            
            // Get header mapping
            Map<Integer, String> columnHeaders = getColumnHeaders(sheet);
            
            // Process in batches
            List<T> currentBatch = new ArrayList<>();
            int batchCount = 0;
            
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                if (config.isEnableMemoryMonitoring()) {
                    monitorMemoryUsage();
                }
                
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                
                try {
                    T bean = processRow(row, columnHeaders, rowIndex);
                    if (bean != null) {
                        currentBatch.add(bean);
                    }
                    
                    processedRecords.incrementAndGet();
                    
                    // Process batch when it reaches the configured size
                    if (currentBatch.size() >= config.getBatchSize()) {
                        processBatch(currentBatch, batchProcessor, batchCount++);
                        currentBatch.clear();
                        
                        // Force garbage collection to free memory
                        if (batchCount % 10 == 0) {
                            System.gc();
                        }
                    }
                    
                    // Report progress
                    if (config.isEnableProgressTracking() && 
                        processedRecords.get() % config.getProgressReportInterval() == 0) {
                        reportProgress();
                    }
                    
                    // Check if we should abort due to too many errors
                    if (validationErrors.size() > config.getMaxErrorsBeforeAbort()) {
                        throw new ValidationException(
                            "Too many validation errors. Aborting processing.", 
                            validationErrors);
                    }
                    
                } catch (Exception e) {
                    if (config.isFailOnFirstError()) {
                        throw new ExcelProcessException("Processing failed at row " + (rowIndex + 1), e, 
                            "PROCESS_ROW", rowIndex, -1, null);
                    } else {
                        logger.warn("Error processing row {}: {}", rowIndex + 1, e.getMessage());
                    }
                }
            }
            
            // Process remaining records in the last batch
            if (!currentBatch.isEmpty()) {
                processBatch(currentBatch, batchProcessor, batchCount);
            }
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            logger.info("Excel processing completed. Processed {} records in {} ms. Errors: {}", 
                processedRecords.get(), processingTime, validationErrors.size());
            
            return new ProcessingResult(processedRecords.get(), validationErrors.size(), 
                validationErrors, processingTime);
            
        } catch (IOException e) {
            throw new ExcelProcessException("Failed to read Excel file", e);
        } finally {
            isProcessing = false;
        }
    }
    
    /**
     * Write data to Excel using streaming for large datasets
     */
    public void writeToExcelStream(List<T> data, OutputStream outputStream) throws ExcelProcessException {
        // Use SXSSF for streaming write operations
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(config.getBatchSize())) {
            workbook.setCompressTempFiles(true); // Compress temporary files
            
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet();
            
            // Write header
            writeHeader(sheet, workbook);
            
            // Write data in batches
            int rowIndex = 1;
            List<String> fieldNames = getFieldNames();
            
            for (int i = 0; i < data.size(); i += config.getBatchSize()) {
                int endIndex = Math.min(i + config.getBatchSize(), data.size());
                List<T> batch = data.subList(i, endIndex);
                
                for (T item : batch) {
                    Row row = sheet.createRow(rowIndex++);
                    writeDataRow(row, item, fieldNames);
                }
                
                // Memory management for large datasets
                if (i % (config.getBatchSize() * 10) == 0) {
                    // SXSSF automatically flushes rows based on window size
                    System.gc(); // Suggest garbage collection for better memory management
                }
                
                if (config.isEnableProgressTracking()) {
                    logger.info("Written {} out of {} records", Math.min(rowIndex - 1, data.size()), data.size());
                }
            }
            
            workbook.write(outputStream);
            workbook.dispose(); // Cleanup temporary files
            
        } catch (IOException e) {
            throw new ExcelProcessException("Failed to write Excel file", e);
        }
    }
    
    private T processRow(Row row, Map<Integer, String> columnHeaders, int rowIndex) {
        try {
            T bean = beanClass.getDeclaredConstructor().newInstance();
            
            for (Map.Entry<Integer, String> entry : columnHeaders.entrySet()) {
                int columnIndex = entry.getKey();
                String columnName = entry.getValue();
                
                Field field = fieldByColumnName.get(columnName);
                if (field == null) continue;
                
                Cell cell = row.getCell(columnIndex);
                String cellValue = getCellValueAsString(cell);
                
                // Validate field
                if (config.isStrictValidation()) {
                    validateField(field.getName(), cellValue, rowIndex, columnIndex);
                }
                
                // Set field value
                setFieldValue(bean, field, cellValue, rowIndex, columnIndex);
            }
            
            return bean;
            
        } catch (Exception e) {
            if (config.isFailOnFirstError()) {
                throw new ExcelProcessException("Failed to process row", e, 
                    "PROCESS_ROW", rowIndex, -1, null);
            } else {
                logger.warn("Failed to process row {}: {}", rowIndex + 1, e.getMessage());
                return null;
            }
        }
    }
    
    private void validateField(String fieldName, String value, int rowIndex, int columnIndex) {
        // Check required fields
        if (config.getRequiredFields().contains(fieldName)) {
            if (value == null || value.trim().isEmpty()) {
                validationErrors.add(new ValidationException.ValidationError(
                    "Required field is empty", fieldName, rowIndex, columnIndex, value, "REQUIRED"));
            }
        }
        
        // Check field-specific validation rules
        ValidationRule rule = config.getFieldValidationRules().get(fieldName);
        if (rule != null) {
            ValidationRule.ValidationResult result = rule.validate(fieldName, value, rowIndex, columnIndex);
            if (!result.isValid()) {
                validationErrors.add(result.getValidationError());
            }
        }
        
        // Apply global validation rules
        for (ValidationRule globalRule : config.getGlobalValidationRules()) {
            ValidationRule.ValidationResult result = globalRule.validate(fieldName, value, rowIndex, columnIndex);
            if (!result.isValid()) {
                validationErrors.add(result.getValidationError());
            }
        }
    }
    
    private void setFieldValue(T bean, Field field, String cellValue, int rowIndex, int columnIndex) 
            throws IllegalAccessException {
        
        field.setAccessible(true);
        
        if (cellValue == null || cellValue.trim().isEmpty()) {
            field.set(bean, null);
            return;
        }
        
        try {
            Object convertedValue = convertToFieldType(cellValue, field.getType());
            field.set(bean, convertedValue);
        } catch (Exception e) {
            if (config.isFailOnFirstError()) {
                throw new ExcelProcessException("Type conversion failed", e, 
                    "TYPE_CONVERSION", rowIndex, columnIndex, cellValue);
            } else {
                logger.warn("Type conversion failed for row {}, column {}, value '{}': {}", 
                    rowIndex + 1, columnIndex + 1, cellValue, e.getMessage());
                field.set(bean, null);
            }
        }
    }
    
    private Object convertToFieldType(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(value);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(value);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(value);
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.parseFloat(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
        }
        // Add more type conversions as needed
        return value;
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }
    
    private Map<String, Field> buildFieldMapping() {
        Map<String, Field> mapping = new HashMap<>();
        
        for (Field field : beanClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ExcelColumn.class)) {
                ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                mapping.put(annotation.name(), field);
            }
        }
        
        return mapping;
    }
    
    private Map<Integer, String> getColumnHeaders(Sheet sheet) {
        Map<Integer, String> headers = new HashMap<>();
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                headers.put(cell.getColumnIndex(), cell.getStringCellValue());
            }
        }
        
        return headers;
    }
    
    private void processBatch(List<T> batch, Consumer<List<T>> batchProcessor, int batchNumber) {
        if (config.isParallelProcessing()) {
            executorService.submit(() -> {
                try {
                    batchProcessor.accept(new ArrayList<>(batch));
                } catch (Exception e) {
                    logger.error("Error processing batch {}: {}", batchNumber, e.getMessage());
                }
            });
        } else {
            batchProcessor.accept(batch);
        }
    }
    
    private void writeHeader(org.apache.poi.ss.usermodel.Sheet sheet, Workbook workbook) {
        Row headerRow = sheet.createRow(0);
        List<String> fieldNames = getFieldNames();
        
        // Create header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        for (int i = 0; i < fieldNames.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(fieldNames.get(i));
            cell.setCellStyle(headerStyle);
        }
    }
    
    private void writeDataRow(Row row, T item, List<String> fieldNames) {
        for (int i = 0; i < fieldNames.size(); i++) {
            Cell cell = row.createCell(i);
            
            try {
                Field field = beanClass.getDeclaredField(fieldNames.get(i));
                field.setAccessible(true);
                Object value = field.get(item);
                
                if (value != null) {
                    if (value instanceof String) {
                        cell.setCellValue((String) value);
                    } else if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to write field {} for row: {}", fieldNames.get(i), e.getMessage());
            }
        }
    }
    
    private List<String> getFieldNames() {
        List<String> fieldNames = new ArrayList<>();
        
        for (Field field : beanClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ExcelColumn.class)) {
                ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                fieldNames.add(annotation.name());
            }
        }
        
        return fieldNames;
    }
    
    private void monitorMemoryUsage() {
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long usedMemoryMB = usedMemory / (1024 * 1024);
        
        if (usedMemoryMB > peakMemoryUsage) {
            peakMemoryUsage = usedMemoryMB;
        }
        
        if (usedMemoryMB > config.getMemoryThresholdMB()) {
            logger.warn("Memory usage is high: {} MB (threshold: {} MB)", 
                usedMemoryMB, config.getMemoryThresholdMB());
            System.gc(); // Suggest garbage collection
        }
    }
    
    private void reportProgress() {
        long currentProcessed = processedRecords.get();
        double progressPercentage = totalRecords > 0 ? (currentProcessed * 100.0 / totalRecords) : 0;
        logger.info("Processing progress: {} / {} records ({}%)", 
            currentProcessed, totalRecords, String.format("%.2f", progressPercentage));
    }
    
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Getters for monitoring (some already generated by Lombok)
    public long getProcessedRecordsValue() {
        return processedRecords.get();
    }
    
    public boolean isProcessing() {
        return isProcessing;
    }
    
    public List<ValidationException.ValidationError> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }
    
    /**
     * Result of processing operation
     */
    @Getter
    public static class ProcessingResult {
        private final long processedRecords;
        private final long errorCount;
        private final List<ValidationException.ValidationError> errors;
        private final long processingTimeMs;
        
        public ProcessingResult(long processedRecords, long errorCount, 
                               List<ValidationException.ValidationError> errors, long processingTimeMs) {
            this.processedRecords = processedRecords;
            this.errorCount = errorCount;
            this.errors = errors;
            this.processingTimeMs = processingTimeMs;
        }
        
        public boolean hasErrors() {
            return errorCount > 0;
        }
        
        @Override
        public String toString() {
            return String.format("ProcessingResult{processed=%d, errors=%d, time=%dms}", 
                processedRecords, errorCount, processingTimeMs);
        }
    }
}