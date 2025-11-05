package com.learnmore.application.dto.migration.sheet;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for sheet "HSBG_theo_CIF" (Customer CIF-based Archive Management)
 * Maps Excel columns to Java fields with business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CifDTO {

    @ExcelColumn(name = "Kho VPBank", index = 0, required = true)
    private String vpbankWarehouse;

    @ExcelColumn(name = "Mã đơn vị", index = 1, required = true)
    private String unitCode;

    @ExcelColumn(name = "Trách nhiệm bàn giao", index = 2)
    private String handoverResponsibility;

    @ExcelColumn(name = "Số CIF khách hàng", index = 3, required = true)
    private String customerCif;

    @ExcelColumn(name = "Tên khách hàng", index = 4)
    private String customerName;

    @ExcelColumn(name = "Tên tập", index = 5)
    private String folderName;

    @ExcelColumn(name = "Số lượng tập", index = 6, required = true)
    private Integer folderQuantity;

    @ExcelColumn(name = "Phân khúc khách hàng", index = 7)
    private String customerSegment;

    @ExcelColumn(name = "Ngày phải bàn giao", index = 8, dateFormat = "yyyy-MM-dd")
    private LocalDate requiredHandoverDate;

    @ExcelColumn(name = "Ngày bàn giao", index = 9, dateFormat = "yyyy-MM-dd")
    private LocalDate actualHandoverDate;

    @ExcelColumn(name = "Ngày giải ngân", index = 10, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate disbursementDate;

    @ExcelColumn(name = "Loại hồ sơ", index = 11, required = true)
    private String documentType;

    @ExcelColumn(name = "Luồng hồ sơ", index = 12, required = true)
    private String documentFlow;

    @ExcelColumn(name = "Phân hạn cấp TD", index = 13, required = true)
    private String retentionPeriodCategory;

    @ExcelColumn(name = "Sản phẩm", index = 14)
    private String product;

    @ExcelColumn(name = "Trạng thái case PDM", index = 15)
    private String pdmCaseStatus;

    @ExcelColumn(name = "Ghi chú", index = 16)
    private String notes;

    @ExcelColumn(name = "Mã NQ", index = 17)
    private String resolutionCode;

    @ExcelColumn(name = "Mã thùng", index = 18, required = true)
    private String boxCode;

    @ExcelColumn(name = "Ngày nhập kho VPBank", index = 19, dateFormat = "yyyy-MM-dd")
    private LocalDate vpbankWarehouseEntryDate;

    @ExcelColumn(name = "Ngày chuyển kho Crown", index = 20, dateFormat = "yyyy-MM-dd")
    private LocalDate crownWarehouseTransferDate;

    @ExcelColumn(name = "Khu vực", index = 21)
    private String zone;

    @ExcelColumn(name = "Hàng", index = 22)
    private Integer row;

    @ExcelColumn(name = "Cột", index = 23)
    private Integer column;

    @ExcelColumn(name = "Tình trạng thùng", index = 24)
    private String boxCondition;

    @ExcelColumn(name = "Trạng thái thùng", index = 25)
    private String boxStatus;

    // Transient fields for validation
    private transient String validationErrors;
    private transient boolean isValid = true;

    /**
     * Business key for duplicate check
     * CT1: Check duplicate data key (Customer CIF + Disbursement Date + Document Type)
     */
    public String generateBusinessKey() {
        return String.format("%s_%s_%s", customerCif, disbursementDate, documentType);
    }

    /**
     * Validate documentFlow must be "HSTD thường"
     * CT2: Check data "Document Flow" = "HSTD thường"
     */
    public boolean isValidDocumentFlow() {
        return "HSTD thường".equals(documentFlow);
    }

    /**
     * Validate retentionPeriodCategory must be "Vĩnh viễn"
     * CT3: Check data "Retention Period Category" = "Vĩnh viễn"
     */
    public boolean isValidRetentionPeriodCategory() {
        return "Vĩnh viễn".equals(retentionPeriodCategory);
    }

    /**
     * Validate documentType is in allowed list
     * CT4: Check "Document Type" is in allowed list
     */
    public boolean isValidDocumentType() {
        if (documentType == null) return false;
        return documentType.equals("PASS TTN") ||
               documentType.equals("SCF VEERFIN") ||
               documentType.equals("Trình cấp TD không qua CPC") ||
               documentType.equals("Hồ sơ mở TKTT nhưng không giải ngân");
    }

    /**
     * Mask sensitive data for GDPR compliance
     */
    public void maskSensitiveData() {
        if (customerCif != null && customerCif.length() > 4) {
            customerCif = customerCif.substring(0, 2) +
                   "*".repeat(customerCif.length() - 4) +
                   customerCif.substring(customerCif.length() - 2);
        }

        if (customerName != null && customerName.length() > 4) {
            customerName = customerName.substring(0, 2) +
                          "*".repeat(customerName.length() - 4) +
                          customerName.substring(customerName.length() - 2);
        }
    }
}
