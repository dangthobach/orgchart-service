package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.dto.migration.MigrationResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho ExcelIngestService
 */
@SpringBootTest
@ActiveProfiles("test")
public class ExcelIngestServiceTest {

    @Autowired
    private ExcelIngestService excelIngestService;

    @Test
    public void testExcelIngestServiceInjection() {
        assertNotNull(excelIngestService, "ExcelIngestService should be injected");
    }

    @Test
    public void testHasErrorDataWithNonExistentJob() {
        String nonExistentJobId = "NON_EXISTENT_JOB";
        boolean hasErrors = excelIngestService.hasErrorData(nonExistentJobId);
        assertFalse(hasErrors, "Non-existent job should not have errors");
    }

    @Test
    public void testGetErrorCountWithNonExistentJob() {
        String nonExistentJobId = "NON_EXISTENT_JOB";
        long errorCount = excelIngestService.getErrorCount(nonExistentJobId);
        assertEquals(0, errorCount, "Non-existent job should have 0 errors");
    }

    @Test
    public void testGenerateErrorFileWithNonExistentJob() {
        String nonExistentJobId = "NON_EXISTENT_JOB";
        var errorFileStream = excelIngestService.generateErrorFile(nonExistentJobId);
        assertNotNull(errorFileStream, "Error file stream should not be null");
        assertEquals(0, errorFileStream.size(), "Non-existent job should have empty error file");
    }
}