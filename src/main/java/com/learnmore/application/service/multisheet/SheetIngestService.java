package com.learnmore.application.service.multisheet;

import com.learnmore.application.config.SheetMigrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
     * Ingest a single sheet from memory using InputStream
     * Reads sheet using SAX streaming and saves to staging_raw_* table
     */
    public MultiSheetProcessor.IngestResult ingestSheetFromMemory(String jobId,
                                                                   InputStream inputStream,
                                                                   SheetMigrationConfig.SheetConfig sheetConfig) {
        log.info("Ingesting sheet from memory: {} for JobId: {}", sheetConfig.getName(), jobId);

        String sheetName = sheetConfig.getName();
        String stagingTable = sheetConfig.getStagingRawTable();
        int batchSize = sheetConfig.getBatchSize();

        long ingestedRows = 0;
        long startTime = System.currentTimeMillis();

        // Use try-with-resources to ensure proper cleanup even on exceptions
        // This prevents resource leaks when multiple threads process sheets concurrently
        try (OPCPackage pkg = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);

            // Find the target sheet
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            InputStream sheetStream = null;
            String foundSheetName = null;
            
            while (sheetIterator.hasNext()) {
                sheetStream = sheetIterator.next();
                foundSheetName = sheetIterator.getSheetName();
                if (foundSheetName.equals(sheetName)) {
                    break;
                }
                // Close stream if not the target sheet to prevent resource leak
                if (sheetStream != null && !foundSheetName.equals(sheetName)) {
                    try {
                        sheetStream.close();
                    } catch (Exception e) {
                        log.warn("Failed to close sheet stream for non-target sheet: {}", foundSheetName, e);
                    }
                    sheetStream = null;
                }
            }

            if (sheetStream == null) {
                throw new RuntimeException("Sheet not found: " + sheetName + ". Available sheets may not include this sheet.");
            }

            // Process sheet with try-with-resources for sheet stream
            try (InputStream finalSheetStream = sheetStream) {
                // Create SAX handler to process rows
                IngestHandler handler = new IngestHandler(jobId, stagingTable, sheetConfig, jdbcTemplate, batchSize);
                
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, handler, false));
                parser.parse(new InputSource(finalSheetStream));

                ingestedRows = handler.getRowCount();
                handler.flush(); // Flush remaining batch
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Sheet '{}' ingestion completed: {} rows in {} ms", sheetName, ingestedRows, duration);

            return MultiSheetProcessor.IngestResult.builder()
                    .ingestedRows(ingestedRows)
                    .build();

        } catch (Exception e) {
            log.error("Error ingesting sheet from memory '{}': {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to ingest sheet: " + sheetName, e);
        }
    }

    /**
     * Ingest a single sheet from Excel file
     * @deprecated Use ingestSheetFromMemory() to avoid file I/O overhead
     */
    @Deprecated
    public MultiSheetProcessor.IngestResult ingestSheet(String jobId,
                                                         String filePath,
                                                         SheetMigrationConfig.SheetConfig sheetConfig) {
        log.info("Ingesting sheet: {} for JobId: {}", sheetConfig.getName(), jobId);

        String sheetName = sheetConfig.getName();

        long ingestedRows = 0;

        try {
            // TODO: Implement actual ingestion using ExcelFacade or migrate to ingestSheetFromMemory()
            // Step 1: Read sheet with SAX streaming
            // Step 2: Normalize data
            // Step 3: Generate business key
            // Step 4: Batch insert to staging_raw table

            // Placeholder implementation
            log.warn("Sheet ingestion not yet implemented. Using placeholder. Consider using ingestSheetFromMemory().");

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

    /**
     * SAX handler for ingesting Excel rows into staging table
     * TODO: Implement actual field mapping and business key generation per sheet type
     */
    private static class IngestHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final String jobId;
        private final String stagingTable;
        private final SheetMigrationConfig.SheetConfig sheetConfig;
        private final JdbcTemplate jdbcTemplate;
        private final int batchSize;
        private final List<Object[]> batchBuffer = new ArrayList<>();
        private int rowCount = 0;
        private int currentRow = -1;
        private List<String> currentRowData = new ArrayList<>();
        @SuppressWarnings("unused")
        private List<String> headers = null; // TODO: Will be used for field mapping

        public IngestHandler(String jobId, String stagingTable, SheetMigrationConfig.SheetConfig sheetConfig,
                             JdbcTemplate jdbcTemplate, int batchSize) {
            this.jobId = jobId;
            this.stagingTable = stagingTable;
            this.sheetConfig = sheetConfig;
            this.jdbcTemplate = jdbcTemplate;
            this.batchSize = batchSize;
        }

        @Override
        public void startRow(int rowNum) {
            this.currentRow = rowNum;
            this.currentRowData = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (currentRow == 0) {
                // Store header row for field mapping
                headers = new ArrayList<>(currentRowData);
                return;
            }

            // Build row data: job_id, row_num, sheet_name, business_key, ...field values
            // TODO: Generate business_key based on sheet-specific logic
            String businessKey = String.format("%s_%s_%d", jobId, sheetConfig.getName(), currentRow);
            
            Object[] rowData = new Object[currentRowData.size() + 4]; // +4 for job_id, row_num, sheet_name, business_key
            rowData[0] = jobId;
            rowData[1] = currentRow;
            rowData[2] = sheetConfig.getName();
            rowData[3] = businessKey;

            // Map columns to fields
            for (int i = 0; i < currentRowData.size(); i++) {
                rowData[i + 4] = currentRowData.get(i);
            }

            batchBuffer.add(rowData);
            rowCount++;

            // Batch insert when buffer is full
            if (batchBuffer.size() >= batchSize) {
                flush();
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            currentRowData.add(formattedValue);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Not needed for ingestion
        }

        @Override
        public void endSheet() {
            flush();
        }

        public void flush() {
            if (batchBuffer.isEmpty()) {
                return;
            }

            // TODO: Generate INSERT SQL based on actual staging table schema
            // For now, use generic placeholder that will need sheet-specific implementation
            int fieldCount = batchBuffer.get(0).length - 4; // Subtract fixed fields
            StringBuilder sql = new StringBuilder(
                String.format("INSERT INTO %s (job_id, row_num, sheet_name, business_key", stagingTable));
            StringBuilder valuePlaceholders = new StringBuilder("(?, ?, ?, ?");

            for (int i = 0; i < fieldCount; i++) {
                sql.append(", col_").append(i); // Placeholder - will use actual column names from schema
                valuePlaceholders.append(", ?");
            }

            sql.append(", created_at) VALUES ").append(valuePlaceholders).append(", CURRENT_TIMESTAMP)");

            try {
                jdbcTemplate.batchUpdate(sql.toString(), batchBuffer);
            } catch (Exception e) {
                // Log and rethrow - TODO: implement error handling per row
                throw new RuntimeException("Batch insert failed: " + e.getMessage(), e);
            }
            
            batchBuffer.clear();
        }

        public int getRowCount() {
            return rowCount;
        }
    }
}
