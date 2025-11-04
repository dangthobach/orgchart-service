package com.learnmore.application.dto.migration.sheet;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * DTO cho sheet "HSBG_theo_tap"
 * Mapping các cột Excel theo business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TapDTO {

    @ExcelColumn(name = "Kho VPBank", index = 0, required = true)
    private String khoVpbank;

    @ExcelColumn(name = "Mã đơn vị", index = 1, required = true)
    private String maDonVi;

    @ExcelColumn(name = "Trách nhiệm bàn giao", index = 2, required = true)
    private String trachNhiemBanGiao;

    @ExcelColumn(name = "Tháng phát sinh", index = 3, required = true, dateFormat = "yyyy-MM")
    private YearMonth thangPhatSinh;

    @ExcelColumn(name = "Tên tập", index = 4)
    private String tenTap;

    @ExcelColumn(name = "Số lượng tập", index = 5, required = true)
    private Integer soLuongTap;

    @ExcelColumn(name = "Ngày phải bàn giao", index = 6, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayPhaiBanGiao;

    @ExcelColumn(name = "Ngày bàn giao", index = 7, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayBanGiao;

    @ExcelColumn(name = "Loại hồ sơ", index = 8, required = true)
    private String loaiHoSo;

    @ExcelColumn(name = "Luồng hồ sơ", index = 9, required = true)
    private String luongHoSo;

    @ExcelColumn(name = "Phân hạn cấp TD", index = 10, required = true)
    private String phanHanCapTd;

    @ExcelColumn(name = "Ngày dự kiến tiêu hủy", index = 11, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate ngayDuKienTieuHuy;

    @ExcelColumn(name = "Sản phẩm", index = 12, required = true)
    private String sanPham;

    @ExcelColumn(name = "Trạng thái case PDM", index = 13)
    private String trangThaiCasePdm;

    @ExcelColumn(name = "Ghi chú", index = 14)
    private String ghiChu;

    @ExcelColumn(name = "Mã thùng", index = 15, required = true)
    private String maThung;

    @ExcelColumn(name = "Ngày nhập kho VPBank", index = 16, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayNhapKhoVpbank;

    @ExcelColumn(name = "Ngày chuyển kho Crown", index = 17, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayChuyenKhoCrown;

    @ExcelColumn(name = "Khu vực", index = 18)
    private String khuVuc;

    @ExcelColumn(name = "Hàng", index = 19)
    private Integer hang;

    @ExcelColumn(name = "Cột", index = 20)
    private Integer cot;

    @ExcelColumn(name = "Tình trạng thùng", index = 21)
    private String tinhTrangThung;

    @ExcelColumn(name = "Trạng thái thùng", index = 22)
    private String trangThaiThung;

    // Transient fields for validation
    private transient String validationErrors;
    private transient boolean isValid = true;

    /**
     * Business key for duplicate check
     * CT1: Check trùng dữ liệu key (Mã DV + TNBG + Tháng phát sinh + Sản phẩm)
     */
    public String generateBusinessKey() {
        return String.format("%s_%s_%s_%s", maDonVi, trachNhiemBanGiao, thangPhatSinh, sanPham);
    }

    /**
     * Validate loaiHoSo must be "KSSV"
     * CT2: Kiểm tra dữ liệu "Loại hồ sơ" = "KSSV"
     */
    public boolean isValidLoaiHoSo() {
        return "KSSV".equals(loaiHoSo);
    }

    /**
     * Validate luongHoSo must be "HSTD thường"
     * CT3: Kiểm tra dữ liệu "Luồng hồ sơ" = "HSTD thường"
     */
    public boolean isValidLuongHoSo() {
        return "HSTD thường".equals(luongHoSo);
    }

    /**
     * Validate phanHanCapTd must be "Vĩnh viễn"
     * CT4: Kiểm tra dữ liệu "Phân hạn cấp TD" = "Vĩnh viễn"
     */
    public boolean isValidPhanHanCapTd() {
        return "Vĩnh viễn".equals(phanHanCapTd);
    }

    /**
     * Validate ngayDuKienTieuHuy must be "31-Dec-9999"
     * CT5: Kiểm tra dữ liệu "Ngày dự kiến tiêu hủy" = "31-Dec-9999"
     */
    public boolean isValidNgayDuKienTieuHuy() {
        if (ngayDuKienTieuHuy == null) return false;
        return ngayDuKienTieuHuy.equals(LocalDate.of(9999, 12, 31));
    }

    /**
     * Validate sanPham must be "KSSV"
     * CT6: Kiểm tra dữ liệu "Sản phẩm" = "KSSV"
     */
    public boolean isValidSanPham() {
        return "KSSV".equals(sanPham);
    }
}
