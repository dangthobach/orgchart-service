package com.learnmore.domain.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Bảng theo dõi migration job
 */
@Entity
@Table(name = "migration_job", indexes = {
    @Index(name = "idx_migration_job_id", columnList = "job_id", unique = true),
    @Index(name = "idx_migration_job_status", columnList = "status"),
    @Index(name = "idx_migration_job_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationJob {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_id", length = 50, nullable = false, unique = true)
    private String jobId;
    
    @Column(name = "filename", length = 500, nullable = false)
    private String filename;
    
    @Column(name = "status", length = 50, nullable = false)
    private String status; // STARTED, INGESTING, VALIDATING, APPLYING, COMPLETED, FAILED
    
    @Column(name = "total_rows")
    private Long totalRows;
    
    @Column(name = "processed_rows")
    private Long processedRows;
    
    @Column(name = "valid_rows")
    private Long validRows;
    
    @Column(name = "error_rows")
    private Long errorRows;
    
    @Column(name = "inserted_rows")
    private Long insertedRows;
    
    @Column(name = "current_phase", length = 50)
    private String currentPhase;
    
    @Column(name = "progress_percent")
    private Double progressPercent;
    
    @Column(name = "error_message", length = 4000)
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
