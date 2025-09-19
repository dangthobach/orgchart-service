package com.learnmore.application.utils.demo;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.strategy.ExcelWriteStrategy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo class để minh họa chiến lược Excel write mới
 * Dựa trên ngưỡng thực tế: ≤2M ô → XSSF, >2M ô → SXSSF, >5M ô → CSV
 */
@Slf4j
public class ExcelStrategyDemo {
    
    @Data
    public static class SampleData {
        @com.learnmore.application.utils.ExcelColumn(name = "ID")
        private String id;
        
        @com.learnmore.application.utils.ExcelColumn(name = "Name")
        private String name;
        
        @com.learnmore.application.utils.ExcelColumn(name = "Email")
        private String email;
        
        @com.learnmore.application.utils.ExcelColumn(name = "Department")
        private String department;
        
        @com.learnmore.application.utils.ExcelColumn(name = "Salary")
        private Double salary;
        
        public SampleData(String id, String name, String email, String department, Double salary) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.department = department;
            this.salary = salary;
        }
    }
    
    public static void main(String[] args) {
        try {
            // Test scenarios với các quy mô khác nhau
            demonstrateStrategy("Small Dataset (10K records)", 10_000);
            demonstrateStrategy("Medium Dataset (100K records)", 100_000);
            demonstrateStrategy("Large Dataset (500K records)", 500_000);
            demonstrateStrategy("Very Large Dataset (1M records)", 1_000_000);
            demonstrateStrategy("Huge Dataset (2M records)", 2_000_000);
            
        } catch (Exception e) {
            log.error("Demo failed", e);
        }
    }
    
    private static void demonstrateStrategy(String scenario, int recordCount) {
        log.info("\n=== {} ===", scenario);
        
        // Tạo sample data
        List<SampleData> data = generateSampleData(recordCount);
        int columnCount = 5; // SampleData có 5 cột
        
        // Cấu hình mặc định
        ExcelConfig config = ExcelConfig.builder()
                .cellCountThresholdForSXSSF(2_000_000L)
                .maxCellsForXSSF(1_500_000L)
                .sxssfRowAccessWindowSize(500)
                .preferCSVForLargeData(true)
                .csvThreshold(5_000_000L)
                .allowXLSFormat(false)
                .build();
        
        // Xác định strategy
        ExcelWriteStrategy.WriteMode strategy = ExcelWriteStrategy.determineWriteStrategy(
                recordCount, columnCount, config);
        
        // Hiển thị recommendations
        String recommendations = ExcelWriteStrategy.getOptimizationRecommendation(
                recordCount, columnCount, config);
        
        log.info("Data size: {} records × {} columns = {} cells", 
                recordCount, columnCount, (long) recordCount * columnCount);
        log.info("Selected strategy: {}", strategy);
        log.info("Recommendations:\n{}", recommendations);
        
        // Tính toán window size tối ưu
        if (strategy == ExcelWriteStrategy.WriteMode.SXSSF_STREAMING || 
            strategy == ExcelWriteStrategy.WriteMode.MULTI_SHEET_SPLIT) {
            int windowSize = ExcelWriteStrategy.calculateOptimalWindowSize(recordCount, columnCount, config);
            log.info("Optimal SXSSF window size: {}", windowSize);
        }
        
        // Multi-sheet calculation
        if (strategy == ExcelWriteStrategy.WriteMode.MULTI_SHEET_SPLIT) {
            int sheetCount = ExcelWriteStrategy.calculateOptimalSheetCount(recordCount, config);
            log.info("Recommended sheet count: {} ({} rows per sheet)", 
                    sheetCount, recordCount / sheetCount);
        }
        
        // Thực tế sẽ ghi file (commented out để tránh tạo file lớn)
        /*
        try {
            String fileName = String.format("demo_%s.xlsx", 
                    scenario.toLowerCase().replaceAll("[^a-z0-9]", "_"));
            ExcelUtil.writeToExcel(fileName, data, 0, 0, config);
            log.info("File written successfully: {}", fileName);
        } catch (Exception e) {
            log.error("Failed to write file", e);
        }
        */
        
        log.info(""); // Empty line for readability
    }
    
    private static List<SampleData> generateSampleData(int count) {
        List<SampleData> data = new ArrayList<>();
        
        // Tạo subset nhỏ để demo (tránh OOM trong demo)
        int actualCount = Math.min(count, 1000); // Chỉ tạo tối đa 1000 records cho demo
        
        for (int i = 1; i <= actualCount; i++) {
            data.add(new SampleData(
                    "EMP" + String.format("%06d", i),
                    "Employee " + i,
                    "emp" + i + "@company.com",
                    "Dept" + (i % 10 + 1),
                    50000.0 + (i % 1000) * 100
            ));
        }
        
        return data;
    }
    
    /**
     * Demo validation file format
     */
    public static void demoFileFormatValidation() {
        log.info("\n=== File Format Validation Demo ===");
        
        ExcelConfig config = ExcelConfig.builder()
                .allowXLSFormat(false)
                .maxRowsForXLS(65_535)
                .maxColsForXLS(256)
                .build();
        
        try {
            // Test .xls với dữ liệu lớn
            ExcelWriteStrategy.validateFileFormat("large_data.xls", 100_000, 5, config);
        } catch (IllegalArgumentException e) {
            log.info("XLS validation failed as expected: {}", e.getMessage());
        }
        
        try {
            // Test .xlsx với dữ liệu lớn - should pass
            ExcelWriteStrategy.validateFileFormat("large_data.xlsx", 100_000, 5, config);
            log.info("XLSX validation passed for large dataset");
        } catch (IllegalArgumentException e) {
            log.error("Unexpected validation failure", e);
        }
    }
}