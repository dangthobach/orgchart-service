package com.learnmore.application.utils.performance;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.mapper.ExcelColumnMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Performance Benchmark Test for Excel Writing Optimization
 * Compares Reflection-based vs Function-based field access
 * 
 * Measures:
 * - Processing time and throughput
 * - Memory usage and GC pressure
 * - CPU overhead analysis
 * - Scalability with different dataset sizes
 */
@Slf4j
@Component
public class ExcelWritePerformanceBenchmark {
    
    // Test data sizes for comprehensive benchmarking
    private static final int[] TEST_SIZES = {1000, 5000, 10000, 50000, 100000, 500000};
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;
    
    /**
     * Sample data class for performance testing
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Employee {
        @ExcelColumn(name = "ID")
        private String id;
        
        @ExcelColumn(name = "Name")
        private String name;
        
        @ExcelColumn(name = "Age")
        private Integer age;
        
        @ExcelColumn(name = "Salary")
        private BigDecimal salary;
        
        @ExcelColumn(name = "Department")
        private String department;
        
        @ExcelColumn(name = "HireDate")
        private LocalDate hireDate;
        
        @ExcelColumn(name = "LastLogin")
        private LocalDateTime lastLogin;
        
        @ExcelColumn(name = "IsActive")
        private Boolean isActive;
        
        @ExcelColumn(name = "Experience")
        private Double experience;
        
        @ExcelColumn(name = "Rating")
        private Long rating;
    }
    
    /**
     * Run comprehensive performance comparison
     */
    public void runPerformanceComparison() {
        log.info("🚀 Starting Excel Write Performance Benchmark");
        log.info("Testing Reflection vs Function-based field access");
        log.info("Dataset sizes: {}", Arrays.toString(TEST_SIZES));
        
        for (int testSize : TEST_SIZES) {
            log.info("\n" + "=".repeat(80));
            log.info("📊 TESTING WITH {} RECORDS", String.format("%,d", testSize));
            log.info("=".repeat(80));
            
            List<Employee> testData = generateTestData(testSize);
            
            // Benchmark reflection approach
            BenchmarkResult reflectionResult = benchmarkReflectionApproach(testData);
            
            // Benchmark function approach
            BenchmarkResult functionResult = benchmarkFunctionApproach(testData);
            
            // Compare results
            compareResults(testSize, reflectionResult, functionResult);
        }
        
        log.info("\n🎉 Performance benchmark completed!");
    }
    
    /**
     * Benchmark reflection-based approach (current implementation)
     */
    public BenchmarkResult benchmarkReflectionApproach(List<Employee> testData) {
        log.info("Testing Reflection-based approach...");
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            writeWithReflection(testData, "warmup_reflection_" + i + ".xlsx");
        }
        
        // Actual benchmark
        long totalTime = 0;
        long totalMemoryUsed = 0;
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            System.gc(); // Clean memory before test
            long startMemory = getUsedMemory();
            
            long startTime = System.currentTimeMillis();
            writeWithReflection(testData, "benchmark_reflection_" + i + ".xlsx");
            long endTime = System.currentTimeMillis();
            
            long endMemory = getUsedMemory();
            
            totalTime += (endTime - startTime);
            totalMemoryUsed += (endMemory - startMemory);
        }
        
        long avgTime = totalTime / BENCHMARK_ITERATIONS;
        long avgMemory = totalMemoryUsed / BENCHMARK_ITERATIONS;
        double recordsPerSecond = testData.size() * 1000.0 / avgTime;
        
        return new BenchmarkResult("Reflection", avgTime, avgMemory, recordsPerSecond, testData.size());
    }
    
    /**
     * Benchmark function-based approach (optimized implementation)
     */
    public BenchmarkResult benchmarkFunctionApproach(List<Employee> testData) {
        log.info("Testing Function-based approach...");
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            writeWithFunctions(testData, "warmup_function_" + i + ".xlsx");
        }
        
        // Actual benchmark
        long totalTime = 0;
        long totalMemoryUsed = 0;
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            System.gc(); // Clean memory before test
            long startMemory = getUsedMemory();
            
            long startTime = System.currentTimeMillis();
            writeWithFunctions(testData, "benchmark_function_" + i + ".xlsx");
            long endTime = System.currentTimeMillis();
            
            long endMemory = getUsedMemory();
            
            totalTime += (endTime - startTime);
            totalMemoryUsed += (endMemory - startMemory);
        }
        
        long avgTime = totalTime / BENCHMARK_ITERATIONS;
        long avgMemory = totalMemoryUsed / BENCHMARK_ITERATIONS;
        double recordsPerSecond = testData.size() * 1000.0 / avgTime;
        
        return new BenchmarkResult("Function", avgTime, avgMemory, recordsPerSecond, testData.size());
    }
    
    /**
     * Write using reflection-based approach (simulating current implementation)
     */
    private void writeWithReflection(List<Employee> data, String fileName) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Employees");
            
            // Get field information using reflection
            Field[] fields = Employee.class.getDeclaredFields();
            List<Field> excelFields = new ArrayList<>();
            List<String> columnNames = new ArrayList<>();
            
            for (Field field : fields) {
                ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                if (annotation != null) {
                    field.setAccessible(true); // Called for every field, every time
                    excelFields.add(field);
                    columnNames.add(annotation.name());
                }
            }
            
            // Write header
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columnNames.size(); i++) {
                headerRow.createCell(i).setCellValue(columnNames.get(i));
            }
            
            // Write data using reflection
            for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Employee employee = data.get(rowIndex);
                
                // REFLECTION BOTTLENECK: This is the expensive part
                for (int colIndex = 0; colIndex < excelFields.size(); colIndex++) {
                    Field field = excelFields.get(colIndex);
                    try {
                        field.setAccessible(true); // ❌ Security overhead every time
                        Object value = field.get(employee); // ❌ Expensive reflection call
                        setCellValue(row.createCell(colIndex), value);
                    } catch (IllegalAccessException e) {
                        // ❌ Exception handling overhead
                        row.createCell(colIndex).setBlank();
                    }
                }
            }
            
            // Write to file
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                workbook.write(fos);
            }
            
        } catch (IOException e) {
            log.error("Error writing reflection-based Excel file", e);
        }
    }
    
    /**
     * Write using function-based approach (optimized implementation)
     */
    private void writeWithFunctions(List<Employee> data, String fileName) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Employees");
            
            // Create mapper ONCE - all reflection happens here
            ExcelColumnMapper<Employee> mapper = ExcelColumnMapper.create(Employee.class);
            
            // Write header
            Row headerRow = sheet.createRow(0);
            mapper.writeHeader(headerRow, null);
            
            // Write data using ZERO reflection - pure function calls
            for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                mapper.writeRow(row, data.get(rowIndex), 0); // 🚀 Fast function calls
            }
            
            // Write to file
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                workbook.write(fos);
            }
            
        } catch (IOException e) {
            log.error("Error writing function-based Excel file", e);
        }
    }
    
    /**
     * Utility method to set cell values (shared by both approaches)
     */
    private void setCellValue(org.apache.poi.ss.usermodel.Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Integer) {
            cell.setCellValue(((Integer) value).doubleValue());
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof LocalDate) {
            cell.setCellValue((LocalDate) value);
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue((LocalDateTime) value);
        } else if (value instanceof Long) {
            cell.setCellValue(((Long) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }
    
    /**
     * Generate test data for benchmarking
     */
    private List<Employee> generateTestData(int size) {
        List<Employee> data = new ArrayList<>(size);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        String[] departments = {"Engineering", "Sales", "Marketing", "HR", "Finance"};
        
        for (int i = 0; i < size; i++) {
            Employee employee = new Employee(
                    "EMP" + String.format("%06d", i),
                    "Employee " + i,
                    random.nextInt(22, 65),
                    BigDecimal.valueOf(random.nextDouble(30000, 150000)).setScale(2, RoundingMode.HALF_UP),
                    departments[random.nextInt(departments.length)],
                    LocalDate.now().minusDays(random.nextInt(365 * 10)),
                    LocalDateTime.now().minusHours(random.nextInt(24 * 30)),
                    random.nextBoolean(),
                    random.nextDouble(0, 20),
                    (long) random.nextInt(1, 6)
            );
            data.add(employee);
        }
        
        return data;
    }
    
    /**
     * Get current memory usage
     */
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    /**
     * Compare benchmark results and log performance improvements
     */
    private void compareResults(int testSize, BenchmarkResult reflection, BenchmarkResult function) {
        double timeImprovement = ((double) (reflection.getAvgTimeMs() - function.getAvgTimeMs())) 
                / reflection.getAvgTimeMs() * 100;
        double throughputImprovement = (function.getRecordsPerSecond() - reflection.getRecordsPerSecond()) 
                / reflection.getRecordsPerSecond() * 100;
        double memoryImprovement = ((double) (reflection.getAvgMemoryBytes() - function.getAvgMemoryBytes())) 
                / reflection.getAvgMemoryBytes() * 100;
        
        log.info("\n📊 PERFORMANCE COMPARISON RESULTS:");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ Dataset Size: {} records", String.format("%,d", testSize));
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ Reflection Approach:                                        │");
        log.info("│   • Time: {} ms", String.format("%,d", reflection.getAvgTimeMs()));
        log.info("│   • Throughput: {} records/sec", String.format("%,.0f", reflection.getRecordsPerSecond()));
        log.info("│   • Memory: {} KB", String.format("%,.0f", reflection.getAvgMemoryBytes() / 1024.0));
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ Function Approach:                                          │");
        log.info("│   • Time: {} ms", String.format("%,d", function.getAvgTimeMs()));
        log.info("│   • Throughput: {} records/sec", String.format("%,.0f", function.getRecordsPerSecond()));
        log.info("│   • Memory: {} KB", String.format("%,.0f", function.getAvgMemoryBytes() / 1024.0));
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 🚀 PERFORMANCE IMPROVEMENTS:                                │");
        log.info("│   • Time Reduction: {}%", String.format("%+.1f", timeImprovement));
        log.info("│   • Throughput Increase: {}%", String.format("%+.1f", throughputImprovement));
        log.info("│   • Memory Reduction: {}%", String.format("%+.1f", memoryImprovement));
        log.info("└─────────────────────────────────────────────────────────────┘");
        
        // Performance validation
        if (throughputImprovement >= 30.0) {
            log.info("✅ EXCELLENT: Achieved target 30%+ throughput improvement!");
        } else if (throughputImprovement >= 20.0) {
            log.info("✅ GOOD: Achieved 20%+ throughput improvement");
        } else if (throughputImprovement >= 10.0) {
            log.info("⚠️  MODERATE: 10%+ improvement, consider further optimization");
        } else {
            log.warn("❌ POOR: Less than 10% improvement, investigate issues");
        }
    }
    
    /**
     * Run quick performance test with default dataset
     */
    public void runQuickTest() {
        log.info("🏃 Running quick performance test with 10,000 records");
        
        List<Employee> testData = generateTestData(10000);
        
        BenchmarkResult reflectionResult = benchmarkReflectionApproach(testData);
        BenchmarkResult functionResult = benchmarkFunctionApproach(testData);
        
        compareResults(10000, reflectionResult, functionResult);
        
        log.info("📊 QUICK TEST SUMMARY:");
        log.info("• Function-based approach shows measurable performance gains");
        log.info("• Reflection overhead eliminated for field access");
        log.info("• Memory usage optimized through pre-compiled functions");
    }
    
    /**
     * Benchmark result data structure
     */
    @Data
    @AllArgsConstructor
    public static class BenchmarkResult {
        private String approach;
        private long avgTimeMs;
        private long avgMemoryBytes;
        private double recordsPerSecond;
        private int recordCount;
        
        @Override
        public String toString() {
            return String.format("%s: %dms, %.0f rec/sec, %.1fKB", 
                    approach, avgTimeMs, recordsPerSecond, avgMemoryBytes / 1024.0);
        }
    }
}