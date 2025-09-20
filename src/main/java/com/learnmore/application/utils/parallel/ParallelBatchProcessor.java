package com.learnmore.application.utils.parallel;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel batch processor for high-throughput Excel processing
 * Supports concurrent processing of multiple batches with configurable thread pool
 */
@Getter
@Setter
public class ParallelBatchProcessor<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(ParallelBatchProcessor.class);
    
    private final ExecutorService executorService;
    private final Consumer<List<T>> batchProcessor;
    private final int threadPoolSize;
    private final boolean shutdownExecutorOnClose;
    
    // Performance tracking
    private final AtomicLong processedBatches = new AtomicLong(0);
    private final AtomicLong processedRecords = new AtomicLong(0);
    private final AtomicLong processingErrors = new AtomicLong(0);
    
    /**
     * Create parallel processor with custom thread pool
     */
    public ParallelBatchProcessor(Consumer<List<T>> batchProcessor, int threadPoolSize) {
        this.batchProcessor = batchProcessor;
        this.threadPoolSize = threadPoolSize;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize, 
            r -> {
                Thread t = new Thread(r, "ExcelParallel-" + Thread.currentThread().getId());
                t.setDaemon(true);
                return t;
            });
        this.shutdownExecutorOnClose = true;
        
        logger.info("Created ParallelBatchProcessor with {} threads", threadPoolSize);
    }
    
    /**
     * Create parallel processor with existing executor service
     */
    public ParallelBatchProcessor(Consumer<List<T>> batchProcessor, ExecutorService executorService) {
        this.batchProcessor = batchProcessor;
        this.executorService = executorService;
        this.threadPoolSize = -1; // Unknown for external executor
        this.shutdownExecutorOnClose = false;
        
        logger.info("Created ParallelBatchProcessor with external ExecutorService");
    }
    
    /**
     * Process batch asynchronously
     */
    public CompletableFuture<BatchProcessingResult> processBatchAsync(List<T> batch, int batchNumber) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                logger.debug("Processing batch #{} with {} records", batchNumber, batch.size());
                
                // Execute actual batch processing
                batchProcessor.accept(batch);
                
                long processingTime = System.currentTimeMillis() - startTime;
                
                processedBatches.incrementAndGet();
                processedRecords.addAndGet(batch.size());
                
                logger.debug("Completed batch #{} in {}ms", batchNumber, processingTime);
                
                return new BatchProcessingResult(batchNumber, batch.size(), true, 
                        processingTime, null);
                
            } catch (Exception e) {
                long processingTime = System.currentTimeMillis() - startTime;
                processingErrors.incrementAndGet();
                
                logger.error("Failed to process batch #{}: {}", batchNumber, e.getMessage(), e);
                
                return new BatchProcessingResult(batchNumber, batch.size(), false, 
                        processingTime, e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * Process multiple batches in parallel and wait for completion
     */
    public ParallelProcessingResult processAllBatches(List<List<T>> batches) {
        long startTime = System.currentTimeMillis();
        
        logger.info("Starting parallel processing of {} batches using {} threads", 
                batches.size(), threadPoolSize);
        
        // Submit all batches for processing
        List<CompletableFuture<BatchProcessingResult>> futures = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            List<T> batch = batches.get(i);
            CompletableFuture<BatchProcessingResult> future = processBatchAsync(batch, i + 1);
            futures.add(future);
        }
        
        // Wait for all batches to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        
        try {
            allOf.get(); // Wait for completion
            
            // Collect results
            List<BatchProcessingResult> results = new ArrayList<>();
            for (CompletableFuture<BatchProcessingResult> future : futures) {
                results.add(future.get());
            }
            
            long totalProcessingTime = System.currentTimeMillis() - startTime;
            
            // Calculate statistics
            long totalRecords = results.stream().mapToLong(BatchProcessingResult::getRecordCount).sum();
            long successfulBatches = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
            long failedBatches = results.size() - successfulBatches;
            
            double recordsPerSecond = (totalRecords * 1000.0) / totalProcessingTime;
            
            logger.info("Parallel processing completed:");
            logger.info("  Total records: {}", totalRecords);
            logger.info("  Successful batches: {}/{}", successfulBatches, results.size());
            logger.info("  Failed batches: {}", failedBatches);
            logger.info("  Total time: {}ms", totalProcessingTime);
            logger.info("  Records/second: {:.2f}", recordsPerSecond);
            
            return new ParallelProcessingResult(
                    results, totalRecords, successfulBatches, failedBatches, 
                    totalProcessingTime, recordsPerSecond);
            
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Parallel processing failed: {}", e.getMessage(), e);
            throw new RuntimeException("Parallel processing failed", e);
        }
    }
    
    /**
     * Get current processing statistics
     */
    public ProcessingStatistics getStatistics() {
        return new ProcessingStatistics(
                processedBatches.get(),
                processedRecords.get(),
                processingErrors.get());
    }
    
    /**
     * Shutdown the processor and cleanup resources
     */
    public void shutdown() {
        if (shutdownExecutorOnClose && executorService != null && !executorService.isShutdown()) {
            logger.info("Shutting down ParallelBatchProcessor");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate gracefully, forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for executor shutdown");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Result of individual batch processing
     */
    @Getter
    public static class BatchProcessingResult {
        private final int batchNumber;
        private final int recordCount;
        private final boolean success;
        private final long processingTimeMs;
        private final String errorMessage;
        
        public BatchProcessingResult(int batchNumber, int recordCount, boolean success, 
                long processingTimeMs, String errorMessage) {
            this.batchNumber = batchNumber;
            this.recordCount = recordCount;
            this.success = success;
            this.processingTimeMs = processingTimeMs;
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Result of parallel processing all batches
     */
    @Getter
    public static class ParallelProcessingResult {
        private final List<BatchProcessingResult> batchResults;
        private final long totalRecords;
        private final long successfulBatches;
        private final long failedBatches;
        private final long totalProcessingTimeMs;
        private final double recordsPerSecond;
        
        public ParallelProcessingResult(List<BatchProcessingResult> batchResults, 
                long totalRecords, long successfulBatches, long failedBatches, 
                long totalProcessingTimeMs, double recordsPerSecond) {
            this.batchResults = batchResults;
            this.totalRecords = totalRecords;
            this.successfulBatches = successfulBatches;
            this.failedBatches = failedBatches;
            this.totalProcessingTimeMs = totalProcessingTimeMs;
            this.recordsPerSecond = recordsPerSecond;
        }
        
        public boolean isAllSuccessful() {
            return failedBatches == 0;
        }
    }
    
    /**
     * Current processing statistics
     */
    @Getter
    public static class ProcessingStatistics {
        private final long processedBatches;
        private final long processedRecords;
        private final long processingErrors;
        
        public ProcessingStatistics(long processedBatches, long processedRecords, long processingErrors) {
            this.processedBatches = processedBatches;
            this.processedRecords = processedRecords;
            this.processingErrors = processingErrors;
        }
        
        public double getErrorRate() {
            return processedBatches > 0 ? (processingErrors * 100.0) / processedBatches : 0.0;
        }
    }
}