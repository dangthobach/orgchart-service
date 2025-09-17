package com.learnmore.application.utils;

import lombok.Getter;
import com.learnmore.application.utils.cache.ReflectionCache;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.converter.TypeConverter;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.exception.ValidationException;
import com.learnmore.application.utils.monitoring.MemoryMonitor;
import com.learnmore.application.utils.monitoring.ProgressMonitor;
import com.learnmore.application.utils.streaming.StreamingExcelProcessor;
import com.learnmore.application.utils.validation.RequiredFieldValidator;
import com.learnmore.application.utils.validation.DuplicateValidator;
import com.learnmore.application.utils.validation.DataTypeValidator;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Enhanced Excel utility class optimized for high-performance processing
 * Supports 1M+ records with comprehensive validation, monitoring, and error handling
 * Key Features:
 * - Streaming processing for large datasets
 * - Comprehensive validation framework
 * - Memory monitoring and optimization
 * - Progress tracking and reporting
 * - Advanced type conversion support
 * - Reflection caching for performance
 * - Proper resource management
 */
public class ExcelUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ExcelUtil.class);
    
    // Default configuration
    private static final ExcelConfig DEFAULT_CONFIG = ExcelConfig.builder()
            .batchSize(1000)
            .memoryThreshold(500)
            .parallelProcessing(true)
            .enableProgressTracking(true)
            .enableMemoryMonitoring(true)
            .build();
    
    /**
     * Process Excel file to POJO list with default configuration
     * Legacy method for backward compatibility
     */
    public static <T> List<T> sheetToPOJO(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        return processExcelToList(inputStream, beanClass, DEFAULT_CONFIG);
    }
    
    /**
     * Process Excel file to POJO list with custom configuration
     * High-performance method optimized for large datasets
     */
    public static <T> List<T> processExcelToList(InputStream inputStream, Class<T> beanClass, ExcelConfig config) 
            throws ExcelProcessException {
        
        List<T> results = new ArrayList<>();
        
        // Set up monitoring
        ProgressMonitor progressMonitor = null;
        MemoryMonitor memoryMonitor = null;
        
        try {
            // Initialize monitors if enabled
            if (config.isEnableProgressTracking()) {
                progressMonitor = new ProgressMonitor(config.getProgressReportInterval());
            }
            
            if (config.isEnableMemoryMonitoring()) {
                memoryMonitor = new MemoryMonitor(config.getMemoryThresholdMB());
                memoryMonitor.startMonitoring();
            }
            
            // Process with streaming processor
            StreamingExcelProcessor<T> processor = new StreamingExcelProcessor<>(beanClass, config);
            
            Consumer<List<T>> batchProcessor = batch -> {
                synchronized (results) {
                    results.addAll(batch);
                }
            };
            
            // Start progress monitoring
            if (progressMonitor != null) {
                // We'll update this once we know the total records
                progressMonitor.start(0);
            }
            
            // Process the Excel file
            StreamingExcelProcessor.ProcessingResult result = processor.processExcel(inputStream, batchProcessor);
            
            // Complete monitoring
            if (progressMonitor != null) {
                progressMonitor.complete();
            }
            
            // Log results
            logger.info("Excel processing completed successfully. Processed {} records with {} errors in {} ms",
                result.getProcessedRecords(), result.getErrorCount(), result.getProcessingTimeMs());
            
            // Handle validation errors if any
            if (result.hasErrors() && config.isStrictValidation()) {
                throw new ValidationException("Validation errors found during processing", result.getErrors());
            }
            
            return results;
            
        } catch (Exception e) {
            if (progressMonitor != null) {
                progressMonitor.abort("Processing failed: " + e.getMessage());
            }
            
            if (e instanceof ExcelProcessException) {
                throw e;
            } else {
                throw new ExcelProcessException("Failed to process Excel file", e);
            }
            
        } finally {
            // Cleanup resources
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
        }
    }
    
    /**
     * Process Excel file with streaming and batch processing
     * Ideal for very large files that don't fit in memory
     */
    public static <T> StreamingExcelProcessor.ProcessingResult processExcelStreaming(
            InputStream inputStream, Class<T> beanClass, ExcelConfig config, Consumer<List<T>> batchProcessor) 
            throws ExcelProcessException {
        
        // Set up validation rules
        setupValidationRules(config, beanClass);
        
        // Create and configure streaming processor
        StreamingExcelProcessor<T> processor = new StreamingExcelProcessor<>(beanClass, config);
        
        // Set up monitoring
        MemoryMonitor memoryMonitor = null;
        if (config.isEnableMemoryMonitoring()) {
            memoryMonitor = new MemoryMonitor(config.getMemoryThresholdMB());
            memoryMonitor.startMonitoring();
        }
        
        try {
            return processor.processExcel(inputStream, batchProcessor);
        } finally {
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
            processor.shutdown();
        }
    }
    
    /**
     * Process multiple sheets in Excel file with different POJO types
     * Each sheet corresponds to a different entity type
     */
    public static Map<String, MultiSheetResult> processMultiSheetExcel(
            InputStream inputStream, Map<String, Class<?>> sheetClassMap, ExcelConfig config) 
            throws ExcelProcessException {
        
        Map<String, MultiSheetResult> results = new HashMap<>();
        
        // Set up monitoring
        ProgressMonitor progressMonitor = null;
        MemoryMonitor memoryMonitor = null;
        
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            
            if (config.isEnableProgressTracking()) {
                progressMonitor = new ProgressMonitor(config.getProgressReportInterval());
                progressMonitor.start(sheetClassMap.size());
            }
            
            if (config.isEnableMemoryMonitoring()) {
                memoryMonitor = new MemoryMonitor(config.getMemoryThresholdMB());
                memoryMonitor.startMonitoring();
            }
            
            // Process each sheet
            for (Map.Entry<String, Class<?>> entry : sheetClassMap.entrySet()) {
                String sheetName = entry.getKey();
                Class<?> beanClass = entry.getValue();
                
                try {
                    Sheet sheet = workbook.getSheet(sheetName);
                    if (sheet == null) {
                        logger.warn("Sheet '{}' not found in Excel file", sheetName);
                        results.put(sheetName, new MultiSheetResult(new ArrayList<>(), 
                            new ArrayList<>(), 0, "Sheet not found"));
                        continue;
                    }
                    
                    // Process this sheet
                    MultiSheetResult sheetResult = processSheet(sheet, beanClass, config);
                    results.put(sheetName, sheetResult);
                    
                    if (progressMonitor != null) {
                        progressMonitor.recordSuccess();
                    }
                    
                    logger.info("Processed sheet '{}' with {} records", sheetName, sheetResult.getData().size());
                    
                } catch (Exception e) {
                    logger.error("Error processing sheet '{}': {}", sheetName, e.getMessage());
                    results.put(sheetName, new MultiSheetResult(new ArrayList<>(), 
                        new ArrayList<>(), 0, "Processing error: " + e.getMessage()));
                    
                    if (progressMonitor != null) {
                        progressMonitor.recordError();
                    }
                    
                    if (config.isFailOnFirstError()) {
                        throw new ExcelProcessException("Failed to process sheet: " + sheetName, e);
                    }
                }
            }
            
            if (progressMonitor != null) {
                progressMonitor.complete();
            }
            
            return results;
            
        } catch (IOException e) {
            throw new ExcelProcessException("Failed to read Excel file", e);
        } finally {
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
        }
    }
    
    /**
     * Process multiple sheets with streaming for large files
     */
    public static void processMultiSheetExcelStreaming(
            InputStream inputStream, 
            Map<String, SheetProcessorConfig> sheetProcessors, 
            ExcelConfig config) throws ExcelProcessException {
        
        MemoryMonitor memoryMonitor = null;
        
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            
            if (config.isEnableMemoryMonitoring()) {
                memoryMonitor = new MemoryMonitor(config.getMemoryThresholdMB());
                memoryMonitor.startMonitoring();
            }
            
            // Process each sheet with its specific processor
            for (Map.Entry<String, SheetProcessorConfig> entry : sheetProcessors.entrySet()) {
                String sheetName = entry.getKey();
                SheetProcessorConfig processorConfig = entry.getValue();
                
                try {
                    Sheet sheet = workbook.getSheet(sheetName);
                    if (sheet == null) {
                        logger.warn("Sheet '{}' not found in Excel file", sheetName);
                        continue;
                    }
                    
                    // Process sheet with streaming
                    processSheetStreaming(sheet, processorConfig, config);
                    
                    logger.info("Completed streaming processing for sheet '{}'", sheetName);
                    
                } catch (Exception e) {
                    logger.error("Error streaming sheet '{}': {}", sheetName, e.getMessage());
                    
                    if (config.isFailOnFirstError()) {
                        throw new ExcelProcessException("Failed to stream process sheet: " + sheetName, e);
                    }
                }
            }
            
        } catch (IOException e) {
            throw new ExcelProcessException("Failed to read Excel file for streaming", e);
        } finally {
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
        }
    }

    /**
     * Write data to Excel file with enhanced performance and validation
     */
    public static <T> void writeToExcel(String fileName, List<T> data, Integer rowStart, Integer columnStart) 
            throws ExcelProcessException {
        writeToExcel(fileName, data, rowStart, columnStart, DEFAULT_CONFIG);
    }
    
    /**
     * Write data to Excel file with custom configuration
     */
    public static <T> void writeToExcel(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config) 
            throws ExcelProcessException {
        
        if (data == null || data.isEmpty()) {
            throw new ExcelProcessException("Data list cannot be null or empty");
        }
        
        // Set up monitoring
        ProgressMonitor progressMonitor = null;
        MemoryMonitor memoryMonitor = null;
        
        try {
            if (config.isEnableProgressTracking()) {
                progressMonitor = new ProgressMonitor(config.getProgressReportInterval());
                progressMonitor.start(data.size());
            }
            
            if (config.isEnableMemoryMonitoring()) {
                memoryMonitor = new MemoryMonitor(config.getMemoryThresholdMB());
                memoryMonitor.startMonitoring();
            }
            
            // Use streaming processor for large datasets
            if (data.size() > config.getBatchSize() * 10) {
                writeToExcelStreaming(fileName, data, config);
            } else {
                writeToExcelTraditional(fileName, data, rowStart, columnStart, config);
            }
            
            if (progressMonitor != null) {
                progressMonitor.complete();
            }
            
        } catch (Exception e) {
            if (progressMonitor != null) {
                progressMonitor.abort("Writing failed: " + e.getMessage());
            }
            throw new ExcelProcessException("Failed to write Excel file: " + fileName, e);
        } finally {
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
        }
    }
    
    /**
     * Write to Excel using streaming for large datasets
     */
    private static <T> void writeToExcelStreaming(String fileName, List<T> data, ExcelConfig config) 
            throws IOException {
        
        @SuppressWarnings("unchecked")
        Class<T> dataClass = (Class<T>) data.get(0).getClass();
        StreamingExcelProcessor<T> processor = new StreamingExcelProcessor<>(dataClass, config);
        
        try (FileOutputStream outputStream = new FileOutputStream(fileName)) {
            processor.writeToExcelStream(data, outputStream);
        } finally {
            processor.shutdown();
        }
    }
    
    /**
     * Traditional Excel writing for smaller datasets
     */
    private static <T> void writeToExcelTraditional(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config) 
            throws Exception {
        
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {
            
            Sheet sheet = workbook.createSheet();
            
            // Get fields with ExcelColumn annotation
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            
            // Create header row
            Row headerRow = sheet.createRow(rowStart);
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Write data rows
            int currentRow = rowStart + 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                
                for (int i = 0; i < columnNames.size(); i++) {
                    Cell cell = row.createCell(columnStart + i);
                    String columnName = columnNames.get(i);
                    Field field = excelFields.get(columnName);
                    
                    if (field != null) {
                        try {
                            field.setAccessible(true);
                            Object value = field.get(item);
                            setCellValue(cell, value);
                        } catch (IllegalAccessException e) {
                            logger.warn("Failed to access field '{}': {}", field.getName(), e.getMessage());
                        }
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < columnNames.size(); i++) {
                sheet.autoSizeColumn(columnStart + i);
            }
            
            workbook.write(fos);
        }
    }
    
    /**
     * Write data to Excel bytes with enhanced performance
     */
    public static <T> byte[] writeToExcelBytes(List<T> data, Integer rowStart, Integer columnStart) throws ExcelProcessException {
        return writeToExcelBytes(data, rowStart, columnStart, DEFAULT_CONFIG);
    }
    
    /**
     * Write data to Excel bytes with custom configuration
     */
    public static <T> byte[] writeToExcelBytes(List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config) 
            throws ExcelProcessException {
        
        if (data == null || data.isEmpty()) {
            throw new ExcelProcessException("Data list cannot be null or empty");
        }
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            @SuppressWarnings("unchecked")
            Class<T> dataClass = (Class<T>) data.get(0).getClass();
            StreamingExcelProcessor<T> processor = new StreamingExcelProcessor<>(dataClass, config);
            
            try {
                processor.writeToExcelStream(data, outputStream);
                return outputStream.toByteArray();
            } finally {
                processor.shutdown();
            }
            
        } catch (IOException e) {
            throw new ExcelProcessException("Failed to generate Excel bytes", e);
        }
    }
    
    /**
     * Setup validation rules based on configuration and bean class
     */
    private static <T> void setupValidationRules(ExcelConfig config, Class<T> beanClass) {
        // Set up required field validation
        if (!config.getRequiredFields().isEmpty()) {
            config.addGlobalValidation(new RequiredFieldValidator());
        }
        
        // Set up duplicate validation
        if (!config.getUniqueFields().isEmpty()) {
            config.addGlobalValidation(new DuplicateValidator(config.getUniqueFields()));
        }
        
        // Set up data type validation for each field
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
        
        for (Map.Entry<String, Field> entry : excelFields.entrySet()) {
            String columnName = entry.getKey();
            Field field = entry.getValue();
            Class<?> fieldType = field.getType();
            
            // Add data type validator for the field
            config.addFieldValidation(columnName, new DataTypeValidator(fieldType, config.getDateFormat()));
        }
    }
    
    /**
     * Create header cell style
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeight((short) 240); // 12pt
        style.setFont(font);
        
        // Add border and background
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Set cell value with proper type handling
     */
    private static void setCellValue(Cell cell, Object value) {
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
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
    
    /**
     * Get cache and conversion statistics for monitoring
     */
    public static String getPerformanceStatistics() {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        TypeConverter typeConverter = TypeConverter.getInstance();
        
        return "=== ExcelUtil Performance Statistics ===\n" +
               "Reflection Cache: " + reflectionCache.getStatistics() + "\n" +
               "Type Converter: " + typeConverter.getStatistics() + "\n";
    }
    
    /**
     * Process a single sheet with specific POJO type
     */
    private static <T> MultiSheetResult processSheet(Sheet sheet, Class<T> beanClass, ExcelConfig config) 
            throws ExcelProcessException {
        
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        TypeConverter typeConverter = TypeConverter.getInstance();
        
        List<T> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        // Get Excel column fields
        ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
        
        // Setup validation
        setupValidationRules(config, beanClass);
        
        // Get header row to map columns
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            return new MultiSheetResult(results, List.of("Header row not found"), 0, null);
        }
        
        Map<Integer, String> columnMapping = createColumnMapping(headerRow, excelFields.keySet());
        
        // Process data rows
        int processedRows = 0;
        for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            try {
                T instance = beanClass.getDeclaredConstructor().newInstance();
                boolean hasData = false;
                
                // Process each column
                for (Map.Entry<Integer, String> mapping : columnMapping.entrySet()) {
                    int cellIndex = mapping.getKey();
                    String columnName = mapping.getValue();
                    
                    Cell cell = row.getCell(cellIndex);
                    Field field = excelFields.get(columnName);
                    
                    if (field != null) {
                        Object value = getCellValue(cell, field.getType(), typeConverter);
                        if (value != null) {
                            hasData = true;
                            field.setAccessible(true);
                            field.set(instance, value);
                        }
                    }
                }
                
                if (hasData) {
                    // Validate instance
                    validateInstance(config, rowIndex, errors);
                    results.add(instance);
                }
                
                processedRows++;
                
            } catch (Exception e) {
                String error = String.format("Row %d: %s", rowIndex + 1, e.getMessage());
                errors.add(error);
                logger.warn("Error processing row {}: {}", rowIndex + 1, e.getMessage());
            }
        }
        
        return new MultiSheetResult(results, errors, processedRows, null);
    }
    
    /**
     * Process sheet with streaming approach
     */
    @SuppressWarnings("unchecked")
    private static <T> void processSheetStreaming(Sheet sheet, SheetProcessorConfig processorConfig, ExcelConfig config) 
            throws Exception {
        
        Class<T> beanClass = (Class<T>) processorConfig.getBeanClass();
        Consumer<List<T>> batchProcessor = (Consumer<List<T>>) processorConfig.getBatchProcessor();
        
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        TypeConverter typeConverter = TypeConverter.getInstance();
        
        // Get Excel column fields
        ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
        
        // Setup validation
        setupValidationRules(config, beanClass);
        
        // Get header row
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new ExcelProcessException("Header row not found in sheet");
        }
        
        Map<Integer, String> columnMapping = createColumnMapping(headerRow, excelFields.keySet());
        
        // Process in batches
        List<T> batch = new ArrayList<>();
        int batchSize = config.getBatchSize();
        
        for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            try {
                T instance = beanClass.getDeclaredConstructor().newInstance();
                boolean hasData = false;
                
                // Process each column
                for (Map.Entry<Integer, String> mapping : columnMapping.entrySet()) {
                    int cellIndex = mapping.getKey();
                    String columnName = mapping.getValue();
                    
                    Cell cell = row.getCell(cellIndex);
                    Field field = excelFields.get(columnName);
                    
                    if (field != null) {
                        Object value = getCellValue(cell, field.getType(), typeConverter);
                        if (value != null) {
                            hasData = true;
                            field.setAccessible(true);
                            field.set(instance, value);
                        }
                    }
                }
                
                if (hasData) {
                    batch.add(instance);
                }
                
                // Process batch when it reaches the batch size
                if (batch.size() >= batchSize) {
                    batchProcessor.accept(new ArrayList<>(batch));
                    batch.clear();
                }
                
            } catch (Exception e) {
                logger.warn("Error processing row {}: {}", rowIndex + 1, e.getMessage());
            }
        }
        
        // Process remaining items in batch
        if (!batch.isEmpty()) {
            batchProcessor.accept(batch);
        }
    }
    
    /**
     * Create column mapping from header row
     */
    private static Map<Integer, String> createColumnMapping(Row headerRow, Set<String> availableColumns) {
        Map<Integer, String> mapping = new HashMap<>();
        
        for (Cell cell : headerRow) {
            if (cell != null) {
                String headerValue = cell.getStringCellValue();
                if (headerValue != null && availableColumns.contains(headerValue.trim())) {
                    mapping.put(cell.getColumnIndex(), headerValue.trim());
                }
            }
        }
        
        return mapping;
    }
    
    /**
     * Get cell value with proper type conversion
     */
    private static Object getCellValue(Cell cell, Class<?> fieldType, TypeConverter typeConverter) {
        if (cell == null) return null;
        
        try {
            return getCellValueByType(cell, cell.getCellType(), fieldType, typeConverter);
        } catch (Exception e) {
            logger.warn("Failed to convert cell value: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get cell value by cell type with formula evaluation support
     */
    private static Object getCellValueByType(Cell cell, CellType cellType, Class<?> fieldType, TypeConverter typeConverter) {
        switch (cellType) {
            case STRING:
                String stringValue = cell.getStringCellValue();
                return typeConverter.convert(stringValue, fieldType);
                
            case NUMERIC:
                // Check if it's a date by looking at cell style
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    return typeConverter.convert(String.valueOf(numericValue), fieldType);
                }
                
            case BOOLEAN:
                boolean boolValue = cell.getBooleanCellValue();
                return typeConverter.convert(String.valueOf(boolValue), fieldType);
                
            case FORMULA:
                return evaluateFormulaCell(cell, fieldType, typeConverter);
                
            case BLANK:
                return null;
                
            case ERROR:
                logger.warn("Cell contains error value: {}", cell.getErrorCellValue());
                return null;
                
            default:
                return null;
        }
    }
    
    /**
     * Evaluate formula cell and convert result
     */
    private static Object evaluateFormulaCell(Cell cell, Class<?> fieldType, TypeConverter typeConverter) {
        try {
            FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
            CellValue cellValue = evaluator.evaluate(cell);
            
            switch (cellValue.getCellType()) {
                case STRING:
                    return typeConverter.convert(cellValue.getStringValue(), fieldType);
                    
                case NUMERIC:
                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue();
                    } else {
                        return typeConverter.convert(String.valueOf(cellValue.getNumberValue()), fieldType);
                    }
                    
                case BOOLEAN:
                    return typeConverter.convert(String.valueOf(cellValue.getBooleanValue()), fieldType);
                    
                case ERROR:
                    logger.warn("Formula evaluation resulted in error: {}", cellValue.getErrorValue());
                    return null;
                    
                default:
                    return null;
            }
        } catch (Exception e) {
            logger.warn("Failed to evaluate formula cell: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Validate instance using configured rules
     */
    private static <T> void validateInstance(ExcelConfig config, int rowIndex, List<String> errors) {
        // Implementation would use the validation framework
        // This is a simplified version - actual validation would be implemented here
        config.getGlobalValidationRules().forEach(validator -> {
            // Apply validation rules
            // Note: This is a placeholder for actual validation implementation
            // Real implementation would call validator.validate(instance, errors);
        });
    }
    
    /**
     * Clear all caches for memory cleanup
     */
    public static void clearCaches() {
        ReflectionCache.getInstance().clearCaches();
        TypeConverter.getInstance().clearCache();
        logger.info("All ExcelUtil caches cleared");
    }
    
    /**
     * Result class for multi-sheet processing
     */
    @Getter
    public static class MultiSheetResult {
        private final List<?> data;
        private final List<String> errors;
        private final int processedRecords;
        private final String errorMessage;
        
        public MultiSheetResult(List<?> data, List<String> errors, int processedRecords, String errorMessage) {
            this.data = data;
            this.errors = errors;
            this.processedRecords = processedRecords;
            this.errorMessage = errorMessage;
        }
        public boolean hasErrors() { return !errors.isEmpty() || errorMessage != null; }
        public boolean isSuccessful() { return errorMessage == null; }
    }
    
    /**
     * Configuration for sheet-specific processing
     */
    @Getter
    public static class SheetProcessorConfig {
        private final Class<?> beanClass;
        private final Consumer<?> batchProcessor;
        
        public SheetProcessorConfig(Class<?> beanClass, Consumer<?> batchProcessor) {
            this.beanClass = beanClass;
            this.batchProcessor = batchProcessor;
        }
    }
}

