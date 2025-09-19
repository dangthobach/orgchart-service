package com.learnmore.application.dto.migration;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

/**
 * DTO mapping với các cột trong Excel file
 * Tương ứng với cấu trúc Excel được mô tả
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelRowDTO {
    
    @ExcelColumn("Kho VPBank")
    @NotBlank(message = "Kho VPBank không được để trống")
    private String khoVpbank;
    
    @ExcelColumn("Mã đơn vị")  
    @NotBlank(message = "Mã đơn vị không được để trống")
    private String maDonVi;
    
    @ExcelColumn("Trách nhiệm bàn giao")
    private String trachNhiemBanGiao;
    
    @ExcelColumn("Loại chứng từ")
    @NotBlank(message = "Loại chứng từ không được để trống")
    private String loaiChungTu;
    
    @ExcelColumn("Ngày chứng từ")
    @NotBlank(message = "Ngày chứng từ không được để trống")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}", 
             message = "Ngày chứng từ phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayChungTu;
    
    @ExcelColumn("Tên tập")
    private String tenTap;
    
    @ExcelColumn("Số lượng tập")
    @NotNull(message = "Số lượng tập không được để trống")
    @Positive(message = "Số lượng tập phải là số dương")
    private Integer soLuongTap;
    
    @ExcelColumn("Ngày phải bàn giao")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày phải bàn giao phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayPhaiBanGiao;
    
    @ExcelColumn("Ngày bàn giao")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày bàn giao phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayBanGiao;
    
    @ExcelColumn("Tình trạng thất lạc")
    private String tinhTrangThatLac;
    
    @ExcelColumn("Tình trạng không hoàn trả")
    private String tinhTrangKhongHoanTra;
    
    @ExcelColumn("Trạng thái case PDM")
    private String trangThaiCasePdm;
    
    @ExcelColumn("Ghi chú case PDM")
    private String ghiChuCasePdm;
    
    @ExcelColumn("Mã thùng")
    @NotBlank(message = "Mã thùng không được để trống")
    private String maThung;
    
    @ExcelColumn("Thời hạn lưu trữ")
    @NotNull(message = "Thời hạn lưu trữ không được để trống")
    @Positive(message = "Thời hạn lưu trữ phải là số dương")
    private Integer thoiHanLuuTru;
    
    @ExcelColumn("Ngày nhập kho VPBank")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày nhập kho VPBank phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayNhapKhoVpbank;
    
    @ExcelColumn("Ngày chuyển kho Crown")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày chuyển kho Crown phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayChuyenKhoCrown;
    
    @ExcelColumn("Khu vực")
    private String khuVuc;
    
    @ExcelColumn("Hàng")
    private Integer hang;
    
    @ExcelColumn("Cột")
    private Integer cot;
    
    @ExcelColumn("Tình trạng thùng")
    private String tinhTrangThung;
    
    @ExcelColumn("Trạng thái thùng")
    private String trangThaiThung;
    
    @ExcelColumn("Lưu ý")
    private String luuY;
    
    // Transient fields for internal processing
    private Integer rowNumber;
    private String jobId;
    
    /**
     * Chuẩn hóa dữ liệu sau khi đọc từ Excel
     */
    public void normalize() {
        if (khoVpbank != null) {
            khoVpbank = khoVpbank.trim().toUpperCase();
        }
        if (maDonVi != null) {
            maDonVi = maDonVi.trim().toUpperCase();
        }
        if (loaiChungTu != null) {
            loaiChungTu = loaiChungTu.trim();
        }
        if (maThung != null) {
            maThung = maThung.trim().toUpperCase();
        }
        if (khuVuc != null) {
            khuVuc = khuVuc.trim().toUpperCase();
        }
        if (trachNhiemBanGiao != null) {
            trachNhiemBanGiao = trachNhiemBanGiao.trim();
        }
        if (tinhTrangThatLac != null) {
            tinhTrangThatLac = tinhTrangThatLac.trim();
        }
        if (tinhTrangKhongHoanTra != null) {
            tinhTrangKhongHoanTra = tinhTrangKhongHoanTra.trim();
        }
        if (trangThaiCasePdm != null) {
            trangThaiCasePdm = trangThaiCasePdm.trim();
        }
        if (tinhTrangThung != null) {
            tinhTrangThung = tinhTrangThung.trim();
        }
        if (trangThaiThung != null) {
            trangThaiThung = trangThaiThung.trim();
        }
    }
    
    /**
     * Tạo business key để check duplicate
     */
    public String getBusinessKey() {
        return String.format("%s_%s_%s_%s_%d", 
            maDonVi != null ? maDonVi : "",
            maThung != null ? maThung : "",
            ngayChungTu != null ? ngayChungTu : "",
            tenTap != null ? tenTap : "",
            soLuongTap != null ? soLuongTap : 0
        );
    }
}
