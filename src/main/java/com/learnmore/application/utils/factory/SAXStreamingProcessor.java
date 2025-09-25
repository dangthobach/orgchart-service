package com.learnmore.application.utils.factory;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.factory.ExcelFactory.ExcelProcessor;
import com.learnmore.application.utils.factory.ExcelFactory.ProcessingStatistics;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SAX-based streaming processor for very large Excel files
 */
@Slf4j
public class SAXStreamingProcessor<T> implements ExcelProcessor<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final ProcessingStatistics statistics;
    
    public SAXStreamingProcessor(Class<T> beanClass, ExcelConfig config) {
        this.beanClass = beanClass;
        this.config = config;
        this.statistics = new ProcessingStatistics();
        this.statistics.setStrategy("SAX_STREAMING");
    }
    
    @Override
    public List<T> process(InputStream inputStream) throws ExcelProcessException {
        List<T> results = new ArrayList<>();
        processStream(inputStream, results::addAll);
        return results;
    }
    
    @Override
    public void processStream(InputStream inputStream, Consumer<List<T>> consumer) 
            throws ExcelProcessException {
        
        long startTime = System.currentTimeMillis();
        
        try {
            TrueStreamingSAXProcessor<T> processor = new TrueStreamingSAXProcessor<>(
                beanClass,
                config,
                config.getGlobalValidationRules(),
                consumer
            );
            
            TrueStreamingSAXProcessor.ProcessingResult result = 
                processor.processExcelStreamTrue(inputStream);
            
            // Update statistics
            statistics.setProcessedRows(result.getProcessedRecords());
            statistics.setErrorRows(result.getErrorCount());
            statistics.setProcessingTimeMs(result.getProcessingTimeMs());
            statistics.setRecordsPerSecond(result.getRecordsPerSecond());
            
            log.info("SAX Streaming completed: {} rows in {}ms ({} rows/sec)", 
                    result.getProcessedRecords(), 
                    result.getProcessingTimeMs(),
                    String.format("%.2f", result.getRecordsPerSecond()));
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            statistics.setProcessingTimeMs(processingTime);
            throw new ExcelProcessException("SAX streaming processing failed", e);
        }
    }
    
    @Override
    public ProcessingStatistics getStatistics() {
        return statistics;
    }
}