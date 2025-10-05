package com.learnmore.application.utils.processor;

import com.learnmore.application.utils.processor.impl.CsvDataProcessor;
// Removed ExcelDataProcessor - Excel processing handled by ExcelFacade/Services
import com.learnmore.application.utils.processor.impl.JsonDataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing data processors
 * Provides processor instances for different data formats
 */
public class DataProcessorFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(DataProcessorFactory.class);
    
    private final Map<String, DataProcessor<?>> processorCache = new ConcurrentHashMap<>();
    
    public DataProcessorFactory() {
        initializeDefaultProcessors();
    }
    
    /**
     * Get processor for specific format
     * 
     * @param format File format/extension
     * @return DataProcessor instance or null if not supported
     */
    @SuppressWarnings("unchecked")
    public <T> DataProcessor<T> getProcessor(String format) {
        if (format == null || format.trim().isEmpty()) {
            return null;
        }
        
        String normalizedFormat = normalizeFormat(format);
        DataProcessor<?> processor = processorCache.get(normalizedFormat);
        
        if (processor == null) {
            processor = createProcessor(normalizedFormat);
            if (processor != null) {
                processorCache.put(normalizedFormat, processor);
            }
        }
        
        return (DataProcessor<T>) processor;
    }
    
    /**
     * Get processor by file name/path
     * 
     * @param fileName File name or path
     * @return DataProcessor instance or null if not supported
     */
    public <T> DataProcessor<T> getProcessorByFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        
        String format = extractFormatFromFileName(fileName);
        return getProcessor(format);
    }
    
    /**
     * Check if format is supported
     * 
     * @param format File format/extension
     * @return true if supported
     */
    public boolean isFormatSupported(String format) {
        return getProcessor(format) != null;
    }
    
    /**
     * Get all supported formats
     * 
     * @return Set of supported formats
     */
    public Set<String> getSupportedFormats() {
        Set<String> allFormats = new HashSet<>();
        
        for (DataProcessor<?> processor : processorCache.values()) {
            allFormats.addAll(processor.getSupportedFormats());
        }
        
        return allFormats;
    }
    
    /**
     * Get processor information
     * 
     * @return Map of format to processor name
     */
    public Map<String, String> getProcessorInfo() {
        Map<String, String> info = new HashMap<>();
        
        for (DataProcessor<?> processor : processorCache.values()) {
            for (String format : processor.getSupportedFormats()) {
                info.put(format, processor.getProcessorName());
            }
        }
        
        return info;
    }
    
    /**
     * Register custom processor
     * 
     * @param processor Custom processor instance
     */
    public void registerProcessor(DataProcessor<?> processor) {
        if (processor == null) {
            throw new IllegalArgumentException("Processor cannot be null");
        }
        
        for (String format : processor.getSupportedFormats()) {
            String normalizedFormat = normalizeFormat(format);
            processorCache.put(normalizedFormat, processor);
            logger.info("Registered processor '{}' for format: {}", 
                    processor.getProcessorName(), normalizedFormat);
        }
    }
    
    /**
     * Create default processing configuration
     * 
     * @param batchSize Batch size for processing
     * @param strictMode Enable strict validation
     * @param maxErrors Maximum allowed errors
     * @return Default configuration
     */
    public DataProcessor.ProcessingConfiguration createDefaultConfiguration(int batchSize, boolean strictMode, int maxErrors) {
        return new DefaultProcessingConfiguration(batchSize, strictMode, maxErrors, null);
    }
    
    /**
     * Create configuration with format-specific settings
     * 
     * @param batchSize Batch size for processing
     * @param strictMode Enable strict validation
     * @param maxErrors Maximum allowed errors
     * @param formatSpecificConfig Format-specific configuration object
     * @return Configuration with format settings
     */
    public DataProcessor.ProcessingConfiguration createConfiguration(int batchSize, boolean strictMode, 
                                                     int maxErrors, Object formatSpecificConfig) {
        return new DefaultProcessingConfiguration(batchSize, strictMode, maxErrors, formatSpecificConfig);
    }
    
    /**
     * Shutdown factory and cleanup resources
     */
    public void shutdown() {
        for (DataProcessor<?> processor : processorCache.values()) {
            if (processor instanceof AbstractDataProcessor) {
                ((AbstractDataProcessor<?>) processor).shutdown();
            }
        }
        processorCache.clear();
        
        logger.info("DataProcessorFactory shutdown completed");
    }
    
    // Private helper methods
    
    private void initializeDefaultProcessors() {
        // Register built-in processors
        // Excel handled externally; no ExcelDataProcessor registration
        registerProcessor(new CsvDataProcessor<>());
        registerProcessor(new JsonDataProcessor<>());
        
        logger.info("Initialized default processors: {}", getProcessorInfo());
    }
    
    private DataProcessor<?> createProcessor(String format) {
        switch (format.toLowerCase()) {
            case "xlsx":
            case "xls":
                return null; // Excel processing not available via DataProcessor
            case "csv":
            case "txt":
                return new CsvDataProcessor<>();
            case "json":
            case "jsonl":
                return new JsonDataProcessor<>();
            default:
                logger.warn("No processor found for format: {}", format);
                return null;
        }
    }
    
    private String normalizeFormat(String format) {
        if (format == null) return "";
        
        String normalized = format.toLowerCase().trim();
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        
        return normalized;
    }
    
    private String extractFormatFromFileName(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
    
    /**
     * Default implementation of ProcessingConfiguration
     */
    private static class DefaultProcessingConfiguration implements DataProcessor.ProcessingConfiguration {
        private final int batchSize;
        private final boolean strictMode;
        private final int maxErrors;
        private final Object formatSpecificConfig;
        
        public DefaultProcessingConfiguration(int batchSize, boolean strictMode, 
                                            int maxErrors, Object formatSpecificConfig) {
            this.batchSize = batchSize;
            this.strictMode = strictMode;
            this.maxErrors = maxErrors;
            this.formatSpecificConfig = formatSpecificConfig;
        }
        
        @Override
        public int getBatchSize() { return batchSize; }
        
        @Override
        public boolean isStrictMode() { return strictMode; }
        
        @Override
        public int getMaxErrors() { return maxErrors; }
        
        @Override
        public Object getFormatSpecificConfig() { return formatSpecificConfig; }
        
        @Override
        public String toString() {
            return String.format("ProcessingConfiguration{batchSize=%d, strictMode=%s, maxErrors=%d}", 
                    batchSize, strictMode, maxErrors);
        }
    }
}