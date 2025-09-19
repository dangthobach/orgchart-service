package com.learnmore.application.utils;

import lombok.Getter;
import com.learnmore.application.utils.cache.ReflectionCache;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.converter.TypeConverter;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.exception.ValidationException;
import com.learnmore.application.utils.monitoring.MemoryMonitor;
import com.learnmore.application.utils.monitoring.ProgressMonitor;
import com.learnmore.application.utils.sax.SAXExcelProcessor;
import com.learnmore.application.utils.streaming.StreamingExcelProcessor;
import com.learnmore.application.utils.validation.*;

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
    
    // Default configuration with optimized thresholds
    private static final ExcelConfig DEFAULT_CONFIG = ExcelConfig.builder()
            .batchSize(1000)
            .memoryThreshold(500)
            .parallelProcessing(true)
            .enableProgressTracking(true)
            .enableMemoryMonitoring(true)
            .cellCountThresholdForSXSSF(2_000_000L)
            .maxCellsForXSSF(1_500_000L)
            .sxssfRowAccessWindowSize(500)
            .preferCSVForLargeData(true)
            .csvThreshold(5_000_000L)
            .allowXLSFormat(false)
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
        try {
            // Use SAX-based streaming for large files
            List<ValidationRule> validationRules = setupValidationRules(beanClass, config);
            SAXExcelProcessor<T> saxProcessor =
                new SAXExcelProcessor<>(beanClass, config, validationRules);
            results = saxProcessor.processExcelStream(inputStream);
            return results;
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to process Excel file with streaming", e);
        }
    }
    
    /**
     * Process Excel file with SAX-based streaming for optimal memory usage
     * Ideal for very large files that don't fit in memory
     */
    public static <T> StreamingExcelProcessor.ProcessingResult processExcelStreaming(
            InputStream inputStream, Class<T> beanClass, ExcelConfig config, Consumer<List<T>> batchProcessor) 
            throws ExcelProcessException {
        
        // Setup enhanced validation rules
        List<ValidationRule> validationRules = setupValidationRules(beanClass, config);
        
        // Set up monitoring
        MemoryMonitor memoryMonitor = null;
        if (config.isEnableMemoryMonitoring()) {
            memoryMonitor = new MemoryMonitor(config.getMemoryThresholdMB());
            memoryMonitor.startMonitoring();
        }
        
        try {
            if (config.isUseStreamingParser()) {
                // Use SAX-based processing for optimal memory usage
                logger.info("Using SAX-based streaming processor for class: {}", beanClass.getSimpleName());
                
                SAXExcelProcessor<T> saxProcessor = new SAXExcelProcessor<>(beanClass, config, validationRules);
                List<T> results = saxProcessor.processExcelStream(inputStream);
                
                // Process in batches if batch processor provided
                if (batchProcessor != null && !results.isEmpty()) {
                    int batchSize = config.getBatchSize();
                    for (int i = 0; i < results.size(); i += batchSize) {
                        int endIndex = Math.min(i + batchSize, results.size());
                        List<T> batch = results.subList(i, endIndex);
                        batchProcessor.accept(batch);
                    }
                }
                
                // Create result object - matching StreamingExcelProcessor.ProcessingResult constructor
                List<ValidationException.ValidationError> emptyErrors = new ArrayList<>();
                long processingTime = System.currentTimeMillis(); // Simple timing for now
                
                return new StreamingExcelProcessor.ProcessingResult(
                    results.size(), 0, emptyErrors, processingTime
                );
                
            } else {
                // Fallback to traditional streaming processor
                logger.info("Using traditional streaming processor for class: {}", beanClass.getSimpleName());
                
                setupValidationRules(config, beanClass);
                StreamingExcelProcessor<T> processor = new StreamingExcelProcessor<>(beanClass, config);
                
                try {
                    return processor.processExcel(inputStream, batchProcessor);
                } finally {
                    processor.shutdown();
                }
            }
            
        } catch (Exception e) {
            logger.error("Excel streaming processing failed: {}", e.getMessage(), e);
            throw new ExcelProcessException("Failed to process Excel file with streaming", e);
        } finally {
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
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
        // Use streaming/SAX for multi-sheet processing
        try {
            com.learnmore.application.utils.sax.SAXMultiSheetProcessor saxMultiSheetProcessor =
                new com.learnmore.application.utils.sax.SAXMultiSheetProcessor(sheetClassMap, config);
            results = saxMultiSheetProcessor.process(inputStream);
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to process multi-sheet Excel file with streaming", e);
        }
        return results;
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
     * Write data to Excel file with custom configuration using intelligent strategy selection
     */
    public static <T> void writeToExcel(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config) 
            throws ExcelProcessException {
        
        if (data == null || data.isEmpty()) {
            throw new ExcelProcessException("Data list cannot be null or empty");
        }
        
        // Calculate data dimensions
        int dataSize = data.size();
        int columnCount = calculateColumnCount(data.get(0).getClass());
        
        // Validate file format constraints
        com.learnmore.application.utils.strategy.ExcelWriteStrategy.validateFileFormat(fileName, dataSize, columnCount, config);
        
        // Determine optimal write strategy
        com.learnmore.application.utils.strategy.ExcelWriteStrategy.WriteMode strategy = 
            com.learnmore.application.utils.strategy.ExcelWriteStrategy.determineWriteStrategy(dataSize, columnCount, config);
        
        // Log strategy decision and recommendations
        String recommendations = com.learnmore.application.utils.strategy.ExcelWriteStrategy.getOptimizationRecommendation(dataSize, columnCount, config);
        logger.info("Excel write strategy selected: {}\nRecommendations:\n{}", strategy, recommendations);
        
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
            
            // Execute strategy
            switch (strategy) {
                case XSSF_TRADITIONAL:
                    writeToExcelXSSF(fileName, data, rowStart, columnStart, config);
                    break;
                    
                case SXSSF_STREAMING:
                    int windowSize = com.learnmore.application.utils.strategy.ExcelWriteStrategy.calculateOptimalWindowSize(dataSize, columnCount, config);
                    writeToExcelStreamingSXSSF(fileName, data, rowStart, columnStart, config, windowSize);
                    break;
                    
                case CSV_STREAMING:
                    String csvFileName = fileName.replaceAll("\\.(xlsx|xls)$", ".csv");
                    logger.info("Converting to CSV format: {} -> {}", fileName, csvFileName);
                    writeToCSVStreaming(csvFileName, data, config);
                    break;
                    
                case MULTI_SHEET_SPLIT:
                    writeToExcelMultiSheet(fileName, data, rowStart, columnStart, config);
                    break;
                    
                default:
                    // Fallback to SXSSF
                    writeToExcelStreamingSXSSF(fileName, data, rowStart, columnStart, config);
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
     * Calculate number of columns for a POJO class
     */
    private static int calculateColumnCount(Class<?> beanClass) {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
        return excelFields.size();
    }
    
    /**
     * Traditional XSSF writing for small datasets (≤1.5M cells)
     */
    private static <T> void writeToExcelXSSF(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config)
            throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName)) {
            
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet();
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            
            // Header
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowStart);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Data rows
            int currentRow = rowStart + 1;
            for (T item : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(currentRow++);
                for (int i = 0; i < columnNames.size(); i++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(columnStart + i);
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
            workbook.write(fos);
        }
    }
    
    /**
     * SXSSF streaming writing with configurable window size
     */
    private static <T> void writeToExcelStreamingSXSSF(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config)
            throws Exception {
        writeToExcelStreamingSXSSF(fileName, data, rowStart, columnStart, config, config.getSxssfRowAccessWindowSize());
    }
    
    /**
     * SXSSF streaming writing with custom window size
     */
    private static <T> void writeToExcelStreamingSXSSF(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config, int windowSize)
            throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(windowSize);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName)) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet();
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            // Header
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowStart);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            // Data rows
            int currentRow = rowStart + 1;
            for (T item : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(currentRow++);
                for (int i = 0; i < columnNames.size(); i++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(columnStart + i);
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
            workbook.write(fos);
            workbook.dispose(); // free temp files
        }
    }
    
    /**
     * Multi-sheet writing for very large datasets (>500k rows)
     */
    private static <T> void writeToExcelMultiSheet(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config)
            throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        
        int sheetCount = com.learnmore.application.utils.strategy.ExcelWriteStrategy.calculateOptimalSheetCount(data.size(), config);
        int rowsPerSheet = (int) Math.ceil((double) data.size() / sheetCount);
        int windowSize = com.learnmore.application.utils.strategy.ExcelWriteStrategy.calculateOptimalWindowSize(data.size(), calculateColumnCount(beanClass), config);
        
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(windowSize);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName)) {
            
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            
            for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet" + (sheetIndex + 1));
                
                // Calculate data range for this sheet
                int startIdx = sheetIndex * rowsPerSheet;
                int endIdx = Math.min(startIdx + rowsPerSheet, data.size());
                List<T> sheetData = data.subList(startIdx, endIdx);
                
                // Write header
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowStart);
                org.apache.poi.ss.usermodel.CellStyle headerStyle = createHeaderStyle(workbook);
                for (int i = 0; i < columnNames.size(); i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(columnStart + i);
                    cell.setCellValue(columnNames.get(i));
                    cell.setCellStyle(headerStyle);
                }
                
                // Write data rows
                int currentRow = rowStart + 1;
                for (T item : sheetData) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(currentRow++);
                    for (int i = 0; i < columnNames.size(); i++) {
                        org.apache.poi.ss.usermodel.Cell cell = row.createCell(columnStart + i);
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
                
                logger.info("Completed sheet {} ({}-{} of {} total records)", 
                        sheetIndex + 1, startIdx + 1, endIdx, data.size());
            }
            
            workbook.write(fos);
            workbook.dispose();
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
    /**
     * Setup validation rules with enhanced validator support
     * Returns list of validators for immediate validation during parsing
     */
    private static <T> List<ValidationRule> setupValidationRules(Class<T> beanClass, ExcelConfig config) {
        List<ValidationRule> validationRules = new ArrayList<>();
        
        // Set up required field validation
        if (!config.getRequiredFields().isEmpty()) {
            RequiredFieldValidator requiredValidator = new RequiredFieldValidator(config.getRequiredFields());
            validationRules.add(requiredValidator);
            config.addGlobalValidation(requiredValidator);
            logger.debug("Added RequiredFieldValidator for fields: {}", config.getRequiredFields());
        }
        
        // Set up duplicate validation with memory-efficient tracking
        if (!config.getUniqueFields().isEmpty()) {
            DuplicateValidator duplicateValidator = new DuplicateValidator(config.getUniqueFields());
            validationRules.add(duplicateValidator);
            config.addGlobalValidation(duplicateValidator);
            logger.debug("Added DuplicateValidator for fields: {}", config.getUniqueFields());
        }
        
        // Set up data type validation for each field with enhanced type checking
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
        
        for (Map.Entry<String, Field> entry : excelFields.entrySet()) {
            String columnName = entry.getKey();
            Field field = entry.getValue();
            Class<?> fieldType = field.getType();
            
            // Add enhanced data type validator
            DataTypeValidator dataTypeValidator = new DataTypeValidator(fieldType, config.getDateFormat());
            validationRules.add(dataTypeValidator);
            config.addFieldValidation(columnName, dataTypeValidator);
            
            logger.debug("Added DataTypeValidator for field: {} (type: {})", columnName, fieldType.getSimpleName());
        }
        
        // Add custom range validation if configured
        if (config.isEnableRangeValidation()) {
            validationRules.add(new NumericRangeValidator(config.getMinValue(), config.getMaxValue()));
            logger.debug("Added NumericRangeValidator (min: {}, max: {})", config.getMinValue(), config.getMaxValue());
        }
        
        // Add email validation for email fields
        validationRules.add(new EmailValidator());
        
        logger.info("Setup {} validation rules for class: {}", validationRules.size(), beanClass.getSimpleName());
        return validationRules;
    }
    
    /**
     * Legacy method for backward compatibility
     */
    private static <T> void setupValidationRules(ExcelConfig config, Class<T> beanClass) {
        setupValidationRules(beanClass, config);
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
    
    /**
     * Write data to CSV file using streaming (không giữ toàn bộ dữ liệu trong RAM)
     */
    public static <T> void writeToCSVStreaming(String fileName, List<T> data, ExcelConfig config) throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        java.util.concurrent.ConcurrentMap<String, java.lang.reflect.Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
        java.util.List<String> columnNames = new java.util.ArrayList<>(excelFields.keySet());
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(fileName), java.nio.charset.StandardCharsets.UTF_8))) {
            // Write header
            writer.write(String.join(config.getDelimiter(), columnNames));
            writer.newLine();
            // Write data rows
            for (T item : data) {
                java.util.List<String> row = new java.util.ArrayList<>();
                for (String columnName : columnNames) {
                    java.lang.reflect.Field field = excelFields.get(columnName);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(item);
                        row.add(value != null ? value.toString() : "");
                    } else {
                        row.add("");
                    }
                }
                writer.write(String.join(config.getDelimiter(), row));
                writer.newLine();
            }
        }
    }
}

