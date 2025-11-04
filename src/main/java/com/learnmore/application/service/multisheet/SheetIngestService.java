package com.learnmore.application.service.multisheet;

import com.learnmore.application.config.SheetMigrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service to ingest Excel sheet data into staging_raw tables
 * Uses ExcelFacade with SAX streaming for high performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SheetIngestService {

    private final JdbcTemplate jdbcTemplate;
    // private final ExcelFacade excelFacade; // TODO: Inject when implementing

    /**
     * Ingest a single sheet from Excel file
     * Reads sheet using SAX streaming and saves to staging_raw_* table
     */
    public MultiSheetProcessor.IngestResult ingestSheet(String jobId,
                                                         String filePath,
                                                         SheetMigrationConfig.SheetConfig sheetConfig) {
        log.info("Ingesting sheet: {} for JobId: {}", sheetConfig.getName(), jobId);

        String sheetName = sheetConfig.getName();
        String stagingTable = sheetConfig.getStagingRawTable();
        int batchSize = sheetConfig.getBatchSize();

        long ingestedRows = 0;

        try {
            // TODO: Implement actual ingestion using ExcelFacade
            // Step 1: Read sheet with SAX streaming
            // Step 2: Normalize data
            // Step 3: Generate business key
            // Step 4: Batch insert to staging_raw table

            // Placeholder implementation
            log.warn("Sheet ingestion not yet implemented. Using placeholder.");

            // Example SQL structure (will be implemented per sheet type)
            String insertSql = String.format("""
                INSERT INTO %s (
                    job_id, row_num, sheet_name, business_key,
                    kho_vpbank_norm, ma_don_vi_norm, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, stagingTable);

            // TODO: Implement actual batch insert with real data
            ingestedRows = 0; // Placeholder

        } catch (Exception e) {
            log.error("Error ingesting sheet '{}': {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to ingest sheet: " + sheetName, e);
        }

        log.info("Sheet '{}' ingestion completed: {} rows", sheetName, ingestedRows);

        return MultiSheetProcessor.IngestResult.builder()
                .ingestedRows(ingestedRows)
                .build();
    }
}
