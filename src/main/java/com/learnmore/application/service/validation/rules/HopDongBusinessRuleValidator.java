package com.learnmore.application.service.validation.rules;

import com.learnmore.application.dto.migration.sheet.HopDongDTO;
import com.learnmore.application.service.validation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Business rule validator for HopDong sheet
 * Implements complex business logic specific to HopDong
 */
@Component
@Slf4j
public class HopDongBusinessRuleValidator implements ValidationRule<HopDongDTO> {

    @Override
    public ValidationResult validate(HopDongDTO data, ValidationContext context) {
        ValidationResult result = ValidationResult.success();

        if (data == null) {
            return result;
        }

        // CT1: Validate destruction date calculation
        validateDestructionDate(data, result);

        // CT6: Validate destruction date for "Vĩnh viễn"
        validateDestructionDateForVinhVien(data, result);

        // CT7: Validate destruction date when ngayDenHan is blank
        validateDestructionDateWhenNoDeadline(data, result);

        // CT5: Validate thoiHanCapTd is positive integer
        validateThoiHanCapTd(data, result);

        return result;
    }

    /**
     * CT1: Công thức tính "Ngày dự kiến tiêu hủy"
     * Must match phanHanCapTd
     */
    private void validateDestructionDate(HopDongDTO data, ValidationResult result) {
        if (data.getPhanHanCapTd() == null || data.getNgayDuKienTieuHuy() == null) {
            return;
        }

        LocalDate calculated = data.calculateDestructionDate();
        LocalDate actual = data.getNgayDuKienTieuHuy();

        if (!calculated.equals(actual)) {
            result.addError(ValidationError.businessRuleViolation(
                    "ngayDuKienTieuHuy",
                    actual.toString(),
                    String.format("Ngày dự kiến tiêu hủy không khớp với phân hạn. Mong đợi: %s", calculated)
            ));
        }
    }

    /**
     * CT6: Kiểm tra "Ngày đến hạn tiêu hủy" (Mặc định là "31-Dec-9999") theo Phân hạn cấp TD
     * Nếu Phân hạn = "Vĩnh viễn" thì Ngày dự kiến tiêu hủy phải là 9999-12-31
     */
    private void validateDestructionDateForVinhVien(HopDongDTO data, ValidationResult result) {
        if ("Vĩnh viễn".equals(data.getPhanHanCapTd())) {
            LocalDate expectedDate = LocalDate.of(9999, 12, 31);

            if (data.getNgayDuKienTieuHuy() == null) {
                result.addError(ValidationError.businessRuleViolation(
                        "ngayDuKienTieuHuy",
                        null,
                        "Phân hạn 'Vĩnh viễn' phải có Ngày dự kiến tiêu hủy = 31-Dec-9999"
                ));
            } else if (data.getNgayDuKienTieuHuy().getYear() != 9999) {
                result.addError(ValidationError.businessRuleViolation(
                        "ngayDuKienTieuHuy",
                        data.getNgayDuKienTieuHuy().toString(),
                        "Phân hạn 'Vĩnh viễn' phải có Ngày dự kiến tiêu hủy = 31-Dec-9999"
                ));
            }
        }
    }

    /**
     * CT7: Kiểm tra "Ngày đến hạn tiêu hủy" theo Ngày đến hạn
     * Nếu Ngày đến hạn = blank thì Ngày dự kiến tiêu hủy phải là 9999-12-31
     */
    private void validateDestructionDateWhenNoDeadline(HopDongDTO data, ValidationResult result) {
        if (data.getNgayDenHan() == null) {
            if (data.getNgayDuKienTieuHuy() == null) {
                result.addError(ValidationError.businessRuleViolation(
                        "ngayDuKienTieuHuy",
                        null,
                        "Khi Ngày đến hạn trống, Ngày dự kiến tiêu hủy phải là 31-Dec-9999"
                ));
            } else if (data.getNgayDuKienTieuHuy().getYear() != 9999) {
                result.addError(ValidationError.businessRuleViolation(
                        "ngayDuKienTieuHuy",
                        data.getNgayDuKienTieuHuy().toString(),
                        "Khi Ngày đến hạn trống, Ngày dự kiến tiêu hủy phải là 31-Dec-9999"
                ));
            }
        }
    }

    /**
     * CT5: Kiểm tra "Thời hạn cấp TD" phải là số nguyên dương
     */
    private void validateThoiHanCapTd(HopDongDTO data, ValidationResult result) {
        if (data.getThoiHanCapTd() != null && data.getThoiHanCapTd() <= 0) {
            result.addError(ValidationError.businessRuleViolation(
                    "thoiHanCapTd",
                    data.getThoiHanCapTd().toString(),
                    "Thời hạn cấp TD phải là số nguyên dương"
            ));
        }
    }

    @Override
    public String getRuleName() {
        return "hop_dong_business_logic";
    }

    @Override
    public String getDescription() {
        return "Business logic validation for HopDong sheet";
    }

    @Override
    public int getPriority() {
        return 50;
    }
}
