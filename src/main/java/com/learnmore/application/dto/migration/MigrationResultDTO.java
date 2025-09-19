package com.learnmore.application.dto.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO cho kết quả migration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationResultDTO {
    
    private String jobId;
    private String status;
    private String filename;
    
    private Long totalRows;
    private Long processedRows;
    private Long validRows;
    private Long errorRows;
    private Long insertedRows;
    
    private String currentPhase;
    private Double progressPercent;
    
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long processingTimeMs;
    
    private String errorMessage;
    private List<ValidationErrorDTO> validationErrors;
    
    // Statistics by phase
    private Long ingestTimeMs;
    private Long validationTimeMs;
    private Long applyTimeMs;
    private Long reconcileTimeMs;
    
    // Memory and performance metrics
    private Long maxMemoryUsedMB;
    private Double avgProcessingRate; // records per second
    
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    public boolean isInProgress() {
        return "STARTED".equals(status) || "INGESTING".equals(status) || 
               "VALIDATING".equals(status) || "APPLYING".equals(status);
    }
}
