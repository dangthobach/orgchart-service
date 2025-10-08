package com.learnmore.application.service;

import com.learnmore.application.excel.strategy.impl.MultiSheetWriteStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Service for generating fake data and exporting to multi-sheet Excel files
 * 
 * This service provides functionality to:
 * - Generate fake data for User, Role, and Permission entities
 * - Export data to Excel files with multiple sheets
 * - Use WriteStrategy pattern for optimal performance
 * - Support customizable data generation parameters
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FakeDataService {
    
    private final MockDataGenerator mockDataGenerator;
    private final MultiSheetWriteStrategy<Object> multiSheetWriteStrategy;
    
    /**
     * Generate fake data and export to Excel with 3 sheets
     * 
     * @param userCount Number of users to generate (default: 1000)
     * @param roleCount Number of roles to generate (default: 100)
     * @param permissionCount Number of permissions to generate (default: 100)
     * @return Generated Excel file path
     * @throws ExcelProcessException if generation or export fails
     */
    public String generateAndExportFakeData(int userCount, int roleCount, int permissionCount) 
            throws ExcelProcessException {
        
        log.info("🎯 Starting fake data generation and export: {} Users, {} Roles, {} Permissions", 
                userCount, roleCount, permissionCount);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Generate all entities
            Map<String, List<?>> allData = mockDataGenerator.generateAllEntities(
                userCount, roleCount, permissionCount
            );
            
            // Create Excel configuration for multi-sheet
            ExcelConfig config = createMultiSheetConfig();
            
            // Generate file name with timestamp
            String fileName = generateFileName();
            
            // Export to Excel using MultiSheetWriteStrategy
            multiSheetWriteStrategy.executeMultiSheet(fileName, allData, config);
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("✅ Fake data generation and export completed in {}ms", totalTime);
            log.info("📁 Excel file generated: {}", fileName);
            
            return fileName;
            
        } catch (Exception e) {
            log.error("❌ Failed to generate and export fake data", e);
            throw new ExcelProcessException("Failed to generate and export fake data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate fake data with default counts and export to Excel
     * 
     * @return Generated Excel file path
     * @throws ExcelProcessException if generation or export fails
     */
    public String generateAndExportFakeData() throws ExcelProcessException {
        return generateAndExportFakeData(1000, 100, 100);
    }
    
    /**
     * Generate fake data with custom user count and default role/permission counts
     * 
     * @param userCount Number of users to generate
     * @return Generated Excel file path
     * @throws ExcelProcessException if generation or export fails
     */
    public String generateAndExportFakeData(int userCount) throws ExcelProcessException {
        return generateAndExportFakeData(userCount, 100, 100);
    }
    
    /**
     * Create Excel configuration optimized for multi-sheet export
     * 
     * @return ExcelConfig with multi-sheet settings
     */
    private ExcelConfig createMultiSheetConfig() {
        ExcelConfig config = ExcelConfig.builder()
            .sheetNames(Arrays.asList("User", "Role", "Permission"))
            .disableAutoSizing(false)
            .sxssfRowAccessWindowSize(1000)
            .batchSize(1000)
            .build();
        
        log.debug("📋 Created multi-sheet Excel configuration: {} sheets", config.getSheetNames().size());
        
        return config;
    }
    
    /**
     * Generate unique file name with timestamp
     * 
     * @return Generated file name
     */
    private String generateFileName() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("fake_data_%s.xlsx", timestamp);
    }
    
    /**
     * Get generation statistics
     * 
     * @return Map containing generation statistics
     */
    public Map<String, Object> getGenerationStats() {
        return mockDataGenerator.getGenerationStats();
    }
    
    /**
     * Clear generator caches for memory efficiency
     */
    public void clearCaches() {
        mockDataGenerator.clearCaches();
        log.info("🧹 Cleared FakeDataService caches");
    }
    
}
