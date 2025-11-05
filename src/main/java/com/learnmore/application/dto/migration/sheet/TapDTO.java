package com.learnmore.application.dto.migration.sheet;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * DTO for sheet "HSBG_theo_tap" (Folder-based Archive Management)
 * Maps Excel columns to Java fields with business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TapDTO {

    @ExcelColumn(name = "Kho VPBank", index = 0, required = true)
    private String vpbankWarehouse;

    @ExcelColumn(name = "Mã đơn vị", index = 1, required = true)
    private String unitCode;

    @ExcelColumn(name = "Trách nhiệm bàn giao", index = 2, required = true)
    private String handoverResponsibility;

    @ExcelColumn(name = "Tháng phát sinh", index = 3, required = true, dateFormat = "yyyy-MM")
    private YearMonth incidentMonth;

    @ExcelColumn(name = "Tên tập", index = 4)
    private String folderName;

    @ExcelColumn(name = "Số lượng tập", index = 5, required = true)
    private Integer folderQuantity;

    @ExcelColumn(name = "Ngày phải bàn giao", index = 6, dateFormat = "yyyy-MM-dd")
    private LocalDate requiredHandoverDate;

    @ExcelColumn(name = "Ngày bàn giao", index = 7, dateFormat = "yyyy-MM-dd")
    private LocalDate actualHandoverDate;

    @ExcelColumn(name = "Loại hồ sơ", index = 8, required = true)
    private String documentType;

    @ExcelColumn(name = "Luồng hồ sơ", index = 9, required = true)
    private String documentFlow;

    @ExcelColumn(name = "Phân hạn cấp TD", index = 10, required = true)
    private String retentionPeriodCategory;

    @ExcelColumn(name = "Ngày dự kiến tiêu hủy", index = 11, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate expectedDestructionDate;

    @ExcelColumn(name = "Sản phẩm", index = 12, required = true)
    private String product;

    @ExcelColumn(name = "Trạng thái case PDM", index = 13)
    private String pdmCaseStatus;

    @ExcelColumn(name = "Ghi chú", index = 14)
    private String notes;

    @ExcelColumn(name = "Mã thùng", index = 15, required = true)
    private String boxCode;

    @ExcelColumn(name = "Ngày nhập kho VPBank", index = 16, dateFormat = "yyyy-MM-dd")
    private LocalDate vpbankWarehouseEntryDate;

    @ExcelColumn(name = "Ngày chuyển kho Crown", index = 17, dateFormat = "yyyy-MM-dd")
    private LocalDate crownWarehouseTransferDate;

    @ExcelColumn(name = "Khu vực", index = 18)
    private String zone;

    @ExcelColumn(name = "Hàng", index = 19)
    private Integer row;

    @ExcelColumn(name = "Cột", index = 20)
    private Integer column;

    @ExcelColumn(name = "Tình trạng thùng", index = 21)
    private String boxCondition;

    @ExcelColumn(name = "Trạng thái thùng", index = 22)
    private String boxStatus;

    // Transient fields for validation
    private transient String validationErrors;
    private transient boolean isValid = true;

    /**
     * Business key for duplicate check
     * CT1: Check duplicate data key (Unit Code + Handover Responsibility + Incident Month + Product)
     */
    public String generateBusinessKey() {
        return String.format("%s_%s_%s_%s", unitCode, handoverResponsibility, incidentMonth, product);
    }

    /**
     * Validate documentType must be "KSSV"
     * CT2: Check data "Document Type" = "KSSV"
     */
    public boolean isValidDocumentType() {
        return "KSSV".equals(documentType);
    }

    /**
     * Validate documentFlow must be "HSTD thường"
     * CT3: Check data "Document Flow" = "HSTD thường"
     */
    public boolean isValidDocumentFlow() {
        return "HSTD thường".equals(documentFlow);
    }

    /**
     * Validate retentionPeriodCategory must be "Vĩnh viễn"
     * CT4: Check data "Retention Period Category" = "Vĩnh viễn"
     */
    public boolean isValidRetentionPeriodCategory() {
        return "Vĩnh viễn".equals(retentionPeriodCategory);
    }

    /**
     * Validate expectedDestructionDate must be "31-Dec-9999"
     * CT5: Check data "Expected Destruction Date" = "31-Dec-9999"
     */
    public boolean isValidExpectedDestructionDate() {
        if (expectedDestructionDate == null) return false;
        return expectedDestructionDate.equals(LocalDate.of(9999, 12, 31));
    }

    /**
     * Validate product must be "KSSV"
     * CT6: Check data "Product" = "KSSV"
     */
    public boolean isValidProduct() {
        return "KSSV".equals(product);
    }
}
