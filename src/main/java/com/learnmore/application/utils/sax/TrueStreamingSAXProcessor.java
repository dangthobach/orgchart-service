package com.learnmore.application.utils.sax;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.converter.TypeConverter;

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
import java.lang.reflect.Field;
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
    private final Map<String, Field> fieldMapping;
    private final Consumer<List<T>> batchProcessor;
    
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
        this.fieldMapping = createFieldMapping();
        this.batchProcessor = batchProcessor;
        this.startTime = System.currentTimeMillis();
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
            
            // Setup SAX parser
            XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                stylesTable, sharedStringsTable, contentHandler, new DataFormatter(), false
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
        
        return new ProcessingResult(
            totalProcessed.get(), 
            totalErrors.get(), 
            processingTime
        );
    }
    
    /**
     * Create field mapping từ ExcelColumn annotations
     */
    private Map<String, Field> createFieldMapping() {
        Map<String, Field> mapping = new HashMap<>();
        
        for (Field field : beanClass.getDeclaredFields()) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                field.setAccessible(true);
                mapping.put(annotation.name(), field);
            }
        }
        
        return mapping;
    }
    
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
        
        @Override
        public void startRow(int rowNum) {
            this.currentRowNum = rowNum;
            
            // Skip rows before start row
            if (rowNum < config.getStartRow()) {
                return;
            }
            
            // Create new instance for data rows
            if (headerProcessed) {
                try {
                    currentInstance = beanClass.getDeclaredConstructor().newInstance();
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
                    
                    // Progress logging
                    if (totalProcessed.get() % 10000 == 0) {
                        log.info("Processed {} rows in streaming mode", totalProcessed.get());
                    }
                    
                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    log.warn("Error processing row {}: {}", rowNum, e.getMessage());
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
            
            Field field = fieldMapping.get(fieldName);
            if (field == null) {
                return;
            }
            
            try {
                // Convert and set value
                Object convertedValue = typeConverter.convert(formattedValue, field.getType());
                field.set(currentInstance, convertedValue);
                
            } catch (Exception e) {
                log.debug("Failed to set field {} with value '{}': {}", fieldName, formattedValue, e.getMessage());
            }
        }
        
        private String findFieldNameByColumnIndex(int colIndex) {
            for (Map.Entry<String, Integer> entry : headerMapping.entrySet()) {
                if (entry.getValue().equals(colIndex)) {
                    // Find field by header name
                    for (Map.Entry<String, Field> fieldEntry : fieldMapping.entrySet()) {
                        if (fieldEntry.getKey().equals(entry.getKey())) {
                            return fieldEntry.getKey();
                        }
                    }
                }
            }
            return null;
        }
        
        private void runValidations(Object instance, int rowNum) {
            try {
                // Required fields validation
                for (String requiredField : config.getRequiredFields()) {
                    Field field = fieldMapping.get(requiredField);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(instance);
                        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                            log.warn("Required field '{}' is empty at row {}", requiredField, rowNum);
                            errorCount.incrementAndGet();
                        }
                    }
                }
                
                // Unique fields validation (simple memory-based check for current batch)
                for (String uniqueField : config.getUniqueFields()) {
                    Field field = fieldMapping.get(uniqueField);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(instance);
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
                    Field field = fieldMapping.get(fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(instance);
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