package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test for Migration Performance with maxRows Validation
 *
 * Tests the complete migration flow with row limit validation:
 * - Validates maxRows parameter enforcement
 * - Verifies mark/reset streaming (NO memory loading)
 * - Measures memory usage (should be constant ~8MB)
 * - Tests full migration pipeline (Ingest → Validate → Apply → Reconcile)
 *
 * CRITICAL FIX TESTED:
 * - BEFORE: readAllBytes() loaded 500MB-2GB into memory
 * - AFTER: mark/reset streaming uses constant 8MB
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Migration Performance Tests - maxRows Validation")
class MigrationPerformanceMaxRowsTest {

    @Autowired
    private MigrationOrchestrationService migrationOrchestrationService;

    private Runtime runtime;

    @BeforeEach
    void setUp() {
        runtime = Runtime.getRuntime();
        // Force GC before each test for accurate memory measurement
        System.gc();
        try {
            Thread.sleep(100); // Give GC time to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Test Case 1: maxRows=10 with 15-row file should REJECT
     *
     * Expected: Validation fails with clear error message
     */
    @Test
    @DisplayName("Should reject when file exceeds maxRows limit (15 rows > 10 maxRows)")
    void testPerformFullMigration_WithMaxRows10_On15RecordsFile_ShouldReject() throws IOException {
        // Given
        String testFile = "test-data-15-rows.xlsx";
        int maxRows = 10;
        int actualRows = 15;

        // When
        MigrationResultDTO result;
        try (InputStream inputStream = loadTestFile(testFile);
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 64 * 1024)) {

            result = migrationOrchestrationService.performFullMigration(
                    bufferedStream,
                    testFile,
                    "test-user",
                    maxRows
            );
        }

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isIn("FAILED", "INGESTING_FAILED");
        assertThat(result.getErrorMessage())
                .isNotNull()
                .containsIgnoringCase("exceed")
                .containsAnyOf("row", "limit", "maximum", String.valueOf(maxRows));

        System.out.println("✅ TEST PASSED: File with " + actualRows + " rows correctly rejected when maxRows=" + maxRows);
        System.out.println("   Error message: " + result.getErrorMessage());
    }

    /**
     * Test Case 2: maxRows=20 with 15-row file should ACCEPT
     *
     * Expected: Success, processes all 15 records
     */
    @Test
    @DisplayName("Should accept when file is within maxRows limit (15 rows ≤ 20 maxRows)")
    void testPerformFullMigration_WithMaxRows20_On15RecordsFile_ShouldPass() throws IOException {
        // Given
        String testFile = "test-data-15-rows.xlsx";
        int maxRows = 20;
        int expectedRows = 15;

        // When
        MigrationResultDTO result;
        try (InputStream inputStream = loadTestFile(testFile);
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 64 * 1024)) {

            result = migrationOrchestrationService.performFullMigration(
                    bufferedStream,
                    testFile,
                    "test-user",
                    maxRows
            );
        }

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.getStatus()).isIn("COMPLETED", "RECONCILIATION_COMPLETED");
        assertThat(result.getTotalRows()).isGreaterThanOrEqualTo(expectedRows);

        System.out.println("✅ TEST PASSED: File with " + expectedRows + " rows accepted when maxRows=" + maxRows);
        System.out.println("   Processed: " + result.getProcessedRows() + " rows");
        System.out.println("   Valid: " + result.getValidRows() + " rows");
    }

    /**
     * Test Case 3: maxRows=0 (no limit) with 15-row file should ACCEPT
     *
     * Expected: Success, processes all 15 records without validation
     */
    @Test
    @DisplayName("Should accept any file size when maxRows=0 (no limit)")
    void testPerformFullMigration_WithMaxRows0_On15RecordsFile_ShouldPass() throws IOException {
        // Given
        String testFile = "test-data-15-rows.xlsx";
        int maxRows = 0; // No limit
        int expectedRows = 15;

        // When
        MigrationResultDTO result;
        try (InputStream inputStream = loadTestFile(testFile);
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 64 * 1024)) {

            result = migrationOrchestrationService.performFullMigration(
                    bufferedStream,
                    testFile,
                    "test-user",
                    maxRows
            );
        }

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.getTotalRows()).isGreaterThanOrEqualTo(expectedRows);

        System.out.println("✅ TEST PASSED: File with " + expectedRows + " rows accepted when maxRows=0 (no limit)");
        System.out.println("   Total rows: " + result.getTotalRows());
    }

    /**
     * Test Case 4: Memory usage should be constant (CRITICAL PERFORMANCE FIX)
     *
     * Tests that memory usage remains constant during processing
     * - BEFORE FIX: 150MB-1.5GB spike (readAllBytes)
     * - AFTER FIX: Constant ~8MB (mark/reset streaming)
     *
     * Expected: Memory increase < 20MB (allowing for JVM overhead)
     */
    @Test
    @DisplayName("Should use constant memory with mark/reset streaming (< 20MB increase)")
    void testMemoryUsage_WithMaxRows10_ShouldUseConstantMemory() throws IOException {
        // Given
        String testFile = "test-data-100-rows.xlsx"; // Larger file to test memory
        int maxRows = 0; // No limit for this test

        // Measure memory BEFORE
        long memoryBefore = getUsedMemory();

        // When
        MigrationResultDTO result;
        try (InputStream inputStream = loadTestFile(testFile);
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 64 * 1024)) {

            result = migrationOrchestrationService.performFullMigration(
                    bufferedStream,
                    testFile,
                    "test-user",
                    maxRows
            );
        }

        // Force GC to get accurate reading
        System.gc();
        Thread.yield();

        // Measure memory AFTER
        long memoryAfter = getUsedMemory();
        long memoryIncrease = memoryAfter - memoryBefore;
        long memoryIncreaseMB = memoryIncrease / (1024 * 1024);

        // Then
        assertThat(result).isNotNull();

        // Memory increase should be minimal (< 20MB)
        // BEFORE FIX: Would be 150MB-1.5GB
        // AFTER FIX: Should be < 20MB
        assertThat(memoryIncrease)
                .as("Memory increase should be minimal (< 20MB) with streaming approach")
                .isLessThan(20 * 1024 * 1024); // 20MB threshold

        System.out.println("✅ TEST PASSED: Memory usage is constant");
        System.out.println("   Memory before: " + (memoryBefore / 1024 / 1024) + " MB");
        System.out.println("   Memory after:  " + (memoryAfter / 1024 / 1024) + " MB");
        System.out.println("   Memory increase: " + memoryIncreaseMB + " MB (threshold: 20MB)");

        if (memoryIncreaseMB > 50) {
            System.err.println("⚠️  WARNING: Memory increase (" + memoryIncreaseMB + "MB) suggests readAllBytes() may still be used!");
        }
    }

    /**
     * Test Case 5: Performance - 100 rows should process in < 2 seconds
     */
    @Test
    @DisplayName("Should process 100 rows in < 2 seconds")
    void testPerformance_100Rows_ShouldCompleteInUnder2Seconds() throws IOException {
        // Given
        String testFile = "test-data-100-rows.xlsx";
        int maxRows = 0; // No limit

        // When
        long startTime = System.currentTimeMillis();

        MigrationResultDTO result;
        try (InputStream inputStream = loadTestFile(testFile);
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 64 * 1024)) {

            result = migrationOrchestrationService.performFullMigration(
                    bufferedStream,
                    testFile,
                    "test-user",
                    maxRows
            );
        }

        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isFalse();
        assertThat(processingTime)
                .as("Processing 100 rows should complete in < 2000ms")
                .isLessThan(2000);

        System.out.println("✅ TEST PASSED: Performance test");
        System.out.println("   Processed: " + result.getTotalRows() + " rows");
        System.out.println("   Time: " + processingTime + " ms");
        System.out.println("   Throughput: " + (result.getTotalRows() * 1000 / processingTime) + " rows/sec");
    }

    /**
     * Test Case 6: Verify mark/reset is used (indirect test)
     *
     * Tests that validation BEFORE processing works correctly
     * This only works if mark/reset is functioning
     */
    @Test
    @DisplayName("Should validate THEN process (mark/reset working)")
    void testMarkResetFunctionality_ShouldValidateBeforeProcessing() throws IOException {
        // Given
        String testFile = "test-data-10-rows.xlsx";
        int maxRows = 5; // Will fail validation

        // When
        MigrationResultDTO result;
        try (InputStream inputStream = loadTestFile(testFile);
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 64 * 1024)) {

            result = migrationOrchestrationService.performFullMigration(
                    bufferedStream,
                    testFile,
                    "test-user",
                    maxRows
            );
        }

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorMessage()).containsIgnoringCase("exceed");

        // If mark/reset didn't work, we'd get a different error
        // (like "stream closed" or "cannot read stream twice")
        assertThat(result.getErrorMessage())
                .doesNotContainIgnoringCase("stream closed")
                .doesNotContainIgnoringCase("cannot read");

        System.out.println("✅ TEST PASSED: mark/reset allows validation before processing");
        System.out.println("   Correctly rejected 10-row file with maxRows=5");
    }

    // ========== Helper Methods ==========

    /**
     * Load test file from classpath with BufferedInputStream for mark/reset support
     */
    private InputStream loadTestFile(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(filename);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Test file not found: " + filename);
        }
        return resource.getInputStream();
    }

    /**
     * Get current JVM used memory in bytes
     */
    private long getUsedMemory() {
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
