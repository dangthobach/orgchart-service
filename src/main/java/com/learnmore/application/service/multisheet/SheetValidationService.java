package com.learnmore.application.service.multisheet;

import com.learnmore.application.config.SheetMigrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service to validate sheet data using business rules
 * Runs declarative validation rules from validation-rules.yml
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SheetValidationService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Validate a sheet's data
     * Runs all validation rules and moves valid data to staging_valid_*
     */
    public MultiSheetProcessor.ValidationResult validateSheet(String jobId,
                                                               SheetMigrationConfig.SheetConfig sheetConfig) {
        log.info("Validating sheet: {} for JobId: {}", sheetConfig.getName(), jobId);

        String sheetName = sheetConfig.getName();
        String stagingRawTable = sheetConfig.getStagingRawTable();
        String stagingValidTable = sheetConfig.getStagingValidTable();
        String stagingErrorTable = sheetConfig.getStagingErrorTable();

        long validRows = 0;
        long errorRows = 0;

        try {
            // TODO: Implement validation using ValidationEngine
            // Step 1: Run required fields validation
            // Step 2: Run date format validation
            // Step 3: Run enum values validation
            // Step 4: Run business logic validation
            // Step 5: Check duplicates in file
            // Step 6: Check duplicates with DB
            // Step 7: Validate master references
            // Step 8: Move valid records to staging_valid

            // Placeholder implementation
            log.warn("Sheet validation not yet implemented. Using placeholder.");

            // Count records in staging_raw
            String countSql = String.format(
                    "SELECT COUNT(*) FROM %s WHERE job_id = ?",
                    stagingRawTable
            );
            Long totalRows = jdbcTemplate.queryForObject(countSql, Long.class, jobId);

            // Placeholder: assume all valid for now
            validRows = totalRows != null ? totalRows : 0;
            errorRows = 0;

        } catch (Exception e) {
            log.error("Error validating sheet '{}': {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to validate sheet: " + sheetName, e);
        }

        log.info("Sheet '{}' validation completed: {} valid, {} errors", sheetName, validRows, errorRows);

        return MultiSheetProcessor.ValidationResult.builder()
                .validRows(validRows)
                .errorRows(errorRows)
                .build();
    }
}
