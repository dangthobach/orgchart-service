-- ================================================================
-- Multi-Sheet Staging Tables for Excel Migration
-- Supports 3 sheet types: HopDong, CIF, Tap
-- ================================================================

-- ----------------------------------------------------------------
-- 1. staging_raw_hopd (HSBG_theo_hop_dong)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging_raw_hopd (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    row_num INTEGER NOT NULL,
    sheet_name VARCHAR(100) DEFAULT 'HSBG_theo_hop_dong',

    -- Raw data columns (normalized)
    kho_vpbank_norm VARCHAR(255),
    ma_don_vi_norm VARCHAR(100),
    trach_nhiem_ban_giao VARCHAR(255),
    so_hop_dong_norm VARCHAR(255),
    ten_tap VARCHAR(500),
    so_luong_tap_norm VARCHAR(20),
    so_cif_norm VARCHAR(100),
    ten_khach_hang VARCHAR(500),
    phan_khuc_khach_hang VARCHAR(100),
    ngay_phai_ban_giao_norm VARCHAR(50),
    ngay_ban_giao_norm VARCHAR(50),
    ngay_giai_ngan_norm VARCHAR(50),
    ngay_den_han_norm VARCHAR(50),
    loai_ho_so_norm VARCHAR(100),
    luong_ho_so VARCHAR(100),
    phan_han_cap_td_norm VARCHAR(50),
    ngay_du_kien_tieu_huy_norm VARCHAR(50),
    san_pham VARCHAR(255),
    trang_thai_case_pdm VARCHAR(100),
    ghi_chu_case_pdm TEXT,
    ma_thung_norm VARCHAR(255),
    ngay_nhap_kho_vpbank_norm VARCHAR(50),
    ngay_chuyen_kho_crown_norm VARCHAR(50),
    khu_vuc_norm VARCHAR(100),
    hang_norm VARCHAR(20),
    cot_norm VARCHAR(20),
    tinh_trang_thung VARCHAR(100),
    trang_thai_thung VARCHAR(100),
    thoi_han_cap_td_norm VARCHAR(20),
    ma_dao VARCHAR(100),
    ma_ts VARCHAR(100),
    rrt_id VARCHAR(100),
    ma_nq VARCHAR(100),

    -- Metadata
    parse_errors TEXT,
    business_key VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    CONSTRAINT staging_raw_hopd_unique UNIQUE (job_id, row_num)
);

CREATE INDEX idx_staging_raw_hopd_job ON staging_raw_hopd(job_id);
CREATE INDEX idx_staging_raw_hopd_business_key ON staging_raw_hopd(job_id, business_key);
CREATE INDEX idx_staging_raw_hopd_so_hop_dong ON staging_raw_hopd(job_id, so_hop_dong_norm);

-- ----------------------------------------------------------------
-- 2. staging_raw_cif (HSBG_theo_CIF)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging_raw_cif (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    row_num INTEGER NOT NULL,
    sheet_name VARCHAR(100) DEFAULT 'HSBG_theo_CIF',

    -- Raw data columns (normalized)
    kho_vpbank_norm VARCHAR(255),
    ma_don_vi_norm VARCHAR(100),
    trach_nhiem_ban_giao VARCHAR(255),
    so_cif_norm VARCHAR(100),
    ten_khach_hang VARCHAR(500),
    ten_tap VARCHAR(500),
    so_luong_tap_norm VARCHAR(20),
    phan_khuc_khach_hang VARCHAR(100),
    ngay_phai_ban_giao_norm VARCHAR(50),
    ngay_ban_giao_norm VARCHAR(50),
    ngay_giai_ngan_norm VARCHAR(50),
    loai_ho_so_norm VARCHAR(100),
    luong_ho_so_norm VARCHAR(100),
    phan_han_cap_td_norm VARCHAR(50),
    san_pham VARCHAR(255),
    trang_thai_case_pdm VARCHAR(100),
    ghi_chu_case_pdm TEXT,
    ma_nq VARCHAR(100),
    ma_thung_norm VARCHAR(255),
    ngay_nhap_kho_vpbank_norm VARCHAR(50),
    ngay_chuyen_kho_crown_norm VARCHAR(50),
    khu_vuc_norm VARCHAR(100),
    hang_norm VARCHAR(20),
    cot_norm VARCHAR(20),
    tinh_trang_thung VARCHAR(100),
    trang_thai_thung VARCHAR(100),

    -- Metadata
    parse_errors TEXT,
    business_key VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    CONSTRAINT staging_raw_cif_unique UNIQUE (job_id, row_num)
);

CREATE INDEX idx_staging_raw_cif_job ON staging_raw_cif(job_id);
CREATE INDEX idx_staging_raw_cif_business_key ON staging_raw_cif(job_id, business_key);
CREATE INDEX idx_staging_raw_cif_so_cif ON staging_raw_cif(job_id, so_cif_norm);

-- ----------------------------------------------------------------
-- 3. staging_raw_tap (HSBG_theo_tap)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging_raw_tap (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    row_num INTEGER NOT NULL,
    sheet_name VARCHAR(100) DEFAULT 'HSBG_theo_tap',

    -- Raw data columns (normalized)
    kho_vpbank_norm VARCHAR(255),
    ma_don_vi_norm VARCHAR(100),
    trach_nhiem_ban_giao_norm VARCHAR(255),
    thang_phat_sinh_norm VARCHAR(50),
    ten_tap VARCHAR(500),
    so_luong_tap_norm VARCHAR(20),
    ngay_phai_ban_giao_norm VARCHAR(50),
    ngay_ban_giao_norm VARCHAR(50),
    loai_ho_so_norm VARCHAR(100),
    luong_ho_so_norm VARCHAR(100),
    phan_han_cap_td_norm VARCHAR(50),
    ngay_du_kien_tieu_huy_norm VARCHAR(50),
    san_pham_norm VARCHAR(255),
    trang_thai_case_pdm VARCHAR(100),
    ghi_chu_case_pdm TEXT,
    ma_thung_norm VARCHAR(255),
    ngay_nhap_kho_vpbank_norm VARCHAR(50),
    ngay_chuyen_kho_crown_norm VARCHAR(50),
    khu_vuc_norm VARCHAR(100),
    hang_norm VARCHAR(20),
    cot_norm VARCHAR(20),
    tinh_trang_thung VARCHAR(100),
    trang_thai_thung VARCHAR(100),

    -- Metadata
    parse_errors TEXT,
    business_key VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    CONSTRAINT staging_raw_tap_unique UNIQUE (job_id, row_num)
);

CREATE INDEX idx_staging_raw_tap_job ON staging_raw_tap(job_id);
CREATE INDEX idx_staging_raw_tap_business_key ON staging_raw_tap(job_id, business_key);
CREATE INDEX idx_staging_raw_tap_ma_don_vi ON staging_raw_tap(job_id, ma_don_vi_norm);

-- ----------------------------------------------------------------
-- 4. staging_valid_hopd (Validated HopDong records)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging_valid_hopd (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    row_num INTEGER NOT NULL,
    sheet_name VARCHAR(100) DEFAULT 'HSBG_theo_hop_dong',

    -- Validated & typed data
    kho_vpbank VARCHAR(255) NOT NULL,
    ma_don_vi VARCHAR(100) NOT NULL,
    trach_nhiem_ban_giao VARCHAR(255),
    so_hop_dong VARCHAR(255) NOT NULL,
    ten_tap VARCHAR(500),
    so_luong_tap INTEGER NOT NULL,
    so_cif VARCHAR(100),
    ten_khach_hang VARCHAR(500),
    phan_khuc_khach_hang VARCHAR(100),
    ngay_phai_ban_giao DATE,
    ngay_ban_giao DATE,
    ngay_giai_ngan DATE NOT NULL,
    ngay_den_han DATE,
    loai_ho_so VARCHAR(100) NOT NULL,
    luong_ho_so VARCHAR(100),
    phan_han_cap_td VARCHAR(50) NOT NULL,
    ngay_du_kien_tieu_huy DATE,
    san_pham VARCHAR(255),
    trang_thai_case_pdm VARCHAR(100),
    ghi_chu_case_pdm TEXT,
    ma_thung VARCHAR(255) NOT NULL,
    ngay_nhap_kho_vpbank DATE,
    ngay_chuyen_kho_crown DATE,
    khu_vuc VARCHAR(100),
    hang INTEGER,
    cot INTEGER,
    tinh_trang_thung VARCHAR(100),
    trang_thai_thung VARCHAR(100),
    thoi_han_cap_td INTEGER,
    ma_dao VARCHAR(100),
    ma_ts VARCHAR(100),
    rrt_id VARCHAR(100),
    ma_nq VARCHAR(100),

    business_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT staging_valid_hopd_unique UNIQUE (job_id, row_num)
);

CREATE INDEX idx_staging_valid_hopd_job ON staging_valid_hopd(job_id);
CREATE INDEX idx_staging_valid_hopd_business_key ON staging_valid_hopd(job_id, business_key);

-- ----------------------------------------------------------------
-- 5. staging_valid_cif (Validated CIF records)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging_valid_cif (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    row_num INTEGER NOT NULL,
    sheet_name VARCHAR(100) DEFAULT 'HSBG_theo_CIF',

    -- Validated & typed data
    kho_vpbank VARCHAR(255) NOT NULL,
    ma_don_vi VARCHAR(100) NOT NULL,
    trach_nhiem_ban_giao VARCHAR(255),
    so_cif VARCHAR(100) NOT NULL,
    ten_khach_hang VARCHAR(500),
    ten_tap VARCHAR(500),
    so_luong_tap INTEGER NOT NULL,
    phan_khuc_khach_hang VARCHAR(100),
    ngay_phai_ban_giao DATE,
    ngay_ban_giao DATE,
    ngay_giai_ngan DATE NOT NULL,
    loai_ho_so VARCHAR(100) NOT NULL,
    luong_ho_so VARCHAR(100) NOT NULL,
    phan_han_cap_td VARCHAR(50) NOT NULL,
    san_pham VARCHAR(255),
    trang_thai_case_pdm VARCHAR(100),
    ghi_chu_case_pdm TEXT,
    ma_nq VARCHAR(100),
    ma_thung VARCHAR(255) NOT NULL,
    ngay_nhap_kho_vpbank DATE,
    ngay_chuyen_kho_crown DATE,
    khu_vuc VARCHAR(100),
    hang INTEGER,
    cot INTEGER,
    tinh_trang_thung VARCHAR(100),
    trang_thai_thung VARCHAR(100),

    business_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT staging_valid_cif_unique UNIQUE (job_id, row_num)
);

CREATE INDEX idx_staging_valid_cif_job ON staging_valid_cif(job_id);
CREATE INDEX idx_staging_valid_cif_business_key ON staging_valid_cif(job_id, business_key);

-- ----------------------------------------------------------------
-- 6. staging_valid_tap (Validated Tap records)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging_valid_tap (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    row_num INTEGER NOT NULL,
    sheet_name VARCHAR(100) DEFAULT 'HSBG_theo_tap',

    -- Validated & typed data
    kho_vpbank VARCHAR(255) NOT NULL,
    ma_don_vi VARCHAR(100) NOT NULL,
    trach_nhiem_ban_giao VARCHAR(255) NOT NULL,
    thang_phat_sinh DATE NOT NULL,
    ten_tap VARCHAR(500),
    so_luong_tap INTEGER NOT NULL,
    ngay_phai_ban_giao DATE,
    ngay_ban_giao DATE,
    loai_ho_so VARCHAR(100) NOT NULL,
    luong_ho_so VARCHAR(100) NOT NULL,
    phan_han_cap_td VARCHAR(50) NOT NULL,
    ngay_du_kien_tieu_huy DATE NOT NULL,
    san_pham VARCHAR(255) NOT NULL,
    trang_thai_case_pdm VARCHAR(100),
    ghi_chu_case_pdm TEXT,
    ma_thung VARCHAR(255) NOT NULL,
    ngay_nhap_kho_vpbank DATE,
    ngay_chuyen_kho_crown DATE,
    khu_vuc VARCHAR(100),
    hang INTEGER,
    cot INTEGER,
    tinh_trang_thung VARCHAR(100),
    trang_thai_thung VARCHAR(100),

    business_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT staging_valid_tap_unique UNIQUE (job_id, row_num)
);

CREATE INDEX idx_staging_valid_tap_job ON staging_valid_tap(job_id);
CREATE INDEX idx_staging_valid_tap_business_key ON staging_valid_tap(job_id, business_key);

-- ----------------------------------------------------------------
-- 7. staging_error_multisheet (Errors for all sheets)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staging_error_multisheet (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    sheet_name VARCHAR(100) NOT NULL,
    row_num INTEGER NOT NULL,
    error_type VARCHAR(50) NOT NULL,
    error_field VARCHAR(100),
    error_value TEXT,
    error_message TEXT NOT NULL,
    error_code VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    original_data TEXT
);

CREATE INDEX idx_staging_error_ms_job ON staging_error_multisheet(job_id);
CREATE INDEX idx_staging_error_ms_sheet ON staging_error_multisheet(job_id, sheet_name);
CREATE INDEX idx_staging_error_ms_type ON staging_error_multisheet(job_id, error_type);

-- ----------------------------------------------------------------
-- 8. migration_job_sheet (Track per-sheet progress)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_job_sheet (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    sheet_name VARCHAR(100) NOT NULL,
    sheet_order INTEGER NOT NULL,

    -- Status tracking
    status VARCHAR(50) DEFAULT 'PENDING',
    current_phase VARCHAR(100),
    progress_percent DECIMAL(5,2) DEFAULT 0.00,

    -- Counters
    total_rows BIGINT DEFAULT 0,
    ingested_rows BIGINT DEFAULT 0,
    valid_rows BIGINT DEFAULT 0,
    error_rows BIGINT DEFAULT 0,
    inserted_rows BIGINT DEFAULT 0,

    -- Timing
    ingest_start_time TIMESTAMP,
    ingest_end_time TIMESTAMP,
    validation_start_time TIMESTAMP,
    validation_end_time TIMESTAMP,
    insertion_start_time TIMESTAMP,
    insertion_end_time TIMESTAMP,

    -- Error info
    error_message TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT migration_job_sheet_unique UNIQUE (job_id, sheet_name)
);

CREATE INDEX idx_migration_job_sheet_job ON migration_job_sheet(job_id);
CREATE INDEX idx_migration_job_sheet_status ON migration_job_sheet(job_id, status);

-- ----------------------------------------------------------------
-- 9. Add comments for documentation
-- ----------------------------------------------------------------
COMMENT ON TABLE staging_raw_hopd IS 'Raw staging table for HSBG_theo_hop_dong sheet - stores unnormalized Excel data';
COMMENT ON TABLE staging_raw_cif IS 'Raw staging table for HSBG_theo_CIF sheet - stores unnormalized Excel data';
COMMENT ON TABLE staging_raw_tap IS 'Raw staging table for HSBG_theo_tap sheet - stores unnormalized Excel data';
COMMENT ON TABLE staging_valid_hopd IS 'Validated staging table for HopDong records - ready for master table insertion';
COMMENT ON TABLE staging_valid_cif IS 'Validated staging table for CIF records - ready for master table insertion';
COMMENT ON TABLE staging_valid_tap IS 'Validated staging table for Tap records - ready for master table insertion';
COMMENT ON TABLE staging_error_multisheet IS 'Validation errors for all sheet types';
COMMENT ON TABLE migration_job_sheet IS 'Per-sheet progress tracking for multi-sheet migration jobs';
