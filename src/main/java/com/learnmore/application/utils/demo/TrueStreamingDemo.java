package com.learnmore.application.utils.demo;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import com.learnmore.application.utils.validation.ExcelEarlyValidator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo để thể hiện sự khác biệt giữa streaming cũ và TRUE streaming mới
 * Chứng minh rằng true streaming không tích lũy kết quả trong memory
 */
@Slf4j
public class TrueStreamingDemo {
    
    @Data
    public static class SampleRecord {
        @com.learnmore.application.utils.ExcelColumn(name = "ID")
        private String id;
        
        @com.learnmore.application.utils.ExcelColumn(name = "Name")
        private String name;
        
        @com.learnmore.application.utils.ExcelColumn(name = "Email")
        private String email;
        
        @com.learnmore.application.utils.ExcelColumn(name = "Department")
        private String department;
        
        public SampleRecord(String id, String name, String email, String department) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.department = department;
        }
        
        public SampleRecord() {} // Default constructor for reflection
    }
    
    public static void main(String[] args) {
        try {
            // Demo early validation
            demoEarlyValidation();
            
            // Demo true streaming vs legacy streaming
            demoTrueStreamingVsLegacy();
            
        } catch (Exception e) {
            log.error("Demo failed", e);
        }
    }
    
    /**
     * Demo early validation - kiểm tra kích thước file trước khi xử lý
     */
    private static void demoEarlyValidation() {
        log.info("\n=== EARLY VALIDATION DEMO ===");
        
        try {
            // Tạo mock Excel data để test
            String testFile = createMockExcelFile(100_000); // 100k records
            
            try (FileInputStream fis = new FileInputStream(testFile)) {
                // Wrap with BufferedInputStream để support mark/reset
                BufferedInputStream bis = new BufferedInputStream(fis);
                
                // Early validation với limit thấp để test validation
                ExcelEarlyValidator.EarlyValidationResult result = 
                    ExcelEarlyValidator.validateRecordCount(bis, 50_000, 1);
                
                log.info("Early validation result: {}", result.isValid());
                log.info("Data rows: {}, Total rows: {}", result.getDataRows(), result.getTotalRows());
                log.info("Estimated cells: {}", result.estimatedCells());
                log.info("Estimated memory: {} MB", result.estimatedMemoryMB());
                
                if (!result.isValid()) {
                    log.info("Error: {}", result.getErrorMessage());
                    log.info("Recommendations:\n{}", result.getRecommendation());
                }
            }
            
            // Cleanup
            new File(testFile).delete();
            
        } catch (Exception e) {
            log.error("Early validation demo failed", e);
        }
    }
    
    /**
     * Demo sự khác biệt giữa TRUE streaming và legacy streaming
     */
    private static void demoTrueStreamingVsLegacy() {
        log.info("\n=== TRUE STREAMING vs LEGACY DEMO ===");
        
        try {
            // Tạo test file với kích thước vừa phải
            String testFile = createMockExcelFile(10_000); // 10k records cho demo
            
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(1000)
                    .enableProgressTracking(true)
                    .enableMemoryMonitoring(true)
                    .build();
            
            // Counter để đếm số batch được xử lý
            AtomicLong processedBatches = new AtomicLong(0);
            AtomicLong processedRecords = new AtomicLong(0);
            
            // Batch processor - mô phỏng xử lý dữ liệu (insert DB, etc.)
            java.util.function.Consumer<List<SampleRecord>> batchProcessor = batch -> {
                long batchNum = processedBatches.incrementAndGet();
                long totalRecords = processedRecords.addAndGet(batch.size());
                
                log.info("Processing batch #{}: {} records (Total: {})", 
                        batchNum, batch.size(), totalRecords);
                
                // Mô phỏng xử lý (insert vào DB, gọi API, etc.)
                try {
                    Thread.sleep(10); // Simulate processing time
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Log sample record từ batch
                if (!batch.isEmpty()) {
                    SampleRecord sample = batch.get(0);
                    log.debug("Sample record: ID={}, Name={}", sample.getId(), sample.getName());
                }
            };
            
            // Test TRUE STREAMING
            log.info("--- Testing TRUE STREAMING ---");
            long startMemory = getUsedMemory();
            long startTime = System.currentTimeMillis();
            
            try (FileInputStream fis = new FileInputStream(testFile)) {
                BufferedInputStream bis = new BufferedInputStream(fis);
                
                TrueStreamingSAXProcessor.ProcessingResult result = 
                    ExcelUtil.processExcelTrueStreaming(bis, SampleRecord.class, config, batchProcessor);
                
                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();
                
                log.info("TRUE STREAMING Results: {}", result);
                log.info("Time: {}ms, Memory delta: {} MB", 
                        endTime - startTime, (endMemory - startMemory) / 1024 / 1024);
            }
            
            // Reset counters
            processedBatches.set(0);
            processedRecords.set(0);
            
            // Force GC để so sánh memory sạch
            System.gc();
            Thread.sleep(100);
            
            // Test LEGACY STREAMING (deprecated method)
            log.info("--- Testing LEGACY STREAMING ---");
            startMemory = getUsedMemory();
            startTime = System.currentTimeMillis();
            
            try (FileInputStream fis = new FileInputStream(testFile)) {
                BufferedInputStream bis = new BufferedInputStream(fis);
                
                @SuppressWarnings("deprecation")
                com.learnmore.application.utils.streaming.StreamingExcelProcessor.ProcessingResult legacyResult = 
                    ExcelUtil.processExcelStreaming(bis, SampleRecord.class, config, batchProcessor);
                
                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();
                
                log.info("LEGACY STREAMING Results: Processed={}, Errors={}, Time={}ms", 
                        legacyResult.getProcessedRecords(), legacyResult.getErrorCount(), 
                        legacyResult.getProcessingTimeMs());
                log.info("Time: {}ms, Memory delta: {} MB", 
                        endTime - startTime, (endMemory - startMemory) / 1024 / 1024);
            }
            
            // Cleanup
            new File(testFile).delete();
            
            log.info("--- COMPARISON SUMMARY ---");
            log.info("TRUE STREAMING: Processes batches immediately, minimal memory footprint");
            log.info("LEGACY STREAMING: Accumulates results in memory, higher memory usage");
            
        } catch (Exception e) {
            log.error("Streaming comparison demo failed", e);
        }
    }
    
    /**
     * Tạo mock Excel file để test (simplified - chỉ tạo CSV)
     */
    private static String createMockExcelFile(int recordCount) throws IOException {
        String fileName = "mock_data_" + recordCount + ".csv";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            // Header
            writer.println("ID,Name,Email,Department");
            
            // Data rows
            for (int i = 1; i <= recordCount; i++) {
                writer.printf("EMP%06d,Employee %d,emp%d@company.com,Dept%d%n", 
                        i, i, i, (i % 10) + 1);
            }
        }
        
        log.info("Created mock file: {} with {} records", fileName, recordCount);
        return fileName;
    }
    
    /**
     * Get current used memory
     */
    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}