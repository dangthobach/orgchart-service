package com.learnmore.application.utils;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ExcelUtil
 * Includes unit tests, performance tests, and memory leak tests
 */
class ExcelUtilTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ExcelUtilTest.class);
    
    private ExcelConfig testConfig;
    
    @BeforeEach
    void setUp() {
        testConfig = ExcelConfig.builder()
                .batchSize(1000)
                .memoryThreshold(100)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .build();
    }
    
    @Test
    @DisplayName("Test basic Excel processing with small dataset")
    void testBasicExcelProcessing() throws Exception {
        List<TestEntity> testData = generateTestData(100);
        
        // Write to Excel
        byte[] excelBytes = ExcelUtil.writeToExcelBytes(testData, 0, 0, testConfig);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
        
        // Read from Excel
        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        List<TestEntity> results = ExcelUtil.processExcelToList(inputStream, TestEntity.class, testConfig);
        
        assertNotNull(results);
        assertEquals(testData.size(), results.size());
        
        // Verify data integrity
        for (int i = 0; i < testData.size(); i++) {
            TestEntity original = testData.get(i);
            TestEntity result = results.get(i);
            
            assertEquals(original.getName(), result.getName());
            assertEquals(original.getAge(), result.getAge());
            assertEquals(original.getSalary(), result.getSalary());
        }
    }
    
    @Test
    @DisplayName("Test Excel processing with validation")
    void testExcelProcessingWithValidation() {
        List<TestEntity> testData = generateTestData(50);
        
        // Add some invalid data
        testData.add(new TestEntity(null, 25, new BigDecimal("50000"), true)); // null name
        testData.add(new TestEntity("", -5, new BigDecimal("60000"), false)); // empty name, negative age
        
        ExcelConfig strictConfig = ExcelConfig.builder()
                .batchSize(100)
                .strictValidation(true)
                .requiredFields("name", "age")
                .build();
        
        // This should throw ValidationException due to invalid data
        assertThrows(ValidationException.class, () -> {
            byte[] excelBytes = ExcelUtil.writeToExcelBytes(testData, 0, 0, strictConfig);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
            ExcelUtil.processExcelToList(inputStream, TestEntity.class, strictConfig);
        });
    }
    
    @Test
    @DisplayName("Test duplicate detection")
    void testDuplicateDetection() {
        List<TestEntity> testData = new ArrayList<>();
        testData.add(new TestEntity("John", 30, new BigDecimal("50000"), true));
        testData.add(new TestEntity("Jane", 25, new BigDecimal("55000"), false));
        testData.add(new TestEntity("John", 35, new BigDecimal("60000"), true)); // Duplicate name
        
        ExcelConfig uniqueConfig = ExcelConfig.builder()
                .batchSize(100)
                .strictValidation(true)
                .uniqueFields("name")
                .build();
        
        // This should detect duplicates
        assertThrows(ValidationException.class, () -> {
            byte[] excelBytes = ExcelUtil.writeToExcelBytes(testData, 0, 0, uniqueConfig);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
            ExcelUtil.processExcelToList(inputStream, TestEntity.class, uniqueConfig);
        });
    }
    
    @Test
    @DisplayName("Test performance with large dataset (10K records)")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testLargeDatasetPerformance() throws Exception {
        int recordCount = 10000;
        List<TestEntity> testData = generateTestData(recordCount);
        
        long startTime = System.currentTimeMillis();
        
        // Write to Excel
        byte[] excelBytes = ExcelUtil.writeToExcelBytes(testData, 0, 0, testConfig);
        assertNotNull(excelBytes);
        
        long writeTime = System.currentTimeMillis() - startTime;
        logger.info("Write time for {} records: {} ms", recordCount, writeTime);
        
        // Read from Excel
        startTime = System.currentTimeMillis();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        List<TestEntity> results = ExcelUtil.processExcelToList(inputStream, TestEntity.class, testConfig);
        
        long readTime = System.currentTimeMillis() - startTime;
        logger.info("Read time for {} records: {} ms", recordCount, readTime);
        
        assertEquals(testData.size(), results.size());
        
        // Performance assertions
        assertTrue(writeTime < 10000, "Write time should be less than 10 seconds");
        assertTrue(readTime < 10000, "Read time should be less than 10 seconds");
    }
    
    @Test
    @DisplayName("Test streaming processing with very large dataset (100K records)")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testStreamingProcessing() throws Exception {
        int recordCount = 100000;
        List<TestEntity> testData = generateTestData(recordCount);
        
        ExcelConfig streamingConfig = ExcelConfig.builder()
                .batchSize(5000)
                .memoryThreshold(200)
                .useStreamingParser(true)
                .enableProgressTracking(true)
                .progressReportInterval(10000)
                .build();
        
        long startTime = System.currentTimeMillis();
        
        // Write using streaming
        // Note: In a real scenario, you'd write to a file rather than memory
        byte[] excelBytes = ExcelUtil.writeToExcelBytes(testData, 0, 0, streamingConfig);
        
        long writeTime = System.currentTimeMillis() - startTime;
        logger.info("Streaming write time for {} records: {} ms", recordCount, writeTime);
        
        // Read using streaming
        startTime = System.currentTimeMillis();
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        List<TestEntity> results = ExcelUtil.processExcelToList(inputStream, TestEntity.class, streamingConfig);
        
        long readTime = System.currentTimeMillis() - startTime;
        logger.info("Streaming read time for {} records: {} ms", recordCount, readTime);
        
        assertEquals(testData.size(), results.size());
        
        // Performance assertions for streaming
        assertTrue(writeTime < 60000, "Streaming write time should be less than 60 seconds");
        assertTrue(readTime < 60000, "Streaming read time should be less than 60 seconds");
    }
    
    @Test
    @DisplayName("Test memory usage monitoring")
    void testMemoryMonitoring() throws Exception {
        int recordCount = 50000;
        List<TestEntity> testData = generateTestData(recordCount);
        
        ExcelConfig memoryConfig = ExcelConfig.builder()
                .batchSize(2000)
                .memoryThreshold(100) // Low threshold to trigger monitoring
                .enableMemoryMonitoring(true)
                .build();
        
        // Clear caches before test
        ExcelUtil.clearCaches();
        
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Process data
        byte[] excelBytes = ExcelUtil.writeToExcelBytes(testData, 0, 0, memoryConfig);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        List<TestEntity> results = ExcelUtil.processExcelToList(inputStream, TestEntity.class, memoryConfig);
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        logger.info("Memory used for {} records: {} MB", recordCount, memoryUsed / (1024 * 1024));
        logger.info("Performance statistics:\n{}", ExcelUtil.getPerformanceStatistics());
        
        assertEquals(testData.size(), results.size());
        
        // Memory should not exceed reasonable limits
        assertTrue(memoryUsed < 200 * 1024 * 1024, "Memory usage should be less than 200MB");
    }
    
    @Test
    @DisplayName("Test error handling and recovery")
    void testErrorHandling() {
        // Test with null input
        assertThrows(ExcelProcessException.class, () -> {
            ExcelUtil.writeToExcelBytes(null, 0, 0);
        });
        
        // Test with empty list
        assertThrows(ExcelProcessException.class, () -> {
            ExcelUtil.writeToExcelBytes(new ArrayList<>(), 0, 0);
        });
        
        // Test with invalid stream
        assertThrows(ExcelProcessException.class, () -> {
            ByteArrayInputStream invalidStream = new ByteArrayInputStream("invalid excel data".getBytes());
            ExcelUtil.processExcelToList(invalidStream, TestEntity.class, testConfig);
        });
    }
    
    @Test
    @DisplayName("Test data type conversion")
    void testDataTypeConversion() throws Exception {
        List<TestEntity> testData = new ArrayList<>();
        testData.add(new TestEntity("John", 30, new BigDecimal("50000.50"), true));
        testData.add(new TestEntity("Jane", 25, new BigDecimal("55000.75"), false));
        
        byte[] excelBytes = ExcelUtil.writeToExcelBytes(testData, 0, 0, testConfig);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        List<TestEntity> results = ExcelUtil.processExcelToList(inputStream, TestEntity.class, testConfig);
        
        assertEquals(testData.size(), results.size());
        
        for (int i = 0; i < testData.size(); i++) {
            TestEntity original = testData.get(i);
            TestEntity result = results.get(i);
            
            assertEquals(original.getName(), result.getName());
            assertEquals(original.getAge(), result.getAge());
            assertEquals(0, original.getSalary().compareTo(result.getSalary()));
            assertEquals(original.isActive(), result.isActive());
        }
    }
    
    @Test
    @DisplayName("Test configuration builder")
    void testConfigurationBuilder() {
        ExcelConfig config = ExcelConfig.builder()
                .batchSize(2000)
                .memoryThreshold(300)
                .dateFormat("dd/MM/yyyy")
                .parallelProcessing(false)
                .strictValidation(true)
                .failOnFirstError(true)
                .requiredFields("name", "age")
                .uniqueFields("name")
                .maxErrorsBeforeAbort(500)
                .build();
        
        assertEquals(2000, config.getBatchSize());
        assertEquals(300, config.getMemoryThresholdMB());
        assertEquals("dd/MM/yyyy", config.getDateFormat());
        assertFalse(config.isParallelProcessing());
        assertTrue(config.isStrictValidation());
        assertTrue(config.isFailOnFirstError());
        assertTrue(config.getRequiredFields().contains("name"));
        assertTrue(config.getUniqueFields().contains("name"));
        assertEquals(500, config.getMaxErrorsBeforeAbort());
    }
    
    /**
     * Generate test data for performance testing
     */
    private List<TestEntity> generateTestData(int count) {
        List<TestEntity> data = new ArrayList<>();
        Random random = new Random();
        
        String[] names = {"John", "Jane", "Bob", "Alice", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"};
        
        for (int i = 0; i < count; i++) {
            String name = names[random.nextInt(names.length)] + "_" + i;
            Integer age = 20 + random.nextInt(50);
            BigDecimal salary = new BigDecimal(30000 + random.nextInt(70000));
            Boolean active = random.nextBoolean();
            
            data.add(new TestEntity(name, age, salary, active));
        }
        
        return data;
    }
    
    /**
     * Test entity for Excel processing
     */
    public static class TestEntity {
        
        @ExcelColumn(name = "name")
        private String name;
        
        @ExcelColumn(name = "age")
        private Integer age;
        
        @ExcelColumn(name = "salary")
        private BigDecimal salary;
        
        @ExcelColumn(name = "active")
        private Boolean active;
        
        public TestEntity() {
        }
        
        public TestEntity(String name, Integer age, BigDecimal salary, Boolean active) {
            this.name = name;
            this.age = age;
            this.salary = salary;
            this.active = active;
        }
        
        // Getters and setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
        
        public BigDecimal getSalary() {
            return salary;
        }
        
        public void setSalary(BigDecimal salary) {
            this.salary = salary;
        }
        
        public Boolean isActive() {
            return active;
        }
        
        public void setActive(Boolean active) {
            this.active = active;
        }
        
        @Override
        public String toString() {
            return String.format("TestEntity{name='%s', age=%d, salary=%s, active=%s}", 
                name, age, salary, active);
        }
    }
}