package com.learnmore.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity để track progress của từng sheet trong migration job
 */
@Entity
@Table(name = "migration_job_sheet")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationJobSheetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 100)
    private String jobId;

    @Column(name = "sheet_name", nullable = false, length = 100)
    private String sheetName;

    @Column(name = "sheet_order", nullable = false)
    private Integer sheetOrder;

    // Status tracking
    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "current_phase", length = 100)
    private String currentPhase;

    @Column(name = "progress_percent", precision = 5, scale = 2)
    private BigDecimal progressPercent;

    // Counters
    @Column(name = "total_rows")
    private Long totalRows;

    @Column(name = "ingested_rows")
    private Long ingestedRows;

    @Column(name = "valid_rows")
    private Long validRows;

    @Column(name = "error_rows")
    private Long errorRows;

    @Column(name = "inserted_rows")
    private Long insertedRows;

    // Timing
    @Column(name = "ingest_start_time")
    private LocalDateTime ingestStartTime;

    @Column(name = "ingest_end_time")
    private LocalDateTime ingestEndTime;

    @Column(name = "validation_start_time")
    private LocalDateTime validationStartTime;

    @Column(name = "validation_end_time")
    private LocalDateTime validationEndTime;

    @Column(name = "insertion_start_time")
    private LocalDateTime insertionStartTime;

    @Column(name = "insertion_end_time")
    private LocalDateTime insertionEndTime;

    // Error info
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking version field
     * Prevents lost updates in concurrent scenarios
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (progressPercent == null) {
            progressPercent = BigDecimal.ZERO;
        }
        if (status == null) {
            status = "PENDING";
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate ingest duration in milliseconds
     */
    public Long getIngestDurationMs() {
        if (ingestStartTime != null && ingestEndTime != null) {
            return java.time.Duration.between(ingestStartTime, ingestEndTime).toMillis();
        }
        return null;
    }

    /**
     * Calculate validation duration in milliseconds
     */
    public Long getValidationDurationMs() {
        if (validationStartTime != null && validationEndTime != null) {
            return java.time.Duration.between(validationStartTime, validationEndTime).toMillis();
        }
        return null;
    }

    /**
     * Calculate insertion duration in milliseconds
     */
    public Long getInsertionDurationMs() {
        if (insertionStartTime != null && insertionEndTime != null) {
            return java.time.Duration.between(insertionStartTime, insertionEndTime).toMillis();
        }
        return null;
    }

    /**
     * Calculate total duration in milliseconds
     */
    public Long getTotalDurationMs() {
        if (ingestStartTime != null && insertionEndTime != null) {
            return java.time.Duration.between(ingestStartTime, insertionEndTime).toMillis();
        }
        return null;
    }

    /**
     * Check if sheet is completed
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    /**
     * Check if sheet is in progress
     */
    public boolean isInProgress() {
        return "INGESTING".equals(status) || "VALIDATING".equals(status) || "INSERTING".equals(status);
    }
}
