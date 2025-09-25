package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel Excel writer for maximum performance on large datasets
 * Best for > 1M records with multi-threading support
 */
@Slf4j
public class ParallelExcelWriter<T> implements ExcelWriter<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final ExecutorService executorService;
    
    // Performance tracking
    private final AtomicLong recordsWritten = new AtomicLong(0);
    private long startTime;
    private long endTime;
    
    public ParallelExcelWriter(Class<T> beanClass, ExcelConfig config) throws ExcelProcessException {
        this.beanClass = beanClass;
        this.config = config;
        this.executorService = Executors.newFixedThreadPool(config.getThreadPoolSize());
        
        log.info("ParallelExcelWriter initialized for {} with {} threads", 
                beanClass.getSimpleName(), config.getThreadPoolSize());
    }
    
    @Override
    public WritingResult write(List<T> data, OutputStream outputStream) throws ExcelProcessException {
        startTime = System.currentTimeMillis();
        
        try {
            // For now, delegate to OptimizedStreamingWriter
            // In a full implementation, this would split data into chunks
            // and process them in parallel, then merge the results
            
            OptimizedStreamingWriter<T> streamingWriter = new OptimizedStreamingWriter<>(beanClass, config);
            WritingResult result = streamingWriter.write(data, outputStream);
            
            // Update our tracking
            recordsWritten.set(result.getTotalRowsWritten());
            endTime = System.currentTimeMillis();
            
            // Return result with our strategy name
            WritingResult parallelResult = new WritingResult(getStrategyName());
            parallelResult.setTotalRowsWritten(result.getTotalRowsWritten());
            parallelResult.setTotalCellsWritten(result.getTotalCellsWritten());
            parallelResult.setProcessingTimeMs(endTime - startTime);
            parallelResult.setSuccess(true);
            
            return parallelResult;
            
        } catch (Exception e) {
            throw new ExcelProcessException("Failed to write Excel data in parallel", e);
        }
    }
    
    @Override
    public WritingResult getStatistics() {
        long duration = endTime > startTime ? endTime - startTime : 0;
        
        WritingResult result = new WritingResult(getStrategyName());
        result.setTotalRowsWritten(recordsWritten.get());
        result.setProcessingTimeMs(duration);
        result.setSuccess(true);
        
        return result;
    }
    
    @Override
    public String getStrategyName() {
        return "ParallelExcelWriter";
    }
    
    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            log.info("ParallelExcelWriter executor service shutdown");
        }
    }
    
    // Note: In a full implementation, this class would:
    // 1. Split the data into chunks based on available threads
    // 2. Create multiple SXSSF workbooks in parallel
    // 3. Write each chunk to a separate temporary file
    // 4. Merge all temporary files into a single Excel file
    // 5. Use concurrent data structures for thread-safe operations
    // 6. Implement proper error handling and resource cleanup
    
    // The current implementation serves as a placeholder that delegates
    // to OptimizedStreamingWriter while maintaining the parallel interface
}