package com.learnmore.application.utils;

import com.learnmore.application.utils.config.ExcelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Test cases for multi-sheet Excel processing functionality
 */
public class MultiSheetExcelTest {
    
    private ExcelConfig testConfig;
    
    @BeforeEach
    void setUp() {
        testConfig = ExcelConfig.builder()
                .batchSize(500)
                .memoryThreshold(100)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .strictValidation(false)
                .failOnFirstError(false)
                .build();
    }
    
    /**
     * Test multi-sheet processing with different POJO types
     */
    @Test
    @DisplayName("Test processing multiple sheets with different POJO types")
    void testProcessMultiSheetExcel() throws Exception {
        
        // Define sheet-class mapping
        Map<String, Class<?>> sheetClassMap = new HashMap<>();
        sheetClassMap.put("Users", UserData.class);
        sheetClassMap.put("Roles", RoleData.class);
        sheetClassMap.put("Departments", DepartmentData.class);
        
        // Create test Excel file (in real scenario, this would be provided)
        try (InputStream inputStream = createTestMultiSheetExcel()) {
            
            // Process multi-sheet Excel
            Map<String, ExcelUtil.MultiSheetResult> results = 
                ExcelUtil.processMultiSheetExcel(inputStream, sheetClassMap, testConfig);
            
            // Verify results
            assertNotNull(results);
            assertTrue(results.containsKey("Users"));
            assertTrue(results.containsKey("Roles"));
            assertTrue(results.containsKey("Departments"));
            
            // Check Users sheet
            ExcelUtil.MultiSheetResult userResult = results.get("Users");
            assertNotNull(userResult);
            assertTrue(userResult.isSuccessful());
            assertFalse(userResult.getData().isEmpty());
            
            // Verify data types
            List<?> userData = userResult.getData();
            assertTrue(userData.get(0) instanceof UserData);
            
            System.out.println("Multi-sheet processing completed successfully:");
            results.forEach((sheetName, result) -> {
                System.out.printf("Sheet '%s': %d records, %d errors%n", 
                    sheetName, result.getProcessedRecords(), result.getErrors().size());
            });
        }
    }
    
    /**
     * Test streaming multi-sheet processing
     */
    @Test
    @DisplayName("Test streaming processing for multiple sheets")
    void testProcessMultiSheetExcelStreaming() throws Exception {
        
        // Define sheet processors
        Map<String, ExcelUtil.SheetProcessorConfig> sheetProcessors = new HashMap<>();
        
        // User data processor
        List<UserData> processedUsers = new ArrayList<>();
        Consumer<List<UserData>> userProcessor = batch -> {
            processedUsers.addAll(batch);
            System.out.println("Processed " + batch.size() + " users");
        };
        sheetProcessors.put("Users", new ExcelUtil.SheetProcessorConfig(UserData.class, userProcessor));
        
        // Role data processor
        List<RoleData> processedRoles = new ArrayList<>();
        Consumer<List<RoleData>> roleProcessor = batch -> {
            processedRoles.addAll(batch);
            System.out.println("Processed " + batch.size() + " roles");
        };
        sheetProcessors.put("Roles", new ExcelUtil.SheetProcessorConfig(RoleData.class, roleProcessor));
        
        // Department data processor
        List<DepartmentData> processedDepartments = new ArrayList<>();
        Consumer<List<DepartmentData>> deptProcessor = batch -> {
            processedDepartments.addAll(batch);
            System.out.println("Processed " + batch.size() + " departments");
        };
        sheetProcessors.put("Departments", new ExcelUtil.SheetProcessorConfig(DepartmentData.class, deptProcessor));
        
        // Process with streaming
        try (InputStream inputStream = createTestMultiSheetExcel()) {
            ExcelUtil.processMultiSheetExcelStreaming(inputStream, sheetProcessors, testConfig);
        }
        
        // Verify results
        assertFalse(processedUsers.isEmpty());
        assertFalse(processedRoles.isEmpty());
        assertFalse(processedDepartments.isEmpty());
        
        System.out.println("Streaming multi-sheet processing completed:");
        System.out.println("Total users processed: " + processedUsers.size());
        System.out.println("Total roles processed: " + processedRoles.size());
        System.out.println("Total departments processed: " + processedDepartments.size());
    }
    
    /**
     * Test error handling in multi-sheet processing
     */
    @Test
    @DisplayName("Test error handling with missing sheets")
    void testMultiSheetErrorHandling() throws Exception {
        
        // Define mapping with non-existent sheet
        Map<String, Class<?>> sheetClassMap = new HashMap<>();
        sheetClassMap.put("Users", UserData.class);
        sheetClassMap.put("NonExistentSheet", RoleData.class);
        
        try (InputStream inputStream = createTestMultiSheetExcel()) {
            Map<String, ExcelUtil.MultiSheetResult> results = 
                ExcelUtil.processMultiSheetExcel(inputStream, sheetClassMap, testConfig);
            
            // Should have results for both sheets
            assertEquals(2, results.size());
            
            // Users sheet should be successful
            assertTrue(results.get("Users").isSuccessful());
            
            // Non-existent sheet should have error
            ExcelUtil.MultiSheetResult nonExistentResult = results.get("NonExistentSheet");
            assertFalse(nonExistentResult.isSuccessful());
            assertEquals("Sheet not found", nonExistentResult.getErrorMessage());
        }
    }
    
    /**
     * Create test Excel file with multiple sheets
     */
    private InputStream createTestMultiSheetExcel() throws IOException {
        // This would create a test Excel file with multiple sheets
        // For demo purposes, returning empty stream
        // In real implementation, you would use Apache POI to create test data
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Create workbook with multiple sheets containing test data
        // ... POI code to create test data ...
        
        return new ByteArrayInputStream(baos.toByteArray());
    }
    
    // Test POJO classes
    public static class UserData {
        @ExcelColumn(name = "User ID")
        private String userId;
        
        @ExcelColumn(name = "Name")
        private String name;
        
        @ExcelColumn(name = "Email")
        private String email;
        
        @ExcelColumn(name = "Department")
        private String department;
        
        // Constructors, getters, setters
        public UserData() {}
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
    }
    
    public static class RoleData {
        @ExcelColumn(name = "Role ID")
        private String roleId;
        
        @ExcelColumn(name = "Role Name")
        private String roleName;
        
        @ExcelColumn(name = "Description")
        private String description;
        
        @ExcelColumn(name = "Active")
        private Boolean active;
        
        // Constructors, getters, setters
        public RoleData() {}
        
        public String getRoleId() { return roleId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
        
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
    
    public static class DepartmentData {
        @ExcelColumn(name = "Dept ID")
        private String deptId;
        
        @ExcelColumn(name = "Department Name")  
        private String departmentName;
        
        @ExcelColumn(name = "Manager")
        private String manager;
        
        @ExcelColumn(name = "Budget")
        private Double budget;
        
        // Constructors, getters, setters
        public DepartmentData() {}
        
        public String getDeptId() { return deptId; }
        public void setDeptId(String deptId) { this.deptId = deptId; }
        
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        
        public String getManager() { return manager; }
        public void setManager(String manager) { this.manager = manager; }
        
        public Double getBudget() { return budget; }
        public void setBudget(Double budget) { this.budget = budget; }
    }
}