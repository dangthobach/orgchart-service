package com.learnmore.application.utils;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to generate test Excel files for migration testing
 */
@Slf4j
public class TestDataGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Generate test Excel file with specified number of rows
     *
     * @param outputPath Output file path
     * @param numRows Number of data rows to generate
     * @throws IOException if file creation fails
     */
    public static void generateTestExcelFile(Path outputPath, int numRows) throws IOException {
        log.info("Generating test Excel file: {} with {} rows", outputPath, numRows);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            // Create header row
            createHeaderRow(sheet);

            // Create data rows
            for (int i = 0; i < numRows; i++) {
                createDataRow(sheet, i + 1, i);
            }

            // Auto-size columns for readability
            for (int i = 0; i < getColumnCount(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fos);
            }

            log.info("Successfully generated test file: {}", outputPath);
        }
    }

    /**
     * Create header row with ExcelRowDTO field names
     */
    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);

        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());

        String[] headers = {
            "Kho VPBank",
            "Mã Đơn Vị",
            "Trách Nhiệm Bàn Giao",
            "Loại Chứng Từ",
            "Ngày Chứng Từ",
            "Tên Tập",
            "Số Lượng Tập",
            "Ngày Phải Bàn Giao",
            "Ngày Bàn Giao",
            "Tình Trạng Thất Lạc",
            "Tình Trạng Không Hoàn Trả",
            "Trạng Thái Case PDM",
            "Ghi Chú Case PDM",
            "Mã Thùng",
            "Thời Hạn Lưu Trữ (năm)",
            "Ngày Nhập Kho VPBank",
            "Ngày Chuyển Kho Crown",
            "Khu Vực",
            "Hàng",
            "Cột",
            "Tình Trạng Thùng",
            "Trạng Thái Thùng",
            "Lưu Ý"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Create data row with test data
     */
    private static void createDataRow(Sheet sheet, int rowNum, int index) {
        Row row = sheet.createRow(rowNum);

        LocalDate baseDate = LocalDate.of(2024, 1, 1);
        LocalDate docDate = baseDate.plusDays(index);
        LocalDate deliveryDate = docDate.plusDays(30);
        LocalDate actualDeliveryDate = deliveryDate.plusDays(2);
        LocalDate storageDate = docDate.plusDays(60);

        int col = 0;
        row.createCell(col++).setCellValue("KHO_HN"); // Kho VPBank
        row.createCell(col++).setCellValue("DV" + String.format("%03d", (index % 100) + 1)); // Mã Đơn Vị
        row.createCell(col++).setCellValue("Nguyen Van " + ((char)('A' + (index % 26)))); // Trách nhiệm bàn giao
        row.createCell(col++).setCellValue("LOAI_" + ((index % 5) + 1)); // Loại chứng từ
        row.createCell(col++).setCellValue(docDate.format(DATE_FORMAT)); // Ngày chứng từ
        row.createCell(col++).setCellValue("TAP_" + String.format("%05d", index + 1)); // Tên tập
        row.createCell(col++).setCellValue((index % 10) + 1); // Số lượng tập
        row.createCell(col++).setCellValue(deliveryDate.format(DATE_FORMAT)); // Ngày phải bàn giao
        row.createCell(col++).setCellValue(actualDeliveryDate.format(DATE_FORMAT)); // Ngày bàn giao
        row.createCell(col++).setCellValue(index % 20 == 0 ? "YES" : "NO"); // Tình trạng thất lạc
        row.createCell(col++).setCellValue("NO"); // Tình trạng không hoàn trả
        row.createCell(col++).setCellValue("COMPLETED"); // Trạng thái Case PDM
        row.createCell(col++).setCellValue("Test note " + (index + 1)); // Ghi chú Case PDM
        row.createCell(col++).setCellValue("BOX" + String.format("%06d", index + 1)); // Mã thùng
        row.createCell(col++).setCellValue((index % 10) + 5); // Thời hạn lưu trữ
        row.createCell(col++).setCellValue(storageDate.format(DATE_FORMAT)); // Ngày nhập kho
        row.createCell(col++).setCellValue(storageDate.plusDays(10).format(DATE_FORMAT)); // Ngày chuyển kho
        row.createCell(col++).setCellValue("KV_" + ((index % 3) + 1)); // Khu vực
        row.createCell(col++).setCellValue((index % 5) + 1); // Hàng
        row.createCell(col++).setCellValue((index % 10) + 1); // Cột
        row.createCell(col++).setCellValue("GOOD"); // Tình trạng thùng
        row.createCell(col++).setCellValue("STORED"); // Trạng thái thùng
        row.createCell(col++).setCellValue("Note " + (index + 1)); // Lưu ý
    }

    /**
     * Create header cell style (bold, blue background)
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

    /**
     * Get total number of columns
     */
    private static int getColumnCount() {
        return 23; // Total columns in ExcelRowDTO
    }

    /**
     * Main method for standalone execution
     */
    public static void main(String[] args) throws IOException {
        // Generate test files with different sizes
        Path testResourcesDir = Path.of("src/test/resources");

        generateTestExcelFile(testResourcesDir.resolve("test-data-5-rows.xlsx"), 5);
        generateTestExcelFile(testResourcesDir.resolve("test-data-10-rows.xlsx"), 10);
        generateTestExcelFile(testResourcesDir.resolve("test-data-15-rows.xlsx"), 15);
        generateTestExcelFile(testResourcesDir.resolve("test-data-20-rows.xlsx"), 20);
        generateTestExcelFile(testResourcesDir.resolve("test-data-100-rows.xlsx"), 100);

        System.out.println("✅ All test files generated successfully!");
    }
}
