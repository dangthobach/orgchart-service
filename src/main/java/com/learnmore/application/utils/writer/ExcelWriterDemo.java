package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Comprehensive demo of the ExcelWriter system with performance testing
 */
@Slf4j
public class ExcelWriterDemo {
    
    public static void main(String[] args) {
        log.info("🚀 Starting ExcelWriter System Demo");
        
        try {
            // Run all demo scenarios
            runSmallDatasetDemo();
            runMediumDatasetDemo();
            runLargeDatasetDemo();
            runPerformanceComparison();
            
            log.info("✅ All demos completed successfully!");
            
        } catch (Exception e) {
            log.error("❌ Demo failed", e);
        }
    }
    
    /**
     * Demo 1: Small dataset with InMemoryWriter
     */
    private static void runSmallDatasetDemo() throws ExcelProcessException, IOException {
        log.info("\n📝 Demo 1: Small Dataset (1,000 records) - InMemoryWriter");
        
        List<Employee> employees = generateEmployeeData(1000);
        
        try (FileOutputStream fos = new FileOutputStream("demo_small_dataset.xlsx")) {
            ExcelWriter<Employee> writer = ExcelWriterFactory.WriterPresets.smallFile(Employee.class);
            
            long startTime = System.currentTimeMillis();
            WritingResult result = writer.write(employees, fos);
            long endTime = System.currentTimeMillis();
            
            log.info("✅ Small dataset written successfully");
            log.info("Strategy: {}", result.getStrategy());
            log.info("Records written: {}", result.getTotalRowsWritten());
            log.info("Processing time: {}ms", endTime - startTime);
            log.info("Speed: {:.1f} records/sec", result.getRowsPerSecond());
        }
    }
    
    /**
     * Demo 2: Medium dataset with OptimizedStreamingWriter
     */
    private static void runMediumDatasetDemo() throws ExcelProcessException, IOException {
        log.info("\n📊 Demo 2: Medium Dataset (50,000 records) - OptimizedStreamingWriter");
        
        List<Employee> employees = generateEmployeeData(50000);
        
        try (FileOutputStream fos = new FileOutputStream("demo_medium_dataset.xlsx")) {
            ExcelWriter<Employee> writer = ExcelWriterFactory.WriterPresets.fastDataExport(Employee.class);
            
            long startTime = System.currentTimeMillis();
            WritingResult result = writer.write(employees, fos);
            long endTime = System.currentTimeMillis();
            
            log.info("✅ Medium dataset written successfully");
            log.info("Strategy: {}", result.getStrategy());
            log.info("Records written: {}", result.getTotalRowsWritten());
            log.info("Processing time: {}ms", endTime - startTime);
            log.info("Speed: {:.1f} records/sec", result.getRowsPerSecond());
        }
    }
    
    /**
     * Demo 3: Large dataset with high performance settings
     */
    private static void runLargeDatasetDemo() throws ExcelProcessException, IOException {
        log.info("\n🏎️ Demo 3: Large Dataset (100,000 records) - High Performance");
        
        List<Employee> employees = generateEmployeeData(100000);
        
        try (FileOutputStream fos = new FileOutputStream("demo_large_dataset.xlsx")) {
            ExcelWriter<Employee> writer = ExcelWriterFactory.WriterPresets.highPerformance(Employee.class);
            
            long startTime = System.currentTimeMillis();
            WritingResult result = writer.write(employees, fos);
            long endTime = System.currentTimeMillis();
            
            log.info("✅ Large dataset written successfully");
            log.info("Strategy: {}", result.getStrategy());
            log.info("Records written: {}", result.getTotalRowsWritten());
            log.info("Processing time: {}ms", endTime - startTime);
            log.info("Speed: {:.1f} records/sec", result.getRowsPerSecond());
        }
    }
    
    /**
     * Demo 4: Performance comparison between strategies
     */
    private static void runPerformanceComparison() throws ExcelProcessException, IOException {
        log.info("\n🔬 Demo 4: Performance Comparison");
        
        List<Employee> testData = generateEmployeeData(10000);
        
        // Test different strategies
        testStrategy("InMemory", ExcelWriterFactory.WriterPresets.smallFile(Employee.class), testData);
        testStrategy("FormattedReport", ExcelWriterFactory.WriterPresets.formattedReport(Employee.class), testData);
        testStrategy("FastDataExport", ExcelWriterFactory.WriterPresets.fastDataExport(Employee.class), testData);
        testStrategy("LowMemory", ExcelWriterFactory.WriterPresets.lowMemory(Employee.class), testData);
        
        log.info("📈 Performance comparison completed");
    }
    
    private static void testStrategy(String name, ExcelWriter<Employee> writer, List<Employee> data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            long startTime = System.currentTimeMillis();
            WritingResult result = writer.write(data, baos);
            long endTime = System.currentTimeMillis();
            
            log.info("{}: {} records in {}ms ({:.1f} rps)", 
                    name, 
                    result.getTotalRowsWritten(), 
                    endTime - startTime,
                    result.getRowsPerSecond());
                    
        } catch (Exception e) {
            log.error("Failed to test strategy: {}", name, e);
        }
    }
    
    /**
     * Generate test employee data
     */
    private static List<Employee> generateEmployeeData(int count) {
        List<Employee> employees = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for consistent results
        
        String[] departments = {"Engineering", "Sales", "Marketing", "HR", "Finance"};
        String[] titles = {"Manager", "Senior", "Junior", "Lead", "Director"};
        
        for (int i = 1; i <= count; i++) {
            Employee emp = new Employee();
            emp.setId(i);
            emp.setName("Employee " + i);
            emp.setEmail("employee" + i + "@company.com");
            emp.setDepartment(departments[random.nextInt(departments.length)]);
            emp.setTitle(titles[random.nextInt(titles.length)]);
            emp.setSalary((double)(30000 + random.nextInt(70000)));
            emp.setActive(random.nextBoolean());
            emp.setHireDate(LocalDateTime.now().minusDays(random.nextInt(3650))); // Within last 10 years
            
            employees.add(emp);
        }
        
        return employees;
    }
    
    /**
     * Sample Employee class for testing
     */
    @Data
    public static class Employee {
        private Integer id;
        private String name;
        private String email;
        private String department;
        private String title;
        private Double salary;
        private Boolean active;
        private LocalDateTime hireDate;
    }
}