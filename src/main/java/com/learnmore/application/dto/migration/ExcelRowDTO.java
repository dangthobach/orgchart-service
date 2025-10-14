package com.learnmore.application.dto.migration;

import com.learnmore.application.utils.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * DTO mapping với các cột trong Excel file
 * Tương ứng với cấu trúc Excel được mô tả
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelRowDTO {
    
    @ExcelColumn(name = "Kho VPBank", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING,
                description = "Mã kho lưu trữ", example = "VPB001", position = "A")
    @NotBlank(message = "Kho VPBank không được để trống")
    private String khoVpbank;
    
    @ExcelColumn(name = "Mã đơn vị", required = true, maxLength = 20, dataType = ExcelColumn.ColumnType.STRING,
                description = "Mã đơn vị chủ quản", example = "DV001", position = "B")
    @NotBlank(message = "Mã đơn vị không được để trống")
    private String maDonVi;
    
    @ExcelColumn(name = "Trách nhiệm bàn giao", required = false, maxLength = 100, dataType = ExcelColumn.ColumnType.STRING,
                description = "Bộ phận chịu trách nhiệm", example = "Phòng Tài chính", position = "C")
    private String trachNhiemBanGiao;
    
    @ExcelColumn(name = "Loại chứng từ", required = true, maxLength = 100, dataType = ExcelColumn.ColumnType.STRING,
                description = "Loại chứng từ tài liệu", example = "Hợp đồng", position = "D")
    @NotBlank(message = "Loại chứng từ không được để trống")
    private String loaiChungTu;
    
    @ExcelColumn(name = "Ngày chứng từ", required = true, dataType = ExcelColumn.ColumnType.DATE,
                pattern = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}",
                description = "Ngày chứng từ (dd/MM/yyyy hoặc yyyy-MM-dd)", example = "01/01/2024", position = "E")
    @NotBlank(message = "Ngày chứng từ không được để trống")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}", 
             message = "Ngày chứng từ phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayChungTu;
    
    @ExcelColumn(name = "Tên tập", required = false, maxLength = 200, dataType = ExcelColumn.ColumnType.STRING,
                description = "Tên tập/chồng hồ sơ", example = "Hồ sơ hợp đồng 2024", position = "F")
    private String tenTap;
    
    @ExcelColumn(name = "Số lượng tập", required = true, dataType = ExcelColumn.ColumnType.INTEGER,
                description = "Số nguyên dương", example = "5", position = "G")
    @NotNull(message = "Số lượng tập không được để trống")
    @Positive(message = "Số lượng tập phải là số dương")
    private Integer soLuongTap;
    
    @ExcelColumn(name = "Ngày phải bàn giao")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày phải bàn giao phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayPhaiBanGiao;
    
    @ExcelColumn(name = "Ngày bàn giao")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày bàn giao phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayBanGiao;
    
    @ExcelColumn(name = "Tình trạng thất lạc")
    private String tinhTrangThatLac;
    
    @ExcelColumn(name = "Tình trạng không hoàn trả")
    private String tinhTrangKhongHoanTra;
    
    @ExcelColumn(name = "Trạng thái case PDM")
    private String trangThaiCasePdm;
    
    @ExcelColumn(name = "Ghi chú case PDM")
    private String ghiChuCasePdm;
    
    @ExcelColumn(name = "Mã thùng")
    @NotBlank(message = "Mã thùng không được để trống")
    private String maThung;
    
    @ExcelColumn(name = "Thời hạn lưu trữ")
    @NotNull(message = "Thời hạn lưu trữ không được để trống")
    @Positive(message = "Thời hạn lưu trữ phải là số dương")
    private Integer thoiHanLuuTru;
    
    @ExcelColumn(name = "Ngày nhập kho VPBank")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày nhập kho VPBank phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayNhapKhoVpbank;
    
    @ExcelColumn(name = "Ngày chuyển kho Crown")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|^$", 
             message = "Ngày chuyển kho Crown phải có định dạng dd/MM/yyyy hoặc yyyy-MM-dd")
    private String ngayChuyenKhoCrown;
    
    @ExcelColumn(name = "Khu vực")
    private String khuVuc;
    
    @ExcelColumn(name = "Hàng")
    private Integer hang;
    
    @ExcelColumn(name = "Cột")
    private Integer cot;
    
    @ExcelColumn(name = "Tình trạng thùng")
    private String tinhTrangThung;
    
    @ExcelColumn(name = "Trạng thái thùng")
    private String trangThaiThung;
    
    @ExcelColumn(name = "Lưu ý")
    private String luuY;
    
    // Transient fields for internal processing
    private Integer rowNumber;
    private String jobId;
    private Integer rowNum; // Vị trí dòng trong sheet (1-based)
    
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
