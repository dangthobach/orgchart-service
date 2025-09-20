package com.learnmore.application.utils.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Advanced checkpoint manager for Excel processing error recovery
 * Provides checkpoint/resume functionality for large data processing operations
 */
public class CheckpointManager {
    private static final Logger logger = LoggerFactory.getLogger(CheckpointManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String checkpointDirectory;
    private final int checkpointInterval;
    private final boolean enableCompression;
    private final ConcurrentHashMap<String, ProcessingCheckpoint> activeCheckpoints;
    private final ReentrantLock fileLock = new ReentrantLock();
    
    public CheckpointManager(String checkpointDirectory, int checkpointInterval, boolean enableCompression) {
        this.checkpointDirectory = checkpointDirectory;
        this.checkpointInterval = checkpointInterval;
        this.enableCompression = enableCompression;
        this.activeCheckpoints = new ConcurrentHashMap<>();
        
        // Create checkpoint directory if not exists
        try {
            Files.createDirectories(Paths.get(checkpointDirectory));
        } catch (IOException e) {
            logger.error("Failed to create checkpoint directory: {}", checkpointDirectory, e);
            throw new RuntimeException("Cannot initialize checkpoint manager", e);
        }
    }
    
    /**
     * Create a new processing checkpoint
     */
    public ProcessingCheckpoint createCheckpoint(String sessionId, String fileName, long totalRows) {
        ProcessingCheckpoint checkpoint = ProcessingCheckpoint.builder()
                .sessionId(sessionId)
                .fileName(fileName)
                .totalRows(totalRows)
                .processedRows(0L)
                .lastCheckpointRow(0L)
                .createdAt(LocalDateTime.now())
                .status(CheckpointStatus.ACTIVE)
                .build();
                
        activeCheckpoints.put(sessionId, checkpoint);
        logger.info("Created checkpoint for session: {} file: {}", sessionId, fileName);
        return checkpoint;
    }
    
    /**
     * Update checkpoint with current progress
     */
    public void updateCheckpoint(String sessionId, long processedRows, Object processingState) {
        ProcessingCheckpoint checkpoint = activeCheckpoints.get(sessionId);
        if (checkpoint == null) {
            logger.warn("No active checkpoint found for session: {}", sessionId);
            return;
        }
        
        checkpoint.setProcessedRows(processedRows);
        checkpoint.setUpdatedAt(LocalDateTime.now());
        checkpoint.setProcessingState(processingState);
        
        // Save checkpoint to disk if interval reached
        if (shouldSaveCheckpoint(checkpoint)) {
            saveCheckpointToDisk(checkpoint);
            checkpoint.setLastCheckpointRow(processedRows);
        }
    }
    
    /**
     * Save checkpoint to persistent storage
     */
    public void saveCheckpointToDisk(ProcessingCheckpoint checkpoint) {
        fileLock.lock();
        try {
            String filePath = getCheckpointFilePath(checkpoint.getSessionId());
            
            if (enableCompression) {
                saveCompressedCheckpoint(checkpoint, filePath);
            } else {
                saveUncompressedCheckpoint(checkpoint, filePath);
            }
            
            logger.debug("Saved checkpoint for session: {} at row: {}", 
                    checkpoint.getSessionId(), checkpoint.getProcessedRows());
                    
        } catch (IOException e) {
            logger.error("Failed to save checkpoint for session: {}", checkpoint.getSessionId(), e);
        } finally {
            fileLock.unlock();
        }
    }
    
    /**
     * Load checkpoint from persistent storage
     */
    public ProcessingCheckpoint loadCheckpoint(String sessionId) {
        fileLock.lock();
        try {
            String filePath = getCheckpointFilePath(sessionId);
            File checkpointFile = new File(filePath);
            
            if (!checkpointFile.exists()) {
                logger.info("No checkpoint file found for session: {}", sessionId);
                return null;
            }
            
            ProcessingCheckpoint checkpoint;
            if (enableCompression) {
                checkpoint = loadCompressedCheckpoint(filePath);
            } else {
                checkpoint = loadUncompressedCheckpoint(filePath);
            }
            
            if (checkpoint != null) {
                activeCheckpoints.put(sessionId, checkpoint);
                logger.info("Loaded checkpoint for session: {} from row: {}", 
                        sessionId, checkpoint.getProcessedRows());
            }
            
            return checkpoint;
            
        } catch (IOException e) {
            logger.error("Failed to load checkpoint for session: {}", sessionId, e);
            return null;
        } finally {
            fileLock.unlock();
        }
    }
    
    /**
     * Mark checkpoint as completed
     */
    public void completeCheckpoint(String sessionId) {
        ProcessingCheckpoint checkpoint = activeCheckpoints.get(sessionId);
        if (checkpoint != null) {
            checkpoint.setStatus(CheckpointStatus.COMPLETED);
            checkpoint.setCompletedAt(LocalDateTime.now());
            saveCheckpointToDisk(checkpoint);
            
            logger.info("Completed checkpoint for session: {}", sessionId);
        }
    }
    
    /**
     * Mark checkpoint as failed
     */
    public void failCheckpoint(String sessionId, String errorMessage, Exception exception) {
        ProcessingCheckpoint checkpoint = activeCheckpoints.get(sessionId);
        if (checkpoint != null) {
            checkpoint.setStatus(CheckpointStatus.FAILED);
            checkpoint.setErrorMessage(errorMessage);
            checkpoint.setException(exception != null ? exception.toString() : null);
            checkpoint.setFailedAt(LocalDateTime.now());
            saveCheckpointToDisk(checkpoint);
            
            logger.error("Failed checkpoint for session: {} - {}", sessionId, errorMessage);
        }
    }
    
    /**
     * Clean up old checkpoint files
     */
    public void cleanupOldCheckpoints(int maxAgeHours) {
        try {
            Path checkpointDir = Paths.get(checkpointDirectory);
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(maxAgeHours);
            
            Files.list(checkpointDir)
                    .filter(path -> path.toString().endsWith(".checkpoint"))
                    .forEach(path -> {
                        try {
                            ProcessingCheckpoint checkpoint = loadUncompressedCheckpoint(path.toString());
                            if (checkpoint != null && 
                                (checkpoint.getCompletedAt() != null && checkpoint.getCompletedAt().isBefore(cutoffTime)) ||
                                (checkpoint.getFailedAt() != null && checkpoint.getFailedAt().isBefore(cutoffTime))) {
                                
                                Files.delete(path);
                                logger.debug("Cleaned up old checkpoint file: {}", path.getFileName());
                            }
                        } catch (IOException e) {
                            logger.warn("Failed to process checkpoint file: {}", path.getFileName(), e);
                        }
                    });
                    
        } catch (IOException e) {
            logger.error("Failed to cleanup old checkpoints", e);
        }
    }
    
    /**
     * Get checkpoint statistics
     */
    public CheckpointStatistics getStatistics() {
        long activeCount = activeCheckpoints.values().stream()
                .mapToLong(cp -> cp.getStatus() == CheckpointStatus.ACTIVE ? 1 : 0)
                .sum();
                
        long completedCount = activeCheckpoints.values().stream()
                .mapToLong(cp -> cp.getStatus() == CheckpointStatus.COMPLETED ? 1 : 0)
                .sum();
                
        long failedCount = activeCheckpoints.values().stream()
                .mapToLong(cp -> cp.getStatus() == CheckpointStatus.FAILED ? 1 : 0)
                .sum();
        
        return CheckpointStatistics.builder()
                .activeCheckpoints(activeCount)
                .completedCheckpoints(completedCount)
                .failedCheckpoints(failedCount)
                .totalCheckpoints(activeCheckpoints.size())
                .build();
    }
    
    // Private helper methods
    
    private boolean shouldSaveCheckpoint(ProcessingCheckpoint checkpoint) {
        return checkpoint.getProcessedRows() - checkpoint.getLastCheckpointRow() >= checkpointInterval;
    }
    
    private String getCheckpointFilePath(String sessionId) {
        return checkpointDirectory + File.separator + sessionId + ".checkpoint";
    }
    
    private void saveCompressedCheckpoint(ProcessingCheckpoint checkpoint, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            
            oos.writeObject(checkpoint);
        }
    }
    
    private void saveUncompressedCheckpoint(ProcessingCheckpoint checkpoint, String filePath) throws IOException {
        objectMapper.writeValue(new File(filePath), checkpoint);
    }
    
    private ProcessingCheckpoint loadCompressedCheckpoint(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            
            return (ProcessingCheckpoint) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize checkpoint", e);
        }
    }
    
    private ProcessingCheckpoint loadUncompressedCheckpoint(String filePath) throws IOException {
        return objectMapper.readValue(new File(filePath), ProcessingCheckpoint.class);
    }
    
    /**
     * Builder pattern for CheckpointManager
     */
    public static class Builder {
        private String checkpointDirectory = System.getProperty("java.io.tmpdir") + File.separator + "excel-checkpoints";
        private int checkpointInterval = 10000;
        private boolean enableCompression = false;
        
        public Builder checkpointDirectory(String checkpointDirectory) {
            this.checkpointDirectory = checkpointDirectory;
            return this;
        }
        
        public Builder checkpointInterval(int checkpointInterval) {
            this.checkpointInterval = checkpointInterval;
            return this;
        }
        
        public Builder enableCompression(boolean enableCompression) {
            this.enableCompression = enableCompression;
            return this;
        }
        
        public CheckpointManager build() {
            return new CheckpointManager(checkpointDirectory, checkpointInterval, enableCompression);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}