package com.learnmore.domain.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Bảng staging lưu trữ dữ liệu đã validate
 */
@Entity
@Table(name = "staging_valid", indexes = {
    @Index(name = "idx_staging_valid_job", columnList = "job_id"),
    @Index(name = "idx_staging_valid_row", columnList = "job_id, row_num")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagingValid {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_id", length = 50, nullable = false)
    private String jobId;
    
    @Column(name = "row_num", nullable = false) 
    private Integer rowNum;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Dữ liệu đã chuẩn hóa và validate
    @Column(name = "kho_vpbank_norm", length = 200, nullable = false)
    private String khoVpbankNorm;
    
    @Column(name = "ma_don_vi_norm", length = 100, nullable = false)
    private String maDonViNorm;
    
    @Column(name = "trach_nhiem_ban_giao", length = 200)
    private String trachNhiemBanGiao;
    
    @Column(name = "loai_chung_tu_norm", length = 200, nullable = false)
    private String loaiChungTuNorm;
    
    @Column(name = "ngay_chung_tu_norm", length = 10, nullable = false)
    private String ngayChungTuNorm;
    
    @Column(name = "ten_tap", length = 500)
    private String tenTap;
    
    @Column(name = "so_luong_tap_norm", nullable = false)
    private Integer soLuongTapNorm;
    
    @Column(name = "ngay_phai_ban_giao_norm", length = 10)
    private String ngayPhaiBanGiaoNorm;
    
    @Column(name = "ngay_ban_giao_norm", length = 10)
    private String ngayBanGiaoNorm;
    
    @Column(name = "tinh_trang_that_lac_norm", length = 100)
    private String tinhTrangThatLacNorm;
    
    @Column(name = "tinh_trang_khong_hoan_tra_norm", length = 100)
    private String tinhTrangKhongHoanTraNorm;
    
    @Column(name = "trang_thai_case_pdm_norm", length = 100)
    private String trangThaiCasePdmNorm;
    
    @Column(name = "ghi_chu_case_pdm", length = 2000)
    private String ghiChuCasePdm;
    
    @Column(name = "ma_thung_norm", length = 100, nullable = false)
    private String maThungNorm;
    
    @Column(name = "thoi_han_luu_tru_norm", nullable = false)
    private Integer thoiHanLuuTruNorm;
    
    @Column(name = "ngay_nhap_kho_vpbank_norm", length = 10)
    private String ngayNhapKhoVpbankNorm;
    
    @Column(name = "ngay_chuyen_kho_crown_norm", length = 10)
    private String ngayChuyenKhoCrownNorm;
    
    @Column(name = "khu_vuc_norm", length = 100)
    private String khuVucNorm;
    
    @Column(name = "hang_norm")
    private Integer hangNorm;
    
    @Column(name = "cot_norm")
    private Integer cotNorm;
    
    @Column(name = "tinh_trang_thung_norm", length = 100)
    private String tinhTrangThungNorm;
    
    @Column(name = "trang_thai_thung_norm", length = 100)
    private String trangThaiThungNorm;
    
    @Column(name = "luu_y", length = 2000)
    private String luuY;
}
