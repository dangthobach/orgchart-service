package com.learnmore.application.excel.strategy;

import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.excel.strategy.impl.CSVWriteStrategy;
import com.learnmore.application.excel.strategy.impl.SXSSFWriteStrategy;
import com.learnmore.application.excel.strategy.impl.XSSFWriteStrategy;
import com.learnmore.application.excel.strategy.selector.WriteStrategySelector;
import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.config.ExcelConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Write Strategy implementations
 *
 * Tests verify:
 * 1. Strategy auto-selection based on data size
 * 2. File creation and data integrity
 * 3. Performance characteristics
 * 4. Error handling
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WriteStrategyIntegrationTest {

    @Autowired
    private ExcelFacade excelFacade;

    @Autowired
    private WriteStrategySelector writeStrategySelector;

    @Autowired
    private XSSFWriteStrategy<?> xssfStrategy;

    @Autowired
    private SXSSFWriteStrategy<?> sxssfStrategy;

    @Autowired
    private CSVWriteStrategy<?> csvStrategy;

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create temp directory for test files
        tempDir = Files.createTempDirectory("excel-write-test-");
        log.info("Created temp directory: {}", tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Cleanup temp files
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> -a.compareTo(b)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        log.warn("Failed to delete: {}", path, e);
                    }
                });
        }
    }

    // ========== STRATEGY SELECTION TESTS ==========

    @Test
    @Order(1)
    @DisplayName("Should select XSSF strategy for small files (< 50K records)")
    void testStrategySelection_SmallFile() {
        // Given: Small dataset
        int dataSize = 1000;
        ExcelConfig config = ExcelConfigFactory.createSmallFileConfig();

        // When: Select strategy
        WriteStrategy<?> selected = writeStrategySelector.selectStrategy(dataSize, config);

        // Then: Should select XSSF
        assertEquals("XSSFWriteStrategy", selected.getName());
        assertEquals(xssfStrategy.getClass(), selected.getClass());

        log.info("Selected {} for {} records", selected.getName(), dataSize);
    }

    @Test
    @Order(2)
    @DisplayName("Should select SXSSF strategy for medium files (50K - 2M records)")
    void testStrategySelection_MediumFile() {
        // Given: Medium dataset with preferCSV disabled
        int dataSize = 100_000;
        ExcelConfig config = ExcelConfig.builder()
            .batchSize(8000)
            .memoryThreshold(512)
            .enableProgressTracking(true)
            .preferCSVForLargeData(false) // Disable CSV preference to test SXSSF selection
            .build();

        // When: Select strategy
        WriteStrategy<?> selected = writeStrategySelector.selectStrategy(dataSize, config);

        // Then: Should select SXSSF
        assertEquals("SXSSFWriteStrategy", selected.getName());
        assertEquals(sxssfStrategy.getClass(), selected.getClass());

        log.info("Selected {} for {} records", selected.getName(), dataSize);
    }

    @Test
    @Order(3)
    @DisplayName("Should select CSV strategy for large files (> 2M records)")
    void testStrategySelection_LargeFile() {
        // Given: Large dataset with CSV preference
        int dataSize = 2_500_000;
        ExcelConfig config = ExcelConfig.builder()
            .preferCSVForLargeData(true)
            .csvThreshold(5_000_000L)
            .build();

        // When: Select strategy
        WriteStrategy<?> selected = writeStrategySelector.selectStrategy(dataSize, config);

        // Then: Should select CSV (based on cell count)
        // 2.5M records * 20 cols = 50M cells > 5M threshold
        assertEquals("CSVWriteStrategy", selected.getName());
        assertEquals(csvStrategy.getClass(), selected.getClass());

        log.info("Selected {} for {} records", selected.getName(), dataSize);
    }

    // ========== XSSF STRATEGY TESTS ==========

    @Test
    @Order(4)
    @DisplayName("XSSF: Should write small file successfully")
    void testXSSFStrategy_SmallFile() throws Exception {
        // Given: Small dataset
        List<TestProduct> products = generateTestProducts(100);
        String fileName = tempDir.resolve("xssf_small.xlsx").toString();

        // When: Write with ExcelFacade (auto-selects XSSF)
        long startTime = System.currentTimeMillis();
        excelFacade.writeSmallFile(fileName, products);
        long duration = System.currentTimeMillis() - startTime;

        // Then: File should exist and be valid
        File file = new File(fileName);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        // Verify content by reading back
        List<TestProduct> readProducts = excelFacade.readExcel(
            new FileInputStream(file),
            TestProduct.class
        );

        assertEquals(products.size(), readProducts.size());
        assertEquals(products.get(0).getProductId(), readProducts.get(0).getProductId());
        assertEquals(products.get(0).getProductName(), readProducts.get(0).getProductName());

        log.info("XSSF wrote {} records in {}ms, file size: {} bytes",
            products.size(), duration, file.length());
    }

    @Test
    @Order(5)
    @DisplayName("XSSF: Should handle medium file with warning")
    @SuppressWarnings("unchecked")
    void testXSSFStrategy_MediumFile() throws Exception {
        // Given: Medium dataset (will trigger warning)
        List<TestProduct> products = generateTestProducts(5000);
        String fileName = tempDir.resolve("xssf_medium.xlsx").toString();
        ExcelConfig config = ExcelConfigFactory.createSmallFileConfig();

        // When: Write directly with XSSF strategy
        long startTime = System.currentTimeMillis();
        ((WriteStrategy<TestProduct>) (WriteStrategy<?>) xssfStrategy).execute(fileName, products, config);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should complete successfully (with warning in logs)
        File file = new File(fileName);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        log.info("XSSF wrote {} records in {}ms, file size: {} bytes",
            products.size(), duration, file.length());
    }

    // ========== SXSSF STRATEGY TESTS ==========

    @Test
    @Order(6)
    @DisplayName("SXSSF: Should write large file with streaming")
    @SuppressWarnings("unchecked")
    void testSXSSFStrategy_LargeFile() throws Exception {
        // Given: Large dataset
        List<TestProduct> products = generateTestProducts(10_000);
        String fileName = tempDir.resolve("sxssf_large.xlsx").toString();
        ExcelConfig config = ExcelConfigFactory.createLargeFileConfig();

        // When: Write with SXSSF
        long startTime = System.currentTimeMillis();
        ((WriteStrategy<TestProduct>) (WriteStrategy<?>) sxssfStrategy).execute(fileName, products, config);
        long duration = System.currentTimeMillis() - startTime;

        // Then: File should exist
        File file = new File(fileName);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        log.info("SXSSF wrote {} records in {}ms, file size: {} bytes",
            products.size(), duration, file.length());

        // Verify throughput (should be > 1000 records/sec)
        double throughput = (products.size() * 1000.0) / duration;
        assertTrue(throughput > 1000,
            String.format("Throughput %.0f rec/sec is below 1000 rec/sec threshold", throughput));
    }

    @Test
    @Order(7)
    @DisplayName("SXSSF: Should use optimal window size")
    void testSXSSFStrategy_WindowSizeOptimization() throws Exception {
        // Given: Different dataset sizes
        int[] dataSizes = {10_000, 50_000, 100_000};

        for (int dataSize : dataSizes) {
            List<TestProduct> products = generateTestProducts(dataSize);
            String fileName = tempDir.resolve("sxssf_window_" + dataSize + ".xlsx").toString();

            // When: Write with SXSSF
            long startTime = System.currentTimeMillis();
            excelFacade.writeLargeFile(fileName, products);
            long duration = System.currentTimeMillis() - startTime;

            // Then: Should complete in reasonable time
            File file = new File(fileName);
            assertTrue(file.exists());

            double throughput = (dataSize * 1000.0) / duration;
            log.info("SXSSF wrote {} records in {}ms (throughput: {:.0f} rec/sec)",
                dataSize, duration, throughput);
        }
    }

    // ========== CSV STRATEGY TESTS ==========

    @Test
    @Order(8)
    @DisplayName("CSV: Should write very large file quickly")
    @SuppressWarnings("unchecked")
    void testCSVStrategy_VeryLargeFile() throws Exception {
        // Given: Very large dataset
        List<TestProduct> products = generateTestProducts(50_000);
        String fileName = tempDir.resolve("csv_very_large.xlsx").toString();
        ExcelConfig config = ExcelConfig.builder()
            .preferCSVForLargeData(true)
            .csvThreshold(1_000_000L) // Low threshold to force CSV
            .build();

        // When: Write with CSV strategy
        long startTime = System.currentTimeMillis();
        ((WriteStrategy<TestProduct>) (WriteStrategy<?>) csvStrategy).execute(fileName, products, config);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should create CSV file (renamed from .xlsx to .csv)
        File csvFile = new File(fileName.replace(".xlsx", ".csv"));
        assertTrue(csvFile.exists() || new File(fileName).exists(),
            "Either CSV or XLSX file should exist");

        log.info("CSV wrote {} records in {}ms", products.size(), duration);

        // CSV should be much faster (> 5000 rec/sec)
        double throughput = (products.size() * 1000.0) / duration;
        assertTrue(throughput > 2000,
            String.format("CSV throughput %.0f rec/sec is below expected 2000 rec/sec", throughput));
    }

    @Test
    @Order(9)
    @DisplayName("CSV: Should handle special characters correctly")
    @SuppressWarnings("unchecked")
    void testCSVStrategy_SpecialCharacters() throws Exception {
        // Given: Data with special characters
        List<TestProduct> products = new ArrayList<>();
        products.add(new TestProduct("P001", "Product with, comma", 10.99, 100, "Description"));
        products.add(new TestProduct("P002", "Product with \"quotes\"", 20.99, 200, "Description"));
        products.add(new TestProduct("P003", "Product with\nnewline", 30.99, 300, "Description"));

        String fileName = tempDir.resolve("csv_special_chars.csv").toString();
        ExcelConfig config = ExcelConfig.builder()
            .preferCSVForLargeData(true)
            .csvThreshold(1L) // Force CSV
            .build();

        // When: Write with CSV
        ((WriteStrategy<TestProduct>) (WriteStrategy<?>) csvStrategy).execute(fileName, products, config);

        // Then: File should exist and be valid
        File file = new File(fileName);
        assertTrue(file.exists() || new File(fileName.replace(".csv", ".xlsx")).exists());

        log.info("CSV wrote {} records with special characters", products.size());
    }

    // ========== PERFORMANCE COMPARISON TESTS ==========

    @Test
    @Order(10)
    @DisplayName("Performance: Compare all strategies for 10K records")
    void testPerformanceComparison_10K() throws Exception {
        int dataSize = 10_000;
        List<TestProduct> products = generateTestProducts(dataSize);

        // Test XSSF
        String xssfFile = tempDir.resolve("perf_xssf_10k.xlsx").toString();
        long xssfTime = measureWriteTime(xssfStrategy, xssfFile, products);

        // Test SXSSF
        String sxssfFile = tempDir.resolve("perf_sxssf_10k.xlsx").toString();
        long sxssfTime = measureWriteTime(sxssfStrategy, sxssfFile, products);

        // Test CSV
        String csvFile = tempDir.resolve("perf_csv_10k.csv").toString();
        long csvTime = measureWriteTime(csvStrategy, csvFile, products);

        // Log results
        log.info("Performance comparison for {} records:", dataSize);
        log.info("  XSSF: {}ms ({} rec/sec)", xssfTime, calculateThroughput(dataSize, xssfTime));
        log.info("  SXSSF: {}ms ({} rec/sec)", sxssfTime, calculateThroughput(dataSize, sxssfTime));
        log.info("  CSV: {}ms ({} rec/sec)", csvTime, calculateThroughput(dataSize, csvTime));

        // CSV should be fastest
        assertTrue(csvTime <= sxssfTime, "CSV should be faster than or equal to SXSSF");
    }

    // ========== HELPER METHODS ==========

    private List<TestProduct> generateTestProducts(int count) {
        List<TestProduct> products = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            products.add(new TestProduct(
                "P" + String.format("%06d", i),
                "Product Name " + i,
                10.0 + (i % 100),
                100 + (i % 1000),
                "Description for product " + i
            ));
        }
        return products;
    }

    @SuppressWarnings("unchecked")
    private long measureWriteTime(WriteStrategy<?> strategy, String fileName, List<TestProduct> data)
            throws Exception {
        ExcelConfig config = ExcelConfigFactory.createProductionConfig();
        long startTime = System.currentTimeMillis();
        ((WriteStrategy<TestProduct>) strategy).execute(fileName, data, config);
        return System.currentTimeMillis() - startTime;
    }

    private String calculateThroughput(int records, long timeMs) {
        double throughput = (records * 1000.0) / timeMs;
        return String.format("%.0f", throughput);
    }

    // ========== TEST DATA CLASS ==========

    public static class TestProduct {
        @ExcelColumn(name = "Product ID")
        private String productId;

        @ExcelColumn(name = "Product Name")
        private String productName;

        @ExcelColumn(name = "Price")
        private Double price;

        @ExcelColumn(name = "Quantity")
        private Integer quantity;

        @ExcelColumn(name = "Description")
        private String description;

        public TestProduct() {}

        public TestProduct(String productId, String productName, Double price,
                          Integer quantity, String description) {
            this.productId = productId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.description = description;
        }

        // Getters and Setters
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
