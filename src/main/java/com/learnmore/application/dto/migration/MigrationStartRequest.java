package com.learnmore.application.dto.migration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for starting multi-sheet migration
 * With validation constraints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationStartRequest {

    @NotBlank(message = "JobId is required")
    @Pattern(regexp = "^JOB-\\d{8}-\\d{3}$",
             message = "JobId must match format: JOB-YYYYMMDD-XXX")
    private String jobId;

    @NotBlank(message = "File path is required")
    private String filePath;

    // Optional parameters
    @Builder.Default
    private Boolean async = false;

    @Builder.Default
    private Boolean testMode = false;

    private Integer testRowLimit;
}
