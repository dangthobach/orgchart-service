package com.learnmore.application.utils.checkpoint;

import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;

/**
 * Result wrapper for recoverable Excel processing operations
 */
public class RecoverableProcessingResult<T> {
    private final String sessionId;
    private ProcessingCheckpoint checkpoint;
    private TrueStreamingSAXProcessor.ProcessingResult processingResult;
    private boolean success;
    private boolean resumedFromCheckpoint;
    private String errorMessage;
    private Exception exception;
    private long processedBatches;
    private long processedRows;
    
    public RecoverableProcessingResult(String sessionId) {
        this.sessionId = sessionId;
        this.success = false;
        this.resumedFromCheckpoint = false;
        this.processedBatches = 0;
        this.processedRows = 0;
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public ProcessingCheckpoint getCheckpoint() {
        return checkpoint;
    }
    
    public void setCheckpoint(ProcessingCheckpoint checkpoint) {
        this.checkpoint = checkpoint;
    }
    
    public TrueStreamingSAXProcessor.ProcessingResult getProcessingResult() {
        return processingResult;
    }
    
    public void setProcessingResult(TrueStreamingSAXProcessor.ProcessingResult processingResult) {
        this.processingResult = processingResult;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public boolean isResumedFromCheckpoint() {
        return resumedFromCheckpoint;
    }
    
    public void setResumedFromCheckpoint(boolean resumedFromCheckpoint) {
        this.resumedFromCheckpoint = resumedFromCheckpoint;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Exception getException() {
        return exception;
    }
    
    public void setException(Exception exception) {
        this.exception = exception;
    }
    
    public long getProcessedBatches() {
        return processedBatches;
    }
    
    public void setProcessedBatches(long processedBatches) {
        this.processedBatches = processedBatches;
    }
    
    public void incrementProcessedBatches() {
        this.processedBatches++;
    }
    
    public long getProcessedRows() {
        return processedRows;
    }
    
    public void setProcessedRows(long processedRows) {
        this.processedRows = processedRows;
    }
    
    public void addProcessedRows(long rows) {
        this.processedRows += rows;
    }
    
    /**
     * Get processing progress from checkpoint
     */
    public double getProgressPercentage() {
        if (checkpoint != null) {
            return checkpoint.getProgressPercentage();
        }
        return 0.0;
    }
    
    /**
     * Check if processing was completed successfully
     */
    public boolean isCompleted() {
        return success && (checkpoint == null || checkpoint.getStatus() == CheckpointStatus.COMPLETED);
    }
    
    /**
     * Check if processing failed
     */
    public boolean isFailed() {
        return !success || (checkpoint != null && checkpoint.getStatus() == CheckpointStatus.FAILED);
    }
    
    /**
     * Get summary information
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Session: ").append(sessionId);
        summary.append(", Success: ").append(success);
        summary.append(", Resumed: ").append(resumedFromCheckpoint);
        summary.append(", Processed Batches: ").append(processedBatches);
        summary.append(", Processed Rows: ").append(processedRows);
        
        if (checkpoint != null) {
            summary.append(", Progress: ").append(String.format("%.2f%%", getProgressPercentage()));
        }
        
        if (!success && errorMessage != null) {
            summary.append(", Error: ").append(errorMessage);
        }
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return "RecoverableProcessingResult{" +
                "sessionId='" + sessionId + '\'' +
                ", success=" + success +
                ", resumedFromCheckpoint=" + resumedFromCheckpoint +
                ", processedBatches=" + processedBatches +
                ", processedRows=" + processedRows +
                ", progressPercentage=" + String.format("%.2f", getProgressPercentage()) + "%" +
                '}';
    }
}