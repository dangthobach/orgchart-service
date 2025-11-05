package com.learnmore.controller;

import com.learnmore.application.service.multisheet.AsyncMigrationJobService;
import com.learnmore.application.service.multisheet.MultiSheetProcessor;
import com.learnmore.infrastructure.repository.MigrationJobSheetRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MultiSheetMigrationController.class, 
            excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
            })
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.security.enabled=false"
})
class MultiSheetMigrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MultiSheetProcessor multiSheetProcessor;

    @MockBean
    private AsyncMigrationJobService asyncMigrationJobService;

    @MockBean
    private MigrationJobSheetRepository migrationJobSheetRepository;

    private byte[] buildWorkbook(int hopdRows, int cifRows, int tapRows, boolean includeHopd, boolean includeCif, boolean includeTap) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (includeHopd) {
                Sheet s = wb.createSheet("HSBG_theo_hop_dong");
                // Header
                Row header = s.createRow(0);
                header.createCell(0).setCellValue("Kho VPBank");
                header.createCell(1).setCellValue("Mã đơn vị");
                header.createCell(2).setCellValue("Trách nhiệm bàn giao");
                header.createCell(3).setCellValue("Số hợp đồng");
                header.createCell(4).setCellValue("Tên tập");
                header.createCell(5).setCellValue("Số lượng tập");
                header.createCell(6).setCellValue("Số CIF/ CCCD/ CMT khách hàng");
                header.createCell(7).setCellValue("Tên khách hàng");
                header.createCell(8).setCellValue("Phân khúc khách hàng");
                header.createCell(9).setCellValue("Ngày phải bàn giao");
                header.createCell(10).setCellValue("Ngày bàn giao");
                header.createCell(11).setCellValue("Ngày giải ngân");
                header.createCell(12).setCellValue("Ngày đến hạn");
                header.createCell(13).setCellValue("Loại hồ sơ");
                header.createCell(14).setCellValue("Luồng hồ sơ");
                header.createCell(15).setCellValue("Phân hạn cấp TD");
                header.createCell(16).setCellValue("Ngày dự kiến tiêu hủy");
                header.createCell(17).setCellValue("Sản phẩm");
                header.createCell(18).setCellValue("Trạng thái case PDM");
                header.createCell(19).setCellValue("Ghi chú");
                header.createCell(20).setCellValue("Mã thùng");
                header.createCell(21).setCellValue("Ngày nhập kho VPBank");
                header.createCell(22).setCellValue("Ngày chuyển kho Crown");
                header.createCell(23).setCellValue("Khu vực");
                header.createCell(24).setCellValue("Hàng");
                header.createCell(25).setCellValue("Cột");
                header.createCell(26).setCellValue("Tình trạng thùng");
                header.createCell(27).setCellValue("Trạng thái thùng");
                header.createCell(28).setCellValue("Thời hạn cấp TD");
                header.createCell(29).setCellValue("Mã DAO");
                header.createCell(30).setCellValue("Mã TS");
                header.createCell(31).setCellValue("RRT.ID");
                header.createCell(32).setCellValue("Mã NQ");
                for (int i = 1; i <= hopdRows; i++) {
                    Row r = s.createRow(i);
                    r.createCell(0).setCellValue("KHO");
                    r.createCell(1).setCellValue("DV");
                    r.createCell(3).setCellValue("HD" + i);
                    r.createCell(6).setCellValue("CIF" + i);
                    r.createCell(11).setCellValue("2024-01-01");
                }
            }
            if (includeCif) {
                Sheet s = wb.createSheet("HSBG_theo_CIF");
                Row header = s.createRow(0);
                header.createCell(0).setCellValue("Kho VPBank");
                header.createCell(1).setCellValue("Mã đơn vị");
                header.createCell(2).setCellValue("Trách nhiệm bàn giao");
                header.createCell(3).setCellValue("Số CIF khách hàng");
                header.createCell(4).setCellValue("Tên khách hàng");
                header.createCell(5).setCellValue("Tên tập");
                header.createCell(6).setCellValue("Số lượng tập");
                header.createCell(7).setCellValue("Phân khúc khách hàng");
                header.createCell(8).setCellValue("Ngày phải bàn giao");
                header.createCell(9).setCellValue("Ngày bàn giao");
                header.createCell(10).setCellValue("Ngày giải ngân");
                header.createCell(11).setCellValue("Loại hồ sơ");
                header.createCell(12).setCellValue("Luồng hồ sơ");
                header.createCell(13).setCellValue("Phân hạn cấp TD");
                header.createCell(14).setCellValue("Sản phẩm");
                header.createCell(15).setCellValue("Trạng thái case PDM");
                header.createCell(16).setCellValue("Ghi chú");
                header.createCell(17).setCellValue("Mã NQ");
                header.createCell(18).setCellValue("Mã thùng");
                header.createCell(19).setCellValue("Ngày nhập kho VPBank");
                header.createCell(20).setCellValue("Ngày chuyển kho Crown");
                header.createCell(21).setCellValue("Khu vực");
                header.createCell(22).setCellValue("Hàng");
                header.createCell(23).setCellValue("Cột");
                header.createCell(24).setCellValue("Tình trạng thùng");
                header.createCell(25).setCellValue("Trạng thái thùng");
                for (int i = 1; i <= cifRows; i++) {
                    Row r = s.createRow(i);
                    r.createCell(3).setCellValue("CIF" + i);
                    r.createCell(10).setCellValue("2024-01-01");
                    r.createCell(11).setCellValue("PASS TTN");
                }
            }
            if (includeTap) {
                Sheet s = wb.createSheet("HSBG_theo_tap");
                Row header = s.createRow(0);
                header.createCell(0).setCellValue("Kho VPBank");
                header.createCell(1).setCellValue("Mã đơn vị");
                header.createCell(2).setCellValue("Trách nhiệm bàn giao");
                header.createCell(3).setCellValue("Tháng phát sinh");
                header.createCell(4).setCellValue("Tên tập");
                header.createCell(5).setCellValue("Số lượng tập");
                header.createCell(6).setCellValue("Ngày phải bàn giao");
                header.createCell(7).setCellValue("Ngày bàn giao");
                header.createCell(8).setCellValue("Loại hồ sơ");
                header.createCell(9).setCellValue("Luồng hồ sơ");
                header.createCell(10).setCellValue("Phân hạn cấp TD");
                header.createCell(11).setCellValue("Ngày dự kiến tiêu hủy");
                header.createCell(12).setCellValue("Sản phẩm");
                header.createCell(13).setCellValue("Trạng thái case PDM");
                header.createCell(14).setCellValue("Ghi chú");
                header.createCell(15).setCellValue("Mã thùng");
                header.createCell(16).setCellValue("Ngày nhập kho VPBank");
                header.createCell(17).setCellValue("Ngày chuyển kho Crown");
                header.createCell(18).setCellValue("Khu vực");
                header.createCell(19).setCellValue("Hàng");
                header.createCell(20).setCellValue("Cột");
                header.createCell(21).setCellValue("Tình trạng thùng");
                header.createCell(22).setCellValue("Trạng thái thùng");
                for (int i = 1; i <= tapRows; i++) {
                    Row r = s.createRow(i);
                    r.createCell(3).setCellValue("2024-01");
                    r.createCell(8).setCellValue("KSSV");
                    r.createCell(10).setCellValue("Vĩnh viễn");
                    r.createCell(12).setCellValue("KSSV");
                }
            }

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    @DisplayName("Upload 3-sheet workbook (<=20 rows each) should be accepted")
    void uploadThreeSheetsUnderLimit_ShouldAccepted() throws Exception {
        Mockito.when(migrationJobSheetRepository.findByJobIdOrderBySheetOrder(Mockito.anyString()))
                .thenReturn(Collections.emptyList());

        byte[] file = buildWorkbook(10, 8, 5, true, true, true);
        MockMultipartFile multipartFile = new MockMultipartFile("file", "three_sheets.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file);

        mockMvc.perform(
                multipart("/api/migration/multisheet/upload")
                        .file(multipartFile)
                        .param("async", "true")
                        .param("testRowLimit", "20")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.async").value(true))
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.sheetRowCounts").exists());
    }

    @Test
    @DisplayName("Upload 2-sheet workbook (<=20 rows each) should be accepted")
    void uploadTwoSheetsUnderLimit_ShouldAccepted() throws Exception {
        Mockito.when(migrationJobSheetRepository.findByJobIdOrderBySheetOrder(Mockito.anyString()))
                .thenReturn(Collections.emptyList());

        // Include only hop dong and CIF
        byte[] file = buildWorkbook(5, 12, 0, true, true, false);
        MockMultipartFile multipartFile = new MockMultipartFile("file", "two_sheets.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file);

        mockMvc.perform(
                multipart("/api/migration/multisheet/upload")
                        .file(multipartFile)
                        .param("async", "true")
                        .param("testRowLimit", "20")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.async").value(true))
                .andExpect(jsonPath("$.status").value("STARTED"));
    }

    @Test
    @DisplayName("Upload workbook exceeding 20 rows in a sheet should fast fail")
    void uploadSheetOverLimit_ShouldBadRequest() throws Exception {
        Mockito.when(migrationJobSheetRepository.findByJobIdOrderBySheetOrder(Mockito.anyString()))
                .thenReturn(Collections.emptyList());

        // hop_dong sheet has 21 rows (>20) to trigger fast-fail
        byte[] file = buildWorkbook(21, 5, 5, true, true, true);
        MockMultipartFile multipartFile = new MockMultipartFile("file", "over_limit.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file);

        mockMvc.perform(
                multipart("/api/migration/multisheet/upload")
                        .file(multipartFile)
                        .param("async", "true")
                        .param("testRowLimit", "20")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.maxAllowedRows").value(20));
    }
}


