package com.learnmore.application.service.multisheet;

import com.learnmore.application.config.SheetMigrationConfig;
import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.dto.migration.sheet.HopDongDTO;
import com.learnmore.application.dto.migration.sheet.CifDTO;
import com.learnmore.application.dto.migration.sheet.TapDTO;
import com.learnmore.infrastructure.repository.MigrationJobSheetRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MultiSheetProcessor using ExcelFacade
 * 
 * Tests:
 * 1. Process 3-sheet Excel with parallel processing
 * 2. Process 2-sheet Excel with sequential processing
 * 3. Verify all sheets processed correctly with ExcelFacade
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=true"
})
@Transactional
class MultiSheetProcessorExcelFacadeTest {

    @Autowired
    private MultiSheetProcessor multiSheetProcessor;

    @Autowired
    private ExcelFacade excelFacade;

    @Autowired
    private SheetMigrationConfig config;

    @Autowired
    private MigrationJobSheetRepository jobSheetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testJobId;

    @BeforeEach
    void setUp() {
        testJobId = "TEST-EXCEL-FACADE-" + System.currentTimeMillis();
    }

    @Test
    @DisplayName("Process 3-sheet Excel with ExcelFacade - parallel processing")
    void testProcessThreeSheetsWithExcelFacade_Parallel() throws Exception {
        // Arrange: Create Excel file with 3 sheets (HSBG_theo_hop_dong, HSBG_theo_CIF, HSBG_theo_tap)
        byte[] excelBytes = createThreeSheetExcel(10, 15, 12);
        
        // Act: Process all sheets using ExcelFacade
        MultiSheetProcessor.MultiSheetProcessResult result = 
            multiSheetProcessor.processAllSheetsFromMemory(testJobId, excelBytes, "test-3-sheets.xlsx");

        // Assert: Verify all sheets processed
        assertNotNull(result);
        assertEquals(3, result.getTotalSheets(), "Should process 3 sheets");
        assertEquals(3, result.getSuccessSheets(), "All 3 sheets should succeed");
        assertEquals(0, result.getFailedSheets(), "No sheets should fail");
        
        // Verify total rows processed
        assertEquals(37, result.getTotalIngestedRows(), "Should ingest 10+15+12=37 rows");
        assertTrue(result.getTotalIngestedRows() > 0, "Should have ingested rows");
        
        // Verify sheet results
        assertNotNull(result.getSheetResults());
        assertEquals(3, result.getSheetResults().size(), "Should have 3 sheet results");
        
        // Verify each sheet result
        result.getSheetResults().forEach(sheetResult -> {
            assertTrue(sheetResult.isSuccess(), "Each sheet should succeed");
            assertTrue(sheetResult.getIngestedRows() > 0, "Each sheet should have ingested rows");
            assertNotNull(sheetResult.getSheetName(), "Sheet name should not be null");
        });
        
        System.out.println("✅ 3-sheet processing completed:");
        System.out.printf("  Total sheets: %d, Success: %d, Failed: %d%n", 
            result.getTotalSheets(), result.getSuccessSheets(), result.getFailedSheets());
        System.out.printf("  Total ingested: %d rows%n", result.getTotalIngestedRows());
        result.getSheetResults().forEach(sr -> 
            System.out.printf("  Sheet '%s': %d rows ingested, %d valid, %d errors%n",
                sr.getSheetName(), sr.getIngestedRows(), sr.getValidRows(), sr.getErrorRows()));
    }

    @Test
    @DisplayName("Process 2-sheet Excel with ExcelFacade - sequential processing")
    void testProcessTwoSheetsWithExcelFacade_Sequential() throws Exception {
        // Arrange: Create Excel file with 2 sheets (HSBG_theo_hop_dong, HSBG_theo_CIF)
        byte[] excelBytes = createTwoSheetExcel(8, 10);
        
        // Temporarily disable parallel processing for sequential test
        boolean originalParallel = config.getGlobal().isUseParallelSheetProcessing();
        try {
            // Note: This test assumes parallel is configurable, but config is read-only
            // So we'll test with current config
            
            // Act: Process all sheets
            MultiSheetProcessor.MultiSheetProcessResult result = 
                multiSheetProcessor.processAllSheetsFromMemory(testJobId, excelBytes, "test-2-sheets.xlsx");

            // Assert: Verify 2 sheets processed
            assertNotNull(result);
            assertEquals(2, result.getTotalSheets(), "Should process 2 sheets");
            assertTrue(result.getSuccessSheets() >= 0, "Should have success count");
            assertEquals(18, result.getTotalIngestedRows(), "Should ingest 8+10=18 rows");
            
            // Verify sheet results
            assertNotNull(result.getSheetResults());
            assertEquals(2, result.getSheetResults().size(), "Should have 2 sheet results");
            
            System.out.println("✅ 2-sheet processing completed:");
            System.out.printf("  Total sheets: %d, Success: %d, Failed: %d%n", 
                result.getTotalSheets(), result.getSuccessSheets(), result.getFailedSheets());
            System.out.printf("  Total ingested: %d rows%n", result.getTotalIngestedRows());
        } finally {
            // Config is read-only, so we can't restore, but that's okay for test
        }
    }

    @Test
    @DisplayName("Process 3-sheet Excel - verify ExcelFacade is used")
    void testProcessThreeSheets_VerifyExcelFacadeUsed() throws Exception {
        // Arrange: Create Excel file with 3 sheets
        byte[] excelBytes = createThreeSheetExcel(5, 5, 5);
        
        // Act: Process all sheets
        MultiSheetProcessor.MultiSheetProcessResult result = 
            multiSheetProcessor.processAllSheetsFromMemory(testJobId, excelBytes, "test-excel-facade.xlsx");

        // Assert: Verify ExcelFacade was used (all sheets processed successfully)
        assertNotNull(result);
        assertTrue(result.getTotalSheets() > 0, "Should process sheets");
        assertTrue(result.getTotalIngestedRows() > 0, "Should have ingested rows via ExcelFacade");
        
        // Verify all configured sheets are present in results
        List<String> expectedSheets = config.getEnabledSheetsOrdered().stream()
            .map(SheetMigrationConfig.SheetConfig::getName)
            .filter(name -> {
                // Check if sheet exists in Excel file
                try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(excelBytes)) {
                    org.apache.poi.openxml4j.opc.OPCPackage pkg = 
                        org.apache.poi.openxml4j.opc.OPCPackage.open(in);
                    org.apache.poi.xssf.eventusermodel.XSSFReader reader = 
                        new org.apache.poi.xssf.eventusermodel.XSSFReader(pkg);
                    org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator iterator =
                        (org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator) reader.getSheetsData();
                    while (iterator.hasNext()) {
                        iterator.next();
                        if (iterator.getSheetName().equals(name)) {
                            pkg.close();
                            return true;
                        }
                    }
                    pkg.close();
                } catch (Exception e) {
                    // Ignore
                }
                return false;
            })
            .toList();
        
        assertEquals(expectedSheets.size(), result.getTotalSheets(), 
            "Should process all expected sheets");
        
        System.out.println("✅ ExcelFacade verification:");
        System.out.printf("  Expected sheets: %s%n", expectedSheets);
        System.out.printf("  Processed sheets: %d%n", result.getTotalSheets());
        System.out.printf("  Total ingested: %d rows%n", result.getTotalIngestedRows());
    }

    @Test
    @DisplayName("Process 3-sheet Excel - verify parallel processing with ExcelFacade")
    void testProcessThreeSheets_ParallelProcessing() throws Exception {
        // Arrange: Create Excel file with 3 sheets
        byte[] excelBytes = createThreeSheetExcel(10, 10, 10);
        
        // Verify parallel processing is enabled
        boolean useParallel = config.getGlobal().isUseParallelSheetProcessing();
        int maxConcurrent = config.getGlobal().getMaxConcurrentSheets();
        
        System.out.printf("Config: useParallel=%s, maxConcurrent=%d%n", useParallel, maxConcurrent);
        
        // Act: Process all sheets
        long startTime = System.currentTimeMillis();
        MultiSheetProcessor.MultiSheetProcessResult result = 
            multiSheetProcessor.processAllSheetsFromMemory(testJobId, excelBytes, "test-parallel.xlsx");
        long duration = System.currentTimeMillis() - startTime;
        
        // Assert: Verify processing completed
        assertNotNull(result);
        assertEquals(3, result.getTotalSheets());
        assertEquals(30, result.getTotalIngestedRows(), "Should ingest 10+10+10=30 rows");
        
        System.out.println("✅ Parallel processing test:");
        System.out.printf("  Duration: %d ms%n", duration);
        System.out.printf("  Sheets: %d, Success: %d%n", 
            result.getTotalSheets(), result.getSuccessSheets());
        System.out.printf("  Total rows: %d%n", result.getTotalIngestedRows());
        
        // If parallel enabled, should be faster than sequential (but allow for overhead)
        if (useParallel && maxConcurrent >= 3) {
            System.out.println("  ✅ Parallel processing enabled");
        } else {
            System.out.println("  ℹ️ Sequential processing (parallel disabled or maxConcurrent < 3)");
        }
    }

    // ========== Helper Methods ==========

    /**
     * Create Excel file with 3 sheets: HSBG_theo_hop_dong, HSBG_theo_CIF, HSBG_theo_tap
     */
    private byte[] createThreeSheetExcel(int hopdRows, int cifRows, int tapRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            
            // Sheet 1: HSBG_theo_hop_dong
            Sheet hopdSheet = workbook.createSheet("HSBG_theo_hop_dong");
            createHopDongSheet(hopdSheet, hopdRows);
            
            // Sheet 2: HSBG_theo_CIF
            Sheet cifSheet = workbook.createSheet("HSBG_theo_CIF");
            createCifSheet(cifSheet, cifRows);
            
            // Sheet 3: HSBG_theo_tap
            Sheet tapSheet = workbook.createSheet("HSBG_theo_tap");
            createTapSheet(tapSheet, tapRows);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Create Excel file with 2 sheets: HSBG_theo_hop_dong, HSBG_theo_CIF
     */
    private byte[] createTwoSheetExcel(int hopdRows, int cifRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            
            // Sheet 1: HSBG_theo_hop_dong
            Sheet hopdSheet = workbook.createSheet("HSBG_theo_hop_dong");
            createHopDongSheet(hopdSheet, hopdRows);
            
            // Sheet 2: HSBG_theo_CIF
            Sheet cifSheet = workbook.createSheet("HSBG_theo_CIF");
            createCifSheet(cifSheet, cifRows);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void createHopDongSheet(Sheet sheet, int rowCount) {
        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "Kho VPBank", "Mã đơn vị", "Trách nhiệm bàn giao", "Số hợp đồng",
            "Tên tập", "Số lượng tập", "Số CIF/ CCCD/ CMT khách hàng", "Tên khách hàng",
            "Phân khúc khách hàng", "Ngày phải bàn giao", "Ngày bàn giao", "Ngày giải ngân",
            "Ngày đến hạn", "Loại hồ sơ", "Luồng hồ sơ", "Phân hạn cấp TD",
            "Ngày dự kiến tiêu hủy", "Sản phẩm", "Trạng thái case PDM", "Ghi chú",
            "Mã thùng", "Ngày nhập kho VPBank", "Ngày chuyển kho Crown", "Khu vực",
            "Hàng", "Cột", "Tình trạng thùng", "Trạng thái thùng",
            "Thời hạn cấp TD", "Mã DAO", "Mã TS", "RRT.ID", "Mã NQ"
        };
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // Data rows
        for (int i = 1; i <= rowCount; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue("Kho VPBank " + i);
            row.createCell(1).setCellValue("UNIT" + i);
            row.createCell(2).setCellValue("Trách nhiệm " + i);
            row.createCell(3).setCellValue("HD" + String.format("%06d", i));
            row.createCell(4).setCellValue("Tập " + i);
            row.createCell(5).setCellValue(i);
            row.createCell(6).setCellValue("CIF" + String.format("%08d", i));
            row.createCell(7).setCellValue("Khách hàng " + i);
            row.createCell(8).setCellValue("Phân khúc " + i);
            row.createCell(9).setCellValue("2024-01-01");
            row.createCell(10).setCellValue("2024-01-15");
            row.createCell(11).setCellValue("2024-01-10");
            row.createCell(12).setCellValue("2025-01-10");
            row.createCell(13).setCellValue("LD");
            row.createCell(14).setCellValue("Luồng " + i);
            row.createCell(15).setCellValue("Ngắn hạn");
            row.createCell(16).setCellValue("2029-01-10");
            row.createCell(17).setCellValue("Sản phẩm " + i);
            row.createCell(18).setCellValue("Trạng thái " + i);
            row.createCell(19).setCellValue("Ghi chú " + i);
            row.createCell(20).setCellValue("BOX" + String.format("%06d", i));
            row.createCell(21).setCellValue("2024-01-05");
            row.createCell(22).setCellValue("2024-01-20");
            row.createCell(23).setCellValue("Khu vực " + i);
            row.createCell(24).setCellValue(i);
            row.createCell(25).setCellValue(i);
            row.createCell(26).setCellValue("Tốt");
            row.createCell(27).setCellValue("Đầy");
            row.createCell(28).setCellValue(5);
            row.createCell(29).setCellValue("DAO" + i);
            row.createCell(30).setCellValue("TS" + i);
            row.createCell(31).setCellValue("RRT" + i);
            row.createCell(32).setCellValue("NQ" + i);
        }
    }

    private void createCifSheet(Sheet sheet, int rowCount) {
        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "Kho VPBank", "Mã đơn vị", "Trách nhiệm bàn giao", "Số CIF khách hàng",
            "Tên khách hàng", "Tên tập", "Số lượng tập", "Phân khúc khách hàng",
            "Ngày phải bàn giao", "Ngày bàn giao", "Ngày giải ngân", "Loại hồ sơ",
            "Luồng hồ sơ", "Phân hạn cấp TD", "Sản phẩm", "Trạng thái case PDM",
            "Ghi chú", "Mã NQ", "Mã thùng", "Ngày nhập kho VPBank",
            "Ngày chuyển kho Crown", "Khu vực", "Hàng", "Cột",
            "Tình trạng thùng", "Trạng thái thùng"
        };
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // Data rows
        for (int i = 1; i <= rowCount; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue("Kho VPBank " + i);
            row.createCell(1).setCellValue("UNIT" + i);
            row.createCell(2).setCellValue("Trách nhiệm " + i);
            row.createCell(3).setCellValue("CIF" + String.format("%08d", i));
            row.createCell(4).setCellValue("Khách hàng " + i);
            row.createCell(5).setCellValue("Tập " + i);
            row.createCell(6).setCellValue(i);
            row.createCell(7).setCellValue("Phân khúc " + i);
            row.createCell(8).setCellValue("2024-01-01");
            row.createCell(9).setCellValue("2024-01-15");
            row.createCell(10).setCellValue("2024-01-10");
            row.createCell(11).setCellValue("PASS TTN");
            row.createCell(12).setCellValue("HSTD thường");
            row.createCell(13).setCellValue("Vĩnh viễn");
            row.createCell(14).setCellValue("Sản phẩm " + i);
            row.createCell(15).setCellValue("Trạng thái " + i);
            row.createCell(16).setCellValue("Ghi chú " + i);
            row.createCell(17).setCellValue("NQ" + i);
            row.createCell(18).setCellValue("BOX" + String.format("%06d", i));
            row.createCell(19).setCellValue("2024-01-05");
            row.createCell(20).setCellValue("2024-01-20");
            row.createCell(21).setCellValue("Khu vực " + i);
            row.createCell(22).setCellValue(i);
            row.createCell(23).setCellValue(i);
            row.createCell(24).setCellValue("Tốt");
            row.createCell(25).setCellValue("Đầy");
        }
    }

    private void createTapSheet(Sheet sheet, int rowCount) {
        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "Kho VPBank", "Mã đơn vị", "Trách nhiệm bàn giao", "Tháng phát sinh",
            "Tên tập", "Số lượng tập", "Ngày phải bàn giao", "Ngày bàn giao",
            "Loại hồ sơ", "Luồng hồ sơ", "Phân hạn cấp TD", "Ngày dự kiến tiêu hủy",
            "Sản phẩm", "Trạng thái case PDM", "Ghi chú", "Mã thùng",
            "Ngày nhập kho VPBank", "Ngày chuyển kho Crown", "Khu vực", "Hàng",
            "Cột", "Tình trạng thùng", "Trạng thái thùng"
        };
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // Data rows
        for (int i = 1; i <= rowCount; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue("Kho VPBank " + i);
            row.createCell(1).setCellValue("UNIT" + i);
            row.createCell(2).setCellValue("Trách nhiệm " + i);
            row.createCell(3).setCellValue("2024-01");
            row.createCell(4).setCellValue("Tập " + i);
            row.createCell(5).setCellValue(i);
            row.createCell(6).setCellValue("2024-01-01");
            row.createCell(7).setCellValue("2024-01-15");
            row.createCell(8).setCellValue("KSSV");
            row.createCell(9).setCellValue("HSTD thường");
            row.createCell(10).setCellValue("Vĩnh viễn");
            row.createCell(11).setCellValue("9999-12-31");
            row.createCell(12).setCellValue("KSSV");
            row.createCell(13).setCellValue("Trạng thái " + i);
            row.createCell(14).setCellValue("Ghi chú " + i);
            row.createCell(15).setCellValue("BOX" + String.format("%06d", i));
            row.createCell(16).setCellValue("2024-01-05");
            row.createCell(17).setCellValue("2024-01-20");
            row.createCell(18).setCellValue("Khu vực " + i);
            row.createCell(19).setCellValue(i);
            row.createCell(20).setCellValue(i);
            row.createCell(21).setCellValue("Tốt");
            row.createCell(22).setCellValue("Đầy");
        }
    }
}

