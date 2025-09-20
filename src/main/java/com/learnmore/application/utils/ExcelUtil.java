package com.learnmore.application.utils;

import lombok.Getter;
import com.learnmore.application.utils.cache.ReflectionCache;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.converter.TypeConverter;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.monitoring.MemoryMonitor;
import com.learnmore.application.utils.validation.*;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import com.learnmore.application.utils.parallel.ParallelBatchProcessor;

/**
 * Refactored Excel utility class optimized for true streaming processing
 * Supports 1M+ records with comprehensive validation, monitoring, and error handling
 * 
 * Key Features:
 * - True streaming processing for large datasets (no memory accumulation)
 * - Comprehensive validation framework
 * - Memory monitoring and optimization
 * - Advanced type conversion support
 * - Proper resource management
 * - Production-ready performance
 */
public class ExcelUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ExcelUtil.class);
    
    // Default configuration optimized for true streaming
    private static final ExcelConfig DEFAULT_CONFIG = ExcelConfig.builder()
            .batchSize(1000)
            .memoryThreshold(512)
            .enableMemoryMonitoring(true)
            .cellCountThresholdForSXSSF(1_500_000L)
            .maxCellsForXSSF(1_000_000L)
            .sxssfRowAccessWindowSize(500)
            .preferCSVForLargeData(true)
            .csvThreshold(3_000_000L)
            .maxErrorsBeforeAbort(500)
            .build();
    
    // ============================================================================
    // MAIN PROCESSING METHODS - TRUE STREAMING ONLY
    // ============================================================================
    
    /**
     * Main entry point for Excel processing - uses true streaming
     * Backward compatible method that delegates to processExcelTrueStreaming
     */
    public static <T> List<T> processExcel(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        return processExcel(inputStream, beanClass, DEFAULT_CONFIG);
    }
    
    /**
     * Process Excel file with configuration - delegates to true streaming
     * Collects results for backward compatibility
     */
    public static <T> List<T> processExcel(InputStream inputStream, Class<T> beanClass, ExcelConfig config) 
            throws ExcelProcessException {
        
        List<T> results = new ArrayList<>();
        Consumer<List<T>> collector = results::addAll;
        
        var processingResult = processExcelTrueStreaming(inputStream, beanClass, config, collector);
        
        logger.info("Processed {} records with true streaming", processingResult.getProcessedRecords());
        return results;
    }
    
    /**
     * Legacy method name for backward compatibility - delegates to processExcel
     */
    public static <T> List<T> processExcelToList(InputStream inputStream, Class<T> beanClass, ExcelConfig config)
            throws ExcelProcessException {
        return processExcel(inputStream, beanClass, config);
    }
    
    /**
     * Process Excel file with TRUE streaming - Main method for large datasets
     * Uses early validation và true streaming để xử lý file rất lớn
     */
    public static <T> com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult 
            processExcelTrueStreaming(InputStream inputStream, Class<T> beanClass, ExcelConfig config, 
                                    Consumer<List<T>> batchProcessor) throws ExcelProcessException {
        
        // Early validation - kiểm tra kích thước file trước khi xử lý
        com.learnmore.application.utils.validation.ExcelEarlyValidator.EarlyValidationResult earlyResult = 
            com.learnmore.application.utils.validation.ExcelEarlyValidator.validateRecordCount(
                inputStream, config.getMaxErrorsBeforeAbort(), 1);
        
        if (!earlyResult.isValid()) {
            logger.warn("Excel file failed early validation: {}", earlyResult.getErrorMessage());
            logger.info("Recommendations:\n{}", earlyResult.getRecommendation());
            throw new ExcelProcessException("File too large: " + earlyResult.getErrorMessage());
        }
        
        // Auto-suggest CSV for very large files
        if (config.isPreferCSVForLargeData() && earlyResult.estimatedCells() > config.getCsvThreshold()) {
            logger.warn("File has {} cells (>{} threshold). Consider CSV format for better performance.", 
                    earlyResult.estimatedCells(), config.getCsvThreshold());
        }
        
        logger.info("Early validation passed. Processing {} rows with true streaming...", 
                earlyResult.getDataRows());
        
        // Setup enhanced validation rules
        List<ValidationRule> validationRules = setupValidationRules(beanClass, config);
        
        // Set up monitoring
        MemoryMonitor memoryMonitor = null;
        if (config.isEnableMemoryMonitoring()) {
            memoryMonitor = new MemoryMonitor(config.getMemoryThresholdMB());
            memoryMonitor.startMonitoring();
        }
        
        try {
            long startTime = System.currentTimeMillis();
            long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            
            // Use TRUE streaming processor - không tích lũy kết quả
            com.learnmore.application.utils.sax.TrueStreamingSAXProcessor<T> trueProcessor = 
                new com.learnmore.application.utils.sax.TrueStreamingSAXProcessor<>(
                    beanClass, config, validationRules, batchProcessor);
            
            var result = trueProcessor.processExcelStreamTrue(inputStream);
            
            // Performance logging
            long endTime = System.currentTimeMillis();
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long processingTime = endTime - startTime;
            long memoryDelta = endMemory - startMemory;
            
            logger.info("TRUE STREAMING PERFORMANCE REPORT:");
            logger.info("  Records processed: {}", result.getProcessedRecords());
            logger.info("  Processing time: {}ms", processingTime);
            logger.info("  Records/second: {:.2f}", result.getRecordsPerSecond());
            logger.info("  Memory delta: {} MB", memoryDelta / 1024 / 1024);
            logger.info("  Error count: {}", result.getErrorCount());
            
            if (result.getErrorCount() > 0) {
                double errorRate = (result.getErrorCount() * 100.0) / result.getProcessedRecords();
                logger.warn("  Error rate: {:.2f}%", errorRate);
                
                if (errorRate > 5.0) {
                    logger.warn("HIGH ERROR RATE detected! Consider reviewing data quality or validation rules.");
                }
            }
            
            // Performance recommendations
            if (result.getRecordsPerSecond() < 1000) {
                logger.warn("LOW PERFORMANCE detected. Consider:");
                logger.warn("  - Increasing batch size (current: {})", config.getBatchSize());
                logger.warn("  - Reducing validation complexity");
                logger.warn("  - Checking database/processing bottlenecks");
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("True streaming processing failed: {}", e.getMessage(), e);
            throw new ExcelProcessException("Failed to process Excel file with true streaming", e);
        } finally {
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
        }
    }
    
    /**
     * Process multiple sheets with TRUE streaming - SAX-based processing
     * Each sheet is processed independently with its own batch processor
     */
    public static Map<String, com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> 
            processMultiSheetExcelTrueStreaming(
                InputStream inputStream, 
                Map<String, Class<?>> sheetClassMap,
                Map<String, Consumer<List<?>>> sheetProcessors,
                ExcelConfig config) throws ExcelProcessException {
        
        try {
            logger.info("Starting multi-sheet true streaming processing for {} sheets", sheetClassMap.size());
            
            com.learnmore.application.utils.sax.TrueStreamingMultiSheetProcessor processor = 
                new com.learnmore.application.utils.sax.TrueStreamingMultiSheetProcessor(
                    sheetClassMap, sheetProcessors, config);
            
            return processor.processTrueStreaming(inputStream);
            
        } catch (Exception e) {
            logger.error("Multi-sheet true streaming failed: {}", e.getMessage(), e);
            throw new ExcelProcessException("Failed to process multi-sheet Excel with true streaming", e);
        }
    }
    
    // ============================================================================
    // ASYNC I/O INTEGRATION - NON-BLOCKING PROCESSING
    // ============================================================================
    
    /**
     * Process Excel file asynchronously with CompletableFuture
     * Non-blocking I/O for large datasets
     */
    public static <T> CompletableFuture<com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> 
            processExcelAsync(InputStream inputStream, Class<T> beanClass, ExcelConfig config,
                            Consumer<List<T>> batchProcessor) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return processExcelTrueStreaming(inputStream, beanClass, config, batchProcessor);
            } catch (ExcelProcessException e) {
                logger.error("Async Excel processing failed: {}", e.getMessage(), e);
                throw new RuntimeException("Async processing failed", e);
            }
        });
    }
    
    /**
     * Process Excel file asynchronously with custom executor service
     */
    public static <T> CompletableFuture<com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> 
            processExcelAsync(InputStream inputStream, Class<T> beanClass, ExcelConfig config,
                            Consumer<List<T>> batchProcessor, ExecutorService executor) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return processExcelTrueStreaming(inputStream, beanClass, config, batchProcessor);
            } catch (ExcelProcessException e) {
                logger.error("Async Excel processing failed: {}", e.getMessage(), e);
                throw new RuntimeException("Async processing failed", e);
            }
        }, executor);
    }
    
    /**
     * Process Excel with parallel batch processing
     * Each batch is processed in parallel using thread pool
     */
    public static <T> ParallelBatchProcessor.ParallelProcessingResult 
            processExcelParallel(InputStream inputStream, Class<T> beanClass, ExcelConfig config,
                               Consumer<List<T>> batchProcessor, int threadPoolSize) throws ExcelProcessException {
        
        if (!config.isParallelProcessing()) {
            logger.warn("Parallel processing is disabled in config. Enable it for better performance.");
        }
        
        // Use parallel batch processor for high-throughput processing
        ParallelBatchProcessor<T> parallelProcessor = new ParallelBatchProcessor<>(batchProcessor, threadPoolSize);
        
        try {
            // Collect batches first (for parallel processing)
            List<List<T>> batches = new ArrayList<>();
            Consumer<List<T>> batchCollector = batches::add;
            
            // Process with true streaming to collect batches
            processExcelTrueStreaming(inputStream, beanClass, config, batchCollector);
            
            logger.info("Collected {} batches for parallel processing", batches.size());
            
            // Process all batches in parallel
            var parallelResult = parallelProcessor.processAllBatches(batches);
            
            logger.info("Parallel processing completed: {} records in {}ms ({:.2f} records/sec)", 
                    parallelResult.getTotalRecords(),
                    parallelResult.getTotalProcessingTimeMs(),
                    parallelResult.getRecordsPerSecond());
            
            return parallelResult;
            
        } finally {
            parallelProcessor.shutdown();
        }
    }
    
    /**
     * Process multiple Excel files asynchronously and combine results
     */
    public static <T> CompletableFuture<List<com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult>> 
            processMultipleExcelFilesAsync(List<InputStream> inputStreams, Class<T> beanClass, 
                                         ExcelConfig config, Consumer<List<T>> batchProcessor) {
        
        List<CompletableFuture<com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult>> futures = 
            new ArrayList<>();
        
        for (int i = 0; i < inputStreams.size(); i++) {
            InputStream inputStream = inputStreams.get(i);
            final int fileIndex = i;
            
            CompletableFuture<com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> future = 
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.info("Processing file #{} asynchronously", fileIndex + 1);
                        return processExcelTrueStreaming(inputStream, beanClass, config, batchProcessor);
                    } catch (ExcelProcessException e) {
                        logger.error("Failed to process file #{}: {}", fileIndex + 1, e.getMessage(), e);
                        throw new RuntimeException("File processing failed", e);
                    }
                });
            
            futures.add(future);
        }
        
        // Combine all results
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> results = 
                        new ArrayList<>();
                    
                    for (CompletableFuture<com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> future : futures) {
                        try {
                            results.add(future.get());
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("Failed to get async result: {}", e.getMessage(), e);
                            throw new RuntimeException("Failed to collect async results", e);
                        }
                    }
                    
                    logger.info("Completed processing {} files asynchronously", results.size());
                    return results;
                });
    }
    
    // ============================================================================
    // EXCEL WRITING METHODS - OPTIMIZED FOR PERFORMANCE
    // ============================================================================
    
    /**
     * Write data to Excel file with default configuration
     */
    public static <T> void writeToExcel(String fileName, List<T> data) throws ExcelProcessException {
        writeToExcel(fileName, data, 0, 0, DEFAULT_CONFIG);
    }
    
    /**
     * Write data to Excel file with custom start positions
     */
    public static <T> void writeToExcel(String fileName, List<T> data, Integer rowStart, Integer columnStart) 
            throws ExcelProcessException {
        writeToExcel(fileName, data, rowStart, columnStart, DEFAULT_CONFIG);
    }
    
    /**
     * Write data to Excel file with full configuration - intelligent strategy selection
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
        
        // Log strategy decision
        String recommendations = com.learnmore.application.utils.strategy.ExcelWriteStrategy.getOptimizationRecommendation(dataSize, columnCount, config);
        logger.info("Excel write strategy selected: {}\nRecommendations:\n{}", strategy, recommendations);
        
        // Set up monitoring
        MemoryMonitor memoryMonitor = null;
        try {
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
                    int defaultWindowSize = config.getSxssfRowAccessWindowSize();
                    writeToExcelStreamingSXSSF(fileName, data, rowStart, columnStart, config, defaultWindowSize);
            }
            
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to write Excel file: " + fileName, e);
        } finally {
            if (memoryMonitor != null) {
                memoryMonitor.stopMonitoring();
            }
        }
    }
    
    /**
     * Write data to Excel bytes with enhanced performance
     */
    public static <T> byte[] writeToExcelBytes(List<T> data) throws ExcelProcessException {
        return writeToExcelBytes(data, 0, 0, DEFAULT_CONFIG);
    }
    
    /**
     * Write data to Excel bytes with custom start positions
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
            // Use SXSSF for streaming even for bytes
            int dataSize = data.size();
            int columnCount = calculateColumnCount(data.get(0).getClass());
            int windowSize = com.learnmore.application.utils.strategy.ExcelWriteStrategy.calculateOptimalWindowSize(dataSize, columnCount, config);
            
            writeToExcelStreamingSXSSFBytes(data, rowStart, columnStart, config, windowSize, outputStream);
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to generate Excel bytes", e);
        }
    }
    
    // ============================================================================
    // PRIVATE HELPER METHODS - STREAMLINED FOR TRUE STREAMING
    // ============================================================================
    
    /**
     * Setup validation rules with enhanced validator support
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
        
        // Set up data type validation for each field
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
        
        // Add numeric range validation if configured
        if (config.isEnableRangeValidation()) {
            validationRules.add(new NumericRangeValidator(config.getMinValue(), config.getMaxValue()));
            logger.debug("Added NumericRangeValidator (min: {}, max: {})", config.getMinValue(), config.getMaxValue());
        }
        
        // Add email validation
        validationRules.add(new EmailValidator());
        
        logger.info("Setup {} validation rules for class: {}", validationRules.size(), beanClass.getSimpleName());
        return validationRules;
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
     * Traditional XSSF writing for small datasets (≤1M cells)
     */
    private static <T> void writeToExcelXSSF(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config)
            throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {
            
            Sheet sheet = workbook.createSheet();
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            
            // Header
            Row headerRow = sheet.createRow(rowStart);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Data rows
            int currentRow = rowStart + 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                writeRowData(row, item, columnNames, excelFields, columnStart);
            }
            workbook.write(fos);
        }
    }
    
    /**
     * SXSSF streaming writing with configurable window size
     */
    private static <T> void writeToExcelStreamingSXSSF(String fileName, List<T> data, Integer rowStart, Integer columnStart, ExcelConfig config, int windowSize)
            throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(windowSize);
             FileOutputStream fos = new FileOutputStream(fileName)) {
            
            Sheet sheet = workbook.createSheet();
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            
            // Header
            Row headerRow = sheet.createRow(rowStart);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Data rows
            int currentRow = rowStart + 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                writeRowData(row, item, columnNames, excelFields, columnStart);
            }
            workbook.write(fos);
            workbook.dispose(); // free temp files
        }
    }
    
    /**
     * Write to SXSSF bytes output stream
     */
    private static <T> void writeToExcelStreamingSXSSFBytes(List<T> data, Integer rowStart, Integer columnStart, 
            ExcelConfig config, int windowSize, ByteArrayOutputStream outputStream) throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(windowSize)) {
            Sheet sheet = workbook.createSheet();
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            
            // Header
            Row headerRow = sheet.createRow(rowStart);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(columnStart + i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Data rows
            int currentRow = rowStart + 1;
            for (T item : data) {
                Row row = sheet.createRow(currentRow++);
                writeRowData(row, item, columnNames, excelFields, columnStart);
            }
            workbook.write(outputStream);
            workbook.dispose();
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
             FileOutputStream fos = new FileOutputStream(fileName)) {
            
            ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
            List<String> columnNames = new ArrayList<>(excelFields.keySet());
            
            for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                Sheet sheet = workbook.createSheet("Sheet" + (sheetIndex + 1));
                
                // Calculate data range for this sheet
                int startIdx = sheetIndex * rowsPerSheet;
                int endIdx = Math.min(startIdx + rowsPerSheet, data.size());
                List<T> sheetData = data.subList(startIdx, endIdx);
                
                // Write header
                Row headerRow = sheet.createRow(rowStart);
                CellStyle headerStyle = createHeaderStyle(workbook);
                for (int i = 0; i < columnNames.size(); i++) {
                    Cell cell = headerRow.createCell(columnStart + i);
                    cell.setCellValue(columnNames.get(i));
                    cell.setCellStyle(headerStyle);
                }
                
                // Write data rows
                int currentRow = rowStart + 1;
                for (T item : sheetData) {
                    Row row = sheet.createRow(currentRow++);
                    writeRowData(row, item, columnNames, excelFields, columnStart);
                }
                
                logger.info("Completed sheet {} ({}-{} of {} total records)", 
                        sheetIndex + 1, startIdx + 1, endIdx, data.size());
            }
            
            workbook.write(fos);
            workbook.dispose();
        }
    }
    
    /**
     * Write data to CSV file using streaming approach
     */
    private static <T> void writeToCSVStreaming(String fileName, List<T> data, ExcelConfig config) throws Exception {
        ReflectionCache reflectionCache = ReflectionCache.getInstance();
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) data.get(0).getClass();
        ConcurrentMap<String, Field> excelFields = reflectionCache.getExcelColumnFields(beanClass);
        List<String> columnNames = new ArrayList<>(excelFields.keySet());
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(fileName), java.nio.charset.StandardCharsets.UTF_8))) {
            
            // Write header
            writer.write(String.join(config.getDelimiter(), columnNames));
            writer.newLine();
            
            // Write data rows
            for (T item : data) {
                List<String> row = new ArrayList<>();
                for (String columnName : columnNames) {
                    Field field = excelFields.get(columnName);
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
    
    /**
     * Write row data to Excel row - consolidated method
     */
    private static <T> void writeRowData(Row row, T item, List<String> columnNames, 
            ConcurrentMap<String, Field> excelFields, Integer columnStart) {
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
    
    // ============================================================================
    // UTILITY METHODS - PERFORMANCE AND MONITORING
    // ============================================================================
    
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
     * Clear all caches for memory cleanup
     */
    public static void clearCaches() {
        ReflectionCache.getInstance().clearCaches();
        TypeConverter.getInstance().clearCache();
        logger.info("All ExcelUtil caches cleared");
    }
    
    // ============================================================================
    // SUPPORT CLASSES - BACKWARD COMPATIBILITY
    // ============================================================================
    
    /**
     * Result class for multi-sheet processing - backward compatibility
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
        
        public boolean hasErrors() { 
            return !errors.isEmpty() || errorMessage != null; 
        }
        
        public boolean isSuccessful() { 
            return errorMessage == null; 
        }
    }
    
    /**
     * Configuration for sheet-specific processing - backward compatibility
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
    
    // ============================================================================
    // BACKWARD COMPATIBILITY METHODS
    // ============================================================================
    
    /**
     * Legacy method for streaming processing - backward compatibility
     * @deprecated Use processExcelTrueStreaming instead
     */
    @Deprecated
    public static <T> com.learnmore.application.utils.streaming.StreamingExcelProcessor.ProcessingResult 
            processExcelStreaming(InputStream inputStream, Class<T> beanClass, ExcelConfig config, 
                                Consumer<List<T>> batchProcessor) throws ExcelProcessException {
        
        // Delegate to true streaming and convert result
        var trueStreamingResult = processExcelTrueStreaming(inputStream, beanClass, config, batchProcessor);
        
        // Convert result to legacy format
        return new com.learnmore.application.utils.streaming.StreamingExcelProcessor.ProcessingResult(
                trueStreamingResult.getProcessedRecords(),
                trueStreamingResult.getErrorCount(),
                Collections.emptyList(), // validation errors not available in legacy format
                trueStreamingResult.getProcessingTimeMs()
        );
    }
    
    /**
     * Legacy method for multi-sheet processing - backward compatibility
     * @deprecated Use processMultiSheetExcelTrueStreaming instead
     */
    @Deprecated
    public static Map<String, MultiSheetResult> processMultiSheetExcel(
            InputStream inputStream, 
            Map<String, Class<?>> sheetConfigurations, 
            ExcelConfig config) throws ExcelProcessException {
        
        // Create empty processors for each sheet (backward compatibility)
        Map<String, Consumer<List<?>>> emptyProcessors = new HashMap<>();
        for (String sheetName : sheetConfigurations.keySet()) {
            emptyProcessors.put(sheetName, batch -> {
                // Empty processor for backward compatibility
            });
        }
        
        // Process each sheet with true streaming
        Map<String, com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> trueStreamingResults = 
                processMultiSheetExcelTrueStreaming(inputStream, sheetConfigurations, emptyProcessors, config);
        
        // Convert results to legacy format
        Map<String, MultiSheetResult> legacyResults = new HashMap<>();
        for (Map.Entry<String, com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> entry : 
                trueStreamingResults.entrySet()) {
            
            var result = entry.getValue();
            MultiSheetResult legacyResult = new MultiSheetResult(
                    Collections.emptyList(), // data not collected in streaming mode
                    Collections.emptyList(), // errors not available in legacy format
                    (int) result.getProcessedRecords(),
                    result.getErrorCount() > 0 ? "Processing had " + result.getErrorCount() + " errors" : null
            );
            
            legacyResults.put(entry.getKey(), legacyResult);
        }
        
        return legacyResults;
    }
    
    /**
     * Legacy method for multi-sheet streaming processing - backward compatibility
     * @deprecated Use processMultiSheetExcelTrueStreaming instead
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static Map<String, com.learnmore.application.utils.sax.TrueStreamingSAXProcessor.ProcessingResult> 
            processMultiSheetExcelStreaming(
                    InputStream inputStream, 
                    Map<String, SheetProcessorConfig> sheetConfigurations, 
                    ExcelConfig config) throws ExcelProcessException {
        
        // Convert SheetProcessorConfig to class mapping and processors
        Map<String, Class<?>> simpleConfigurations = new HashMap<>();
        Map<String, Consumer<List<?>>> processors = new HashMap<>();
        
        for (Map.Entry<String, SheetProcessorConfig> entry : sheetConfigurations.entrySet()) {
            simpleConfigurations.put(entry.getKey(), entry.getValue().getBeanClass());
            // Cast the processor to generic consumer
            processors.put(entry.getKey(), (Consumer<List<?>>) entry.getValue().getBatchProcessor());
        }
        
        return processMultiSheetExcelTrueStreaming(inputStream, simpleConfigurations, processors, config);
    }
}