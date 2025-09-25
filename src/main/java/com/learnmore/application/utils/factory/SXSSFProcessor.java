package com.learnmore.application.utils.factory;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.factory.ExcelFactory.ExcelProcessor;
import com.learnmore.application.utils.factory.ExcelFactory.ProcessingStatistics;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SXSSF streaming processor for large Excel files
 */
@Slf4j
public class SXSSFProcessor<T> implements ExcelProcessor<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final ProcessingStatistics statistics;
    
    public SXSSFProcessor(Class<T> beanClass, ExcelConfig config) {
        this.beanClass = beanClass;
        this.config = config;
        this.statistics = new ProcessingStatistics();
        this.statistics.setStrategy("SXSSF");
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
            // For now, delegate to SAX processor
            // In a real implementation, this would use POI's SXSSF streaming
            SAXStreamingProcessor<T> delegate = new SAXStreamingProcessor<>(beanClass, config);
            delegate.processStream(inputStream, consumer);
            
            // Copy statistics
            ProcessingStatistics delegateStats = delegate.getStatistics();
            statistics.setProcessedRows(delegateStats.getProcessedRows());
            statistics.setErrorRows(delegateStats.getErrorRows());
            statistics.setProcessingTimeMs(delegateStats.getProcessingTimeMs());
            statistics.setRecordsPerSecond(delegateStats.getRecordsPerSecond());
            
            log.info("SXSSF processing completed: {} rows in {}ms", 
                    statistics.getProcessedRows(), statistics.getProcessingTimeMs());
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            statistics.setProcessingTimeMs(processingTime);
            throw new ExcelProcessException("SXSSF processing failed", e);
        }
    }
    
    @Override
    public ProcessingStatistics getStatistics() {
        return statistics;
    }
}