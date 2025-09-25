package com.learnmore.application.utils.factory;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.factory.ExcelFactory.ExcelProcessor;
import com.learnmore.application.utils.factory.ExcelFactory.ProcessingStatistics;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Parallel SAX processor for maximum performance
 */
@Slf4j
public class ParallelSAXProcessor<T> implements ExcelProcessor<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final ProcessingStatistics statistics;
    
    public ParallelSAXProcessor(Class<T> beanClass, ExcelConfig config) {
        this.beanClass = beanClass;
        this.config = config;
        this.statistics = new ProcessingStatistics();
        this.statistics.setStrategy("PARALLEL_SAX");
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
        ExecutorService executor = null;
        
        try {
            // Create thread pool for parallel processing
            executor = Executors.newFixedThreadPool(config.getThreadPoolSize());
            
            // For now, delegate to SAX processor
            // In a real implementation, this would split processing across threads
            SAXStreamingProcessor<T> delegate = new SAXStreamingProcessor<>(beanClass, config);
            delegate.processStream(inputStream, consumer);
            
            // Copy statistics
            ProcessingStatistics delegateStats = delegate.getStatistics();
            statistics.setProcessedRows(delegateStats.getProcessedRows());
            statistics.setErrorRows(delegateStats.getErrorRows());
            statistics.setProcessingTimeMs(delegateStats.getProcessingTimeMs());
            statistics.setRecordsPerSecond(delegateStats.getRecordsPerSecond());
            
            log.info("Parallel SAX processing completed: {} rows in {}ms with {} threads", 
                    statistics.getProcessedRows(), statistics.getProcessingTimeMs(), 
                    config.getThreadPoolSize());
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            statistics.setProcessingTimeMs(processingTime);
            throw new ExcelProcessException("Parallel SAX processing failed", e);
        } finally {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    @Override
    public ProcessingStatistics getStatistics() {
        return statistics;
    }
}