package com.learnmore.application.service.multisheet;

import com.learnmore.application.utils.converter.TypeConverter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class to map Excel column headers (Vietnamese) to database column names (English)
 * and normalize values for staging_raw tables
 */
@Slf4j
public class SheetColumnMapper {

    private static final TypeConverter typeConverter = TypeConverter.getInstance();
    
    // Mapping from Excel header (Vietnamese) to DB column (English)
    private static final Map<String, Map<String, String>> HEADER_TO_COLUMN_MAP = new HashMap<>();
    
    static {
        // HSBG_theo_hop_dong mapping
        Map<String, String> hopDongMap = new HashMap<>();
        hopDongMap.put("Kho VPBank", "vpbank_warehouse");
        hopDongMap.put("Mã đơn vị", "unit_code");
        hopDongMap.put("Trách nhiệm bàn giao", "handover_responsibility");
        hopDongMap.put("Số hợp đồng", "contract_number");
        hopDongMap.put("Tên tập", "folder_name");
        hopDongMap.put("Số lượng tập", "folder_quantity");
        hopDongMap.put("Số CIF/ CCCD/ CMT khách hàng", "customer_cif");
        hopDongMap.put("Tên khách hàng", "customer_name");
        hopDongMap.put("Phân khúc khách hàng", "customer_segment");
        hopDongMap.put("Ngày phải bàn giao", "required_handover_date");
        hopDongMap.put("Ngày bàn giao", "actual_handover_date");
        hopDongMap.put("Ngày giải ngân", "disbursement_date");
        hopDongMap.put("Ngày đến hạn", "due_date");
        hopDongMap.put("Loại hồ sơ", "document_type");
        hopDongMap.put("Luồng hồ sơ", "document_flow");
        hopDongMap.put("Phân hạn cấp TD", "retention_period_category");
        hopDongMap.put("Ngày dự kiến tiêu hủy", "expected_destruction_date");
        hopDongMap.put("Sản phẩm", "product");
        hopDongMap.put("Trạng thái case PDM", "pdm_case_status");
        hopDongMap.put("Ghi chú", "notes");
        hopDongMap.put("Mã thùng", "box_code");
        hopDongMap.put("Ngày nhập kho VPBank", "vpbank_warehouse_entry_date");
        hopDongMap.put("Ngày chuyển kho Crown", "crown_warehouse_transfer_date");
        hopDongMap.put("Khu vực", "zone");
        hopDongMap.put("Hàng", "row");
        hopDongMap.put("Cột", "column");
        hopDongMap.put("Tình trạng thùng", "box_condition");
        hopDongMap.put("Trạng thái thùng", "box_status");
        hopDongMap.put("Thời hạn cấp TD", "retention_period_years");
        hopDongMap.put("Mã DAO", "dao_code");
        hopDongMap.put("Mã TS", "asset_code");
        hopDongMap.put("RRT.ID", "rrt_id");
        hopDongMap.put("Mã NQ", "resolution_code");
        HEADER_TO_COLUMN_MAP.put("HSBG_theo_hop_dong", hopDongMap);

        // HSBG_theo_CIF mapping
        Map<String, String> cifMap = new HashMap<>();
        cifMap.put("Kho VPBank", "vpbank_warehouse");
        cifMap.put("Mã đơn vị", "unit_code");
        cifMap.put("Trách nhiệm bàn giao", "handover_responsibility");
        cifMap.put("Số CIF khách hàng", "customer_cif");
        cifMap.put("Tên khách hàng", "customer_name");
        cifMap.put("Tên tập", "folder_name");
        cifMap.put("Số lượng tập", "folder_quantity");
        cifMap.put("Phân khúc khách hàng", "customer_segment");
        cifMap.put("Ngày phải bàn giao", "required_handover_date");
        cifMap.put("Ngày bàn giao", "actual_handover_date");
        cifMap.put("Ngày giải ngân", "disbursement_date");
        cifMap.put("Loại hồ sơ", "document_type");
        cifMap.put("Luồng hồ sơ", "document_flow");
        cifMap.put("Phân hạn cấp TD", "retention_period_category");
        cifMap.put("Sản phẩm", "product");
        cifMap.put("Trạng thái case PDM", "pdm_case_status");
        cifMap.put("Ghi chú", "notes");
        cifMap.put("Mã NQ", "resolution_code");
        cifMap.put("Mã thùng", "box_code");
        cifMap.put("Ngày nhập kho VPBank", "vpbank_warehouse_entry_date");
        cifMap.put("Ngày chuyển kho Crown", "crown_warehouse_transfer_date");
        cifMap.put("Khu vực", "zone");
        cifMap.put("Hàng", "row");
        cifMap.put("Cột", "column");
        cifMap.put("Tình trạng thùng", "box_condition");
        cifMap.put("Trạng thái thùng", "box_status");
        HEADER_TO_COLUMN_MAP.put("HSBG_theo_CIF", cifMap);

        // HSBG_theo_tap mapping
        Map<String, String> tapMap = new HashMap<>();
        tapMap.put("Kho VPBank", "vpbank_warehouse");
        tapMap.put("Mã đơn vị", "unit_code");
        tapMap.put("Trách nhiệm bàn giao", "handover_responsibility");
        tapMap.put("Tháng phát sinh", "incident_month");
        tapMap.put("Tên tập", "folder_name");
        tapMap.put("Số lượng tập", "folder_quantity");
        tapMap.put("Ngày phải bàn giao", "required_handover_date");
        tapMap.put("Ngày bàn giao", "actual_handover_date");
        tapMap.put("Loại hồ sơ", "document_type");
        tapMap.put("Luồng hồ sơ", "document_flow");
        tapMap.put("Phân hạn cấp TD", "retention_period_category");
        tapMap.put("Ngày dự kiến tiêu hủy", "expected_destruction_date");
        tapMap.put("Sản phẩm", "product");
        tapMap.put("Trạng thái case PDM", "pdm_case_status");
        tapMap.put("Ghi chú", "notes");
        tapMap.put("Mã thùng", "box_code");
        tapMap.put("Ngày nhập kho VPBank", "vpbank_warehouse_entry_date");
        tapMap.put("Ngày chuyển kho Crown", "crown_warehouse_transfer_date");
        tapMap.put("Khu vực", "zone");
        tapMap.put("Hàng", "row");
        tapMap.put("Cột", "column");
        tapMap.put("Tình trạng thùng", "box_condition");
        tapMap.put("Trạng thái thùng", "box_status");
        HEADER_TO_COLUMN_MAP.put("HSBG_theo_tap", tapMap);
    }

    private final String sheetName;
    private final Map<String, Integer> headerIndexMap; // Excel header -> column index
    private final List<String> dbColumnOrder; // Ordered list of DB columns for INSERT
    private final Map<Integer, String> indexToDbColumn; // Column index -> DB column name

    public SheetColumnMapper(String sheetName, List<String> excelHeaders) {
        this.sheetName = sheetName;
        Map<String, String> headerToColumn = HEADER_TO_COLUMN_MAP.get(sheetName);
        
        if (headerToColumn == null) {
            throw new IllegalArgumentException("Unknown sheet name: " + sheetName);
        }

        // Build header index map and column order
        this.headerIndexMap = new HashMap<>();
        this.indexToDbColumn = new HashMap<>();
        this.dbColumnOrder = headerToColumn.values().stream().distinct().collect(Collectors.toList());

        // Map Excel headers to indices
        for (int i = 0; i < excelHeaders.size(); i++) {
            String header = excelHeaders.get(i).trim();
            String dbColumn = headerToColumn.get(header);
            if (dbColumn != null) {
                headerIndexMap.put(header, i);
                indexToDbColumn.put(i, dbColumn);
            }
        }
    }

    /**
     * Get normalized value for a column
     */
    public String normalizeValue(String excelHeader, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String dbColumn = HEADER_TO_COLUMN_MAP.get(sheetName).get(excelHeader);
        if (dbColumn == null) {
            return value.trim();
        }

        // Normalize dates
        // Support both legacy (Vietnamese) and new English column names
        if (dbColumn.contains("ngay") || dbColumn.contains("thang")
                || dbColumn.endsWith("_date") || dbColumn.equals("incident_month")
                || dbColumn.contains("_entry_date") || dbColumn.contains("_transfer_date")) {
            return normalizeDate(value);
        }

        // Normalize numbers (remove commas, spaces)
        if (dbColumn.contains("so_luong") || dbColumn.contains("thoi_han")
                || dbColumn.equals("folder_quantity") || dbColumn.equals("retention_period_years")
                || dbColumn.equals("row") || dbColumn.equals("column")) {
            return normalizeNumber(value);
        }

        // Default: trim whitespace
        return value.trim();
    }

    /**
     * Normalize date string to yyyy-MM-dd or yyyy-MM format
     */
    private String normalizeDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();

        // Try parsing as LocalDate
        try {
            LocalDate date = typeConverter.convert(trimmed, LocalDate.class);
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            // Try parsing as YearMonth (for "Tháng phát sinh")
            try {
                YearMonth yearMonth = YearMonth.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM"));
                return yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (Exception e2) {
                // Return as-is if can't parse
                log.warn("Unable to normalize date: {}", trimmed);
                return trimmed;
            }
        }
    }

    /**
     * Normalize number string (remove commas, spaces)
     */
    private String normalizeNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim().replaceAll("[,\\s]", "");
    }

    /**
     * Get DB column name for Excel header
     */
    public String getDbColumn(String excelHeader) {
        return HEADER_TO_COLUMN_MAP.get(sheetName).get(excelHeader);
    }

    /**
     * Get ordered list of DB columns for INSERT statement
     */
    public List<String> getDbColumnOrder() {
        return dbColumnOrder;
    }

    /**
     * Get value for a specific DB column from row data
     */
    public String getValueForColumn(List<String> rowData, String dbColumn) {
        // Find Excel header that maps to this DB column
        for (Map.Entry<String, Integer> headerEntry : headerIndexMap.entrySet()) {
            String excelHeader = headerEntry.getKey();
            String mappedDbColumn = HEADER_TO_COLUMN_MAP.get(sheetName).get(excelHeader);
            if (mappedDbColumn != null && mappedDbColumn.equals(dbColumn)) {
                Integer index = headerEntry.getValue();
                if (index != null && index < rowData.size()) {
                    return normalizeValue(excelHeader, rowData.get(index));
                }
            }
        }
        
        return null;
    }

    /**
     * Generate business key based on sheet-specific logic
     */
    public String generateBusinessKey(List<String> rowData) {
        if ("HSBG_theo_hop_dong".equals(sheetName)) {
            String soHopDong = getValue(rowData, "Số hợp đồng");
            String loaiHoSo = getValue(rowData, "Loại hồ sơ");
            String ngayGiaiNgan = getValue(rowData, "Ngày giải ngân");
            String soCif = getValue(rowData, "Số CIF/ CCCD/ CMT khách hàng");
            String maDonVi = getValue(rowData, "Mã đơn vị");
            
            // Business key logic from HopDongDTO.generateBusinessKey()
            if (isLoanType(loaiHoSo)) {
                return String.format("%s_%s_%s", soHopDong, loaiHoSo, ngayGiaiNgan);
            } else if (isCreditCardType(loaiHoSo)) {
                return String.format("%s_%s_%s", soHopDong, loaiHoSo, soCif);
            } else if ("TTK".equals(loaiHoSo)) {
                return String.format("%s_%s_%s_%s_%s", soHopDong, loaiHoSo, soCif, maDonVi, ngayGiaiNgan);
            }
            return String.format("%s_%s", soHopDong != null ? soHopDong : "", loaiHoSo != null ? loaiHoSo : "");
            
        } else if ("HSBG_theo_CIF".equals(sheetName)) {
            String soCif = getValue(rowData, "Số CIF khách hàng");
            String ngayGiaiNgan = getValue(rowData, "Ngày giải ngân");
            String loaiHoSo = getValue(rowData, "Loại hồ sơ");
            return String.format("%s_%s_%s", soCif, ngayGiaiNgan, loaiHoSo);
            
        } else if ("HSBG_theo_tap".equals(sheetName)) {
            String maDonVi = getValue(rowData, "Mã đơn vị");
            String trachNhiem = getValue(rowData, "Trách nhiệm bàn giao");
            String thangPhatSinh = getValue(rowData, "Tháng phát sinh");
            String sanPham = getValue(rowData, "Sản phẩm");
            return String.format("%s_%s_%s_%s", maDonVi, trachNhiem, thangPhatSinh, sanPham);
        }
        
        return "";
    }

    private String getValue(List<String> rowData, String excelHeader) {
        Integer index = headerIndexMap.get(excelHeader);
        if (index != null && index < rowData.size()) {
            return rowData.get(index);
        }
        return "";
    }

    private boolean isLoanType(String loaiHoSo) {
        if (loaiHoSo == null) return false;
        return loaiHoSo.equals("LD") || loaiHoSo.equals("MD") || loaiHoSo.equals("OD") ||
               loaiHoSo.equals("HDHM") || loaiHoSo.equals("KSSV") ||
               loaiHoSo.equals("Bao thanh toán") || loaiHoSo.equals("Biên nhận thế chấp");
    }

    private boolean isCreditCardType(String loaiHoSo) {
        if (loaiHoSo == null) return false;
        return loaiHoSo.equals("CC") || loaiHoSo.equals("TSBD");
    }
}

