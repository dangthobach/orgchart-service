package com.learnmore.application.dto.migration.sheet;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO cho sheet "HSBG_theo_hop_dong"
 * Mapping các cột Excel theo business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HopDongDTO {

    @ExcelColumn(name = "Kho VPBank", index = 0, required = true)
    private String khoVpbank;

    @ExcelColumn(name = "Mã đơn vị", index = 1, required = true)
    private String maDonVi;

    @ExcelColumn(name = "Trách nhiệm bàn giao", index = 2)
    private String trachNhiemBanGiao;

    @ExcelColumn(name = "Số hợp đồng", index = 3, required = true)
    private String soHopDong;

    @ExcelColumn(name = "Tên tập", index = 4)
    private String tenTap;

    @ExcelColumn(name = "Số lượng tập", index = 5, required = true)
    private Integer soLuongTap;

    @ExcelColumn(name = "Số CIF/ CCCD/ CMT khách hàng", index = 6)
    private String soCif;

    @ExcelColumn(name = "Tên khách hàng", index = 7)
    private String tenKhachHang;

    @ExcelColumn(name = "Phân khúc khách hàng", index = 8)
    private String phanKhucKhachHang;

    @ExcelColumn(name = "Ngày phải bàn giao", index = 9, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayPhaiBanGiao;

    @ExcelColumn(name = "Ngày bàn giao", index = 10, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayBanGiao;

    @ExcelColumn(name = "Ngày giải ngân", index = 11, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate ngayGiaiNgan;

    @ExcelColumn(name = "Ngày đến hạn", index = 12, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayDenHan;

    @ExcelColumn(name = "Loại hồ sơ", index = 13, required = true)
    private String loaiHoSo;

    @ExcelColumn(name = "Luồng hồ sơ", index = 14)
    private String luongHoSo;

    @ExcelColumn(name = "Phân hạn cấp TD", index = 15, required = true)
    private String phanHanCapTd;

    @ExcelColumn(name = "Ngày dự kiến tiêu hủy", index = 16, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayDuKienTieuHuy;

    @ExcelColumn(name = "Sản phẩm", index = 17)
    private String sanPham;

    @ExcelColumn(name = "Trạng thái case PDM", index = 18)
    private String trangThaiCasePdm;

    @ExcelColumn(name = "Ghi chú", index = 19)
    private String ghiChu;

    @ExcelColumn(name = "Mã thùng", index = 20, required = true)
    private String maThung;

    @ExcelColumn(name = "Ngày nhập kho VPBank", index = 21, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayNhapKhoVpbank;

    @ExcelColumn(name = "Ngày chuyển kho Crown", index = 22, dateFormat = "yyyy-MM-dd")
    private LocalDate ngayChuyenKhoCrown;

    @ExcelColumn(name = "Khu vực", index = 23)
    private String khuVuc;

    @ExcelColumn(name = "Hàng", index = 24)
    private Integer hang;

    @ExcelColumn(name = "Cột", index = 25)
    private Integer cot;

    @ExcelColumn(name = "Tình trạng thùng", index = 26)
    private String tinhTrangThung;

    @ExcelColumn(name = "Trạng thái thùng", index = 27)
    private String trangThaiThung;

    @ExcelColumn(name = "Thời hạn cấp TD", index = 28)
    private Integer thoiHanCapTd;

    @ExcelColumn(name = "Mã DAO", index = 29)
    private String maDao;

    @ExcelColumn(name = "Mã TS", index = 30)
    private String maTs;

    @ExcelColumn(name = "RRT.ID", index = 31)
    private String rrtId;

    @ExcelColumn(name = "Mã NQ", index = 32)
    private String maNq;

    // Transient fields for validation
    private transient String validationErrors;
    private transient boolean isValid = true;

    /**
     * Business key for duplicate check
     * Rule varies by loaiHoSo
     */
    public String generateBusinessKey() {
        // CT2: Check trùng dữ liệu theo Loại hồ sơ
        if (isLoanType()) {
            // Trùng: Số HD + Loại HS + Ngày giải ngân
            return String.format("%s_%s_%s", soHopDong, loaiHoSo, ngayGiaiNgan);
        } else if (isCreditCardType()) {
            // Trùng: Số HD + Loại HS + Số CIF
            return String.format("%s_%s_%s", soHopDong, loaiHoSo, soCif);
        } else if ("TTK".equals(loaiHoSo)) {
            // Trùng: Số HD + Loại HS + Số CIF + Mã đơn vị + Ngày giải ngân
            return String.format("%s_%s_%s_%s_%s", soHopDong, loaiHoSo, soCif, maDonVi, ngayGiaiNgan);
        }
        return String.format("%s_%s", soHopDong, loaiHoSo);
    }

    /**
     * Check if loaiHoSo is loan type
     * LD, MD, OD, HDHM, KSSV, Bao thanh toán, Biên nhận thế chấp
     */
    private boolean isLoanType() {
        return loaiHoSo != null && (
            loaiHoSo.equals("LD") || loaiHoSo.equals("MD") || loaiHoSo.equals("OD") ||
            loaiHoSo.equals("HDHM") || loaiHoSo.equals("KSSV") ||
            loaiHoSo.equals("Bao thanh toán") || loaiHoSo.equals("Biên nhận thế chấp")
        );
    }

    /**
     * Check if loaiHoSo is credit card type
     * CC, TSBD
     */
    private boolean isCreditCardType() {
        return loaiHoSo != null && (loaiHoSo.equals("CC") || loaiHoSo.equals("TSBD"));
    }

    /**
     * Calculate destruction date based on phanHanCapTd and ngayGiaiNgan
     * CT1: Công thức tính "Ngày dự kiến tiêu hủy"
     */
    public LocalDate calculateDestructionDate() {
        if ("Vĩnh viễn".equals(phanHanCapTd)) {
            return LocalDate.of(9999, 12, 31);
        }

        if (ngayGiaiNgan == null) {
            return LocalDate.of(9999, 12, 31);
        }

        if ("Ngắn hạn".equals(phanHanCapTd)) {
            return ngayGiaiNgan.plusYears(5);
        } else if ("Trung hạn".equals(phanHanCapTd)) {
            return ngayGiaiNgan.plusYears(10);
        } else if ("Dài hạn".equals(phanHanCapTd)) {
            return ngayGiaiNgan.plusYears(15);
        }

        return LocalDate.of(9999, 12, 31);
    }

    /**
     * Mask sensitive data
     */
    public void maskSensitiveData() {
        if (soHopDong != null && soHopDong.length() > 4) {
            soHopDong = soHopDong.substring(0, 2) +
                       "*".repeat(soHopDong.length() - 4) +
                       soHopDong.substring(soHopDong.length() - 2);
        }

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
