package com.learnmore.application.utils.demo;

import com.learnmore.application.utils.checkpoint.*;
import com.learnmore.application.utils.config.ExcelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demonstration of Advanced Error Recovery features with checkpoint/resume capability
 * Showcases how to process large Excel files with resilience to interruptions
 */
public class AdvancedErrorRecoveryDemo {
    private static final Logger logger = LoggerFactory.getLogger(AdvancedErrorRecoveryDemo.class);
    
    public static void main(String[] args) {
        AdvancedErrorRecoveryDemo demo = new AdvancedErrorRecoveryDemo();
        
        try {
            System.out.println("=== Advanced Error Recovery Demo ===\n");
            
            // Demo 1: Basic checkpoint/resume functionality
            demo.demonstrateBasicCheckpointing();
            
            // Demo 2: Resume from interrupted processing
            demo.demonstrateResumeFromInterruption();
            
            // Demo 3: Async processing with checkpoints
            demo.demonstrateAsyncProcessingWithCheckpoints();
            
            // Demo 4: Checkpoint statistics and monitoring
            demo.demonstrateCheckpointStatistics();
            
            // Demo 5: Cleanup and maintenance
            demo.demonstrateCheckpointCleanup();
            
        } catch (Exception e) {
            logger.error("Demo failed", e);
        }
    }
    
    /**
     * Demonstrate basic checkpoint/resume functionality
     */
    public void demonstrateBasicCheckpointing() {
        System.out.println("1. Basic Checkpoint/Resume Demo");
        System.out.println("==================================");
        
        try {
            // Setup checkpoint manager
            CheckpointManager checkpointManager = CheckpointManager.builder()
                    .checkpointDirectory("./checkpoints")
                    .checkpointInterval(5000) // Save checkpoint every 5000 records
                    .enableCompression(false) // For demo, use JSON format
                    .build();
            
            // Create recoverable processor
            RecoverableExcelProcessor processor = RecoverableExcelProcessor.builder()
                    .checkpointManager(checkpointManager)
                    .build();
            
            // Configure processing
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(1000)
                    .memoryThreshold(256)
                    .strictValidation(false)
                    .build();
            
            // Sample data processor
            var dataProcessor = createSampleDataProcessor();
            
            // Create sample Excel data
            byte[] sampleExcelData = createSampleExcelData();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(sampleExcelData);
            
            // Process with checkpoint support
            System.out.println("Processing Excel file with checkpoint support...");
            RecoverableProcessingResult<SampleRecord> result = processor.processExcelWithCheckpoint(
                    inputStream, 
                    SampleRecord.class, 
                    config, 
                    dataProcessor,
                    "sample-large-file.xlsx"
            );
            
            System.out.println("Processing completed:");
            System.out.println("- Session ID: " + result.getSessionId());
            System.out.println("- Success: " + result.isSuccess());
            System.out.println("- Processed Batches: " + result.getProcessedBatches());
            System.out.println("- Processed Rows: " + result.getProcessedRows());
            System.out.println("- Progress: " + String.format("%.2f%%", result.getProgressPercentage()));
            
            if (result.getCheckpoint() != null) {
                System.out.println("- Checkpoint Status: " + result.getCheckpoint().getStatus());
            }
            
            processor.shutdown();
            
        } catch (Exception e) {
            logger.error("Basic checkpointing demo failed", e);
            System.err.println("Error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate resume from interrupted processing
     */
    public void demonstrateResumeFromInterruption() {
        System.out.println("2. Resume from Interruption Demo");
        System.out.println("=================================");
        
        try {
            CheckpointManager checkpointManager = CheckpointManager.builder()
                    .checkpointDirectory("./checkpoints")
                    .checkpointInterval(2000)
                    .build();
            
            RecoverableExcelProcessor processor = RecoverableExcelProcessor.builder()
                    .checkpointManager(checkpointManager)
                    .build();
            
            // Simulate a specific session ID for resume demo
            String sessionId = "demo-resume-session-001";
            
            // Create a checkpoint manually (simulating previous interrupted processing)
            ProcessingCheckpoint existingCheckpoint = ProcessingCheckpoint.builder()
                    .sessionId(sessionId)
                    .fileName("large-data-file.xlsx")
                    .totalRows(100000)
                    .processedRows(45000) // Processed 45k out of 100k
                    .status(CheckpointStatus.ACTIVE)
                    .build();
            
            // Save the checkpoint
            checkpointManager.saveCheckpointToDisk(existingCheckpoint);
            
            System.out.println("Simulated interrupted processing:");
            System.out.println("- Total rows: " + existingCheckpoint.getTotalRows());
            System.out.println("- Processed rows: " + existingCheckpoint.getProcessedRows());
            System.out.println("- Progress: " + String.format("%.1f%%", existingCheckpoint.getProgressPercentage()));
            System.out.println("- Remaining rows: " + existingCheckpoint.getRemainingRows());
            
            // Check if processing can be resumed
            if (existingCheckpoint.canResume()) {
                System.out.println("\n✓ Processing can be resumed from checkpoint");
                
                // Load checkpoint
                ProcessingCheckpoint loadedCheckpoint = checkpointManager.loadCheckpoint(sessionId);
                if (loadedCheckpoint != null) {
                    System.out.println("✓ Checkpoint loaded successfully");
                    System.out.println("- Will resume from row: " + loadedCheckpoint.getProcessedRows());
                }
            }
            
            processor.shutdown();
            
        } catch (Exception e) {
            logger.error("Resume demo failed", e);
            System.err.println("Error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate async processing with checkpoints
     */
    public void demonstrateAsyncProcessingWithCheckpoints() {
        System.out.println("3. Async Processing with Checkpoints Demo");
        System.out.println("==========================================");
        
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        
        try {
            CheckpointManager checkpointManager = CheckpointManager.builder()
                    .checkpointDirectory("./checkpoints")
                    .checkpointInterval(3000)
                    .build();
            
            RecoverableExcelProcessor processor = RecoverableExcelProcessor.builder()
                    .checkpointManager(checkpointManager)
                    .executorService(executorService)
                    .build();
            
            ExcelConfig config = ExcelConfig.builder()
                    .batchSize(500)
                    .parallelProcessing(true)
                    .threadPoolSize(2)
                    .build();
            
            // Process multiple files asynchronously
            byte[] sampleData1 = createSampleExcelData();
            byte[] sampleData2 = createSampleExcelData();
            
            System.out.println("Starting async processing of 2 files...");
            
            CompletableFuture<RecoverableProcessingResult<SampleRecord>> future1 = 
                    processor.processExcelWithCheckpointAsync(
                            new ByteArrayInputStream(sampleData1),
                            SampleRecord.class,
                            config,
                            createSampleDataProcessor(),
                            "async-file-1.xlsx"
                    );
            
            CompletableFuture<RecoverableProcessingResult<SampleRecord>> future2 = 
                    processor.processExcelWithCheckpointAsync(
                            new ByteArrayInputStream(sampleData2),
                            SampleRecord.class,
                            config,
                            createSampleDataProcessor(),
                            "async-file-2.xlsx"
                    );
            
            // Wait for completion
            CompletableFuture.allOf(future1, future2).get();
            
            RecoverableProcessingResult<SampleRecord> result1 = future1.get();
            RecoverableProcessingResult<SampleRecord> result2 = future2.get();
            
            System.out.println("\nAsync processing completed:");
            System.out.println("File 1: " + result1.getSummary());
            System.out.println("File 2: " + result2.getSummary());
            
            processor.shutdown();
            
        } catch (Exception e) {
            logger.error("Async processing demo failed", e);
            System.err.println("Error: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate checkpoint statistics and monitoring
     */
    public void demonstrateCheckpointStatistics() {
        System.out.println("4. Checkpoint Statistics Demo");
        System.out.println("=============================");
        
        try {
            CheckpointManager checkpointManager = CheckpointManager.builder()
                    .checkpointDirectory("./checkpoints")
                    .checkpointInterval(1000)
                    .build();
            
            // Create some sample checkpoints
            for (int i = 1; i <= 5; i++) {
                ProcessingCheckpoint checkpoint = ProcessingCheckpoint.builder()
                        .sessionId("stats-demo-session-" + i)
                        .fileName("demo-file-" + i + ".xlsx")
                        .totalRows(10000 * i)
                        .processedRows(5000 * i)
                        .status(i <= 3 ? CheckpointStatus.COMPLETED : CheckpointStatus.ACTIVE)
                        .build();
                
                checkpointManager.saveCheckpointToDisk(checkpoint);
            }
            
            // Get statistics
            CheckpointStatistics stats = checkpointManager.getStatistics();
            
            System.out.println("Checkpoint Statistics:");
            System.out.println("- Active checkpoints: " + stats.getActiveCheckpoints());
            System.out.println("- Completed checkpoints: " + stats.getCompletedCheckpoints());
            System.out.println("- Failed checkpoints: " + stats.getFailedCheckpoints());
            System.out.println("- Total checkpoints: " + stats.getTotalCheckpoints());
            System.out.println("- Success rate: " + String.format("%.1f%%", stats.getSuccessRate()));
            System.out.println("- Failure rate: " + String.format("%.1f%%", stats.getFailureRate()));
            
        } catch (Exception e) {
            logger.error("Statistics demo failed", e);
            System.err.println("Error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate checkpoint cleanup and maintenance
     */
    public void demonstrateCheckpointCleanup() {
        System.out.println("5. Checkpoint Cleanup Demo");
        System.out.println("==========================");
        
        try {
            CheckpointManager checkpointManager = CheckpointManager.builder()
                    .checkpointDirectory("./checkpoints")
                    .build();
            
            System.out.println("Performing checkpoint cleanup...");
            
            // Clean up checkpoints older than 24 hours
            checkpointManager.cleanupOldCheckpoints(24);
            
            System.out.println("✓ Cleanup completed");
            
            // Show final statistics
            CheckpointStatistics finalStats = checkpointManager.getStatistics();
            System.out.println("Final statistics: " + finalStats);
            
        } catch (Exception e) {
            logger.error("Cleanup demo failed", e);
            System.err.println("Error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    // Helper methods
    
    private java.util.function.Consumer<List<SampleRecord>> createSampleDataProcessor() {
        return batch -> {
            // Simulate processing time
            try {
                Thread.sleep(10); // Small delay to simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Log progress occasionally
            if (Math.random() < 0.1) { // 10% chance
                System.out.printf("Processed batch of %d records%n", batch.size());
            }
        };
    }
    
    private byte[] createSampleExcelData() {
        // This would normally create actual Excel data
        // For demo purposes, return empty data that won't cause processing errors
        return new byte[0];
    }
    
    /**
     * Sample record class for demo
     */
    public static class SampleRecord {
        private String name;
        private String email;
        private Integer age;
        
        // Constructors, getters, setters
        public SampleRecord() {}
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        
        @Override
        public String toString() {
            return "SampleRecord{name='" + name + "', email='" + email + "', age=" + age + "}";
        }
    }
}