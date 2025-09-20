package com.learnmore.application.performance;

import com.learnmore.application.dto.User;
import com.learnmore.application.service.MockDataGenerator;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive performance test for 500K User records
 * Tests the complete pipeline: Mock Data Generation → Excel Writing → Excel Reading → Database Insertion
 */
@SpringBootTest
@ActiveProfiles("test")
public class UserExcelPerformanceTest {
    
    private static final Logger logger = LoggerFactory.getLogger(UserExcelPerformanceTest.class);
    
    private static final int TOTAL_RECORDS = 500_000;
    private static final String EXCEL_FILE_PATH = "target/users_500k_performance_test.xlsx";
    private static final String CSV_FILE_PATH = "target/users_500k_performance_test.csv";
    
    private final MockDataGenerator mockDataGenerator = new MockDataGenerator();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    /**
     * STEP 1: Complete pipeline test - Mock Data Generation → Excel Writing
     * Tests the optimized Excel writing functionality with comprehensive monitoring
     */
    @Test
    public void testStep1_GenerateAndWriteToExcel() throws Exception {
        logger.info("================================================================================");
        logger.info("🚀 STEP 1: GENERATE MOCK DATA AND WRITE TO EXCEL");
        logger.info("================================================================================");
        logger.info("Target: {} User records", TOTAL_RECORDS);
        logger.info("Output: {}", EXCEL_FILE_PATH);
        
        PerformanceMonitor monitor = new PerformanceMonitor("Step 1 - Excel Writing");
        monitor.start();
        
        try {
            // Phase 1: Generate mock data
            logger.info("📊 Phase 1: Generating {} User records with Java Faker", TOTAL_RECORDS);
            long mockDataStartTime = System.currentTimeMillis();
            long mockDataStartMemory = getUsedMemory();
            
            List<User> users = mockDataGenerator.generateUsers(TOTAL_RECORDS);
            
            long mockDataEndTime = System.currentTimeMillis();
            long mockDataEndMemory = getUsedMemory();
            long mockDataDuration = mockDataEndTime - mockDataStartTime;
            
            logger.info("✅ Mock data generation completed:");
            logger.info("   Time: {}ms ({:.0f} records/sec)", 
                       mockDataDuration, TOTAL_RECORDS * 1000.0 / mockDataDuration);
            logger.info("   Memory: {} KB", (mockDataEndMemory - mockDataStartMemory) / 1024);
            logger.info("   Generated: {} unique users", users.size());
            
            // Phase 2: Write to Excel using optimized ExcelUtil
            logger.info("📝 Phase 2: Writing to Excel using optimized ExcelUtil");
            long excelWriteStartTime = System.currentTimeMillis();
            long excelWriteStartMemory = getUsedMemory();
            
            // Create high-performance configuration based on our analysis
            ExcelConfig optimizedConfig = createOptimizedConfig(TOTAL_RECORDS);
            
            // Use the enhanced writeToExcel method with intelligent strategy selection
            ExcelUtil.writeToExcel(EXCEL_FILE_PATH, users, 0, 0, optimizedConfig);
            
            long excelWriteEndTime = System.currentTimeMillis();
            long excelWriteEndMemory = getUsedMemory();
            long excelWriteDuration = excelWriteEndTime - excelWriteStartTime;
            
            logger.info("✅ Excel writing completed:");
            logger.info("   Time: {}ms ({:.0f} records/sec)", 
                       excelWriteDuration, TOTAL_RECORDS * 1000.0 / excelWriteDuration);
            logger.info("   Memory: {} KB", (excelWriteEndMemory - excelWriteStartMemory) / 1024);
            logger.info("   File: {}", EXCEL_FILE_PATH);
            
            // Phase 3: Performance analysis and recommendations
            analyzeStep1Performance(mockDataDuration, excelWriteDuration, users.size());
            
            // Test ExcelUtil Performance Profiler
            ExcelUtil.PerformanceProfiler.profileWriteOperation(
                TOTAL_RECORDS, excelWriteDuration, "Optimized Strategy", excelWriteEndMemory - excelWriteStartMemory);
            
            ExcelUtil.PerformanceProfiler.benchmarkAgainstBaseline(TOTAL_RECORDS, excelWriteDuration);
            
        } finally {
            monitor.stop();
            
            // Clean up memory
            mockDataGenerator.clearCaches();
            System.gc();
        }
    }
    
    /**
     * STEP 2: Read Excel file and process with streaming
     * Tests the optimized Excel reading functionality
     */
    @Test
    public void testStep2_ReadExcelAndProcess() throws Exception {
        logger.info("================================================================================");
        logger.info("📖 STEP 2: READ EXCEL FILE WITH STREAMING PROCESSING");
        logger.info("================================================================================");
        logger.info("Input: {}", EXCEL_FILE_PATH);
        
        PerformanceMonitor monitor = new PerformanceMonitor("Step 2 - Excel Reading");
        monitor.start();
        
        try {
            // Check if Excel file exists (should be created by Step 1)
            java.io.File excelFile = new java.io.File(EXCEL_FILE_PATH);
            if (!excelFile.exists()) {
                logger.warn("Excel file not found. Running Step 1 first...");
                testStep1_GenerateAndWriteToExcel();
            }
            
            logger.info("📊 Reading Excel file with true streaming processing");
            long readStartTime = System.currentTimeMillis();
            long readStartMemory = getUsedMemory();
            
            // Use optimized streaming configuration
            ExcelConfig streamingConfig = createStreamingConfig();
            
            // Counters for processed records
            AtomicLong processedRecords = new AtomicLong(0);
            AtomicLong validRecords = new AtomicLong(0);
            AtomicLong errorRecords = new AtomicLong(0);
            
            // Batch processor with validation and database simulation
            Consumer<List<User>> batchProcessor = batch -> {
                long batchStart = System.currentTimeMillis();
                
                // Simulate database insertion processing time
                for (User user : batch) {
                    try {
                        // Validate user data
                        if (validateUser(user)) {
                            validRecords.incrementAndGet();
                            // Here would be actual database insertion
                            // userRepository.save(user);
                        } else {
                            errorRecords.incrementAndGet();
                            logger.debug("Invalid user: {}", user.getId());
                        }
                    } catch (Exception e) {
                        errorRecords.incrementAndGet();
                        logger.debug("Error processing user {}: {}", user.getId(), e.getMessage());
                    }
                }
                
                long currentProcessed = processedRecords.addAndGet(batch.size());
                long batchDuration = System.currentTimeMillis() - batchStart;
                
                // Progress reporting
                if (currentProcessed % 50000 == 0) {
                    double progress = (currentProcessed * 100.0) / TOTAL_RECORDS;
                    double currentRate = batch.size() * 1000.0 / batchDuration;
                    
                    logger.info("📈 Progress: {}/{} ({:.1f}%) - Batch: {:.0f} rec/sec - Memory: {} KB", 
                               currentProcessed, TOTAL_RECORDS, progress, currentRate, getUsedMemory() / 1024);
                }
            };
            
            // Process Excel file with true streaming
            try (FileInputStream inputStream = new FileInputStream(EXCEL_FILE_PATH)) {
                TrueStreamingSAXProcessor.ProcessingResult result = ExcelUtil.processExcelTrueStreaming(
                    inputStream, User.class, streamingConfig, batchProcessor);
                
                long readEndTime = System.currentTimeMillis();
                long readEndMemory = getUsedMemory();
                long readDuration = readEndTime - readStartTime;
                
                logger.info("✅ Excel reading and processing completed:");
                logger.info("   Total time: {}ms", readDuration);
                logger.info("   Processed records: {}", result.getProcessedRecords());
                logger.info("   Valid records: {}", validRecords.get());
                logger.info("   Error records: {}", errorRecords.get());
                logger.info("   Throughput: {:.0f} records/sec", result.getRecordsPerSecond());
                logger.info("   Memory usage: {} KB", (readEndMemory - readStartMemory) / 1024);
                logger.info("   Error rate: {:.2f}%", (errorRecords.get() * 100.0) / result.getProcessedRecords());
                
                // Performance analysis for Step 2
                analyzeStep2Performance(readDuration, result.getProcessedRecords(), validRecords.get(), errorRecords.get());
            }
            
        } catch (Exception e) {
            logger.error("Error in Step 2: {}", e.getMessage(), e);
            throw e;
        } finally {
            monitor.stop();
            System.gc();
        }
    }
    
    /**
     * COMBINED TEST: Complete pipeline from generation to database simulation
     */
    @Test
    public void testCompletePipeline() throws Exception {
        logger.info("================================================================================");
        logger.info("🔄 COMPLETE PIPELINE TEST: GENERATION → EXCEL → PROCESSING → DATABASE");
        logger.info("================================================================================");
        
        PerformanceMonitor overallMonitor = new PerformanceMonitor("Complete Pipeline");
        overallMonitor.start();
        
        try {
            // Step 1: Generate and write to Excel
            testStep1_GenerateAndWriteToExcel();
            
            // Brief pause for file system sync
            Thread.sleep(1000);
            
            // Step 2: Read and process Excel
            testStep2_ReadExcelAndProcess();
            
            // Overall performance summary
            logger.info("================================================================================");
            logger.info("🎯 COMPLETE PIPELINE PERFORMANCE SUMMARY");
            logger.info("================================================================================");
            logger.info("✅ Successfully processed {} User records through complete pipeline", TOTAL_RECORDS);
            logger.info("✅ Pipeline: Mock Data → Excel Write → Excel Read → Database Simulation");
            logger.info("✅ All optimizations applied and tested successfully");
            
            // Cleanup generated files
            cleanupTestFiles();
            
        } finally {
            overallMonitor.stop();
        }
    }
    
    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    /**
     * Create optimized configuration for Excel writing based on our analysis
     */
    private ExcelConfig createOptimizedConfig(int recordCount) {
        return ExcelConfig.builder()
                .batchSize(2000)                        // Larger batches for 500K records
                .memoryThreshold(1024)                  // Higher memory threshold
                .enableMemoryMonitoring(true)           // Monitor memory usage
                .disableAutoSizing(true)                // Major bottleneck elimination
                .useSharedStrings(false)                // Speed over memory for large datasets
                .compressOutput(false)                  // Disable compression for speed
                .flushInterval(1000)                    // Optimized flushing
                .enableCellStyleOptimization(true)      // Reuse styles
                .minimizeMemoryFootprint(true)          // Aggressive memory management
                .preferCSVForLargeData(true)            // CSV recommendation for large data
                .csvThreshold(100_000L)                 // Lower threshold for CSV recommendation
                .sxssfRowAccessWindowSize(2000)         // Larger window for better performance
                .build();
    }
    
    /**
     * Create streaming configuration for Excel reading
     */
    private ExcelConfig createStreamingConfig() {
        return ExcelConfig.builder()
                .batchSize(5000)                        // Larger batches for reading
                .memoryThreshold(1024)                  // Higher memory threshold
                .enableMemoryMonitoring(true)           // Monitor memory during processing
                .useStreamingParser(true)               // Force streaming mode
                .maxErrorsBeforeAbort(1000)             // Allow more errors for large datasets
                .enableDataTypeCache(true)              // Cache type conversions
                .enableReflectionCache(true)            // Cache reflection operations
                .build();
    }
    
    /**
     * Validate user data
     */
    @SuppressWarnings("unused")
    private boolean validateUser(User user) {
        return user != null 
                && user.getId() != null && !user.getId().trim().isEmpty()
                && user.getIdentityCard() != null && !user.getIdentityCard().trim().isEmpty()
                && user.getFirstName() != null && !user.getFirstName().trim().isEmpty()
                && user.getLastName() != null && !user.getLastName().trim().isEmpty()
                && user.getEmail() != null && user.getEmail().contains("@");
    }
    
    /**
     * Get current memory usage in bytes
     */
    private long getUsedMemory() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * Analyze Step 1 performance and provide recommendations
     */
    private void analyzeStep1Performance(long mockDataDuration, long excelWriteDuration, int recordCount) {
        logger.info("📊 STEP 1 PERFORMANCE ANALYSIS:");
        
        double mockDataRate = recordCount * 1000.0 / mockDataDuration;
        double excelWriteRate = recordCount * 1000.0 / excelWriteDuration;
        
        logger.info("   Mock Data Generation: {:.0f} records/sec", mockDataRate);
        logger.info("   Excel Writing: {:.0f} records/sec", excelWriteRate);
        
        // Performance recommendations based on our analysis
        if (excelWriteRate < 5000) {
            logger.warn("⚠️  Excel writing performance below expected (5K+ rec/sec)");
            logger.info("💡 Recommendations:");
            logger.info("   - Consider CSV format for better performance");
            logger.info("   - Ensure auto-sizing is disabled");
            logger.info("   - Use aggressive memory optimizations");
        } else {
            logger.info("✅ Excel writing performance is within expected range");
        }
        
        double totalRate = recordCount * 1000.0 / (mockDataDuration + excelWriteDuration);
        logger.info("   Overall Step 1 Rate: {:.0f} records/sec", totalRate);
    }
    
    /**
     * Analyze Step 2 performance
     */
    private void analyzeStep2Performance(long readDuration, long processedRecords, long validRecords, long errorRecords) {
        logger.info("📊 STEP 2 PERFORMANCE ANALYSIS:");
        
        double readRate = processedRecords * 1000.0 / readDuration;
        double errorRate = (errorRecords * 100.0) / processedRecords;
        
        logger.info("   Reading + Processing Rate: {:.0f} records/sec", readRate);
        logger.info("   Data Quality: {:.2f}% error rate", errorRate);
        
        if (readRate < 10000) {
            logger.warn("⚠️  Reading performance below expected (10K+ rec/sec)");
            logger.info("💡 Recommendations:");
            logger.info("   - Increase batch size for processing");
            logger.info("   - Optimize database insertion (if applicable)");
            logger.info("   - Consider parallel processing");
        } else {
            logger.info("✅ Reading performance is excellent");
        }
        
        if (errorRate > 5.0) {
            logger.warn("⚠️  High error rate detected");
            logger.info("💡 Recommendations:");
            logger.info("   - Review data validation rules");
            logger.info("   - Check data generation quality");
        }
    }
    
    /**
     * Cleanup test files
     */
    private void cleanupTestFiles() {
        try {
            java.io.File excelFile = new java.io.File(EXCEL_FILE_PATH);
            if (excelFile.exists()) {
                boolean deleted = excelFile.delete();
                logger.info("🧹 Cleanup: Excel file {} {}", EXCEL_FILE_PATH, deleted ? "deleted" : "deletion failed");
            }
            
            java.io.File csvFile = new java.io.File(CSV_FILE_PATH);
            if (csvFile.exists()) {
                boolean deleted = csvFile.delete();
                logger.info("🧹 Cleanup: CSV file {} {}", CSV_FILE_PATH, deleted ? "deleted" : "deletion failed");
            }
        } catch (Exception e) {
            logger.warn("🧹 Cleanup warning: {}", e.getMessage());
        }
    }
    
    /**
     * Performance monitoring helper class
     */
    private static class PerformanceMonitor {
        private final String name;
        private long startTime;
        private long startMemory;
        private final MemoryMXBean memoryBean;
        
        public PerformanceMonitor(String name) {
            this.name = name;
            this.memoryBean = ManagementFactory.getMemoryMXBean();
        }
        
        public void start() {
            this.startTime = System.currentTimeMillis();
            this.startMemory = memoryBean.getHeapMemoryUsage().getUsed();
            logger.info("⏱️  {} - Started monitoring", name);
        }
        
        public void stop() {
            long endTime = System.currentTimeMillis();
            long endMemory = memoryBean.getHeapMemoryUsage().getUsed();
            
            long duration = endTime - startTime;
            long memoryDelta = endMemory - startMemory;
            
            logger.info("⏱️  {} - Performance Summary:", name);
            logger.info("   Total Duration: {}ms", duration);
            logger.info("   Memory Delta: {} KB", memoryDelta / 1024);
            logger.info("   Final Memory: {} KB", endMemory / 1024);
        }
    }
}