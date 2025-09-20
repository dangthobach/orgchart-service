package com.learnmore.application.utils.checkpoint;

/**
 * Statistics for checkpoint management operations
 */
public class CheckpointStatistics {
    private final long activeCheckpoints;
    private final long completedCheckpoints;
    private final long failedCheckpoints;
    private final long totalCheckpoints;
    
    private CheckpointStatistics(Builder builder) {
        this.activeCheckpoints = builder.activeCheckpoints;
        this.completedCheckpoints = builder.completedCheckpoints;
        this.failedCheckpoints = builder.failedCheckpoints;
        this.totalCheckpoints = builder.totalCheckpoints;
    }
    
    public long getActiveCheckpoints() {
        return activeCheckpoints;
    }
    
    public long getCompletedCheckpoints() {
        return completedCheckpoints;
    }
    
    public long getFailedCheckpoints() {
        return failedCheckpoints;
    }
    
    public long getTotalCheckpoints() {
        return totalCheckpoints;
    }
    
    public double getSuccessRate() {
        if (totalCheckpoints == 0) return 0.0;
        return (double) completedCheckpoints / totalCheckpoints * 100.0;
    }
    
    public double getFailureRate() {
        if (totalCheckpoints == 0) return 0.0;
        return (double) failedCheckpoints / totalCheckpoints * 100.0;
    }
    
    @Override
    public String toString() {
        return "CheckpointStatistics{" +
                "active=" + activeCheckpoints +
                ", completed=" + completedCheckpoints +
                ", failed=" + failedCheckpoints +
                ", total=" + totalCheckpoints +
                ", successRate=" + String.format("%.2f", getSuccessRate()) + "%" +
                ", failureRate=" + String.format("%.2f", getFailureRate()) + "%" +
                '}';
    }
    
    public static class Builder {
        private long activeCheckpoints;
        private long completedCheckpoints;
        private long failedCheckpoints;
        private long totalCheckpoints;
        
        public Builder activeCheckpoints(long activeCheckpoints) {
            this.activeCheckpoints = activeCheckpoints;
            return this;
        }
        
        public Builder completedCheckpoints(long completedCheckpoints) {
            this.completedCheckpoints = completedCheckpoints;
            return this;
        }
        
        public Builder failedCheckpoints(long failedCheckpoints) {
            this.failedCheckpoints = failedCheckpoints;
            return this;
        }
        
        public Builder totalCheckpoints(long totalCheckpoints) {
            this.totalCheckpoints = totalCheckpoints;
            return this;
        }
        
        public CheckpointStatistics build() {
            return new CheckpointStatistics(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}