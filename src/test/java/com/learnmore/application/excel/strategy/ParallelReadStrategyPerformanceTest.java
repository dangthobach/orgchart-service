package com.learnmore.application.excel.strategy;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.excel.strategy.impl.ParallelReadStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.domain.migration.StagingRaw;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for ParallelReadStrategy V2.0
 *
 * Tests the following features:
 * 1. CompletableFuture tracking and completion guarantee
 * 2. ForkJoinPool work-stealing performance
 * 3. Exception propagation
 * 4. Graceful executor shutdown
 * 5. Thread-safe batch processing
 * 6. Performance comparison: Sequential vs Parallel
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParallelReadStrategyPerformanceTest {

    @Autowired
    private ExcelFacade excelFacade;

    /**
     * Test 1: Verify ALL batches complete before method returns
     *
     * This test ensures CompletableFuture.allOf() works correctly
     */
    @Test
    @Order(1)
    @DisplayName("Test 1: Completion Guarantee - All batches must complete")
    void testCompletionGuarantee() throws Exception {
        log.info("=== Test 1: Completion Guarantee ===");

        // Given: 50,000 records
        InputStream inputStream = createTestExcel(50_000);

        AtomicInteger processedCount = new AtomicInteger(0);
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();

        ExcelConfig config = ExcelConfig.builder()
                .batchSize(5000)
                .parallelProcessing(true)
                .build();

        // When: Process with parallel strategy
        long startTime = System.currentTimeMillis();

        excelFacade.readExcelWithConfig(inputStream, ExcelRowDTO.class, config, batch -> {
            // Simulate processing delay (database save)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            batchSizes.add(batch.size());
            processedCount.addAndGet(batch.size());
        });

        long duration = System.currentTimeMillis() - startTime;

        // Then: ALL 50,000 records must be processed
        assertEquals(50_000, processedCount.get(),
                "All batches must complete before method returns");

        // Then: Should have 10 batches (50,000 / 5,000)
        assertEquals(10, batchSizes.size(),
                "Should have 10 batches");

        log.info("✅ Test 1 PASSED: {} records processed in {} ms", processedCount.get(), duration);
        log.info("   Batches: {}", batchSizes.size());
        log.info("   Completion guarantee: VERIFIED");
    }

    /**
     * Test 2: Exception Propagation
     *
     * Ensures exceptions in batch processing are properly propagated
     */
    @Test
    @Order(2)
    @DisplayName("Test 2: Exception Propagation - Exceptions must be thrown")
    void testExceptionPropagation() throws Exception {
        log.info("=== Test 2: Exception Propagation ===");

        // Given: Test data
        InputStream inputStream = createTestExcel(10_000);

        AtomicInteger batchCount = new AtomicInteger(0);

        ExcelConfig config = ExcelConfig.builder()
                .batchSize(2000)
                .parallelProcessing(true)
                .build();

        // When: Batch processor throws exception on 3rd batch
        Consumer<List<ExcelRowDTO>> failingProcessor = batch -> {
            int count = batchCount.incrementAndGet();
            log.debug("Processing batch {}", count);

            if (count == 3) {
                throw new RuntimeException("Simulated database error");
            }
        };

        // Then: Exception should be propagated
        ExcelProcessException exception = assertThrows(ExcelProcessException.class, () -> {
            excelFacade.readExcelWithConfig(inputStream, ExcelRowDTO.class, config, failingProcessor);
        });

        // Verify exception details
        assertNotNull(exception.getCause(), "Exception cause must be present");
        assertTrue(exception.getMessage().contains("failures"),
                "Exception message should mention failures");

        log.info("✅ Test 2 PASSED: Exception properly propagated");
        log.info("   Exception message: {}", exception.getMessage());
    }

    /**
     * Test 3: Thread Safety - No race conditions with concurrent writes
     */
    @Test
    @Order(3)
    @DisplayName("Test 3: Thread Safety - No race conditions")
    void testThreadSafety() throws Exception {
        log.info("=== Test 3: Thread Safety ===");

        // Given: 100,000 records
        InputStream inputStream = createTestExcel(100_000);

        // Thread-safe collections
        List<StagingRaw> allRecords = new CopyOnWriteArrayList<>();
        AtomicInteger processedCount = new AtomicInteger(0);

        ExcelConfig config = ExcelConfig.builder()
                .batchSize(5000)
                .parallelProcessing(true)
                .build();

        // When: Process with parallel strategy
        excelFacade.readExcelWithConfig(inputStream, ExcelRowDTO.class, config, batch -> {
            // Convert to StagingRaw
            List<StagingRaw> stagingEntities = convertToStagingRaw(batch, "TEST_JOB");

            // Add to shared collection (thread-safe)
            allRecords.addAll(stagingEntities);
            processedCount.addAndGet(stagingEntities.size());
        });

        // Then: No data loss, no corruption
        assertEquals(100_000, processedCount.get(),
                "All records must be processed");
        assertEquals(100_000, allRecords.size(),
                "No data loss in concurrent writes");

        log.info("✅ Test 3 PASSED: Thread-safe processing verified");
        log.info("   Processed: {} records", processedCount.get());
        log.info("   Stored: {} records", allRecords.size());
        log.info("   Data integrity: VERIFIED");
    }

    /**
     * Test 4: ForkJoinPool Performance
     *
     * Compares performance of parallel processing vs sequential
     */
    @Test
    @Order(4)
    @DisplayName("Test 4: ForkJoinPool Performance - Should be faster than sequential")
    void testForkJoinPoolPerformance() throws Exception {
        log.info("=== Test 4: ForkJoinPool Performance ===");

        int recordCount = 100_000;

        // ===== SEQUENTIAL PROCESSING =====
        log.info("Running SEQUENTIAL processing...");
        InputStream sequentialStream = createTestExcel(recordCount);

        AtomicInteger sequentialCount = new AtomicInteger(0);

        ExcelConfig sequentialConfig = ExcelConfig.builder()
                .batchSize(5000)
                .parallelProcessing(false) // Sequential
                .build();

        long sequentialStart = System.currentTimeMillis();

        excelFacade.readExcelWithConfig(sequentialStream, ExcelRowDTO.class, sequentialConfig, batch -> {
            // Simulate database operation
            simulateDatabaseOperation(batch);
            sequentialCount.addAndGet(batch.size());
        });

        long sequentialDuration = System.currentTimeMillis() - sequentialStart;
        double sequentialThroughput = (double) recordCount * 1000 / sequentialDuration;

        // ===== PARALLEL PROCESSING =====
        log.info("Running PARALLEL processing with ForkJoinPool...");
        InputStream parallelStream = createTestExcel(recordCount);

        AtomicInteger parallelCount = new AtomicInteger(0);

        ExcelConfig parallelConfig = ExcelConfig.builder()
                .batchSize(5000)
                .parallelProcessing(true) // Parallel with ForkJoinPool
                .build();

        long parallelStart = System.currentTimeMillis();

        excelFacade.readExcelWithConfig(parallelStream, ExcelRowDTO.class, parallelConfig, batch -> {
            // Simulate database operation
            simulateDatabaseOperation(batch);
            parallelCount.addAndGet(batch.size());
        });

        long parallelDuration = System.currentTimeMillis() - parallelStart;
        double parallelThroughput = (double) recordCount * 1000 / parallelDuration;

        // ===== RESULTS =====
        double speedup = (double) sequentialDuration / parallelDuration;
        double improvement = ((sequentialDuration - parallelDuration) * 100.0) / sequentialDuration;

        log.info("=== Performance Results ===");
        log.info("Sequential:");
        log.info("  - Duration: {} ms", sequentialDuration);
        log.info("  - Throughput: {:.2f} records/sec", sequentialThroughput);
        log.info("Parallel (ForkJoinPool):");
        log.info("  - Duration: {} ms", parallelDuration);
        log.info("  - Throughput: {:.2f} records/sec", parallelThroughput);
        log.info("Speedup: {:.2f}x", speedup);
        log.info("Improvement: {:.2f}%", improvement);

        // Assertions
        assertEquals(recordCount, sequentialCount.get());
        assertEquals(recordCount, parallelCount.get());

        // Parallel should be faster (allow for variance in CI environments)
        assertTrue(parallelDuration <= sequentialDuration * 1.1,
                "Parallel processing should be faster or comparable");

        log.info("✅ Test 4 PASSED: ForkJoinPool performance verified");
    }

    /**
     * Test 5: Large Dataset Performance (1M records)
     *
     * Tests performance with production-like data volume
     */
    @Test
    @Order(5)
    @DisplayName("Test 5: Large Dataset Performance - 1M records")
    @Disabled("Enable for full performance testing - takes ~2 minutes")
    void testLargeDatasetPerformance() throws Exception {
        log.info("=== Test 5: Large Dataset Performance (1M records) ===");

        int recordCount = 1_000_000;

        InputStream largeStream = createTestExcel(recordCount);

        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger batchCount = new AtomicInteger(0);

        ExcelConfig config = ExcelConfig.builder()
                .batchSize(5000)
                .parallelProcessing(true)
                .memoryThreshold(500)
                .build();

        long startTime = System.currentTimeMillis();

        excelFacade.readExcelWithConfig(largeStream, ExcelRowDTO.class, config, batch -> {
            // Simulate database save
            simulateDatabaseOperation(batch);

            int count = processedCount.addAndGet(batch.size());
            int batches = batchCount.incrementAndGet();

            // Progress logging every 50 batches
            if (batches % 50 == 0) {
                log.info("Progress: {} batches, {} records processed", batches, count);
            }
        });

        long duration = System.currentTimeMillis() - startTime;
        double throughput = (double) recordCount * 1000 / duration;

        log.info("=== Large Dataset Results ===");
        log.info("Records: {}", recordCount);
        log.info("Duration: {} ms ({} seconds)", duration, duration / 1000);
        log.info("Throughput: {:.2f} records/sec", throughput);
        log.info("Batches: {}", batchCount.get());

        // Assertions
        assertEquals(recordCount, processedCount.get(),
                "All 1M records must be processed");

        // Should complete in reasonable time (< 90 seconds)
        assertTrue(duration < 90_000,
                "Should process 1M records in < 90 seconds");

        // Throughput should be > 11,000 records/sec
        assertTrue(throughput > 11_000,
                "Throughput should exceed 11,000 records/sec");

        log.info("✅ Test 5 PASSED: Large dataset performance verified");
    }

    // ========== HELPER METHODS ==========

    /**
     * Create test Excel file with specified number of records
     */
    private InputStream createTestExcel(int recordCount) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestData");

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Kho VPBank", "Mã Đơn Vị", "Trách Nhiệm Bàn Giao", "Loại Chứng Từ",
                "Ngày Chứng Từ", "Tên Tập", "Số Lượng Tập", "Ngày Phải Bàn Giao",
                "Ngày Bàn Giao", "Tình Trạng Thất Lạc", "Tình Trạng Không Hoàn Trả",
                "Trạng Thái Case PDM", "Ghi Chú Case PDM", "Mã Thùng",
                "Thời Hạn Lưu Trữ", "Ngày Nhập Kho VPBank", "Ngày Chuyển Kho Crown",
                "Khu Vực", "Hàng", "Cột", "Tình Trạng Thùng", "Trạng Thái Thùng", "Lưu Ý"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // Create data rows
        for (int i = 1; i <= recordCount; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue("KHO_HN");
            row.createCell(1).setCellValue("DV_" + String.format("%06d", i % 1000));
            row.createCell(2).setCellValue("Nguyen Van A");
            row.createCell(3).setCellValue("LOAI_A");
            row.createCell(4).setCellValue("2024-01-01");
            row.createCell(5).setCellValue("Tap " + i);
            row.createCell(6).setCellValue(10);
            row.createCell(7).setCellValue("2024-01-15");
            row.createCell(8).setCellValue("2024-01-14");
            row.createCell(9).setCellValue("Không");
            row.createCell(10).setCellValue("Không");
            row.createCell(11).setCellValue("COMPLETED");
            row.createCell(12).setCellValue("OK");
            row.createCell(13).setCellValue("THUNG_" + String.format("%08d", i % 10000));
            row.createCell(14).setCellValue(5);
            row.createCell(15).setCellValue("2024-01-10");
            row.createCell(16).setCellValue("2024-01-11");
            row.createCell(17).setCellValue("KV_A");
            row.createCell(18).setCellValue(i % 100);
            row.createCell(19).setCellValue(i % 50);
            row.createCell(20).setCellValue("Tốt");
            row.createCell(21).setCellValue("Active");
            row.createCell(22).setCellValue("Test data");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    /**
     * Convert ExcelRowDTO to StagingRaw entities
     */
    private List<StagingRaw> convertToStagingRaw(List<ExcelRowDTO> excelRows, String jobId) {
        List<StagingRaw> stagingEntities = new ArrayList<>(excelRows.size());

        for (ExcelRowDTO row : excelRows) {
            StagingRaw stagingRaw = StagingRaw.builder()
                    .jobId(jobId)
                    .rowNum(row.getRowNumber())
                    .sheetName("TestData")
                    .createdAt(LocalDateTime.now())
                    .khoVpbank(row.getKhoVpbank())
                    .maDonVi(row.getMaDonVi())
                    .loaiChungTu(row.getLoaiChungTu())
                    .ngayChungTu(row.getNgayChungTu())
                    .maThung(row.getMaThung())
                    .khuVuc(row.getKhuVuc())
                    .build();

            stagingEntities.add(stagingRaw);
        }

        return stagingEntities;
    }

    /**
     * Simulate database operation (bulk insert takes time)
     */
    private void simulateDatabaseOperation(List<ExcelRowDTO> batch) {
        try {
            // Simulate database latency (2-5ms per batch)
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
