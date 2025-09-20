package com.learnmore.application.utils.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Optimized database batch processor for high-performance Excel data insertion
 * Uses prepared statements and batch operations for maximum throughput
 */
public class OptimizedDatabaseProcessor<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizedDatabaseProcessor.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final String insertSql;
    private final BatchStatementSetter<T> statementSetter;
    private final int batchSize;
    
    // Performance tracking
    private long totalRecordsProcessed = 0;
    private long totalBatchesProcessed = 0;
    private long totalProcessingTime = 0;
    
    public OptimizedDatabaseProcessor(JdbcTemplate jdbcTemplate, String insertSql, 
                                    BatchStatementSetter<T> statementSetter, int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertSql = insertSql;
        this.statementSetter = statementSetter;
        this.batchSize = batchSize;
        
        logger.info("Created OptimizedDatabaseProcessor with batch size: {}", batchSize);
        logger.debug("Insert SQL: {}", insertSql);
    }
    
    /**
     * Create optimized batch processor for Excel data
     */
    public Consumer<List<T>> createBatchProcessor() {
        return batch -> {
            if (batch == null || batch.isEmpty()) {
                logger.warn("Received empty batch, skipping processing");
                return;
            }
            
            long startTime = System.currentTimeMillis();
            
            try {
                processBatch(batch);
                
                long processingTime = System.currentTimeMillis() - startTime;
                totalRecordsProcessed += batch.size();
                totalBatchesProcessed++;
                totalProcessingTime += processingTime;
                
                double recordsPerSecond = batch.size() * 1000.0 / processingTime;
                
                logger.debug("Processed batch with {} records in {}ms ({:.2f} records/sec)", 
                        batch.size(), processingTime, recordsPerSecond);
                
                // Log performance warning for slow batches
                if (recordsPerSecond < 1000) {
                    logger.warn("Slow batch processing detected: {:.2f} records/sec", recordsPerSecond);
                }
                
            } catch (Exception e) {
                logger.error("Failed to process batch with {} records: {}", batch.size(), e.getMessage(), e);
                throw new RuntimeException("Database batch processing failed", e);
            }
        };
    }
    
    /**
     * Process a single batch with optimized prepared statement
     */
    @Transactional
    protected void processBatch(List<T> batch) {
        logger.debug("Processing database batch with {} records", batch.size());
        
        // Use Spring's batch update with prepared statement setter
        int[] updateCounts = jdbcTemplate.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                T record = batch.get(i);
                statementSetter.setValues(ps, record, i);
            }
            
            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
        
        // Validate all records were inserted
        int successfulInserts = 0;
        for (int count : updateCounts) {
            if (count > 0) {
                successfulInserts++;
            }
        }
        
        if (successfulInserts != batch.size()) {
            logger.warn("Batch processing incomplete: {}/{} records inserted successfully", 
                    successfulInserts, batch.size());
        }
        
        logger.debug("Successfully inserted {}/{} records in batch", successfulInserts, batch.size());
    }
    
    /**
     * Process batch with custom chunk size for very large batches
     */
    @Transactional
    public void processLargeBatch(List<T> largeBatch) {
        if (largeBatch.size() <= batchSize) {
            processBatch(largeBatch);
            return;
        }
        
        logger.info("Processing large batch with {} records in chunks of {}", 
                largeBatch.size(), batchSize);
        
        // Split into smaller chunks
        for (int i = 0; i < largeBatch.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, largeBatch.size());
            List<T> chunk = largeBatch.subList(i, endIndex);
            
            try {
                processBatch(chunk);
                logger.debug("Processed chunk {}-{} of {}", i + 1, endIndex, largeBatch.size());
            } catch (Exception e) {
                logger.error("Failed to process chunk {}-{}: {}", i + 1, endIndex, e.getMessage(), e);
                throw e; // Re-throw to maintain transaction consistency
            }
        }
        
        logger.info("Completed processing large batch with {} records", largeBatch.size());
    }
    
    /**
     * Get current processing statistics
     */
    public ProcessingStatistics getStatistics() {
        double avgRecordsPerBatch = totalBatchesProcessed > 0 ? 
                (double) totalRecordsProcessed / totalBatchesProcessed : 0;
        double avgProcessingTimePerBatch = totalBatchesProcessed > 0 ? 
                (double) totalProcessingTime / totalBatchesProcessed : 0;
        double overallRecordsPerSecond = totalProcessingTime > 0 ? 
                totalRecordsProcessed * 1000.0 / totalProcessingTime : 0;
        
        return new ProcessingStatistics(
                totalRecordsProcessed,
                totalBatchesProcessed,
                totalProcessingTime,
                avgRecordsPerBatch,
                avgProcessingTimePerBatch,
                overallRecordsPerSecond);
    }
    
    /**
     * Reset statistics counters
     */
    public void resetStatistics() {
        totalRecordsProcessed = 0;
        totalBatchesProcessed = 0;
        totalProcessingTime = 0;
        logger.debug("Database processor statistics reset");
    }
    
    /**
     * Interface for setting values in prepared statement
     */
    @FunctionalInterface
    public interface BatchStatementSetter<T> {
        void setValues(PreparedStatement ps, T record, int index) throws SQLException;
    }
    
    /**
     * Processing statistics for monitoring
     */
    public static class ProcessingStatistics {
        private final long totalRecordsProcessed;
        private final long totalBatchesProcessed;
        private final long totalProcessingTimeMs;
        private final double avgRecordsPerBatch;
        private final double avgProcessingTimePerBatch;
        private final double overallRecordsPerSecond;
        
        public ProcessingStatistics(long totalRecordsProcessed, long totalBatchesProcessed, 
                                  long totalProcessingTimeMs, double avgRecordsPerBatch, 
                                  double avgProcessingTimePerBatch, double overallRecordsPerSecond) {
            this.totalRecordsProcessed = totalRecordsProcessed;
            this.totalBatchesProcessed = totalBatchesProcessed;
            this.totalProcessingTimeMs = totalProcessingTimeMs;
            this.avgRecordsPerBatch = avgRecordsPerBatch;
            this.avgProcessingTimePerBatch = avgProcessingTimePerBatch;
            this.overallRecordsPerSecond = overallRecordsPerSecond;
        }
        
        // Getters
        public long getTotalRecordsProcessed() { return totalRecordsProcessed; }
        public long getTotalBatchesProcessed() { return totalBatchesProcessed; }
        public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
        public double getAvgRecordsPerBatch() { return avgRecordsPerBatch; }
        public double getAvgProcessingTimePerBatch() { return avgProcessingTimePerBatch; }
        public double getOverallRecordsPerSecond() { return overallRecordsPerSecond; }
        
        @Override
        public String toString() {
            return String.format(
                "Database Processing Statistics:\n" +
                "  Total records: %d\n" +
                "  Total batches: %d\n" +
                "  Total time: %dms\n" +
                "  Avg records/batch: %.1f\n" +
                "  Avg time/batch: %.1fms\n" +
                "  Overall records/sec: %.2f",
                totalRecordsProcessed, totalBatchesProcessed, totalProcessingTimeMs,
                avgRecordsPerBatch, avgProcessingTimePerBatch, overallRecordsPerSecond);
        }
    }
    
    /**
     * Builder for creating optimized database processors with common configurations
     */
    public static class Builder<T> {
        private JdbcTemplate jdbcTemplate;
        private String tableName;
        private String[] columnNames;
        private BatchStatementSetter<T> statementSetter;
        private int batchSize = 1000;
        
        public Builder<T> jdbcTemplate(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
            return this;
        }
        
        public Builder<T> tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }
        
        public Builder<T> columns(String... columnNames) {
            this.columnNames = columnNames;
            return this;
        }
        
        public Builder<T> statementSetter(BatchStatementSetter<T> statementSetter) {
            this.statementSetter = statementSetter;
            return this;
        }
        
        public Builder<T> batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }
        
        public OptimizedDatabaseProcessor<T> build() {
            if (jdbcTemplate == null) {
                throw new IllegalArgumentException("JdbcTemplate is required");
            }
            if (tableName == null || tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("Table name is required");
            }
            if (columnNames == null || columnNames.length == 0) {
                throw new IllegalArgumentException("Column names are required");
            }
            if (statementSetter == null) {
                throw new IllegalArgumentException("Statement setter is required");
            }
            
            // Build INSERT SQL
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(tableName).append(" (");
            
            for (int i = 0; i < columnNames.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(columnNames[i]);
            }
            
            sql.append(") VALUES (");
            for (int i = 0; i < columnNames.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
            }
            sql.append(")");
            
            return new OptimizedDatabaseProcessor<>(jdbcTemplate, sql.toString(), statementSetter, batchSize);
        }
    }
}