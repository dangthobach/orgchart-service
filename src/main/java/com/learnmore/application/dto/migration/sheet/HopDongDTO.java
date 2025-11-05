package com.learnmore.application.dto.migration.sheet;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for sheet "HSBG_theo_hop_dong" (Contract-based Archive Management)
 * Maps Excel columns to Java fields with business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopDongDTO {

    @ExcelColumn(name = "Kho VPBank", index = 0, required = true)
    private String vpbankWarehouse;

    @ExcelColumn(name = "Mã đơn vị", index = 1, required = true)
    private String unitCode;

    @ExcelColumn(name = "Trách nhiệm bàn giao", index = 2)
    private String handoverResponsibility;

    @ExcelColumn(name = "Số hợp đồng", index = 3, required = true)
    private String contractNumber;

    @ExcelColumn(name = "Tên tập", index = 4)
    private String folderName;

    @ExcelColumn(name = "Số lượng tập", index = 5, required = true)
    private Integer folderQuantity;

    @ExcelColumn(name = "Số CIF/ CCCD/ CMT khách hàng", index = 6)
    private String customerCif;

    @ExcelColumn(name = "Tên khách hàng", index = 7)
    private String customerName;

    @ExcelColumn(name = "Phân khúc khách hàng", index = 8)
    private String customerSegment;

    @ExcelColumn(name = "Ngày phải bàn giao", index = 9, dateFormat = "yyyy-MM-dd")
    private LocalDate requiredHandoverDate;

    @ExcelColumn(name = "Ngày bàn giao", index = 10, dateFormat = "yyyy-MM-dd")
    private LocalDate actualHandoverDate;

    @ExcelColumn(name = "Ngày giải ngân", index = 11, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate disbursementDate;

    @ExcelColumn(name = "Ngày đến hạn", index = 12, dateFormat = "yyyy-MM-dd")
    private LocalDate dueDate;

    @ExcelColumn(name = "Loại hồ sơ", index = 13, required = true)
    private String documentType;

    @ExcelColumn(name = "Luồng hồ sơ", index = 14)
    private String documentFlow;

    @ExcelColumn(name = "Phân hạn cấp TD", index = 15, required = true)
    private String retentionPeriodCategory;

    @ExcelColumn(name = "Ngày dự kiến tiêu hủy", index = 16, dateFormat = "yyyy-MM-dd")
    private LocalDate expectedDestructionDate;

    @ExcelColumn(name = "Sản phẩm", index = 17)
    private String product;

    @ExcelColumn(name = "Trạng thái case PDM", index = 18)
    private String pdmCaseStatus;

    @ExcelColumn(name = "Ghi chú", index = 19)
    private String notes;

    @ExcelColumn(name = "Mã thùng", index = 20, required = true)
    private String boxCode;

    @ExcelColumn(name = "Ngày nhập kho VPBank", index = 21, dateFormat = "yyyy-MM-dd")
    private LocalDate vpbankWarehouseEntryDate;

    @ExcelColumn(name = "Ngày chuyển kho Crown", index = 22, dateFormat = "yyyy-MM-dd")
    private LocalDate crownWarehouseTransferDate;

    @ExcelColumn(name = "Khu vực", index = 23)
    private String zone;

    @ExcelColumn(name = "Hàng", index = 24)
    private Integer row;

    @ExcelColumn(name = "Cột", index = 25)
    private Integer column;

    @ExcelColumn(name = "Tình trạng thùng", index = 26)
    private String boxCondition;

    @ExcelColumn(name = "Trạng thái thùng", index = 27)
    private String boxStatus;

    @ExcelColumn(name = "Thời hạn cấp TD", index = 28)
    private Integer retentionPeriodYears;

    @ExcelColumn(name = "Mã DAO", index = 29)
    private String daoCode;

    @ExcelColumn(name = "Mã TS", index = 30)
    private String assetCode;

    @ExcelColumn(name = "RRT.ID", index = 31)
    private String rrtId;

    @ExcelColumn(name = "Mã NQ", index = 32)
    private String resolutionCode;

    // Transient fields for validation
    private transient String validationErrors;
    private transient boolean isValid = true;

    /**
     * Business key for duplicate check
     * Rule varies by documentType
     */
    public String generateBusinessKey() {
        // CT2: Check duplicate data by Document Type
        if (isLoanType()) {
            // Duplicate check: Contract Number + Document Type + Disbursement Date
            return String.format("%s_%s_%s", contractNumber, documentType, disbursementDate);
        } else if (isCreditCardType()) {
            // Duplicate check: Contract Number + Document Type + Customer CIF
            return String.format("%s_%s_%s", contractNumber, documentType, customerCif);
        } else if ("TTK".equals(documentType)) {
            // Duplicate check: Contract Number + Document Type + Customer CIF + Unit Code + Disbursement Date
            return String.format("%s_%s_%s_%s_%s", contractNumber, documentType, customerCif, unitCode, disbursementDate);
        }
        return String.format("%s_%s", contractNumber, documentType);
    }

    /**
     * Check if documentType is loan type
     * LD, MD, OD, HDHM, KSSV, Bao thanh toán, Biên nhận thế chấp
     */
    private boolean isLoanType() {
        return documentType != null && (
            documentType.equals("LD") || documentType.equals("MD") || documentType.equals("OD") ||
            documentType.equals("HDHM") || documentType.equals("KSSV") ||
            documentType.equals("Bao thanh toán") || documentType.equals("Biên nhận thế chấp")
        );
    }

    /**
     * Check if documentType is credit card type
     * CC, TSBD
     */
    private boolean isCreditCardType() {
        return documentType != null && (documentType.equals("CC") || documentType.equals("TSBD"));
    }

    /**
     * Calculate destruction date based on retentionPeriodCategory and disbursementDate
     * CT1: Formula to calculate "Expected Destruction Date"
     */
    public LocalDate calculateDestructionDate() {
        if ("Vĩnh viễn".equals(retentionPeriodCategory)) {
            return LocalDate.of(9999, 12, 31);
        }

        if (disbursementDate == null) {
            return LocalDate.of(9999, 12, 31);
        }

        if ("Ngắn hạn".equals(retentionPeriodCategory)) {
            return disbursementDate.plusYears(5);
        } else if ("Trung hạn".equals(retentionPeriodCategory)) {
            return disbursementDate.plusYears(10);
        } else if ("Dài hạn".equals(retentionPeriodCategory)) {
            return disbursementDate.plusYears(15);
        }

        return LocalDate.of(9999, 12, 31);
    }

    /**
     * Mask sensitive data for GDPR compliance
     */
    public void maskSensitiveData() {
        if (contractNumber != null && contractNumber.length() > 4) {
            contractNumber = contractNumber.substring(0, 2) +
                       "*".repeat(contractNumber.length() - 4) +
                       contractNumber.substring(contractNumber.length() - 2);
        }

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
