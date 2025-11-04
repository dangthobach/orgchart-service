package com.learnmore.application.dto.migration.sheet;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO cho sheet "HSBG_theo_CIF"
 * Mapping các cột Excel theo business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CifDTO {

    @ExcelColumn(name = "Kho VPBank", index = 0, required = true)
    private String khoVpbank;

    @ExcelColumn(name = "Mã đơn vị", index = 1, required = true)
    private String maDonVi;

    @ExcelColumn(name = "Trách nhiệm bàn giao", index = 2)
    private String trachNhiemBanGiao;

    @ExcelColumn(name = "Số CIF khách hàng", index = 3, required = true)
    private String soCif;

    @ExcelColumn(name = "Tên khách hàng", index = 4)
    private String tenKhachHang;

    @ExcelColumn(name = "Tên tập", index = 5)
    private String tenTap;

    @ExcelColumn(name = "Số lượng tập", index = 6, required = true)
    private Integer soLuongTap;

    @ExcelColumn(name = "Phân khúc khách hàng", index = 7)
    private String phanKhucKhachHang;

    @ExcelColumn(name = "Ngày phải bàn giao", index = 8, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayPhaiBanGiao;

    @ExcelColumn(name = "Ngày bàn giao", index = 9, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayBanGiao;

    @ExcelColumn(name = "Ngày giải ngân", index = 10, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate ngayGiaiNgan;

    @ExcelColumn(name = "Loại hồ sơ", index = 11, required = true)
    private String loaiHoSo;

    @ExcelColumn(name = "Luồng hồ sơ", index = 12, required = true)
    private String luongHoSo;

    @ExcelColumn(name = "Phân hạn cấp TD", index = 13, required = true)
    private String phanHanCapTd;

    @ExcelColumn(name = "Sản phẩm", index = 14)
    private String sanPham;

    @ExcelColumn(name = "Trạng thái case PDM", index = 15)
    private String trangThaiCasePdm;

    @ExcelColumn(name = "Ghi chú", index = 16)
    private String ghiChu;

    @ExcelColumn(name = "Mã NQ", index = 17)
    private String maNq;

    @ExcelColumn(name = "Mã thùng", index = 18, required = true)
    private String maThung;

    @ExcelColumn(name = "Ngày nhập kho VPBank", index = 19, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayNhapKhoVpbank;

    @ExcelColumn(name = "Ngày chuyển kho Crown", index = 20, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayChuyenKhoCrown;

    @ExcelColumn(name = "Khu vực", index = 21)
    private String khuVuc;

    @ExcelColumn(name = "Hàng", index = 22)
    private Integer hang;

    @ExcelColumn(name = "Cột", index = 23)
    private Integer cot;

    @ExcelColumn(name = "Tình trạng thùng", index = 24)
    private String tinhTrangThung;

    @ExcelColumn(name = "Trạng thái thùng", index = 25)
    private String trangThaiThung;

    // Transient fields for validation
    private transient String validationErrors;
    private transient boolean isValid = true;

    /**
     * Business key for duplicate check
     * CT1: Check trùng dữ liệu key (Số CIF + Ngày giải ngân + Loại HS)
     */
    public String generateBusinessKey() {
        return String.format("%s_%s_%s", soCif, ngayGiaiNgan, loaiHoSo);
    }

    /**
     * Validate luongHoSo must be "HSTD thường"
     * CT2: Kiểm tra dữ liệu "Luồng hồ sơ" = "HSTD thường"
     */
    public boolean isValidLuongHoSo() {
        return "HSTD thường".equals(luongHoSo);
    }

    /**
     * Validate phanHanCapTd must be "Vĩnh viễn"
     * CT3: Kiểm tra dữ liệu "Phân hạn cấp TD" = "Vĩnh viễn"
     */
    public boolean isValidPhanHanCapTd() {
        return "Vĩnh viễn".equals(phanHanCapTd);
    }

    /**
     * Validate loaiHoSo is in allowed list
     * CT4: Kiểm tra "Loại hồ sơ" có nằm trong DS cho phép ko
     */
    public boolean isValidLoaiHoSo() {
        if (loaiHoSo == null) return false;
        return loaiHoSo.equals("PASS TTN") ||
               loaiHoSo.equals("SCF VEERFIN") ||
               loaiHoSo.equals("Trình cấp TD không qua CPC") ||
               loaiHoSo.equals("Hồ sơ mở TKTT nhưng không giải ngân");
    }

    /**
     * Mask sensitive data
     */
    public void maskSensitiveData() {
        if (soCif != null && soCif.length() > 4) {
            soCif = soCif.substring(0, 2) +
                   "*".repeat(soCif.length() - 4) +
                   soCif.substring(soCif.length() - 2);
        }

        if (tenKhachHang != null && tenKhachHang.length() > 4) {
            tenKhachHang = tenKhachHang.substring(0, 2) +
                          "*".repeat(tenKhachHang.length() - 4) +
                          tenKhachHang.substring(tenKhachHang.length() - 2);
        }
    }
}
