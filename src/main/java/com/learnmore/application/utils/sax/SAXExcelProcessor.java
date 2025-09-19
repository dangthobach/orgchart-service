package com.learnmore.application.utils.sax;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.converter.TypeConverter;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.exception.ValidationException;
import com.learnmore.application.utils.validation.ValidationRule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SAX-based Excel processor for memory-efficient streaming
 * Uses event-driven parsing to minimize memory footprint
 */
@Slf4j
public class SAXExcelProcessor<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final List<ValidationRule> validationRules;
    private final TypeConverter typeConverter;
    private final Map<String, Field> fieldMapping;
    
    @Getter
    public static class ProcessingResult {
        private final List<Object> data;
        private final long processedRows;
        private final List<String> validationErrors;
        private final Map<String, Object> statistics;
        
        public ProcessingResult(List<Object> data, long processedRows, 
                               List<String> validationErrors, Map<String, Object> statistics) {
            this.data = data;
            this.processedRows = processedRows;
            this.validationErrors = validationErrors;
            this.statistics = statistics;
        }
    }
    
    public SAXExcelProcessor(Class<T> beanClass, ExcelConfig config, List<ValidationRule> validationRules) {
        this.beanClass = beanClass;
        this.config = config;
        this.validationRules = validationRules != null ? validationRules : new ArrayList<>();
        this.typeConverter = new TypeConverter();
        this.fieldMapping = createFieldMapping();
    }
    
    /**
     * Process Excel stream using SAX parser
     */
    public List<T> processExcelStream(InputStream inputStream) throws Exception {
        List<T> result = new ArrayList<>();
        
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            SharedStringsTable sharedStringsTable = xssfReader.getSharedStringsTable();
            StylesTable stylesTable = xssfReader.getStylesTable();
            
            // Create custom content handler
            SAXExcelContentHandler contentHandler = new SAXExcelContentHandler(
                beanClass, fieldMapping, config, validationRules, typeConverter, result
            );
            
            // Setup SAX parser
            XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                stylesTable, sharedStringsTable, contentHandler, new DataFormatter(), false
            );
            xmlReader.setContentHandler(sheetHandler);
            
            // Process first sheet
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (sheetIterator.hasNext()) {
                try (InputStream sheetStream = sheetIterator.next()) {
                    xmlReader.parse(new InputSource(sheetStream));
                }
            }
        }
        
        return result;
    }
    
    /**
     * Create field mapping from Excel column annotations
     */
    private Map<String, Field> createFieldMapping() {
        Map<String, Field> mapping = new HashMap<>();
        
        for (Field field : beanClass.getDeclaredFields()) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                field.setAccessible(true);
                mapping.put(annotation.value(), field);
            }
        }
        
        return mapping;
    }
    
    /**
     * Custom content handler for SAX-based Excel processing
     */
    private static class SAXExcelContentHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        
        private final Class<?> beanClass;
        private final Map<String, Field> fieldMapping;
        private final ExcelConfig config;
        private final List<ValidationRule> validationRules;
        private final TypeConverter typeConverter;
        private final List<Object> result;
        
        private final AtomicLong processedRows = new AtomicLong(0);
        private final List<String> validationErrors = new ArrayList<>();
        
        private Map<String, Integer> headerMapping = new HashMap<>();
        private Object currentInstance;
        private int currentRowNum = 0;
        private boolean headerProcessed = false;
        
        public SAXExcelContentHandler(Class<?> beanClass, Map<String, Field> fieldMapping,
                                     ExcelConfig config, List<ValidationRule> validationRules,
                                     TypeConverter typeConverter, List<Object> result) {
            this.beanClass = beanClass;
            this.fieldMapping = fieldMapping;
            this.config = config;
            this.validationRules = validationRules;
            this.typeConverter = typeConverter;
            this.result = result;
        }
        
        @Override
        public void startRow(int rowNum) {
            this.currentRowNum = rowNum;
            
            // Skip rows before start row
            if (rowNum < config.getStartRow()) {
                return;
            }
            
            // Process header row
            if (rowNum == config.getStartRow() && !headerProcessed) {
                return; // Header will be processed in cell method
            }
            
            // Create new instance for data rows
            if (headerProcessed) {
                try {
                    currentInstance = beanClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    log.error("Failed to create instance for row {}: {}", rowNum, e.getMessage());
                }
            }
        }
        
        @Override
        public void cell(String cellReference, String formattedValue) {
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
                    
                    // Add to result if valid
                    result.add(currentInstance);
                    processedRows.incrementAndGet();
                    
                    if (processedRows.get() % 1000 == 0) {
                        log.debug("Processed {} rows", processedRows.get());
                    }
                    
                } catch (ValidationException e) {
                    validationErrors.add("Row " + rowNum + ": " + e.getMessage());
                    log.warn("Validation error at row {}: {}", rowNum, e.getMessage());
                }
                
                currentInstance = null;
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
                log.warn("Failed to set field {} with value '{}': {}", fieldName, formattedValue, e.getMessage());
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
        
        private void runValidations(Object instance, int rowNum) throws ValidationException {
            for (ValidationRule rule : validationRules) {
                rule.validate(instance, rowNum);
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
}