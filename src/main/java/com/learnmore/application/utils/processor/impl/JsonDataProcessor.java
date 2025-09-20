package com.learnmore.application.utils.processor.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmore.application.utils.processor.AbstractDataProcessor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * JSON data processor implementation
 * Supports .json, .jsonl (JSON Lines) formats
 */
public class JsonDataProcessor<T> extends AbstractDataProcessor<T> {
    
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("json", "jsonl");
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
            
            JsonConfig jsonConfig = getJsonConfig(configuration);
            boolean isJsonLines = jsonConfig.isJsonLines();
            
            List<T> batch = new ArrayList<>();
            int batchSize = configuration.getBatchSize();
            
            if (isJsonLines) {
                // Process JSON Lines format (one JSON object per line)
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        if (!line.trim().isEmpty()) {
                            T record = objectMapper.readValue(line, targetClass);
                            if (record != null) {
                                batch.add(record);
                                processedRecords++;
                                
                                if (batch.size() >= batchSize) {
                                    batchProcessor.accept(new ArrayList<>(batch));
                                    batch.clear();
                                }
                            }
                        }
                    } catch (Exception e) {
                        errorCount++;
                        logger.debug("Error parsing JSON line: {}", e.getMessage());
                        
                        if (errorCount >= configuration.getMaxErrors()) {
                            logger.warn("Maximum error count reached: {}", errorCount);
                            break;
                        }
                    }
                }
                
            } else {
                // Process standard JSON format (single array or object)
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
                
                String jsonContent = jsonBuilder.toString().trim();
                try {
                    JsonNode rootNode = objectMapper.readTree(jsonContent);
                    
                    if (rootNode.isArray()) {
                        // Process JSON array
                        for (JsonNode elementNode : rootNode) {
                            try {
                                T record = objectMapper.treeToValue(elementNode, targetClass);
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
                                if (errorCount >= configuration.getMaxErrors()) {
                                    break;
                                }
                            }
                        }
                    } else {
                        // Process single JSON object
                        T record = objectMapper.treeToValue(rootNode, targetClass);
                        if (record != null) {
                            batch.add(record);
                            processedRecords++;
                        }
                    }
                    
                } catch (Exception e) {
                    return createErrorResult(startTime, "JSON parsing failed: " + e.getMessage());
                }
            }
            
            // Process remaining batch
            if (!batch.isEmpty()) {
                batchProcessor.accept(batch);
            }
            
            return createSuccessResult(startTime, processedRecords, errorCount, 
                    Map.of("format", isJsonLines ? "JSON Lines" : "JSON",
                           "totalProcessed", processedRecords));
            
        } catch (Exception e) {
            return createErrorResult(startTime, "JSON processing failed: " + e.getMessage());
        }
    }
    
    @Override
    protected ValidationResult performFormatSpecificValidation(InputStream inputStream, 
                                                             ProcessingConfiguration configuration) {
        try {
            if (inputStream.available() == 0) {
                return ValidationResult.invalid("Empty JSON file");
            }
            
            // Try to parse a small portion to validate JSON format
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                
                StringBuilder sample = new StringBuilder();
                String line;
                int lineCount = 0;
                
                while ((line = reader.readLine()) != null && lineCount < 10) {
                    sample.append(line);
                    lineCount++;
                }
                
                String sampleContent = sample.toString().trim();
                if (sampleContent.isEmpty()) {
                    return ValidationResult.invalid("Empty JSON content");
                }
                
                // Basic JSON validation
                objectMapper.readTree(sampleContent);
                
                return ValidationResult.valid(lineCount); // Rough estimate
                
            } catch (Exception e) {
                return ValidationResult.invalid("Invalid JSON format: " + e.getMessage());
            }
            
        } catch (Exception e) {
            return ValidationResult.invalid("JSON file validation failed: " + e.getMessage());
        }
    }
    
    @Override
    public List<String> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }
    
    @Override
    public String getProcessorName() {
        return "JSON";
    }
    
    private JsonConfig getJsonConfig(ProcessingConfiguration configuration) {
        Object formatConfig = configuration.getFormatSpecificConfig();
        if (formatConfig instanceof JsonConfig) {
            return (JsonConfig) formatConfig;
        }
        return new JsonConfig(); // Default config
    }
    
    /**
     * JSON-specific configuration
     */
    public static class JsonConfig {
        private boolean jsonLines = false;
        private boolean ignoreUnknownProperties = true;
        private String dateFormat = "yyyy-MM-dd HH:mm:ss";
        
        public boolean isJsonLines() { return jsonLines; }
        public void setJsonLines(boolean jsonLines) { this.jsonLines = jsonLines; }
        
        public boolean isIgnoreUnknownProperties() { return ignoreUnknownProperties; }
        public void setIgnoreUnknownProperties(boolean ignoreUnknownProperties) { 
            this.ignoreUnknownProperties = ignoreUnknownProperties; 
        }
        
        public String getDateFormat() { return dateFormat; }
        public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    }
}