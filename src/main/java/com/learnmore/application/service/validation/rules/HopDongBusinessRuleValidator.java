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
     * CT1: Formula to calculate "Expected Destruction Date"
     * Must match retentionPeriodCategory
     */
    private void validateDestructionDate(HopDongDTO data, ValidationResult result) {
        if (data.getRetentionPeriodCategory() == null || data.getExpectedDestructionDate() == null) {
            return;
        }

        LocalDate calculated = data.calculateDestructionDate();
        LocalDate actual = data.getExpectedDestructionDate();

        if (!calculated.equals(actual)) {
            result.addError(ValidationError.businessRuleViolation(
                    "expectedDestructionDate",
                    actual.toString(),
                    String.format("Expected destruction date does not match retention period. Expected: %s", calculated)
            ));
        }
    }

    /**
     * CT6: Check "Destruction Due Date" (Default is "31-Dec-9999") by Retention Period Category
     * If Retention Period = "Vĩnh viễn" then Expected Destruction Date must be 9999-12-31
     */
    private void validateDestructionDateForVinhVien(HopDongDTO data, ValidationResult result) {
        if ("Vĩnh viễn".equals(data.getRetentionPeriodCategory())) {
            LocalDate expectedDate = LocalDate.of(9999, 12, 31);

            if (data.getExpectedDestructionDate() == null) {
                result.addError(ValidationError.businessRuleViolation(
                        "expectedDestructionDate",
                        null,
                        "Retention period 'Vĩnh viễn' must have Expected Destruction Date = 31-Dec-9999"
                ));
            } else if (data.getExpectedDestructionDate().getYear() != 9999) {
                result.addError(ValidationError.businessRuleViolation(
                        "expectedDestructionDate",
                        data.getExpectedDestructionDate().toString(),
                        "Retention period 'Vĩnh viễn' must have Expected Destruction Date = 31-Dec-9999"
                ));
            }
        }
    }

    /**
     * CT7: Check "Destruction Due Date" by Due Date
     * If Due Date = blank then Expected Destruction Date must be 9999-12-31
     */
    private void validateDestructionDateWhenNoDeadline(HopDongDTO data, ValidationResult result) {
        if (data.getDueDate() == null) {
            if (data.getExpectedDestructionDate() == null) {
                result.addError(ValidationError.businessRuleViolation(
                        "expectedDestructionDate",
                        null,
                        "When Due Date is blank, Expected Destruction Date must be 31-Dec-9999"
                ));
            } else if (data.getExpectedDestructionDate().getYear() != 9999) {
                result.addError(ValidationError.businessRuleViolation(
                        "expectedDestructionDate",
                        data.getExpectedDestructionDate().toString(),
                        "When Due Date is blank, Expected Destruction Date must be 31-Dec-9999"
                ));
            }
        }
    }

    /**
     * CT5: Check "Retention Period Years" must be positive integer
     */
    private void validateThoiHanCapTd(HopDongDTO data, ValidationResult result) {
        if (data.getRetentionPeriodYears() != null && data.getRetentionPeriodYears() <= 0) {
            result.addError(ValidationError.businessRuleViolation(
                    "retentionPeriodYears",
                    data.getRetentionPeriodYears().toString(),
                    "Retention period years must be positive integer"
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
