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
        hopDongMap.put("Kho VPBank", "kho_vpbank_norm");
        hopDongMap.put("Mã đơn vị", "ma_don_vi_norm");
        hopDongMap.put("Trách nhiệm bàn giao", "trach_nhiem_ban_giao");
        hopDongMap.put("Số hợp đồng", "so_hop_dong_norm");
        hopDongMap.put("Tên tập", "ten_tap");
        hopDongMap.put("Số lượng tập", "so_luong_tap_norm");
        hopDongMap.put("Số CIF/ CCCD/ CMT khách hàng", "so_cif_norm");
        hopDongMap.put("Tên khách hàng", "ten_khach_hang");
        hopDongMap.put("Phân khúc khách hàng", "phan_khuc_khach_hang");
        hopDongMap.put("Ngày phải bàn giao", "ngay_phai_ban_giao_norm");
        hopDongMap.put("Ngày bàn giao", "ngay_ban_giao_norm");
        hopDongMap.put("Ngày giải ngân", "ngay_giai_ngan_norm");
        hopDongMap.put("Ngày đến hạn", "ngay_den_han_norm");
        hopDongMap.put("Loại hồ sơ", "loai_ho_so_norm");
        hopDongMap.put("Luồng hồ sơ", "luong_ho_so");
        hopDongMap.put("Phân hạn cấp TD", "phan_han_cap_td_norm");
        hopDongMap.put("Ngày dự kiến tiêu hủy", "ngay_du_kien_tieu_huy_norm");
        hopDongMap.put("Sản phẩm", "san_pham");
        hopDongMap.put("Trạng thái case PDM", "trang_thai_case_pdm");
        hopDongMap.put("Ghi chú", "ghi_chu_case_pdm");
        hopDongMap.put("Mã thùng", "ma_thung_norm");
        hopDongMap.put("Ngày nhập kho VPBank", "ngay_nhap_kho_vpbank_norm");
        hopDongMap.put("Ngày chuyển kho Crown", "ngay_chuyen_kho_crown_norm");
        hopDongMap.put("Khu vực", "khu_vuc_norm");
        hopDongMap.put("Hàng", "hang_norm");
        hopDongMap.put("Cột", "cot_norm");
        hopDongMap.put("Tình trạng thùng", "tinh_trang_thung");
        hopDongMap.put("Trạng thái thùng", "trang_thai_thung");
        hopDongMap.put("Thời hạn cấp TD", "thoi_han_cap_td_norm");
        hopDongMap.put("Mã DAO", "ma_dao");
        hopDongMap.put("Mã TS", "ma_ts");
        hopDongMap.put("RRT.ID", "rrt_id");
        hopDongMap.put("Mã NQ", "ma_nq");
        HEADER_TO_COLUMN_MAP.put("HSBG_theo_hop_dong", hopDongMap);

        // HSBG_theo_CIF mapping
        Map<String, String> cifMap = new HashMap<>();
        cifMap.put("Kho VPBank", "kho_vpbank_norm");
        cifMap.put("Mã đơn vị", "ma_don_vi_norm");
        cifMap.put("Trách nhiệm bàn giao", "trach_nhiem_ban_giao");
        cifMap.put("Số CIF khách hàng", "so_cif_norm");
        cifMap.put("Tên khách hàng", "ten_khach_hang");
        cifMap.put("Tên tập", "ten_tap");
        cifMap.put("Số lượng tập", "so_luong_tap_norm");
        cifMap.put("Phân khúc khách hàng", "phan_khuc_khach_hang");
        cifMap.put("Ngày phải bàn giao", "ngay_phai_ban_giao_norm");
        cifMap.put("Ngày bàn giao", "ngay_ban_giao_norm");
        cifMap.put("Ngày giải ngân", "ngay_giai_ngan_norm");
        cifMap.put("Loại hồ sơ", "loai_ho_so_norm");
        cifMap.put("Luồng hồ sơ", "luong_ho_so_norm");
        cifMap.put("Phân hạn cấp TD", "phan_han_cap_td_norm");
        cifMap.put("Sản phẩm", "san_pham");
        cifMap.put("Trạng thái case PDM", "trang_thai_case_pdm");
        cifMap.put("Ghi chú", "ghi_chu_case_pdm");
        cifMap.put("Mã NQ", "ma_nq");
        cifMap.put("Mã thùng", "ma_thung_norm");
        cifMap.put("Ngày nhập kho VPBank", "ngay_nhap_kho_vpbank_norm");
        cifMap.put("Ngày chuyển kho Crown", "ngay_chuyen_kho_crown_norm");
        cifMap.put("Khu vực", "khu_vuc_norm");
        cifMap.put("Hàng", "hang_norm");
        cifMap.put("Cột", "cot_norm");
        cifMap.put("Tình trạng thùng", "tinh_trang_thung");
        cifMap.put("Trạng thái thùng", "trang_thai_thung");
        HEADER_TO_COLUMN_MAP.put("HSBG_theo_CIF", cifMap);

        // HSBG_theo_tap mapping
        Map<String, String> tapMap = new HashMap<>();
        tapMap.put("Kho VPBank", "kho_vpbank_norm");
        tapMap.put("Mã đơn vị", "ma_don_vi_norm");
        tapMap.put("Trách nhiệm bàn giao", "trach_nhiem_ban_giao_norm");
        tapMap.put("Tháng phát sinh", "thang_phat_sinh_norm");
        tapMap.put("Tên tập", "ten_tap");
        tapMap.put("Số lượng tập", "so_luong_tap_norm");
        tapMap.put("Ngày phải bàn giao", "ngay_phai_ban_giao_norm");
        tapMap.put("Ngày bàn giao", "ngay_ban_giao_norm");
        tapMap.put("Loại hồ sơ", "loai_ho_so_norm");
        tapMap.put("Luồng hồ sơ", "luong_ho_so_norm");
        tapMap.put("Phân hạn cấp TD", "phan_han_cap_td_norm");
        tapMap.put("Ngày dự kiến tiêu hủy", "ngay_du_kien_tieu_huy_norm");
        tapMap.put("Sản phẩm", "san_pham_norm");
        tapMap.put("Trạng thái case PDM", "trang_thai_case_pdm");
        tapMap.put("Ghi chú", "ghi_chu_case_pdm");
        tapMap.put("Mã thùng", "ma_thung_norm");
        tapMap.put("Ngày nhập kho VPBank", "ngay_nhap_kho_vpbank_norm");
        tapMap.put("Ngày chuyển kho Crown", "ngay_chuyen_kho_crown_norm");
        tapMap.put("Khu vực", "khu_vuc_norm");
        tapMap.put("Hàng", "hang_norm");
        tapMap.put("Cột", "cot_norm");
        tapMap.put("Tình trạng thùng", "tinh_trang_thung");
        tapMap.put("Trạng thái thùng", "trang_thai_thung");
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
        if (dbColumn.contains("ngay") || dbColumn.contains("thang")) {
            return normalizeDate(value);
        }

        // Normalize numbers (remove commas, spaces)
        if (dbColumn.contains("so_luong") || dbColumn.contains("thoi_han")) {
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

