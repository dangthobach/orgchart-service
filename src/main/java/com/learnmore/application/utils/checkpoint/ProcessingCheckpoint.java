package com.learnmore.application.utils.checkpoint;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Processing checkpoint data structure for error recovery
 */
public class ProcessingCheckpoint implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String sessionId;
    private String fileName;
    private long totalRows;
    private long processedRows;
    private long lastCheckpointRow;
    private CheckpointStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private String errorMessage;
    private String exception;
    
    @JsonIgnore
    private transient Object processingState;
    
    // Default constructor for Jackson
    public ProcessingCheckpoint() {}
    
    // Private constructor for builder
    private ProcessingCheckpoint(Builder builder) {
        this.sessionId = builder.sessionId;
        this.fileName = builder.fileName;
        this.totalRows = builder.totalRows;
        this.processedRows = builder.processedRows;
        this.lastCheckpointRow = builder.lastCheckpointRow;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.completedAt = builder.completedAt;
        this.failedAt = builder.failedAt;
        this.errorMessage = builder.errorMessage;
        this.exception = builder.exception;
        this.processingState = builder.processingState;
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public long getTotalRows() {
        return totalRows;
    }
    
    public void setTotalRows(long totalRows) {
        this.totalRows = totalRows;
    }
    
    public long getProcessedRows() {
        return processedRows;
    }
    
    public void setProcessedRows(long processedRows) {
        this.processedRows = processedRows;
    }
    
    public long getLastCheckpointRow() {
        return lastCheckpointRow;
    }
    
    public void setLastCheckpointRow(long lastCheckpointRow) {
        this.lastCheckpointRow = lastCheckpointRow;
    }
    
    public CheckpointStatus getStatus() {
        return status;
    }
    
    public void setStatus(CheckpointStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public LocalDateTime getFailedAt() {
        return failedAt;
    }
    
    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getException() {
        return exception;
    }
    
    public void setException(String exception) {
        this.exception = exception;
    }
    
    public Object getProcessingState() {
        return processingState;
    }
    
    public void setProcessingState(Object processingState) {
        this.processingState = processingState;
    }
    
    /**
     * Calculate processing progress percentage
     */
    public double getProgressPercentage() {
        if (totalRows == 0) return 0.0;
        return (double) processedRows / totalRows * 100.0;
    }
    
    /**
     * Check if processing can be resumed
     */
    public boolean canResume() {
        return status == CheckpointStatus.ACTIVE && processedRows < totalRows;
    }
    
    /**
     * Get remaining rows to process
     */
    public long getRemainingRows() {
        return totalRows - processedRows;
    }
    
    @Override
    public String toString() {
        return "ProcessingCheckpoint{" +
                "sessionId='" + sessionId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", totalRows=" + totalRows +
                ", processedRows=" + processedRows +
                ", status=" + status +
                ", progressPercentage=" + String.format("%.2f", getProgressPercentage()) + "%" +
                '}';
    }
    
    /**
     * Builder pattern for ProcessingCheckpoint
     */
    public static class Builder {
        private String sessionId;
        private String fileName;
        private long totalRows;
        private long processedRows = 0L;
        private long lastCheckpointRow = 0L;
        private CheckpointStatus status = CheckpointStatus.ACTIVE;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private String errorMessage;
        private String exception;
        private Object processingState;
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }
        
        public Builder totalRows(long totalRows) {
            this.totalRows = totalRows;
            return this;
        }
        
        public Builder processedRows(long processedRows) {
            this.processedRows = processedRows;
            return this;
        }
        
        public Builder lastCheckpointRow(long lastCheckpointRow) {
            this.lastCheckpointRow = lastCheckpointRow;
            return this;
        }
        
        public Builder status(CheckpointStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }
        
        public Builder failedAt(LocalDateTime failedAt) {
            this.failedAt = failedAt;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder exception(String exception) {
            this.exception = exception;
            return this;
        }
        
        public Builder processingState(Object processingState) {
            this.processingState = processingState;
            return this;
        }
        
        public ProcessingCheckpoint build() {
            return new ProcessingCheckpoint(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}