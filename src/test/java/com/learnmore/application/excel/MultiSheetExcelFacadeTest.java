package com.learnmore.application.excel;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for multi-sheet Excel processing with ExcelFacade
 *
 * This test verifies that ExcelFacade can correctly process Excel files
 * with multiple sheets, each containing different data types.
 */
@SpringBootTest
public class MultiSheetExcelFacadeTest {

    @Autowired
    private ExcelFacade excelFacade;

    private ExcelConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = ExcelConfig.builder()
                .batchSize(100)
                .memoryThreshold(100)
                .enableProgressTracking(false)
                .enableMemoryMonitoring(false)
                .strictValidation(false)
                .failOnFirstError(false)
                .build();
    }

    @Test
    @DisplayName("Should process multi-sheet Excel with different data types")
    void testProcessMultiSheetExcel() throws Exception {
        // Given: Excel file with 3 sheets
        InputStream inputStream = createTestMultiSheetExcel();

        // When: Process each sheet with ExcelFacade
        Map<String, Class<?>> sheetClassMap = new HashMap<>();
        sheetClassMap.put("Users", UserData.class);
        sheetClassMap.put("Roles", RoleData.class);
        sheetClassMap.put("Departments", DepartmentData.class);

        Map<String, List<Object>> results = new HashMap<>();
        Map<String, Consumer<List<?>>> sheetProcessors = new HashMap<>();

        sheetProcessors.put("Users", batch -> {
            results.computeIfAbsent("Users", k -> new ArrayList<>()).addAll((List<Object>) batch);
        });
        sheetProcessors.put("Roles", batch -> {
            results.computeIfAbsent("Roles", k -> new ArrayList<>()).addAll((List<Object>) batch);
        });
        sheetProcessors.put("Departments", batch -> {
            results.computeIfAbsent("Departments", k -> new ArrayList<>()).addAll((List<Object>) batch);
        });

        // Process multi-sheet Excel
        Map<String, TrueStreamingSAXProcessor.ProcessingResult> processingResults =
            excelFacade.readMultiSheet(inputStream, sheetClassMap, sheetProcessors, testConfig);

        // Then: Verify results
        assertNotNull(processingResults);
        assertEquals(3, processingResults.size());

        // Verify Users sheet
        assertTrue(processingResults.containsKey("Users"));
        TrueStreamingSAXProcessor.ProcessingResult userResult = processingResults.get("Users");
        assertTrue(userResult.getProcessedRecords() > 0);

        // Verify Roles sheet
        assertTrue(processingResults.containsKey("Roles"));
        TrueStreamingSAXProcessor.ProcessingResult roleResult = processingResults.get("Roles");
        assertTrue(roleResult.getProcessedRecords() > 0);

        // Verify Departments sheet
        assertTrue(processingResults.containsKey("Departments"));
        TrueStreamingSAXProcessor.ProcessingResult deptResult = processingResults.get("Departments");
        assertTrue(deptResult.getProcessedRecords() > 0);

        System.out.println("Multi-sheet processing completed successfully:");
        processingResults.forEach((sheetName, result) -> {
            System.out.printf("Sheet '%s': %d records processed%n",
                sheetName, result.getProcessedRecords());
        });
    }

    @Test
    @DisplayName("Should process single sheet from multi-sheet Excel")
    void testProcessSingleSheetFromMultiSheet() throws Exception {
        // Given: Multi-sheet Excel file
        InputStream inputStream = createTestMultiSheetExcel();

        // When: Process only Users sheet (using standard readExcel)
        // Note: TrueStreamingSAXProcessor processes first sheet by default
        List<UserData> users = new ArrayList<>();
        excelFacade.readExcel(inputStream, UserData.class, users::addAll);

        // Then: Verify Users data
        assertFalse(users.isEmpty());
        assertTrue(users.size() >= 2); // We created 2 users

        UserData firstUser = users.get(0);
        assertNotNull(firstUser.getUserId());
        assertNotNull(firstUser.getName());
        assertNotNull(firstUser.getEmail());

        System.out.println("Single sheet processing completed: " + users.size() + " users");
    }

    /**
     * Create test Excel file with multiple sheets
     */
    private InputStream createTestMultiSheetExcel() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create Users sheet
            Sheet usersSheet = workbook.createSheet("Users");
            Row userHeader = usersSheet.createRow(0);
            userHeader.createCell(0).setCellValue("User ID");
            userHeader.createCell(1).setCellValue("Name");
            userHeader.createCell(2).setCellValue("Email");
            userHeader.createCell(3).setCellValue("Department");

            Row user1 = usersSheet.createRow(1);
            user1.createCell(0).setCellValue("U001");
            user1.createCell(1).setCellValue("John Doe");
            user1.createCell(2).setCellValue("john@example.com");
            user1.createCell(3).setCellValue("IT");

            Row user2 = usersSheet.createRow(2);
            user2.createCell(0).setCellValue("U002");
            user2.createCell(1).setCellValue("Jane Smith");
            user2.createCell(2).setCellValue("jane@example.com");
            user2.createCell(3).setCellValue("HR");

            // Create Roles sheet
            Sheet rolesSheet = workbook.createSheet("Roles");
            Row roleHeader = rolesSheet.createRow(0);
            roleHeader.createCell(0).setCellValue("Role ID");
            roleHeader.createCell(1).setCellValue("Role Name");
            roleHeader.createCell(2).setCellValue("Description");
            roleHeader.createCell(3).setCellValue("Active");

            Row role1 = rolesSheet.createRow(1);
            role1.createCell(0).setCellValue("R001");
            role1.createCell(1).setCellValue("Admin");
            role1.createCell(2).setCellValue("System Administrator");
            role1.createCell(3).setCellValue(true);

            Row role2 = rolesSheet.createRow(2);
            role2.createCell(0).setCellValue("R002");
            role2.createCell(1).setCellValue("User");
            role2.createCell(2).setCellValue("Regular User");
            role2.createCell(3).setCellValue(true);

            // Create Departments sheet
            Sheet deptsSheet = workbook.createSheet("Departments");
            Row deptHeader = deptsSheet.createRow(0);
            deptHeader.createCell(0).setCellValue("Dept ID");
            deptHeader.createCell(1).setCellValue("Department Name");
            deptHeader.createCell(2).setCellValue("Manager");
            deptHeader.createCell(3).setCellValue("Budget");

            Row dept1 = deptsSheet.createRow(1);
            dept1.createCell(0).setCellValue("D001");
            dept1.createCell(1).setCellValue("Information Technology");
            dept1.createCell(2).setCellValue("John Doe");
            dept1.createCell(3).setCellValue(500000.0);

            Row dept2 = deptsSheet.createRow(2);
            dept2.createCell(0).setCellValue("D002");
            dept2.createCell(1).setCellValue("Human Resources");
            dept2.createCell(2).setCellValue("Jane Smith");
            dept2.createCell(3).setCellValue(300000.0);

            // Write to ByteArrayOutputStream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);

            return new ByteArrayInputStream(baos.toByteArray());
        }
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
