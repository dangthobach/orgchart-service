package com.learnmore.domain.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Bảng staging lưu trữ lỗi validation
 */
@Entity
@Table(name = "staging_error", indexes = {
    @Index(name = "idx_staging_error_job", columnList = "job_id"),
    @Index(name = "idx_staging_error_type", columnList = "job_id, error_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagingError {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_id", length = 50, nullable = false)
    private String jobId;
    
    @Column(name = "row_num", nullable = false)
    private Integer rowNum;
    
    @Column(name = "error_type", length = 100, nullable = false)
    private String errorType; // REQUIRED_MISSING, INVALID_DATE, DUP_IN_FILE, REF_NOT_FOUND, DUP_IN_DB
    
    @Column(name = "error_field", length = 100)
    private String errorField;
    
    @Column(name = "error_value", length = 1000)
    private String errorValue;
    
    @Column(name = "error_message", length = 2000, nullable = false)
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Dữ liệu gốc để trace back
    @Column(name = "original_data", columnDefinition = "TEXT")
    private String originalData;
}
