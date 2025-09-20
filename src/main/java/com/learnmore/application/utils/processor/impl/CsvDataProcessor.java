package com.learnmore.application.utils.processor.impl;

import com.learnmore.application.utils.processor.AbstractDataProcessor;
import com.learnmore.application.utils.ExcelColumn;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * CSV data processor implementation
 * Supports .csv format with customizable delimiters
 */
public class CsvDataProcessor<T> extends AbstractDataProcessor<T> {
    
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("csv", "txt");
    
    @Override
    protected ProcessingResult doProcess(InputStream inputStream, 
                                       Class<T> targetClass, 
                                       ProcessingConfiguration configuration,
                                       Consumer<List<T>> batchProcessor) {
        
        long startTime = System.currentTimeMillis();
        long processedRecords = 0;
        long errorCount = 0;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            CsvConfig csvConfig = getCsvConfig(configuration);
            String delimiter = csvConfig.getDelimiter();
            boolean hasHeader = csvConfig.isHasHeader();
            
            // Parse field mappings
            Map<Integer, Field> fieldMappings = parseFieldMappings(targetClass);
            String[] headers = null;
            
            String line;
            List<T> batch = new ArrayList<>();
            int batchSize = configuration.getBatchSize();
            
            // Skip header if present
            if (hasHeader && (line = reader.readLine()) != null) {
                headers = parseCSVLine(line, delimiter);
                fieldMappings = mapHeadersToFields(headers, targetClass);
            }
            
            while ((line = reader.readLine()) != null) {
                try {
                    T record = parseRecord(line, delimiter, targetClass, fieldMappings);
                    if (record != null) {
                        batch.add(record);
                        processedRecords++;
                        
                        if (batch.size() >= batchSize) {
                            batchProcessor.accept(new ArrayList<>(batch));
                            batch.clear();
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    logger.debug("Error parsing CSV line: {}", e.getMessage());
                    
                    if (errorCount >= configuration.getMaxErrors()) {
                        logger.warn("Maximum error count reached: {}", errorCount);
                        break;
                    }
                }
            }
            
            // Process remaining batch
            if (!batch.isEmpty()) {
                batchProcessor.accept(batch);
            }
            
            return createSuccessResult(startTime, processedRecords, errorCount, 
                    Map.of("headers", headers != null ? Arrays.asList(headers) : Collections.emptyList()));
            
        } catch (Exception e) {
            return createErrorResult(startTime, "CSV processing failed: " + e.getMessage());
        }
    }
    
    @Override
    protected ValidationResult performFormatSpecificValidation(InputStream inputStream, 
                                                             ProcessingConfiguration configuration) {
        try {
            // Basic CSV validation - check if stream has content
            if (inputStream.available() == 0) {
                return ValidationResult.invalid("Empty CSV file");
            }
            
            // Try to estimate record count by sampling
            long estimatedRecords = estimateRecordCount(inputStream);
            return ValidationResult.valid(estimatedRecords);
            
        } catch (Exception e) {
            return ValidationResult.invalid("CSV file validation failed: " + e.getMessage());
        }
    }
    
    @Override
    public List<String> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }
    
    @Override
    public String getProcessorName() {
        return "CSV";
    }
    
    // Helper methods
    
    private Map<Integer, Field> parseFieldMappings(Class<T> targetClass) {
        Map<Integer, Field> mappings = new HashMap<>();
        Field[] fields = targetClass.getDeclaredFields();
        
        // For CSV without headers, map fields by order
        int index = 0;
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                field.setAccessible(true);
                mappings.put(index++, field);
            }
        }
        
        return mappings;
    }
    
    private Map<Integer, Field> mapHeadersToFields(String[] headers, Class<T> targetClass) {
        Map<Integer, Field> mappings = new HashMap<>();
        Field[] fields = targetClass.getDeclaredFields();
        
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                String fieldName = annotation.name().isEmpty() ? field.getName() : annotation.name();
                
                for (int i = 0; i < headers.length; i++) {
                    if (fieldName.equalsIgnoreCase(headers[i].trim())) {
                        field.setAccessible(true);
                        mappings.put(i, field);
                        break;
                    }
                }
            }
        }
        
        return mappings;
    }
    
    private String[] parseCSVLine(String line, String delimiter) {
        // Simple CSV parsing - could be enhanced for quoted fields
        return line.split(delimiter, -1);
    }
    
    private T parseRecord(String line, String delimiter, Class<T> targetClass, 
                         Map<Integer, Field> fieldMappings) throws Exception {
        
        String[] values = parseCSVLine(line, delimiter);
        T record = targetClass.getDeclaredConstructor().newInstance();
        
        for (Map.Entry<Integer, Field> entry : fieldMappings.entrySet()) {
            int index = entry.getKey();
            Field field = entry.getValue();
            
            if (index < values.length) {
                String value = values[index].trim();
                if (!value.isEmpty()) {
                    setFieldValue(record, field, value);
                }
            }
        }
        
        return record;
    }
    
    private void setFieldValue(T record, Field field, String value) throws Exception {
        Class<?> fieldType = field.getType();
        
        if (fieldType == String.class) {
            field.set(record, value);
        } else if (fieldType == Integer.class || fieldType == int.class) {
            field.set(record, Integer.parseInt(value));
        } else if (fieldType == Long.class || fieldType == long.class) {
            field.set(record, Long.parseLong(value));
        } else if (fieldType == Double.class || fieldType == double.class) {
            field.set(record, Double.parseDouble(value));
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            field.set(record, Boolean.parseBoolean(value));
        } else {
            // For other types, try to convert as string
            field.set(record, value);
        }
    }
    
    private long estimateRecordCount(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            long count = 0;
            while (reader.readLine() != null && count < 1000) { // Sample first 1000 lines
                count++;
            }
            
            return count > 0 ? count : -1; // -1 indicates unknown
            
        } catch (Exception e) {
            return -1;
        }
    }
    
    private CsvConfig getCsvConfig(ProcessingConfiguration configuration) {
        Object formatConfig = configuration.getFormatSpecificConfig();
        if (formatConfig instanceof CsvConfig) {
            return (CsvConfig) formatConfig;
        }
        return new CsvConfig(); // Default config
    }
    
    /**
     * CSV-specific configuration
     */
    public static class CsvConfig {
        private String delimiter = ",";
        private boolean hasHeader = true;
        private String encoding = "UTF-8";
        
        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
        
        public boolean isHasHeader() { return hasHeader; }
        public void setHasHeader(boolean hasHeader) { this.hasHeader = hasHeader; }
        
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
    }
}