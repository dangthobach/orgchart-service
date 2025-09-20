package com.learnmore.application.utils.checkpoint;

/**
 * Checkpoint status enumeration for tracking processing state
 */
public enum CheckpointStatus {
    /**
     * Processing is currently active and can be resumed
     */
    ACTIVE("Active"),
    
    /**
     * Processing has been completed successfully
     */
    COMPLETED("Completed"),
    
    /**
     * Processing has failed and requires intervention
     */
    FAILED("Failed"),
    
    /**
     * Processing has been paused and can be resumed
     */
    PAUSED("Paused"),
    
    /**
     * Processing has been cancelled by user
     */
    CANCELLED("Cancelled");
    
    private final String displayName;
    
    CheckpointStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if the checkpoint can be resumed from this status
     */
    public boolean canResume() {
        return this == ACTIVE || this == PAUSED;
    }
    
    /**
     * Check if the checkpoint is in a final state
     */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}