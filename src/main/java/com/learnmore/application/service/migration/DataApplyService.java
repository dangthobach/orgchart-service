package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.domain.migration.*;
import com.learnmore.infrastructure.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service xử lý Pha 3: Apply dữ liệu vào các bảng master
 * - Insert/upsert theo thứ tự phụ thuộc
 * - Bulk insert để tối ưu hiệu năng
 * - Đảm bảo idempotent và data consistency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataApplyService {
    
    private final MigrationJobRepository migrationJobRepository;
    private final StagingValidRepository stagingValidRepository;
    
    // Master repositories
    private final WarehouseRepository warehouseRepository;
    private final UnitRepository unitRepository;
    private final DocTypeRepository docTypeRepository;
    private final StatusRepository statusRepository;
    private final LocationRepository locationRepository;
    private final RetentionPeriodRepository retentionPeriodRepository;
    private final BoxRepository boxRepository;
    private final CaseDetailRepository caseDetailRepository;
    
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * Bắt đầu quá trình apply dữ liệu
     */
    @Transactional
    public MigrationResultDTO startApplyProcess(String jobId) {
        
        log.info("Starting data apply process for JobId: {}", jobId);
        
        // Tìm migration job
        MigrationJob migrationJob = migrationJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Migration job not found: " + jobId));
        
        try {
            // Cập nhật status
            migrationJob.setStatus("APPLYING");
            migrationJob.setCurrentPhase("APPLY_DATA");
            migrationJob.setProgressPercent(0.0);
            migrationJobRepository.save(migrationJob);
            
            long startTime = System.currentTimeMillis();
            
            // Thực hiện apply data theo thứ tự phụ thuộc
            ApplyResult result = performApplyData(jobId);
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Cập nhật job results
            migrationJob.setStatus("APPLY_COMPLETED");
            migrationJob.setCurrentPhase("APPLY_COMPLETED");
            migrationJob.setInsertedRows(result.getInsertedRows());
            migrationJob.setProgressPercent(100.0);
            migrationJobRepository.save(migrationJob);
            
            log.info("Apply data completed for JobId: {}, Inserted: {}, Time: {}ms", 
                    jobId, result.getInsertedRows(), processingTime);
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("APPLY_COMPLETED")
                    .insertedRows(result.getInsertedRows())
                    .currentPhase("APPLY_COMPLETED")
                    .progressPercent(100.0)
                    .applyTimeMs(processingTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Apply data failed for JobId: {}, Error: {}", jobId, e.getMessage(), e);
            
            migrationJob.setStatus("FAILED");
            migrationJob.setCurrentPhase("APPLY_FAILED");
            migrationJob.setErrorMessage(e.getMessage());
            migrationJobRepository.save(migrationJob);
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("FAILED")
                    .currentPhase("APPLY_FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Thực hiện apply data theo thứ tự phụ thuộc
     */
    private ApplyResult performApplyData(String jobId) {
        
        long totalInserted = 0;
        
        // Phase 1: Insert independent master tables
        log.info("Phase 1: Inserting independent master data for JobId: {}", jobId);
        totalInserted += insertWarehouseMaster(jobId);
        totalInserted += insertUnitMaster(jobId);
        totalInserted += insertDocTypeMaster(jobId);
        totalInserted += insertStatusMaster(jobId);
        totalInserted += insertRetentionPeriodMaster(jobId);
        
        // Phase 2: Insert dependent master tables
        log.info("Phase 2: Inserting dependent master data for JobId: {}", jobId);
        totalInserted += insertLocationMaster(jobId);
        totalInserted += insertBoxMaster(jobId);
        
        // Phase 3: Insert main business data
        log.info("Phase 3: Inserting main business data for JobId: {}", jobId);
        totalInserted += insertCaseDetailData(jobId);
        
        return ApplyResult.builder()
                .insertedRows(totalInserted)
                .build();
    }
    
    /**
     * Insert warehouse master data
     */
    private long insertWarehouseMaster(String jobId) {
        String sql = """
            INSERT INTO warehouse (code, name, is_active, created_at, updated_at)
            SELECT DISTINCT 
                sv.kho_vpbank_norm as code,
                sv.kho_vpbank_norm as name,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND sv.kho_vpbank_norm IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM warehouse w 
                WHERE w.code = sv.kho_vpbank_norm
            )
            """;
        
        int inserted = jdbcTemplate.update(sql, jobId);
        log.debug("Inserted {} warehouse records for JobId: {}", inserted, jobId);
        return inserted;
    }
    
    /**
     * Insert unit master data
     */
    private long insertUnitMaster(String jobId) {
        String sql = """
            INSERT INTO unit (code, name, level, is_active, created_at, updated_at)
            SELECT DISTINCT 
                sv.ma_don_vi_norm as code,
                sv.ma_don_vi_norm as name,
                1 as level,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND sv.ma_don_vi_norm IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM unit u 
                WHERE u.code = sv.ma_don_vi_norm
            )
            """;
        
        int inserted = jdbcTemplate.update(sql, jobId);
        log.debug("Inserted {} unit records for JobId: {}", inserted, jobId);
        return inserted;
    }
    
    /**
     * Insert doc_type master data
     */
    private long insertDocTypeMaster(String jobId) {
        String sql = """
            INSERT INTO doc_type (code, name, is_active, created_at, updated_at)
            SELECT DISTINCT 
                UPPER(REPLACE(sv.loai_chung_tu_norm, ' ', '_')) as code,
                sv.loai_chung_tu_norm as name,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND sv.loai_chung_tu_norm IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM doc_type dt 
                WHERE dt.name = sv.loai_chung_tu_norm
            )
            """;
        
        int inserted = jdbcTemplate.update(sql, jobId);
        log.debug("Inserted {} doc_type records for JobId: {}", inserted, jobId);
        return inserted;
    }
    
    /**
     * Insert status master data for various types
     */
    private long insertStatusMaster(String jobId) {
        long totalInserted = 0;
        
        // Insert CASE_PDM status
        String casePdmSql = """
            INSERT INTO status (code, name, type, is_active, created_at, updated_at)
            SELECT DISTINCT 
                UPPER(REPLACE(COALESCE(sv.trang_thai_case_pdm_norm, 'UNKNOWN'), ' ', '_')) as code,
                COALESCE(sv.trang_thai_case_pdm_norm, 'Unknown') as name,
                'CASE_PDM' as type,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND NOT EXISTS (
                SELECT 1 FROM status s 
                WHERE s.code = UPPER(REPLACE(COALESCE(sv.trang_thai_case_pdm_norm, 'UNKNOWN'), ' ', '_'))
                AND s.type = 'CASE_PDM'
            )
            """;
        
        totalInserted += jdbcTemplate.update(casePdmSql, jobId);
        
        // Insert BOX_STATUS
        String boxStatusSql = """
            INSERT INTO status (code, name, type, is_active, created_at, updated_at)
            SELECT DISTINCT 
                UPPER(REPLACE(COALESCE(sv.tinh_trang_thung_norm, 'UNKNOWN'), ' ', '_')) as code,
                COALESCE(sv.tinh_trang_thung_norm, 'Unknown') as name,
                'BOX_STATUS' as type,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND NOT EXISTS (
                SELECT 1 FROM status s 
                WHERE s.code = UPPER(REPLACE(COALESCE(sv.tinh_trang_thung_norm, 'UNKNOWN'), ' ', '_'))
                AND s.type = 'BOX_STATUS'
            )
            """;
        
        totalInserted += jdbcTemplate.update(boxStatusSql, jobId);
        
        // Insert BOX_STATE
        String boxStateSql = """
            INSERT INTO status (code, name, type, is_active, created_at, updated_at)
            SELECT DISTINCT 
                UPPER(REPLACE(COALESCE(sv.trang_thai_thung_norm, 'UNKNOWN'), ' ', '_')) as code,
                COALESCE(sv.trang_thai_thung_norm, 'Unknown') as name,
                'BOX_STATE' as type,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND NOT EXISTS (
                SELECT 1 FROM status s 
                WHERE s.code = UPPER(REPLACE(COALESCE(sv.trang_thai_thung_norm, 'UNKNOWN'), ' ', '_'))
                AND s.type = 'BOX_STATE'
            )
            """;
        
        totalInserted += jdbcTemplate.update(boxStateSql, jobId);
        
        log.debug("Inserted {} status records for JobId: {}", totalInserted, jobId);
        return totalInserted;
    }
    
    /**
     * Insert retention_period master data
     */
    private long insertRetentionPeriodMaster(String jobId) {
        String sql = """
            INSERT INTO retention_period (years, description, is_active, created_at, updated_at)
            SELECT DISTINCT 
                sv.thoi_han_luu_tru_norm as years,
                CONCAT(sv.thoi_han_luu_tru_norm, ' năm') as description,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND sv.thoi_han_luu_tru_norm IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM retention_period rp 
                WHERE rp.years = sv.thoi_han_luu_tru_norm
            )
            """;
        
        int inserted = jdbcTemplate.update(sql, jobId);
        log.debug("Inserted {} retention_period records for JobId: {}", inserted, jobId);
        return inserted;
    }
    
    /**
     * Insert location master data
     */
    private long insertLocationMaster(String jobId) {
        String sql = """
            INSERT INTO location (area, row_num, column_num, is_active, created_at, updated_at)
            SELECT DISTINCT 
                sv.khu_vuc_norm as area,
                sv.hang_norm as row_num,
                sv.cot_norm as column_num,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            WHERE sv.job_id = ?
            AND sv.khu_vuc_norm IS NOT NULL
            AND sv.hang_norm IS NOT NULL
            AND sv.cot_norm IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM location l 
                WHERE l.area = sv.khu_vuc_norm
                AND l.row_num = sv.hang_norm
                AND l.column_num = sv.cot_norm
            )
            """;
        
        int inserted = jdbcTemplate.update(sql, jobId);
        log.debug("Inserted {} location records for JobId: {}", inserted, jobId);
        return inserted;
    }
    
    /**
     * Insert box master data
     */
    private long insertBoxMaster(String jobId) {
        String sql = """
            INSERT INTO box (code, warehouse_id, location_id, box_status_id, box_state_id, 
                           entry_date, transfer_date, is_active, created_at, updated_at)
            SELECT DISTINCT 
                sv.ma_thung_norm as code,
                w.id as warehouse_id,
                l.id as location_id,
                bs.id as box_status_id,
                bst.id as box_state_id,
                CASE WHEN sv.ngay_nhap_kho_vpbank_norm IS NOT NULL THEN sv.ngay_nhap_kho_vpbank_norm::date ELSE NULL END as entry_date,
                CASE WHEN sv.ngay_chuyen_kho_crown_norm IS NOT NULL THEN sv.ngay_chuyen_kho_crown_norm::date ELSE NULL END as transfer_date,
                true as is_active,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            JOIN warehouse w ON w.code = sv.kho_vpbank_norm
            LEFT JOIN location l ON l.area = sv.khu_vuc_norm AND l.row_num = sv.hang_norm AND l.column_num = sv.cot_norm
            LEFT JOIN status bs ON bs.code = UPPER(REPLACE(COALESCE(sv.tinh_trang_thung_norm, 'UNKNOWN'), ' ', '_')) AND bs.type = 'BOX_STATUS'
            LEFT JOIN status bst ON bst.code = UPPER(REPLACE(COALESCE(sv.trang_thai_thung_norm, 'UNKNOWN'), ' ', '_')) AND bst.type = 'BOX_STATE'
            WHERE sv.job_id = ?
            AND sv.ma_thung_norm IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM box b 
                WHERE b.code = sv.ma_thung_norm
            )
            """;
        
        int inserted = jdbcTemplate.update(sql, jobId);
        log.debug("Inserted {} box records for JobId: {}", inserted, jobId);
        return inserted;
    }
    
    /**
     * Insert main case_detail business data
     */
    private long insertCaseDetailData(String jobId) {
        String sql = """
            INSERT INTO case_detail (
                unit_id, doc_type_id, box_id, retention_period_id, case_status_id, 
                loss_status_id, return_status_id, responsibility, doc_date, case_title, 
                quantity, due_date, handover_date, case_notes, general_notes, 
                created_at, updated_at
            )
            SELECT 
                u.id as unit_id,
                dt.id as doc_type_id,
                b.id as box_id,
                rp.id as retention_period_id,
                cs.id as case_status_id,
                ls.id as loss_status_id,
                rs.id as return_status_id,
                sv.trach_nhiem_ban_giao as responsibility,
                sv.ngay_chung_tu_norm::date as doc_date,
                sv.ten_tap as case_title,
                sv.so_luong_tap_norm as quantity,
                CASE WHEN sv.ngay_phai_ban_giao_norm IS NOT NULL THEN sv.ngay_phai_ban_giao_norm::date ELSE NULL END as due_date,
                CASE WHEN sv.ngay_ban_giao_norm IS NOT NULL THEN sv.ngay_ban_giao_norm::date ELSE NULL END as handover_date,
                sv.ghi_chu_case_pdm as case_notes,
                sv.luu_y as general_notes,
                CURRENT_TIMESTAMP as created_at,
                CURRENT_TIMESTAMP as updated_at
            FROM staging_valid sv
            JOIN unit u ON u.code = sv.ma_don_vi_norm
            JOIN doc_type dt ON dt.name = sv.loai_chung_tu_norm
            JOIN box b ON b.code = sv.ma_thung_norm
            LEFT JOIN retention_period rp ON rp.years = sv.thoi_han_luu_tru_norm
            LEFT JOIN status cs ON cs.code = UPPER(REPLACE(COALESCE(sv.trang_thai_case_pdm_norm, 'UNKNOWN'), ' ', '_')) AND cs.type = 'CASE_PDM'
            LEFT JOIN status ls ON ls.code = UPPER(REPLACE(COALESCE(sv.tinh_trang_that_lac_norm, 'NO'), ' ', '_')) AND ls.type = 'LOSS_STATUS'
            LEFT JOIN status rs ON rs.code = UPPER(REPLACE(COALESCE(sv.tinh_trang_khong_hoan_tra_norm, 'NO'), ' ', '_')) AND rs.type = 'RETURN_STATUS'
            WHERE sv.job_id = ?
            """;
        
        int inserted = jdbcTemplate.update(sql, jobId);
        log.debug("Inserted {} case_detail records for JobId: {}", inserted, jobId);
        return inserted;
    }
    
    /**
     * Inner class cho kết quả apply
     */
    @lombok.Data
    @lombok.Builder
    private static class ApplyResult {
        private Long insertedRows;
    }
}
