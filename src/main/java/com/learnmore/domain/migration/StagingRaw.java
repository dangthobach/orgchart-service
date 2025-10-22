package com.learnmore.domain.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bảng staging lưu trữ dữ liệu thô từ Excel
 */
@Entity
@Table(name = "staging_raw", indexes = {
    @Index(name = "idx_staging_raw_job", columnList = "job_id"),
    @Index(name = "idx_staging_raw_row", columnList = "job_id, row_num"),
    @Index(name = "idx_staging_raw_business_key", columnList = "job_id, ma_don_vi_norm, ma_thung_norm, ngay_chung_tu_norm, so_luong_tap_norm")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagingRaw {
    
    @Id
    private UUID id;
    
    @Column(name = "job_id", length = 50, nullable = false)
    private String jobId;
    
    @Column(name = "row_num", nullable = false)
    private Integer rowNum;
    
    @Column(name = "sheet_name", length = 100)
    private String sheetName;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Dữ liệu gốc từ Excel
    @Column(name = "kho_vpbank", length = 200)
    private String khoVpbank;
    
    @Column(name = "ma_don_vi", length = 100)
    private String maDonVi;
    
    @Column(name = "trach_nhiem_ban_giao", length = 200)
    private String trachNhiemBanGiao;
    
    @Column(name = "loai_chung_tu", length = 200)
    private String loaiChungTu;
    
    @Column(name = "ngay_chung_tu", length = 50)
    private String ngayChungTu;
    
    @Column(name = "ten_tap", length = 500)
    private String tenTap;
    
    @Column(name = "so_luong_tap", length = 50)
    private String soLuongTap;
    
    @Column(name = "ngay_phai_ban_giao", length = 50)
    private String ngayPhaiBanGiao;
    
    @Column(name = "ngay_ban_giao", length = 50)
    private String ngayBanGiao;
    
    @Column(name = "tinh_trang_that_lac", length = 100)
    private String tinhTrangThatLac;
    
    @Column(name = "tinh_trang_khong_hoan_tra", length = 100)
    private String tinhTrangKhongHoanTra;
    
    @Column(name = "trang_thai_case_pdm", length = 100)
    private String trangThaiCasePdm;
    
    @Column(name = "ghi_chu_case_pdm", length = 2000)
    private String ghiChuCasePdm;
    
    @Column(name = "ma_thung", length = 100)
    private String maThung;
    
    @Column(name = "thoi_han_luu_tru", length = 50)
    private String thoiHanLuuTru;
    
    @Column(name = "ngay_nhap_kho_vpbank", length = 50)
    private String ngayNhapKhoVpbank;
    
    @Column(name = "ngay_chuyen_kho_crown", length = 50)
    private String ngayChuyenKhoCrown;
    
    @Column(name = "khu_vuc", length = 100)
    private String khuVuc;
    
    @Column(name = "hang", length = 50)
    private String hang;
    
    @Column(name = "cot", length = 50)
    private String cot;
    
    @Column(name = "tinh_trang_thung", length = 100)
    private String tinhTrangThung;
    
    @Column(name = "trang_thai_thung", length = 100)
    private String trangThaiThung;
    
    @Column(name = "luu_y", length = 2000)
    private String luuY;
    
    // Dữ liệu đã chuẩn hóa
    @Column(name = "kho_vpbank_norm", length = 200)
    private String khoVpbankNorm;
    
    @Column(name = "ma_don_vi_norm", length = 100)
    private String maDonViNorm;
    
    @Column(name = "loai_chung_tu_norm", length = 200)
    private String loaiChungTuNorm;
    
    @Column(name = "ngay_chung_tu_norm", length = 50)
    private String ngayChungTuNorm;
    
    @Column(name = "so_luong_tap_norm", length = 50)
    private String soLuongTapNorm;
    
    @Column(name = "ma_thung_norm", length = 100)
    private String maThungNorm;
    
    @Column(name = "thoi_han_luu_tru_norm", length = 50)
    private String thoiHanLuuTruNorm;
    
    @Column(name = "khu_vuc_norm", length = 100)
    private String khuVucNorm;
    
    @Column(name = "hang_norm", length = 50)
    private String hangNorm;
    
    @Column(name = "cot_norm", length = 50)
    private String cotNorm;
    
    // Lỗi parse nếu có
    @Column(name = "parse_errors", length = 4000)
    private String parseErrors;
    
    // Thông tin lỗi validation
    @Column(name = "error_message", length = 4000)
    private String errorMessage;
    
    @Column(name = "error_code", length = 1000)
    private String errorCode;
}
