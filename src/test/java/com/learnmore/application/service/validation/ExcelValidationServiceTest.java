package com.learnmore.application.service.validation;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho ExcelValidationService
 */
@SpringBootTest
public class ExcelValidationServiceTest {

    @Autowired
    private ExcelValidationService validationService;

    @Test
    public void testValidationServiceInjection() {
        assertNotNull(validationService, "ExcelValidationService should be injected");
    }

    @Test
    public void testValidateRowWithValidData() {
        // Tạo ExcelRowDTO với dữ liệu hợp lệ
        ExcelRowDTO validRow = ExcelRowDTO.builder()
                .rowNumber(1)
                .maDonVi("DV001")
                .loaiChungTu("CT001")
                .ngayChungTu("01/01/2024")
                .maThung("TH001")
                .soLuongTap(10)
                .thoiHanLuuTru(365)
                .hang(1)
                .cot(1)
                .build();

        ExcelValidationService.ValidationResult result = validationService.validateRow(validRow);
        
        assertFalse(result.isHasErrors(), "Valid row should not have errors");
        assertNull(result.getErrorMessage(), "Valid row should not have error message");
        assertNull(result.getErrorCode(), "Valid row should not have error code");
    }

    @Test
    public void testValidateRowWithMissingRequiredFields() {
        // Tạo ExcelRowDTO thiếu trường bắt buộc
        ExcelRowDTO invalidRow = ExcelRowDTO.builder()
                .rowNumber(1)
                .maDonVi(null) // Thiếu mã đơn vị
                .loaiChungTu("CT001")
                .ngayChungTu("01/01/2024")
                .maThung("TH001")
                .build();

        ExcelValidationService.ValidationResult result = validationService.validateRow(invalidRow);
        
        assertTrue(result.isHasErrors(), "Invalid row should have errors");
        assertNotNull(result.getErrorMessage(), "Invalid row should have error message");
        assertNotNull(result.getErrorCode(), "Invalid row should have error code");
        assertTrue(result.getErrorMessage().contains("Mã đơn vị không được để trống"), 
                   "Error message should contain required field message");
        assertTrue(result.getErrorCode().contains("REQUIRED_MA_DON_VI"), 
                   "Error code should contain required field code");
    }

    @Test
    public void testValidateRowWithInvalidDateFormat() {
        // Tạo ExcelRowDTO với định dạng ngày không hợp lệ
        ExcelRowDTO invalidRow = ExcelRowDTO.builder()
                .rowNumber(1)
                .maDonVi("DV001")
                .loaiChungTu("CT001")
                .ngayChungTu("invalid-date") // Định dạng ngày không hợp lệ
                .maThung("TH001")
                .build();

        ExcelValidationService.ValidationResult result = validationService.validateRow(invalidRow);
        
        assertTrue(result.isHasErrors(), "Invalid date format should have errors");
        assertTrue(result.getErrorMessage().contains("Ngày chứng từ không đúng định dạng"), 
                   "Error message should contain date format message");
        assertTrue(result.getErrorCode().contains("INVALID_DATE_FORMAT"), 
                   "Error code should contain date format code");
    }
}
