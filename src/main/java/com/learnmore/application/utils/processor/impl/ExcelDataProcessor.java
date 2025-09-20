package com.learnmore.application.utils.processor.impl;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.processor.AbstractDataProcessor;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel data processor implementation
 * Supports .xlsx, .xls formats using ExcelUtil
 */
public class ExcelDataProcessor<T> extends AbstractDataProcessor<T> {
    
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("xlsx", "xls");
    
    @Override
    public ProcessingResult doProcess(InputStream inputStream, 
                                    Class<T> targetClass, 
                                    ProcessingConfiguration configuration,
                                    Consumer<List<T>> batchProcessor) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Convert generic configuration to ExcelConfig
            ExcelConfig excelConfig = convertToExcelConfig(configuration);
            
            // Process using ExcelUtil
            var result = ExcelUtil.processExcelTrueStreaming(
                    inputStream, targetClass, excelConfig, batchProcessor);
            
            // Convert ExcelUtil result to DataProcessor result
            return new ProcessingResult(
                    result.getProcessedRecords(),
                    result.getErrorCount(),
                    result.getProcessingTimeMs(),
                    result.getErrorCount() == 0,
                    result.getErrorCount() > 0 ? "Processing had " + result.getErrorCount() + " errors" : null,
                    result
            );
            
        } catch (Exception e) {
            return createErrorResult(startTime, "Excel processing failed: " + e.getMessage());
        }
    }
    
    @Override
    protected ValidationResult performFormatSpecificValidation(InputStream inputStream, 
                                                             ProcessingConfiguration configuration) {
        try {
            // Basic Excel file validation could be added here
            // For now, assume valid if stream is available
            if (inputStream.available() == 0) {
                return ValidationResult.invalid("Empty Excel file");
            }
            
            return ValidationResult.valid(-1); // Unknown record count for Excel
            
        } catch (Exception e) {
            return ValidationResult.invalid("Excel file validation failed: " + e.getMessage());
        }
    }
    
    @Override
    public List<String> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }
    
    @Override
    public String getProcessorName() {
        return "Excel";
    }
    
    private ExcelConfig convertToExcelConfig(ProcessingConfiguration configuration) {
        ExcelConfig.Builder builder = ExcelConfig.builder()
                .batchSize(configuration.getBatchSize())
                .strictValidation(configuration.isStrictMode())
                .maxErrorsBeforeAbort(configuration.getMaxErrors());
        
        // Apply format-specific configuration if available
        Object formatConfig = configuration.getFormatSpecificConfig();
        if (formatConfig instanceof ExcelConfig) {
            ExcelConfig excelConfig = (ExcelConfig) formatConfig;
            builder.memoryThreshold(excelConfig.getMemoryThresholdMB())
                   .parallelProcessing(excelConfig.isParallelProcessing())
                   .threadPoolSize(excelConfig.getThreadPoolSize());
        }
        
        return builder.build();
    }
}