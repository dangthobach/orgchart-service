package com.learnmore.application.utils.sax;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.converter.TypeConverter;
import com.learnmore.application.utils.reflection.MethodHandleMapper;
import com.learnmore.application.utils.validation.ValidationRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * True streaming SAX processor - không tích lũy kết quả trong memory
 * Đẩy từng batch trực tiếp vào batchProcessor để xử lý ngay
 */
@Slf4j
public class TrueStreamingSAXProcessor<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final List<ValidationRule> validationRules;
    private final TypeConverter typeConverter;
    private final Consumer<List<T>> batchProcessor;
    private final MethodHandleMapper<T> methodHandleMapper;

    
    // Statistics
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final long startTime;
    
    public TrueStreamingSAXProcessor(Class<T> beanClass, ExcelConfig config, 
                                   List<ValidationRule> validationRules, 
                                   Consumer<List<T>> batchProcessor) {
        this.beanClass = beanClass;
        this.config = config;
        this.validationRules = validationRules != null ? validationRules : new ArrayList<>();
        this.typeConverter = TypeConverter.getInstance();
        this.batchProcessor = batchProcessor;
        this.methodHandleMapper = MethodHandleMapper.forClass(beanClass);
        this.startTime = System.currentTimeMillis();
        
        log.info("Initialized TrueStreamingSAXProcessor with MethodHandle optimization for class: {}", 
                 beanClass.getSimpleName());
    }
    
    /**
     * Process Excel với true streaming - không tích lũy kết quả
     */
    public ProcessingResult processExcelStreamTrue(InputStream inputStream) throws Exception {
        
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            org.apache.poi.xssf.model.SharedStringsTable sharedStringsTable = 
                (org.apache.poi.xssf.model.SharedStringsTable) xssfReader.getSharedStringsTable();
            StylesTable stylesTable = xssfReader.getStylesTable();
            
            // True streaming content handler - xử lý từng batch ngay
            TrueStreamingContentHandler contentHandler = new TrueStreamingContentHandler();
            
            // Setup SAX parser with proper date formatting
            XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            
            // Create DataFormatter with proper date formatting
            DataFormatter dataFormatter = new DataFormatter();
            dataFormatter.setUseCachedValuesForFormulaCells(false);
            
            XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                stylesTable, sharedStringsTable, contentHandler, dataFormatter, false
            );
            xmlReader.setContentHandler(sheetHandler);
            
            // Process first sheet với true streaming
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (sheetIterator.hasNext()) {
                try (InputStream sheetStream = sheetIterator.next()) {
                    xmlReader.parse(new InputSource(sheetStream));
                }
            }
            
            // Flush remaining batch
            contentHandler.flushRemainingBatch();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Throw if no data rows were processed
        if (totalProcessed.get() == 0) {
            throw new RuntimeException("Tập không có dữ liệu");
        }
        
        return new ProcessingResult(
            totalProcessed.get(), 
            totalErrors.get(), 
            processingTime
        );
    }
    
    // fieldMapping removed; MethodHandleMapper handles both Excel column names and direct field names
    
    /**
     * True streaming content handler - xử lý batch ngay, không tích lũy
     */
    private class TrueStreamingContentHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        
        private final List<T> currentBatch = new ArrayList<>();
        private final Map<String, Integer> headerMapping = new HashMap<>();
        private final Set<String> seenUniqueValues = new HashSet<>();
        private final AtomicLong errorCount = new AtomicLong(0);
        private Object currentInstance;
        private int currentRowNum = 0;
        private boolean headerProcessed = false;
        private boolean rowHasValue = false;
        
        @Override
        public void startRow(int rowNum) {
            this.currentRowNum = rowNum;
            
            // Skip rows before start row
            if (rowNum < config.getStartRow()) {
                return;
            }
            
            // Create new instance for data rows using MethodHandle (5x faster)
            if (headerProcessed) {
                rowHasValue = false;
                try {
                    currentInstance = methodHandleMapper.createInstance();
                    
                    // Set rowNum if field exists
                    @SuppressWarnings("unchecked")
                    T typedInstance = (T) currentInstance;
                    if (methodHandleMapper.hasField("rowNum")) {
                        methodHandleMapper.setFieldValue(typedInstance, "rowNum", rowNum + 1);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to create instance for row {}: {}", rowNum, e.getMessage());
                    totalErrors.incrementAndGet();
                }
            }
        }
        
        @Override
        public void cell(String cellReference, String formattedValue, 
                        org.apache.poi.xssf.usermodel.XSSFComment comment) {
            if (currentRowNum < config.getStartRow()) {
                return;
            }
            
            int colIndex = getColumnIndex(cellReference);
            
            // Process header row
            if (currentRowNum == config.getStartRow() && !headerProcessed) {
                if (formattedValue != null && !formattedValue.trim().isEmpty()) {
                    headerMapping.put(formattedValue.trim(), colIndex);
                }
                return;
            }
            
            // Process data rows
            if (headerProcessed && currentInstance != null) {
                processDataCell(colIndex, formattedValue);
            }
        }
        
        @Override
        public void endRow(int rowNum) {
            // Mark header as processed
            if (rowNum == config.getStartRow() && !headerProcessed) {
                headerProcessed = true;
                log.debug("Header processed with {} columns", headerMapping.size());
                return;
            }

            // Process completed data row
            if (headerProcessed && currentInstance != null) {
                try {
                    // Skip completely empty data rows
                    if (!rowHasValue) {
                        log.debug("Skipping empty row {}", rowNum);
                        currentInstance = null;
                        return;
                    }
                    // ✅ INLINE maxRows VALIDATION (during streaming, NO buffering)
                    if (config.getMaxRows() > 0) {
                        int dataRowsProcessed = (int) totalProcessed.get() + 1; // +1 for current row
                        if (dataRowsProcessed > config.getMaxRows()) {
                            throw new RuntimeException(String.format(
                                "Số lượng bản ghi trong file (%d) vượt quá giới hạn cho phép (%d). " +
                                "Vui lòng chia nhỏ file hoặc tăng giới hạn xử lý.",
                                dataRowsProcessed, config.getMaxRows()));
                        }
                    }

                    // Run validations
                    runValidations(currentInstance, rowNum);

                    // Add to current batch
                    @SuppressWarnings("unchecked")
                    T typedInstance = (T) currentInstance;
                    currentBatch.add(typedInstance);
                    totalProcessed.incrementAndGet();

                    // Process batch khi đủ size
                    if (currentBatch.size() >= config.getBatchSize()) {
                        processBatch();
                    }

                    // Progress tracking - respects config.enableProgressTracking and configurable interval
                    if (config.isEnableProgressTracking() &&
                        totalProcessed.get() % config.getProgressReportInterval() == 0) {
                        log.info("Processed {} rows in streaming mode", totalProcessed.get());
                    }

                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    log.warn("Error processing row {}: {}", rowNum, e.getMessage());
                    // Re-throw if it's a maxRows violation (don't continue processing)
                    if (e.getMessage() != null && e.getMessage().contains("vượt quá giới hạn")) {
                        throw new RuntimeException(e);
                    }
                }

                currentInstance = null;
            }
        }
        
        /**
         * Process current batch và clear ngay để tiếp tục streaming
         */
        private void processBatch() {
            if (!currentBatch.isEmpty() && batchProcessor != null) {
                try {
                    // Tạo copy để xử lý
                    List<T> batchToProcess = new ArrayList<>(currentBatch);
                    
                    // Process batch ngay lập tức
                    batchProcessor.accept(batchToProcess);
                    
                    // Clear batch để tiếp tục streaming
                    currentBatch.clear();
                    
                    log.debug("Processed batch of {} records", batchToProcess.size());
                    
                } catch (Exception e) {
                    log.error("Error processing batch: {}", e.getMessage(), e);
                    totalErrors.addAndGet(currentBatch.size());
                    currentBatch.clear();
                }
            }
        }
        
        /**
         * Flush remaining batch cuối file
         */
        public void flushRemainingBatch() {
            if (!currentBatch.isEmpty()) {
                log.info("Flushing final batch of {} records", currentBatch.size());
                processBatch();
            }
        }
        
        private void processDataCell(int colIndex, String formattedValue) {
            // Find field by column index
            String fieldName = findFieldNameByColumnIndex(colIndex);
            if (fieldName == null) {
                return;
            }
            
            try {
                // Get field type using MethodHandle mapper
                Class<?> fieldType = methodHandleMapper.getFieldType(fieldName);
                if (fieldType != null) {
                    // Convert and set value using MethodHandle (5x faster)
                    if (formattedValue != null && !formattedValue.trim().isEmpty()) {
                        rowHasValue = true;
                    }
                    
                    // ✅ ENHANCED DATE PROCESSING: Handle date formatting issues
                    String processedValue = processDateValue(formattedValue, fieldType);
                    Object convertedValue = typeConverter.convert(processedValue, fieldType);
                    
                    @SuppressWarnings("unchecked")
                    T typedInstance = (T) currentInstance;
                    methodHandleMapper.setFieldValue(typedInstance, fieldName, convertedValue);
                }
                
            } catch (Exception e) {
                log.debug("Failed to set field {} with value '{}': {}", fieldName, formattedValue, e.getMessage());
            }
        }
        
        /**
         * Process date values to handle Excel date formatting issues
         */
        private String processDateValue(String formattedValue, Class<?> fieldType) {
            if (formattedValue == null || formattedValue.trim().isEmpty()) {
                return formattedValue;
            }
            
            // Only process if field type is date-related
            if (fieldType == String.class && isDateField(formattedValue)) {
                // Handle cases like "11/15/25" -> "11/15/2025"
                if (formattedValue.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
                    String[] parts = formattedValue.split("/");
                    if (parts.length == 3) {
                        String month = parts[0];
                        String day = parts[1];
                        String year = parts[2];
                        
                        // Convert 2-digit year to 4-digit year
                        if (year.length() == 2) {
                            int yearInt = Integer.parseInt(year);
                            if (yearInt >= 0 && yearInt <= 99) {
                                // Assume years 00-30 are 2000-2030, 31-99 are 1931-1999
                                if (yearInt <= 30) {
                                    year = "20" + year;
                                } else {
                                    year = "19" + year;
                                }
                            }
                        }
                        
                        return month + "/" + day + "/" + year;
                    }
                }
            }
            
            return formattedValue;
        }
        
        /**
         * Check if a value looks like a date
         */
        private boolean isDateField(String value) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }
            
            // Check for common date patterns
            return value.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}") ||  // MM/dd/yyyy or MM/dd/yy
                   value.matches("\\d{1,2}-\\d{1,2}-\\d{2,4}") ||  // MM-dd-yyyy or MM-dd-yy
                   value.matches("\\d{4}-\\d{1,2}-\\d{1,2}") ||   // yyyy-MM-dd
                   value.matches("\\d{1,2}-\\d{4}");              // MM-yyyy
        }
        
        private String findFieldNameByColumnIndex(int colIndex) {
            for (Map.Entry<String, Integer> entry : headerMapping.entrySet()) {
                if (entry.getValue().equals(colIndex)) {
                    String headerName = entry.getKey();
                    // Prefer Excel header name if mapper knows it
                    if (methodHandleMapper.hasField(headerName)) {
                        return headerName;
                    }
                    // Fallback: if header equals actual field name
                    if (methodHandleMapper.hasField(headerName)) {
                        return headerName;
                    }
                }
            }
            return null;
        }
        
        private void runValidations(Object instance, int rowNum) {
            try {
                // Required fields validation
                for (String requiredField : config.getRequiredFields()) {
                    if (methodHandleMapper.hasField(requiredField)) {
                        @SuppressWarnings("unchecked")
                        T typedInstance = (T) instance;
                        Object value = methodHandleMapper.getFieldValue(typedInstance, requiredField);
                        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                            log.warn("Required field '{}' is empty at row {}", requiredField, rowNum);
                            errorCount.incrementAndGet();
                        }
                    }
                }
                
                // Unique fields validation (simple memory-based check for current batch)
                for (String uniqueField : config.getUniqueFields()) {
                    if (methodHandleMapper.hasField(uniqueField)) {
                        @SuppressWarnings("unchecked")
                        T typedInstance = (T) instance;
                        Object value = methodHandleMapper.getFieldValue(typedInstance, uniqueField);
                        if (value != null) {
                            String key = uniqueField + ":" + value.toString();
                            if (seenUniqueValues.contains(key)) {
                                log.warn("Duplicate value '{}' for unique field '{}' at row {}", 
                                        value, uniqueField, rowNum);
                                errorCount.incrementAndGet();
                            } else {
                                seenUniqueValues.add(key);
                            }
                        }
                    }
                }
                
                // Custom field validation rules
                for (Map.Entry<String, ValidationRule> entry : config.getFieldValidationRules().entrySet()) {
                    String fieldName = entry.getKey();
                    ValidationRule rule = entry.getValue();
                    if (methodHandleMapper.hasField(fieldName)) {
                        @SuppressWarnings("unchecked")
                        T typedInstance = (T) instance;
                        Object value = methodHandleMapper.getFieldValue(typedInstance, fieldName);
                        if (value != null) {
                            var result = rule.validate(fieldName, value, rowNum, 0);
                            if (!result.isValid()) {
                                log.warn("Validation failed for field '{}' with value '{}' at row {}: {}", 
                                        fieldName, value, rowNum, result.getErrorMessage());
                                errorCount.incrementAndGet();
                            }
                        }
                    }
                }
                
                // Global validation rules
                for (ValidationRule rule : config.getGlobalValidationRules()) {
                    var result = rule.validate("global", instance, rowNum, 0);
                    if (!result.isValid()) {
                        log.warn("Global validation failed for instance at row {}: {}", rowNum, result.getErrorMessage());
                        errorCount.incrementAndGet();
                    }
                }
                
            } catch (Exception e) {
                log.error("Validation error at row {}: {}", rowNum, e.getMessage());
                errorCount.incrementAndGet();
            }
        }
        
        private int getColumnIndex(String cellReference) {
            // Extract column index from cell reference (e.g., "A1" -> 0, "B1" -> 1)
            String colRef = cellReference.replaceAll("\\d", "");
            int colIndex = 0;
            for (int i = 0; i < colRef.length(); i++) {
                colIndex = colIndex * 26 + (colRef.charAt(i) - 'A' + 1);
            }
            return colIndex - 1;
        }
    }
    
    /**
     * Result class for true streaming processing
     */
    public static class ProcessingResult {
        private final long processedRecords;
        private final long errorCount;
        private final long processingTimeMs;
        
        public ProcessingResult(long processedRecords, long errorCount, long processingTimeMs) {
            this.processedRecords = processedRecords;
            this.errorCount = errorCount;
            this.processingTimeMs = processingTimeMs;
        }
        
        public long getProcessedRecords() { return processedRecords; }
        public long getErrorCount() { return errorCount; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public double getRecordsPerSecond() { 
            return processingTimeMs > 0 ? (processedRecords * 1000.0) / processingTimeMs : 0; 
        }
        
        @Override
        public String toString() {
            return String.format("ProcessingResult{processed=%d, errors=%d, time=%dms, rate=%.1f rec/sec}", 
                    processedRecords, errorCount, processingTimeMs, getRecordsPerSecond());
        }
    }
}