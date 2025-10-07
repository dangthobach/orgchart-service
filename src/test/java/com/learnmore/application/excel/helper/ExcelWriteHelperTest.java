package com.learnmore.application.excel.helper;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.utils.config.ExcelConfig;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExcelWriteHelperTest {

    private final ExcelWriteHelper helper = new ExcelWriteHelper();

    @Test
    public void testWriteToBytesXSSF_EmptyData_WithSchema_WritesHeader() throws Exception {
        ExcelConfig cfg = ExcelConfig.builder()
                .outputBeanClassName(ExcelRowDTO.class.getName())
                .build();

        byte[] bytes = helper.writeToBytesXSSF(Collections.emptyList(), cfg);

        Assertions.assertNotNull(bytes);
        Assertions.assertTrue(bytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Assertions.assertEquals(1, wb.getNumberOfSheets());
            var sheet = wb.getSheetAt(0);
            var header = sheet.getRow(0);
            Assertions.assertNotNull(header);

            // header should contain some known column names
            boolean foundKnown = false;
            for (int i = 0; i < header.getLastCellNum(); i++) {
                var cell = header.getCell(i);
                if (cell != null) {
                    String v = cell.getStringCellValue();
                    if ("Mã đơn vị".equals(v) || "Kho VPBank".equals(v) || "Loại chứng từ".equals(v)) {
                        foundKnown = true;
                        break;
                    }
                }
            }
            Assertions.assertTrue(foundKnown, "Header should contain known column names");
        }
    }

    @Test
    public void testWriteToBytesXSSF_NonEmpty_WritesRows() throws Exception {
        ExcelConfig cfg = ExcelConfig.builder().build();

        List<ExcelRowDTO> rows = new ArrayList<>();
        rows.add(ExcelRowDTO.builder()
                .khoVpbank("A")
                .maDonVi("MDV001")
                .loaiChungTu("LC1")
                .ngayChungTu("2024-01-01")
                .soLuongTap(1)
                .maThung("TH001")
                .thoiHanLuuTru(1)
                .build());

        byte[] bytes = helper.writeToBytesXSSF(rows, cfg);

        Assertions.assertNotNull(bytes);
        Assertions.assertTrue(bytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            Assertions.assertNotNull(sheet.getRow(0)); // header
            Assertions.assertNotNull(sheet.getRow(1)); // first data row
        }
    }

    @Test
    public void testWriteToBytesSXSSF_EmptyData_WithSchema_WritesHeader() throws Exception {
        ExcelConfig cfg = ExcelConfig.builder()
                .outputBeanClassName(ExcelRowDTO.class.getName())
                .sxssfRowAccessWindowSize(200)
                .build();

        byte[] bytes = helper.writeToBytesSXSSF(Collections.emptyList(), cfg, 200);

        Assertions.assertNotNull(bytes);
        Assertions.assertTrue(bytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            var header = sheet.getRow(0);
            Assertions.assertNotNull(header);
        }
    }
}


