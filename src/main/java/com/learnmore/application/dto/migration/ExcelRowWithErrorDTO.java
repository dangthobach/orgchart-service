package com.learnmore.application.dto.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO cho Excel row với thông tin lỗi validation
 * Sử dụng để ghi file lỗi với 2 cột errorMessage và errorCode
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelRowWithErrorDTO {
    
    // Thông tin cơ bản từ ExcelRowDTO
    private Integer rowNumber;
    private String khoVpbank;
    private String maDonVi;
    private String trachNhiemBanGiao;
    private String loaiChungTu;
    private String ngayChungTu;
    private String tenTap;
    private Integer soLuongTap;
    private String ngayPhaiBanGiao;
    private String ngayBanGiao;
    private String tinhTrangThatLac;
    private String tinhTrangKhongHoanTra;
    private String trangThaiCasePdm;
    private String ghiChuCasePdm;
    private String maThung;
    private Integer thoiHanLuuTru;
    private String ngayNhapKhoVpbank;
    private String ngayChuyenKhoCrown;
    private String khuVuc;
    private Integer hang;
    private Integer cot;
    private String tinhTrangThung;
    private String trangThaiThung;
    private String luuY;
    
    // Thông tin lỗi validation
    private String errorMessage;
    private String errorCode;
    
    /**
     * Tạo ExcelRowWithErrorDTO từ ExcelRowDTO và thông tin lỗi
     */
//    public static ExcelRowWithErrorDTO fromExcelRowDTO(ExcelRowDTO excelRow, String errorMessage, String errorCode) {
//        return ExcelRowWithErrorDTO.builder()
//                .rowNumber(excelRow.getRowNumber())
//                .khoVpbank(excelRow.getKhoVpbank())
//                .maDonVi(excelRow.getMaDonVi())
//                .trachNhiemBanGiao(excelRow.getTrachNhiemBanGiao())
//                .loaiChungTu(excelRow.getLoaiChungTu())
//                .ngayChungTu(excelRow.getNgayChungTu())
//                .tenTap(excelRow.getTenTap())
//                .soLuongTap(excelRow.getSoLuongTap())
//                .ngayPhaiBanGiao(excelRow.getNgayPhaiBanGiao())
//                .ngayBanGiao(excelRow.getNgayBanGiao())
//                .tinhTrangThatLac(excelRow.getTinhTrangThatLac())
//                .tinhTrangKhongHoanTra(excelRow.getTinhTrangKhongHoanTra())
//                .trangThaiCasePdm(excelRow.getTrangThaiCasePdm())
//                .ghiChuCasePdm(excelRow.getGhiChuCasePdm())
//                .maThung(excelRow.getMaThung())
//                .thoiHanLuuTru(excelRow.getThoiHanLuuTru())
//                .ngayNhapKhoVpbank(excelRow.getNgayNhapKhoVpbank())
//                .ngayChuyenKhoCrown(excelRow.getNgayChuyenKhoCrown())
//                .khuVuc(excelRow.getKhuVuc())
//                .hang(excelRow.getHang())
//                .cot(excelRow.getCot())
//                .tinhTrangThung(excelRow.getTinhTrangThung())
//                .trangThaiThung(excelRow.getTrangThaiThung())
//                .luuY(excelRow.getLuuY())
//                .errorMessage(errorMessage)
//                .errorCode(errorCode)
//                .build();
//    }
}
