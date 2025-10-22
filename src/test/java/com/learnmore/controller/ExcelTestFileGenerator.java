package com.learnmore.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to generate Excel test files for MigrationController tests
 * Uses the same header format as TestDataGenerator to ensure compatibility
 */
public class ExcelTestFileGenerator {

    private static final String TEST_RESOURCES_PATH = "src/test/resources/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static void main(String[] args) throws IOException {
        System.out.println("Generating Excel test files...");

        // 1. Valid data file with 10 rows
        generateValidDataFile();
        System.out.println("✓ Generated: test-valid-data.xlsx");

        // 2. Valid headers but no data rows
        generateEmptyDataFile();
        System.out.println("✓ Generated: test-empty-data.xlsx");

        // 3. Invalid template
        generateInvalidTemplateFile();
        System.out.println("✓ Generated: test-invalid-template.xlsx");

        System.out.println("\nAll test files generated successfully!");
    }

    /**
     * Generate Excel file with valid data (10 rows)
     */
    private static void generateValidDataFile() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Data");

        // Create header row
        Row headerRow = sheet.createRow(0);
        createHeaders(headerRow, workbook);

        // Create 10 valid data rows
        for (int i = 1; i <= 10; i++) {
            createDataRow(sheet, i);
        }

        // Auto-size columns
        for (int i = 0; i < 23; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write to file
        Path filePath = Paths.get(TEST_RESOURCES_PATH, "test-valid-data.xlsx");
        try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    /**
     * Generate Excel file with valid headers but no data rows
     */
    private static void generateEmptyDataFile() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Data");

        // Create header row only (same as TestDataGenerator)
        Row headerRow = sheet.createRow(0);
        createHeaders(headerRow, workbook);

        // Auto-size columns
        for (int i = 0; i < 23; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write to file
        Path filePath = Paths.get(TEST_RESOURCES_PATH, "test-empty-data.xlsx");
        try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    /**
     * Generate Excel file with invalid template (wrong headers)
     */
    private static void generateInvalidTemplateFile() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        // Create invalid header row
        Row headerRow = sheet.createRow(0);
        String[] invalidHeaders = {
                "Wrong Column 1",
                "Wrong Column 2",
                "Wrong Column 3",
                "Invalid Header",
                "Not Matching"
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < invalidHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(invalidHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add some data rows
        for (int i = 1; i <= 5; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < invalidHeaders.length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue("Data " + i + "-" + j);
            }
        }

        // Write to file
        Path filePath = Paths.get(TEST_RESOURCES_PATH, "test-invalid-template.xlsx");
        try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    /**
     * Create header row with exact same format as TestDataGenerator
     */
    private static void createHeaders(Row row, Workbook workbook) {
        CellStyle headerStyle = createHeaderStyle(workbook);

        // Exact headers from @ExcelColumn annotations in ExcelRowDTO.java
        String[] headers = {
            "Kho VPBank",
            "Mã đơn vị",
            "Trách nhiệm bàn giao",
            "Loại chứng từ",
            "Ngày chứng từ",
            "Tên tập",
            "Số lượng tập",
            "Ngày phải bàn giao",
            "Ngày bàn giao",
            "Tình trạng thất lạc",
            "Tình trạng không hoàn trả",
            "Trạng thái case PDM",
            "Ghi chú case PDM",
            "Mã thùng",
            "Thời hạn lưu trữ",
            "Ngày nhập kho VPBank",
            "Ngày chuyển kho Crown",
            "Khu vực",
            "Hàng",
            "Cột",
            "Tình trạng thùng",
            "Trạng thái thùng",
            "Lưu ý"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Create data row with test data
     */
    private static void createDataRow(Sheet sheet, int rowNum) {
        Row row = sheet.createRow(rowNum);

        LocalDate baseDate = LocalDate.of(2024, 1, 1);
        LocalDate docDate = baseDate.plusDays(rowNum);
        LocalDate deliveryDate = docDate.plusDays(30);
        LocalDate actualDeliveryDate = deliveryDate.plusDays(2);
        LocalDate storageDate = docDate.plusDays(60);

        int col = 0;
        row.createCell(col++).setCellValue("KHO_HN"); // Kho VPBank
        row.createCell(col++).setCellValue("DV" + String.format("%03d", rowNum)); // Mã đơn vị
        row.createCell(col++).setCellValue("Phòng Tài chính " + rowNum); // Trách nhiệm bàn giao
        row.createCell(col++).setCellValue("Hợp đồng"); // Loại chứng từ
        row.createCell(col++).setCellValue(docDate.format(DATE_FORMAT)); // Ngày chứng từ
        row.createCell(col++).setCellValue("TAP_" + String.format("%05d", rowNum)); // Tên tập
        row.createCell(col++).setCellValue(rowNum); // Số lượng tập
        row.createCell(col++).setCellValue(deliveryDate.format(DATE_FORMAT)); // Ngày phải bàn giao
        row.createCell(col++).setCellValue(actualDeliveryDate.format(DATE_FORMAT)); // Ngày bàn giao
        row.createCell(col++).setCellValue("NO"); // Tình trạng thất lạc
        row.createCell(col++).setCellValue("NO"); // Tình trạng không hoàn trả
        row.createCell(col++).setCellValue("COMPLETED"); // Trạng thái Case PDM
        row.createCell(col++).setCellValue("Test note " + rowNum); // Ghi chú Case PDM
        row.createCell(col++).setCellValue("BOX" + String.format("%06d", rowNum)); // Mã thùng
        row.createCell(col++).setCellValue(5); // Thời hạn lưu trữ
        row.createCell(col++).setCellValue(storageDate.format(DATE_FORMAT)); // Ngày nhập kho
        row.createCell(col++).setCellValue(storageDate.plusDays(10).format(DATE_FORMAT)); // Ngày chuyển kho
        row.createCell(col++).setCellValue("KV_1"); // Khu vực
        row.createCell(col++).setCellValue(rowNum); // Hàng
        row.createCell(col++).setCellValue(rowNum); // Cột
        row.createCell(col++).setCellValue("GOOD"); // Tình trạng thùng
        row.createCell(col++).setCellValue("STORED"); // Trạng thái thùng
        row.createCell(col++).setCellValue("Note " + rowNum); // Lưu ý
    }

    /**
     * Create header cell style (bold, blue background) - same as TestDataGenerator
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }
}
