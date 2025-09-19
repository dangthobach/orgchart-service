package com.learnmore.application.utils.performance;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.demo.TrueStreamingDemo.SampleRecord;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark và performance test cho True Streaming implementation
 * So sánh performance giữa các phương pháp khác nhau
 */
@Slf4j
public class ExcelPerformanceBenchmark {
    
    @Data
    public static class BenchmarkResult {
        private String method;
        private long recordCount;
        private long processingTimeMs;
        private long memoryUsedMB;
        private long peakMemoryMB;
        private double recordsPerSecond;
        private boolean success;
        private String errorMessage;
        
        @Override
        public String toString() {
            DecimalFormat df = new DecimalFormat("#,###");
            return String.format(
                "%s: %s records in %s ms (%.2f rec/sec), Memory: %s MB (peak: %s MB) - %s",
                method, df.format(recordCount), df.format(processingTimeMs), 
                recordsPerSecond, df.format(memoryUsedMB), df.format(peakMemoryMB),
                success ? "SUCCESS" : "FAILED: " + errorMessage
            );
        }
    }
    
    public static void main(String[] args) {
        try {
            log.info("=== EXCEL PERFORMANCE BENCHMARK ===");
            
            // Test với các kích thước khác nhau
            int[] testSizes = {10_000, 50_000, 100_000, 500_000};
            
            for (int size : testSizes) {
                log.info("\n--- Testing with {} records ---", size);
                runBenchmarkSuite(size);
            }
            
        } catch (Exception e) {
            log.error("Benchmark failed", e);
        }
    }
    
    private static void runBenchmarkSuite(int recordCount) {
        try {
            // Tạo test file
            String xlsxFile = createTestXlsxFile(recordCount);
            
            // Config cho test
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(5000)
                    .enableProgressTracking(true)
                    .enableMemoryMonitoring(true)
                    .maxErrorsBeforeAbort(100)
                    .build();
            
            // Counter cho batch processing
            AtomicLong processedRecords = new AtomicLong(0);
            java.util.function.Consumer<List<SampleRecord>> batchProcessor = batch -> {
                processedRecords.addAndGet(batch.size());
                // Simulate processing
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            
            // Test True Streaming
            BenchmarkResult trueStreamingResult = benchmarkTrueStreaming(
                    xlsxFile, config, batchProcessor, recordCount);
            log.info("TRUE STREAMING: {}", trueStreamingResult);
            
            // Reset counter
            processedRecords.set(0);
            
            // Force GC
            System.gc();
            Thread.sleep(100);
            
            // Test Legacy Streaming
            BenchmarkResult legacyResult = benchmarkLegacyStreaming(
                    xlsxFile, config, batchProcessor, recordCount);
            log.info("LEGACY STREAMING: {}", legacyResult);
            
            // Comparison
            if (trueStreamingResult.isSuccess() && legacyResult.isSuccess()) {
                double speedImprovement = (legacyResult.getProcessingTimeMs() - trueStreamingResult.getProcessingTimeMs()) 
                        / (double) legacyResult.getProcessingTimeMs() * 100;
                double memoryReduction = (legacyResult.getMemoryUsedMB() - trueStreamingResult.getMemoryUsedMB()) 
                        / (double) legacyResult.getMemoryUsedMB() * 100;
                
                log.info("IMPROVEMENT: Speed: {:.1f}% faster, Memory: {:.1f}% less", 
                        speedImprovement, memoryReduction);
            }
            
            // Cleanup
            new File(xlsxFile).delete();
            
        } catch (Exception e) {
            log.error("Benchmark suite failed for {} records", recordCount, e);
        }
    }
    
    private static BenchmarkResult benchmarkTrueStreaming(
            String filePath, ExcelConfig config, 
            java.util.function.Consumer<List<SampleRecord>> batchProcessor, 
            int expectedRecords) {
        
        BenchmarkResult result = new BenchmarkResult();
        result.setMethod("TRUE_STREAMING");
        result.setRecordCount(expectedRecords);
        
        try {
            // Memory tracking
            long startMemory = getUsedMemory();
            
            // Time tracking
            long startTime = System.currentTimeMillis();
            
            try (FileInputStream fis = new FileInputStream(filePath)) {
                BufferedInputStream bis = new BufferedInputStream(fis);
                
                // Memory monitoring during processing
                Thread memoryMonitor = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            long currentMemory = getUsedMemory();
                            long current = result.getPeakMemoryMB();
                            if (currentMemory / 1024 / 1024 > current) {
                                result.setPeakMemoryMB(currentMemory / 1024 / 1024);
                            }
                            Thread.sleep(100);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                memoryMonitor.start();
                
                var processingResult = ExcelUtil.processExcelTrueStreaming(
                        bis, SampleRecord.class, config, batchProcessor);
                
                memoryMonitor.interrupt();
                
                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();
                
                result.setProcessingTimeMs(endTime - startTime);
                result.setMemoryUsedMB((endMemory - startMemory) / 1024 / 1024);
                result.setRecordsPerSecond(processingResult.getProcessedRecords() * 1000.0 / result.getProcessingTimeMs());
                result.setSuccess(processingResult.getErrorCount() == 0);
                
                if (!result.isSuccess()) {
                    result.setErrorMessage("Processing had " + processingResult.getErrorCount() + " errors");
                }
            }
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("True streaming benchmark failed", e);
        }
        
        return result;
    }
    
    private static BenchmarkResult benchmarkLegacyStreaming(
            String filePath, ExcelConfig config, 
            java.util.function.Consumer<List<SampleRecord>> batchProcessor, 
            int expectedRecords) {
        
        BenchmarkResult result = new BenchmarkResult();
        result.setMethod("LEGACY_STREAMING");
        result.setRecordCount(expectedRecords);
        
        try {
            // Memory tracking
            long startMemory = getUsedMemory();
            
            // Time tracking
            long startTime = System.currentTimeMillis();
            
            try (FileInputStream fis = new FileInputStream(filePath)) {
                BufferedInputStream bis = new BufferedInputStream(fis);
                
                // Memory monitoring during processing
                Thread memoryMonitor = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            long currentMemory = getUsedMemory();
                            long current = result.getPeakMemoryMB();
                            if (currentMemory / 1024 / 1024 > current) {
                                result.setPeakMemoryMB(currentMemory / 1024 / 1024);
                            }
                            Thread.sleep(100);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                memoryMonitor.start();
                
                @SuppressWarnings("deprecation")
                var processingResult = ExcelUtil.processExcelStreaming(
                        bis, SampleRecord.class, config, batchProcessor);
                
                memoryMonitor.interrupt();
                
                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();
                
                result.setProcessingTimeMs(endTime - startTime);
                result.setMemoryUsedMB((endMemory - startMemory) / 1024 / 1024);
                result.setRecordsPerSecond(processingResult.getProcessedRecords() * 1000.0 / result.getProcessingTimeMs());
                result.setSuccess(processingResult.getErrorCount() == 0);
                
                if (!result.isSuccess()) {
                    result.setErrorMessage("Processing had " + processingResult.getErrorCount() + " errors");
                }
            }
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("Legacy streaming benchmark failed", e);
        }
        
        return result;
    }
    
    /**
     * Tạo XLSX file thật để test
     */
    private static String createTestXlsxFile(int recordCount) throws IOException {
        String fileName = "benchmark_" + recordCount + ".xlsx";
        
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {
            
            Sheet sheet = workbook.createSheet("Data");
            
            // Header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Name");
            headerRow.createCell(2).setCellValue("Email");
            headerRow.createCell(3).setCellValue("Department");
            
            // Data rows
            for (int i = 1; i <= recordCount; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue("EMP" + String.format("%06d", i));
                row.createCell(1).setCellValue("Employee " + i);
                row.createCell(2).setCellValue("emp" + i + "@company.com");
                row.createCell(3).setCellValue("Dept" + ((i % 10) + 1));
                
                // Log progress
                if (i % 50000 == 0) {
                    log.info("Created {} rows...", i);
                }
            }
            
            workbook.write(fos);
        }
        
        log.info("Created benchmark file: {} with {} records", fileName, recordCount);
        return fileName;
    }
    
    /**
     * Get current used memory in bytes
     */
    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}