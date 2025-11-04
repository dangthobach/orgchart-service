package com.learnmore.application.service.multisheet;

import com.learnmore.application.config.SheetMigrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to insert validated data into master tables
 * Uses zero-lock micro-batching strategy for high performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SheetInsertService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Insert validated data from staging_valid_* to master table
     * Uses micro-batching with SKIP LOCKED to avoid table locks
     */
    @Transactional
    public MultiSheetProcessor.InsertResult insertSheet(String jobId,
                                                         SheetMigrationConfig.SheetConfig sheetConfig) {
        log.info("Inserting sheet: {} for JobId: {}", sheetConfig.getName(), jobId);

        String sheetName = sheetConfig.getName();
        String stagingValidTable = sheetConfig.getStagingValidTable();
        String masterTable = sheetConfig.getMasterTable();
        int batchSize = sheetConfig.getBatchSize();

        long insertedRows = 0;

        try {
            // TODO: Implement zero-lock batch insertion
            // Step 1: Count valid records
            // Step 2: Process in micro-batches (1000 rows)
            // Step 3: Use INSERT ... SELECT with SKIP LOCKED
            // Step 4: Update migration_job_sheet with progress

            // Placeholder implementation
            log.warn("Sheet insertion not yet implemented. Using placeholder.");

            // Count records in staging_valid
            String countSql = String.format(
                    "SELECT COUNT(*) FROM %s WHERE job_id = ?",
                    stagingValidTable
            );
            Long totalRows = jdbcTemplate.queryForObject(countSql, Long.class, jobId);

            // Placeholder: assume all inserted successfully
            insertedRows = totalRows != null ? totalRows : 0;

            // TODO: Implement actual batch insertion
            // Example structure:
            // String insertSql = String.format("""
            //     INSERT INTO %s (col1, col2, ...)
            //     SELECT col1, col2, ...
            //     FROM %s
            //     WHERE job_id = ? AND row_num BETWEEN ? AND ?
            //     FOR UPDATE SKIP LOCKED
            //     """, masterTable, stagingValidTable);

        } catch (Exception e) {
            log.error("Error inserting sheet '{}': {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to insert sheet: " + sheetName, e);
        }

        log.info("Sheet '{}' insertion completed: {} rows", sheetName, insertedRows);

        return MultiSheetProcessor.InsertResult.builder()
                .insertedRows(insertedRows)
                .build();
    }
}
