package com.learnmore.application.utils.async;

import lombok.extern.slf4j.Slf4j;
import com.learnmore.application.utils.monitoring.SimpleExcelProcessingMetrics;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Asynchronous Excel Processor using NIO.2 for non-blocking I/O operations
 * 
 * Key Features:
 * - Non-blocking file I/O with AsynchronousFileChannel
 * - CompletableFuture pipeline for async processing
 * - Efficient memory management with direct buffers
 * - Concurrent processing with back-pressure control
 * - Real-time progress monitoring and metrics
 * 
 * Performance Benefits:
 * - Non-blocking I/O prevents thread blocking on file operations
 * - Better resource utilization with async pipeline
 * - Scalable concurrent processing
 * - Reduced context switching overhead
 */
@Slf4j
public class AsyncExcelProcessor {
    
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024; // 64KB
    private static final int DEFAULT_MAX_CONCURRENT_OPERATIONS = 16;
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024; // 1MB
    
    private final int bufferSize;
    private final int maxConcurrentOperations;
    private final int chunkSize;
    private final ExecutorService executorService;
    private final SimpleExcelProcessingMetrics metrics;
    private final Semaphore concurrencyControl;
    
    public AsyncExcelProcessor() {
        this(DEFAULT_BUFFER_SIZE, DEFAULT_MAX_CONCURRENT_OPERATIONS, DEFAULT_CHUNK_SIZE);
    }
    
    public AsyncExcelProcessor(int bufferSize, int maxConcurrentOps, int chunkSize) {
        this.bufferSize = bufferSize;
        this.maxConcurrentOperations = maxConcurrentOps;
        this.chunkSize = chunkSize;
        this.executorService = Executors.newFixedThreadPool(
                Math.min(maxConcurrentOps, Runtime.getRuntime().availableProcessors()));
        this.metrics = new SimpleExcelProcessingMetrics();
        this.concurrencyControl = new Semaphore(maxConcurrentOperations);
        
        log.info("AsyncExcelProcessor initialized: bufferSize={}KB, maxConcurrentOps={}, chunkSize={}KB",
                bufferSize / 1024, maxConcurrentOps, chunkSize / 1024);
    }
    
    /**
     * Process Excel file asynchronously using non-blocking I/O
     * 
     * @param filePath Path to Excel file
     * @param processor Function to process each row
     * @param <T> Type of result from row processing
     * @return CompletableFuture with processing results
     */
    public <T> CompletableFuture<AsyncProcessingResult<T>> processExcelAsync(
            Path filePath, Function<Row, T> processor) {
        
        long startTime = System.currentTimeMillis();
        String taskId = metrics.startProcessingTimer();
        
        return readFileAsync(filePath)
                .thenCompose(fileData -> processWorkbookAsync(fileData, processor))
                .whenComplete((result, throwable) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (throwable == null) {
                        metrics.endProcessingTimer(taskId, result.getTotalRecords());
                        log.info("Async Excel processing completed: {} records in {}ms", 
                                result.getTotalRecords(), duration);
                    } else {
                        log.error("Async Excel processing failed after {}ms", duration, throwable);
                        metrics.recordProcessingError("async_processing_error", 
                                throwable instanceof Exception ? (Exception) throwable : 
                                new RuntimeException(throwable));
                    }
                });
    }
    
    /**
     * Read file asynchronously using AsynchronousFileChannel
     */
    private CompletableFuture<byte[]> readFileAsync(Path filePath) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        try {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
                    filePath, StandardOpenOption.READ);
            
            long fileSize = fileChannel.size();
            
            if (fileSize > Integer.MAX_VALUE) {
                // For very large files, read in chunks
                return readLargeFileInChunks(fileChannel, fileSize);
            } else {
                // Read entire file for smaller files
                return readSmallFileAsync(fileChannel, (int) fileSize);
            }
            
        } catch (IOException e) {
            log.error("Error opening file for async reading: {}", filePath, e);
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * Read small file (< 2GB) in single async operation
     */
    private CompletableFuture<byte[]> readSmallFileAsync(AsynchronousFileChannel fileChannel, int fileSize) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(fileSize);
        
        fileChannel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                try {
                    if (bytesRead == fileSize) {
                        attachment.flip();
                        byte[] data = new byte[attachment.remaining()];
                        attachment.get(data);
                        future.complete(data);
                    } else {
                        future.completeExceptionally(
                                new IOException("Incomplete file read: " + bytesRead + "/" + fileSize));
                    }
                } finally {
                    try {
                        fileChannel.close();
                    } catch (IOException e) {
                        log.warn("Error closing file channel", e);
                    }
                }
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    log.warn("Error closing file channel after failure", e);
                }
                future.completeExceptionally(exc);
            }
        });
        
        return future;
    }
    
    /**
     * Read large file (>2GB) in chunks using async I/O
     */
    private CompletableFuture<byte[]> readLargeFileInChunks(AsynchronousFileChannel fileChannel, long fileSize) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        List<CompletableFuture<ByteBuffer>> chunkFutures = new ArrayList<>();
        long position = 0;
        
        // Create async read operations for each chunk
        while (position < fileSize) {
            long remainingSize = fileSize - position;
            int currentChunkSize = (int) Math.min(chunkSize, remainingSize);
            
            CompletableFuture<ByteBuffer> chunkFuture = readChunkAsync(
                    fileChannel, position, currentChunkSize);
            chunkFutures.add(chunkFuture);
            
            position += currentChunkSize;
        }
        
        // Combine all chunks when complete
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    try {
                        // Combine all chunks into single byte array
                        int totalSize = chunkFutures.stream()
                                .mapToInt(cf -> cf.join().remaining())
                                .sum();
                        
                        byte[] combinedData = new byte[totalSize];
                        int offset = 0;
                        
                        for (CompletableFuture<ByteBuffer> chunkFuture : chunkFutures) {
                            ByteBuffer chunk = chunkFuture.join();
                            int chunkSize = chunk.remaining();
                            chunk.get(combinedData, offset, chunkSize);
                            offset += chunkSize;
                        }
                        
                        return combinedData;
                        
                    } finally {
                        try {
                            fileChannel.close();
                        } catch (IOException e) {
                            log.warn("Error closing file channel", e);
                        }
                    }
                })
                .whenComplete((data, throwable) -> {
                    if (throwable == null) {
                        future.complete(data);
                    } else {
                        future.completeExceptionally(throwable);
                    }
                });
        
        return future;
    }
    
    /**
     * Read single chunk asynchronously
     */
    private CompletableFuture<ByteBuffer> readChunkAsync(AsynchronousFileChannel fileChannel, 
                                                        long position, int chunkSize) {
        CompletableFuture<ByteBuffer> future = new CompletableFuture<>();
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(chunkSize);
        
        fileChannel.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead > 0) {
                    attachment.flip();
                    future.complete(attachment);
                } else {
                    future.completeExceptionally(
                            new IOException("No bytes read from position " + position));
                }
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                future.completeExceptionally(exc);
            }
        });
        
        return future;
    }
    
    /**
     * Process workbook data asynchronously
     */
    private <T> CompletableFuture<AsyncProcessingResult<T>> processWorkbookAsync(
            byte[] fileData, Function<Row, T> processor) {
        
        return CompletableFuture.supplyAsync(() -> {
            try (XSSFWorkbook workbook = new XSSFWorkbook(
                    new java.io.ByteArrayInputStream(fileData))) {
                
                return processWorkbookConcurrently(workbook, processor);
                
            } catch (Exception e) {
                log.error("Error processing workbook async", e);
                throw new RuntimeException("Workbook processing failed", e);
            }
        }, executorService);
    }
    
    /**
     * Process workbook sheets concurrently
     */
    private <T> AsyncProcessingResult<T> processWorkbookConcurrently(
            Workbook workbook, Function<Row, T> processor) {
        
        List<CompletableFuture<SheetProcessingResult<T>>> sheetFutures = new ArrayList<>();
        AtomicLong totalRecords = new AtomicLong(0);
        long startTime = System.currentTimeMillis();
        
        // Process each sheet asynchronously
        for (Sheet sheet : workbook) {
            CompletableFuture<SheetProcessingResult<T>> sheetFuture = 
                    processSheetAsync(sheet, processor, totalRecords);
            sheetFutures.add(sheetFuture);
        }
        
        // Wait for all sheets to complete
        List<SheetProcessingResult<T>> sheetResults;
        try {
            sheetResults = CompletableFuture.allOf(
                    sheetFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> sheetFutures.stream()
                            .map(CompletableFuture::join)
                            .collect(java.util.stream.Collectors.toList()))
                    .get();
        } catch (Exception e) {
            log.error("Error waiting for sheet processing completion", e);
            throw new RuntimeException("Sheet processing failed", e);
        }
        
        // Combine results
        List<T> allResults = new ArrayList<>();
        long totalProcessingTime = System.currentTimeMillis() - startTime;
        
        for (SheetProcessingResult<T> sheetResult : sheetResults) {
            allResults.addAll(sheetResult.getResults());
        }
        
        double recordsPerSecond = totalRecords.get() > 0 ? 
                (totalRecords.get() * 1000.0 / totalProcessingTime) : 0;
        
        return new AsyncProcessingResult<>(
                allResults,
                totalRecords.get(),
                totalProcessingTime,
                recordsPerSecond,
                sheetResults.size()
        );
    }
    
    /**
     * Process single sheet asynchronously
     */
    private <T> CompletableFuture<SheetProcessingResult<T>> processSheetAsync(
            Sheet sheet, Function<Row, T> processor, AtomicLong totalRecords) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire concurrency control
                concurrencyControl.acquire();
                
                List<T> sheetResults = new ArrayList<>();
                long sheetStartTime = System.currentTimeMillis();
                int processedRows = 0;
                
                Iterator<Row> rowIterator = sheet.iterator();
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    
                    try {
                        T result = processor.apply(row);
                        if (result != null) {
                            sheetResults.add(result);
                        }
                        processedRows++;
                        
                        // Progress logging
                        if (processedRows % 1000 == 0) {
                            log.debug("Sheet '{}': processed {} rows", 
                                    sheet.getSheetName(), processedRows);
                        }
                        
                    } catch (Exception e) {
                        log.warn("Error processing row {} in sheet '{}': {}", 
                                row.getRowNum(), sheet.getSheetName(), e.getMessage());
                        metrics.recordProcessingError("row_processing_error", e);
                    }
                }
                
                totalRecords.addAndGet(processedRows);
                long sheetProcessingTime = System.currentTimeMillis() - sheetStartTime;
                
                log.debug("Sheet '{}' processing completed: {} rows in {}ms", 
                        sheet.getSheetName(), processedRows, sheetProcessingTime);
                
                return new SheetProcessingResult<>(
                        sheet.getSheetName(),
                        sheetResults,
                        processedRows,
                        sheetProcessingTime
                );
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Sheet processing interrupted", e);
            } finally {
                concurrencyControl.release();
            }
        }, executorService);
    }
    
    /**
     * Get current processing metrics
     */
    public SimpleExcelProcessingMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Get current async processing statistics
     */
    public AsyncProcessingStats getAsyncStats() {
        return new AsyncProcessingStats(
                concurrencyControl.availablePermits(),
                maxConcurrentOperations - concurrencyControl.availablePermits(),
                executorService.isShutdown()
        );
    }
    
    /**
     * Shutdown processor and cleanup resources
     */
    public void shutdown() {
        log.info("Shutting down AsyncExcelProcessor");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Executor service did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("AsyncExcelProcessor shutdown completed");
    }
    
    /**
     * Result class for async processing
     */
    public static class AsyncProcessingResult<T> {
        private final List<T> results;
        private final long totalRecords;
        private final long processingTimeMs;
        private final double recordsPerSecond;
        private final int sheetsProcessed;
        
        public AsyncProcessingResult(List<T> results, long totalRecords, 
                                   long processingTimeMs, double recordsPerSecond,
                                   int sheetsProcessed) {
            this.results = results;
            this.totalRecords = totalRecords;
            this.processingTimeMs = processingTimeMs;
            this.recordsPerSecond = recordsPerSecond;
            this.sheetsProcessed = sheetsProcessed;
        }
        
        // Getters
        public List<T> getResults() { return results; }
        public long getTotalRecords() { return totalRecords; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public double getRecordsPerSecond() { return recordsPerSecond; }
        public int getSheetsProcessed() { return sheetsProcessed; }
        
        @Override
        public String toString() {
            return String.format(
                "AsyncProcessingResult{records=%d, sheets=%d, time=%dms, rate=%.2f rec/sec}",
                totalRecords, sheetsProcessed, processingTimeMs, recordsPerSecond);
        }
    }
    
    /**
     * Result class for sheet processing
     */
    private static class SheetProcessingResult<T> {
        private final String sheetName;
        private final List<T> results;
        private final int processedRows;
        private final long processingTimeMs;
        
        public SheetProcessingResult(String sheetName, List<T> results, 
                                   int processedRows, long processingTimeMs) {
            this.sheetName = sheetName;
            this.results = results;
            this.processedRows = processedRows;
            this.processingTimeMs = processingTimeMs;
        }
        
        public String getSheetName() { return sheetName; }
        public List<T> getResults() { return results; }
        public int getProcessedRows() { return processedRows; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }
    
    /**
     * Async processing statistics
     */
    public static class AsyncProcessingStats {
        private final int availablePermits;
        private final int activeOperations;
        private final boolean isShutdown;
        
        public AsyncProcessingStats(int availablePermits, int activeOperations, boolean isShutdown) {
            this.availablePermits = availablePermits;
            this.activeOperations = activeOperations;
            this.isShutdown = isShutdown;
        }
        
        public int getAvailablePermits() { return availablePermits; }
        public int getActiveOperations() { return activeOperations; }
        public boolean isShutdown() { return isShutdown; }
        
        @Override
        public String toString() {
            return String.format(
                "AsyncProcessingStats{available=%d, active=%d, shutdown=%s}",
                availablePermits, activeOperations, isShutdown);
        }
    }
}