package com.learnmore.application.utils.writer;

/**
 * Result class for Excel writing operations
 * Contains comprehensive statistics and performance metrics
 */
public class WritingResult {
    
    private long totalRowsWritten = 0;
    private long totalCellsWritten = 0;
    private long processingTimeMs = 0;
    private double rowsPerSecond = 0.0;
    private double cellsPerSecond = 0.0;
    private String strategy = "UNKNOWN";
    private String errorMessage;
    private boolean success = true;
    
    // File output metrics
    private long outputSizeBytes = 0;
    private int totalColumns = 0;
    private int stylesUsed = 0;
    private int formatsUsed = 0;
    
    // Performance metrics
    private long headerWriteTimeMs = 0;
    private long dataWriteTimeMs = 0;
    private long styleApplicationTimeMs = 0;
    private long flushTimeMs = 0;
    
    // Memory metrics
    private long peakMemoryUsageMB = 0;
    private int flushOperations = 0;
    
    public WritingResult() {}
    
    public WritingResult(String strategy) {
        this.strategy = strategy;
    }
    
    // Core metrics getters/setters
    public long getTotalRowsWritten() { return totalRowsWritten; }
    public void setTotalRowsWritten(long totalRowsWritten) { 
        this.totalRowsWritten = totalRowsWritten;
        updateDerivedMetrics();
    }
    
    public long getTotalCellsWritten() { return totalCellsWritten; }
    public void setTotalCellsWritten(long totalCellsWritten) { 
        this.totalCellsWritten = totalCellsWritten;
        updateDerivedMetrics();
    }
    
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { 
        this.processingTimeMs = processingTimeMs;
        updateDerivedMetrics();
    }
    
    public double getRowsPerSecond() { return rowsPerSecond; }
    public void setRowsPerSecond(double rowsPerSecond) { this.rowsPerSecond = rowsPerSecond; }
    
    public double getCellsPerSecond() { return cellsPerSecond; }
    public void setCellsPerSecond(double cellsPerSecond) { this.cellsPerSecond = cellsPerSecond; }
    
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { 
        this.errorMessage = errorMessage;
        this.success = (errorMessage == null);
    }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    // File metrics getters/setters
    public long getOutputSizeBytes() { return outputSizeBytes; }
    public void setOutputSizeBytes(long outputSizeBytes) { this.outputSizeBytes = outputSizeBytes; }
    
    public int getTotalColumns() { return totalColumns; }
    public void setTotalColumns(int totalColumns) { this.totalColumns = totalColumns; }
    
    public int getStylesUsed() { return stylesUsed; }
    public void setStylesUsed(int stylesUsed) { this.stylesUsed = stylesUsed; }
    
    public int getFormatsUsed() { return formatsUsed; }
    public void setFormatsUsed(int formatsUsed) { this.formatsUsed = formatsUsed; }
    
    // Performance timing getters/setters
    public long getHeaderWriteTimeMs() { return headerWriteTimeMs; }
    public void setHeaderWriteTimeMs(long headerWriteTimeMs) { this.headerWriteTimeMs = headerWriteTimeMs; }
    
    public long getDataWriteTimeMs() { return dataWriteTimeMs; }
    public void setDataWriteTimeMs(long dataWriteTimeMs) { this.dataWriteTimeMs = dataWriteTimeMs; }
    
    public long getStyleApplicationTimeMs() { return styleApplicationTimeMs; }
    public void setStyleApplicationTimeMs(long styleApplicationTimeMs) { this.styleApplicationTimeMs = styleApplicationTimeMs; }
    
    public long getFlushTimeMs() { return flushTimeMs; }
    public void setFlushTimeMs(long flushTimeMs) { this.flushTimeMs = flushTimeMs; }
    
    // Memory metrics getters/setters
    public long getPeakMemoryUsageMB() { return peakMemoryUsageMB; }
    public void setPeakMemoryUsageMB(long peakMemoryUsageMB) { this.peakMemoryUsageMB = peakMemoryUsageMB; }
    
    public int getFlushOperations() { return flushOperations; }
    public void setFlushOperations(int flushOperations) { this.flushOperations = flushOperations; }
    
    /**
     * Update derived metrics when core metrics change
     */
    private void updateDerivedMetrics() {
        if (processingTimeMs > 0) {
            this.rowsPerSecond = totalRowsWritten * 1000.0 / processingTimeMs;
            this.cellsPerSecond = totalCellsWritten * 1000.0 / processingTimeMs;
        }
    }
    
    /**
     * Add timing measurement
     */
    public void addTiming(String operation, long timeMs) {
        switch (operation.toLowerCase()) {
            case "header":
                this.headerWriteTimeMs += timeMs;
                break;
            case "data":
                this.dataWriteTimeMs += timeMs;
                break;
            case "style":
                this.styleApplicationTimeMs += timeMs;
                break;
            case "flush":
                this.flushTimeMs += timeMs;
                this.flushOperations++;
                break;
        }
    }
    
    /**
     * Get efficiency ratio (rows per MB of memory)
     */
    public double getMemoryEfficiency() {
        return peakMemoryUsageMB > 0 ? (double) totalRowsWritten / peakMemoryUsageMB : 0.0;
    }
    
    /**
     * Get compression ratio (cells per KB of output)
     */
    public double getCompressionRatio() {
        return outputSizeBytes > 0 ? totalCellsWritten / (outputSizeBytes / 1024.0) : 0.0;
    }
    
    /**
     * Create summary string for logging
     */
    public String getSummary() {
        return String.format("Strategy: %s | Rows: %,d | Time: %,dms | Speed: %.1f rows/sec | Size: %s",
                strategy, totalRowsWritten, processingTimeMs, rowsPerSecond, formatBytes(outputSizeBytes));
    }
    
    /**
     * Create detailed performance breakdown
     */
    public String getPerformanceBreakdown() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s Performance Breakdown ===\n", strategy));
        sb.append(String.format("Total Rows: %,d | Total Cells: %,d\n", totalRowsWritten, totalCellsWritten));
        sb.append(String.format("Processing Time: %,dms (%.2f rows/sec, %.1f cells/sec)\n", 
                processingTimeMs, rowsPerSecond, cellsPerSecond));
        
        if (headerWriteTimeMs > 0 || dataWriteTimeMs > 0) {
            sb.append("--- Timing Breakdown ---\n");
            if (headerWriteTimeMs > 0) sb.append(String.format("Header: %dms\n", headerWriteTimeMs));
            if (dataWriteTimeMs > 0) sb.append(String.format("Data: %dms\n", dataWriteTimeMs));
            if (styleApplicationTimeMs > 0) sb.append(String.format("Styling: %dms\n", styleApplicationTimeMs));
            if (flushTimeMs > 0) sb.append(String.format("Flush: %dms (%d ops)\n", flushTimeMs, flushOperations));
        }
        
        if (outputSizeBytes > 0) {
            sb.append(String.format("Output Size: %s | Compression: %.1f cells/KB\n", 
                    formatBytes(outputSizeBytes), getCompressionRatio()));
        }
        
        if (peakMemoryUsageMB > 0) {
            sb.append(String.format("Peak Memory: %dMB | Efficiency: %.1f rows/MB\n", 
                    peakMemoryUsageMB, getMemoryEfficiency()));
        }
        
        return sb.toString();
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
}