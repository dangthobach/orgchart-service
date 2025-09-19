package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.domain.migration.*;
import com.learnmore.infrastructure.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service xử lý Pha 2: Validation dữ liệu
 * - Validate dữ liệu bắt buộc, format, enum
 * - Check duplicate trong file
 * - Validate tham chiếu với master tables
 * - Check duplicate với dữ liệu cũ trong DB
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {
    
    private final MigrationJobRepository migrationJobRepository;
    private final StagingRawRepository stagingRawRepository;
    private final StagingValidRepository stagingValidRepository;
    private final StagingErrorRepository stagingErrorRepository;
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * Bắt đầu quá trình validation
     */
    @Transactional
    public MigrationResultDTO startValidation(String jobId) {
        
        log.info("Starting validation process for JobId: {}", jobId);
        
        // Tìm migration job
        MigrationJob migrationJob = migrationJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Migration job not found: " + jobId));
        
        try {
            // Cập nhật status
            migrationJob.setStatus("VALIDATING");
            migrationJob.setCurrentPhase("VALIDATION");
            migrationJob.setProgressPercent(0.0);
            migrationJobRepository.save(migrationJob);
            
            long startTime = System.currentTimeMillis();
            
            // Thực hiện validation bằng SQL set-based operations
            ValidationResult result = performValidation(jobId);
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Cập nhật job results
            migrationJob.setStatus("VALIDATION_COMPLETED");
            migrationJob.setCurrentPhase("VALIDATION_COMPLETED");
            migrationJob.setValidRows(result.getValidRows());
            migrationJob.setErrorRows(result.getErrorRows());
            migrationJob.setProgressPercent(100.0);
            migrationJobRepository.save(migrationJob);
            
            log.info("Validation completed for JobId: {}, Valid: {}, Errors: {}, Time: {}ms", 
                    jobId, result.getValidRows(), result.getErrorRows(), processingTime);
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("VALIDATION_COMPLETED")
                    .validRows(result.getValidRows())
                    .errorRows(result.getErrorRows())
                    .currentPhase("VALIDATION_COMPLETED")
                    .progressPercent(100.0)
                    .validationTimeMs(processingTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Validation failed for JobId: {}, Error: {}", jobId, e.getMessage(), e);
            
            migrationJob.setStatus("FAILED");
            migrationJob.setCurrentPhase("VALIDATION_FAILED");
            migrationJob.setErrorMessage(e.getMessage());
            migrationJobRepository.save(migrationJob);
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("FAILED")
                    .currentPhase("VALIDATION_FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Thực hiện validation sử dụng SQL set-based operations
     */
    private ValidationResult performValidation(String jobId) {
        
        log.info("Performing set-based validation for JobId: {}", jobId);
        
        // Step 1: Validate required fields
        validateRequiredFields(jobId);
        
        // Step 2: Validate date formats and business rules
        validateDateFormats(jobId);
        
        // Step 3: Validate numeric fields
        validateNumericFields(jobId);
        
        // Step 4: Check duplicates within file
        checkDuplicatesInFile(jobId);
        
        // Step 5: Validate references with master tables
        validateMasterReferences(jobId);
        
        // Step 6: Check duplicates with existing data
        checkDuplicatesWithExistingData(jobId);
        
        // Step 7: Move valid records to staging_valid
        long validRows = moveValidRecordsToStagingValid(jobId);
        
        // Get error count
        long errorRows = stagingErrorRepository.countByJobId(jobId);
        
        return ValidationResult.builder()
                .validRows(validRows)
                .errorRows(errorRows)
                .build();
    }
    
    /**
     * Validate required fields
     */
    private void validateRequiredFields(String jobId) {
        log.debug("Validating required fields for JobId: {}", jobId);
        
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'REQUIRED_MISSING' as error_type,
                CASE 
                    WHEN sr.kho_vpbank_norm IS NULL OR sr.kho_vpbank_norm = '' THEN 'kho_vpbank'
                    WHEN sr.ma_don_vi_norm IS NULL OR sr.ma_don_vi_norm = '' THEN 'ma_don_vi'
                    WHEN sr.loai_chung_tu_norm IS NULL OR sr.loai_chung_tu_norm = '' THEN 'loai_chung_tu'
                    WHEN sr.ngay_chung_tu_norm IS NULL OR sr.ngay_chung_tu_norm = '' THEN 'ngay_chung_tu'
                    WHEN sr.so_luong_tap_norm IS NULL OR sr.so_luong_tap_norm = '' THEN 'so_luong_tap'
                    WHEN sr.ma_thung_norm IS NULL OR sr.ma_thung_norm = '' THEN 'ma_thung'
                    WHEN sr.thoi_han_luu_tru_norm IS NULL OR sr.thoi_han_luu_tru_norm = '' THEN 'thoi_han_luu_tru'
                END as error_field,
                CASE 
                    WHEN sr.kho_vpbank_norm IS NULL OR sr.kho_vpbank_norm = '' THEN sr.kho_vpbank
                    WHEN sr.ma_don_vi_norm IS NULL OR sr.ma_don_vi_norm = '' THEN sr.ma_don_vi
                    WHEN sr.loai_chung_tu_norm IS NULL OR sr.loai_chung_tu_norm = '' THEN sr.loai_chung_tu
                    WHEN sr.ngay_chung_tu_norm IS NULL OR sr.ngay_chung_tu_norm = '' THEN sr.ngay_chung_tu
                    WHEN sr.so_luong_tap_norm IS NULL OR sr.so_luong_tap_norm = '' THEN sr.so_luong_tap
                    WHEN sr.ma_thung_norm IS NULL OR sr.ma_thung_norm = '' THEN sr.ma_thung
                    WHEN sr.thoi_han_luu_tru_norm IS NULL OR sr.thoi_han_luu_tru_norm = '' THEN sr.thoi_han_luu_tru
                END as error_value,
                'Trường bắt buộc không được để trống' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi, '|MaThung:', sr.ma_thung) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND (
                sr.kho_vpbank_norm IS NULL OR sr.kho_vpbank_norm = '' OR
                sr.ma_don_vi_norm IS NULL OR sr.ma_don_vi_norm = '' OR
                sr.loai_chung_tu_norm IS NULL OR sr.loai_chung_tu_norm = '' OR
                sr.ngay_chung_tu_norm IS NULL OR sr.ngay_chung_tu_norm = '' OR
                sr.so_luong_tap_norm IS NULL OR sr.so_luong_tap_norm = '' OR
                sr.ma_thung_norm IS NULL OR sr.ma_thung_norm = '' OR
                sr.thoi_han_luu_tru_norm IS NULL OR sr.thoi_han_luu_tru_norm = ''
            )
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records with missing required fields for JobId: {}", affected, jobId);
    }
    
    /**
     * Validate date formats and business rules
     */
    private void validateDateFormats(String jobId) {
        log.debug("Validating date formats for JobId: {}", jobId);
        
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'INVALID_DATE' as error_type,
                CASE 
                    WHEN sr.ngay_chung_tu_norm IS NOT NULL AND sr.ngay_chung_tu_norm !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN 'ngay_chung_tu'
                    WHEN sr.ngay_phai_ban_giao IS NOT NULL AND sr.ngay_phai_ban_giao != '' AND sr.ngay_phai_ban_giao !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN 'ngay_phai_ban_giao'
                    WHEN sr.ngay_ban_giao IS NOT NULL AND sr.ngay_ban_giao != '' AND sr.ngay_ban_giao !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN 'ngay_ban_giao'
                    WHEN sr.ngay_nhap_kho_vpbank IS NOT NULL AND sr.ngay_nhap_kho_vpbank != '' AND sr.ngay_nhap_kho_vpbank !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN 'ngay_nhap_kho_vpbank'
                    WHEN sr.ngay_chuyen_kho_crown IS NOT NULL AND sr.ngay_chuyen_kho_crown != '' AND sr.ngay_chuyen_kho_crown !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN 'ngay_chuyen_kho_crown'
                END as error_field,
                CASE 
                    WHEN sr.ngay_chung_tu_norm IS NOT NULL AND sr.ngay_chung_tu_norm !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN sr.ngay_chung_tu
                    WHEN sr.ngay_phai_ban_giao IS NOT NULL AND sr.ngay_phai_ban_giao != '' AND sr.ngay_phai_ban_giao !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN sr.ngay_phai_ban_giao
                    WHEN sr.ngay_ban_giao IS NOT NULL AND sr.ngay_ban_giao != '' AND sr.ngay_ban_giao !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN sr.ngay_ban_giao
                    WHEN sr.ngay_nhap_kho_vpbank IS NOT NULL AND sr.ngay_nhap_kho_vpbank != '' AND sr.ngay_nhap_kho_vpbank !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN sr.ngay_nhap_kho_vpbank
                    WHEN sr.ngay_chuyen_kho_crown IS NOT NULL AND sr.ngay_chuyen_kho_crown != '' AND sr.ngay_chuyen_kho_crown !~ '^\\d{4}-\\d{2}-\\d{2}$' THEN sr.ngay_chuyen_kho_crown
                END as error_value,
                'Định dạng ngày không hợp lệ (phải là yyyy-MM-dd)' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi, '|MaThung:', sr.ma_thung) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            AND (
                (sr.ngay_chung_tu_norm IS NOT NULL AND sr.ngay_chung_tu_norm !~ '^\\d{4}-\\d{2}-\\d{2}$') OR
                (sr.ngay_phai_ban_giao IS NOT NULL AND sr.ngay_phai_ban_giao != '' AND sr.ngay_phai_ban_giao !~ '^\\d{4}-\\d{2}-\\d{2}$') OR 
                (sr.ngay_ban_giao IS NOT NULL AND sr.ngay_ban_giao != '' AND sr.ngay_ban_giao !~ '^\\d{4}-\\d{2}-\\d{2}$') OR
                (sr.ngay_nhap_kho_vpbank IS NOT NULL AND sr.ngay_nhap_kho_vpbank != '' AND sr.ngay_nhap_kho_vpbank !~ '^\\d{4}-\\d{2}-\\d{2}$') OR
                (sr.ngay_chuyen_kho_crown IS NOT NULL AND sr.ngay_chuyen_kho_crown != '' AND sr.ngay_chuyen_kho_crown !~ '^\\d{4}-\\d{2}-\\d{2}$')
            )
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records with invalid date formats for JobId: {}", affected, jobId);
    }
    
    /**
     * Validate numeric fields
     */
    private void validateNumericFields(String jobId) {
        log.debug("Validating numeric fields for JobId: {}", jobId);
        
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'INVALID_NUMERIC' as error_type,
                CASE 
                    WHEN sr.so_luong_tap_norm IS NOT NULL AND (sr.so_luong_tap_norm !~ '^\\d+$' OR sr.so_luong_tap_norm::integer <= 0) THEN 'so_luong_tap'
                    WHEN sr.thoi_han_luu_tru_norm IS NOT NULL AND (sr.thoi_han_luu_tru_norm !~ '^\\d+$' OR sr.thoi_han_luu_tru_norm::integer <= 0) THEN 'thoi_han_luu_tru'
                    WHEN sr.hang_norm IS NOT NULL AND sr.hang_norm != '' AND (sr.hang_norm !~ '^\\d+$' OR sr.hang_norm::integer <= 0) THEN 'hang'
                    WHEN sr.cot_norm IS NOT NULL AND sr.cot_norm != '' AND (sr.cot_norm !~ '^\\d+$' OR sr.cot_norm::integer <= 0) THEN 'cot'
                END as error_field,
                CASE 
                    WHEN sr.so_luong_tap_norm IS NOT NULL AND (sr.so_luong_tap_norm !~ '^\\d+$' OR sr.so_luong_tap_norm::integer <= 0) THEN sr.so_luong_tap
                    WHEN sr.thoi_han_luu_tru_norm IS NOT NULL AND (sr.thoi_han_luu_tru_norm !~ '^\\d+$' OR sr.thoi_han_luu_tru_norm::integer <= 0) THEN sr.thoi_han_luu_tru
                    WHEN sr.hang_norm IS NOT NULL AND sr.hang_norm != '' AND (sr.hang_norm !~ '^\\d+$' OR sr.hang_norm::integer <= 0) THEN sr.hang
                    WHEN sr.cot_norm IS NOT NULL AND sr.cot_norm != '' AND (sr.cot_norm !~ '^\\d+$' OR sr.cot_norm::integer <= 0) THEN sr.cot
                END as error_value,
                'Giá trị số không hợp lệ (phải là số nguyên dương)' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi, '|MaThung:', sr.ma_thung) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            AND (
                (sr.so_luong_tap_norm IS NOT NULL AND (sr.so_luong_tap_norm !~ '^\\d+$' OR sr.so_luong_tap_norm::integer <= 0)) OR
                (sr.thoi_han_luu_tru_norm IS NOT NULL AND (sr.thoi_han_luu_tru_norm !~ '^\\d+$' OR sr.thoi_han_luu_tru_norm::integer <= 0)) OR
                (sr.hang_norm IS NOT NULL AND sr.hang_norm != '' AND (sr.hang_norm !~ '^\\d+$' OR sr.hang_norm::integer <= 0)) OR
                (sr.cot_norm IS NOT NULL AND sr.cot_norm != '' AND (sr.cot_norm !~ '^\\d+$' OR sr.cot_norm::integer <= 0))
            )
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records with invalid numeric fields for JobId: {}", affected, jobId);
    }
    
    /**
     * Check duplicates within file using window functions
     */
    private void checkDuplicatesInFile(String jobId) {
        log.debug("Checking duplicates within file for JobId: {}", jobId);
        
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                dup.job_id,
                dup.row_num,
                'DUP_IN_FILE' as error_type,
                'business_key' as error_field,
                dup.business_key as error_value,
                CONCAT('Trùng lặp với dòng ', dup.first_row, ' trong file') as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', dup.row_num, '|MaDV:', dup.ma_don_vi_norm, '|MaThung:', dup.ma_thung_norm) as original_data
            FROM (
                SELECT 
                    sr.job_id,
                    sr.row_num,
                    sr.ma_don_vi_norm,
                    sr.ma_thung_norm,
                    CONCAT(sr.ma_don_vi_norm, '_', sr.ma_thung_norm, '_', sr.ngay_chung_tu_norm, '_', sr.so_luong_tap_norm) as business_key,
                    ROW_NUMBER() OVER (PARTITION BY sr.ma_don_vi_norm, sr.ma_thung_norm, sr.ngay_chung_tu_norm, sr.so_luong_tap_norm ORDER BY sr.row_num) as rn,
                    FIRST_VALUE(sr.row_num) OVER (PARTITION BY sr.ma_don_vi_norm, sr.ma_thung_norm, sr.ngay_chung_tu_norm, sr.so_luong_tap_norm ORDER BY sr.row_num) as first_row
                FROM staging_raw sr
                WHERE sr.job_id = ?
                AND sr.parse_errors IS NULL
                AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            ) dup
            WHERE dup.rn > 1
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} duplicate records within file for JobId: {}", affected, jobId);
    }
    
    /**
     * Validate references with master tables
     */
    private void validateMasterReferences(String jobId) {
        log.debug("Validating master references for JobId: {}", jobId);
        
        // Validate warehouse references
        validateWarehouseReferences(jobId);
        
        // Validate unit references  
        validateUnitReferences(jobId);
        
        // Validate doc_type references
        validateDocTypeReferences(jobId);
        
        // Validate retention_period references
        validateRetentionPeriodReferences(jobId);
        
        // Add more reference validations as needed
    }
    
    private void validateWarehouseReferences(String jobId) {
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'REF_NOT_FOUND' as error_type,
                'kho_vpbank' as error_field,
                sr.kho_vpbank_norm as error_value,
                'Kho VPBank không tồn tại trong hệ thống' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi_norm, '|MaThung:', sr.ma_thung_norm) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND sr.kho_vpbank_norm IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            AND NOT EXISTS (SELECT 1 FROM warehouse w WHERE w.code = sr.kho_vpbank_norm AND w.is_active = true)
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records with invalid warehouse references for JobId: {}", affected, jobId);
    }
    
    private void validateUnitReferences(String jobId) {
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'REF_NOT_FOUND' as error_type,
                'ma_don_vi' as error_field,
                sr.ma_don_vi_norm as error_value,
                'Mã đơn vị không tồn tại trong hệ thống' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi_norm, '|MaThung:', sr.ma_thung_norm) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND sr.ma_don_vi_norm IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            AND NOT EXISTS (SELECT 1 FROM unit u WHERE u.code = sr.ma_don_vi_norm AND u.is_active = true)
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records with invalid unit references for JobId: {}", affected, jobId);
    }
    
    private void validateDocTypeReferences(String jobId) {
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'REF_NOT_FOUND' as error_type,
                'loai_chung_tu' as error_field,
                sr.loai_chung_tu_norm as error_value,
                'Loại chứng từ không tồn tại trong hệ thống' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi_norm, '|MaThung:', sr.ma_thung_norm) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND sr.loai_chung_tu_norm IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            AND NOT EXISTS (SELECT 1 FROM doc_type dt WHERE dt.name = sr.loai_chung_tu_norm AND dt.is_active = true)
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records with invalid doc_type references for JobId: {}", affected, jobId);
    }
    
    private void validateRetentionPeriodReferences(String jobId) {
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'REF_NOT_FOUND' as error_type,
                'thoi_han_luu_tru' as error_field,
                sr.thoi_han_luu_tru_norm as error_value,
                'Thời hạn lưu trữ không tồn tại trong hệ thống' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi_norm, '|MaThung:', sr.ma_thung_norm) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND sr.thoi_han_luu_tru_norm IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            AND NOT EXISTS (SELECT 1 FROM retention_period rp WHERE rp.years = sr.thoi_han_luu_tru_norm::integer AND rp.is_active = true)
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records with invalid retention_period references for JobId: {}", affected, jobId);
    }
    
    /**
     * Check duplicates with existing data in database
     */
    private void checkDuplicatesWithExistingData(String jobId) {
        log.debug("Checking duplicates with existing data for JobId: {}", jobId);
        
        String sql = """
            INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
            SELECT 
                sr.job_id,
                sr.row_num,
                'DUP_IN_DB' as error_type,
                'business_key' as error_field,
                CONCAT(sr.ma_don_vi_norm, '_', sr.ma_thung_norm, '_', sr.ngay_chung_tu_norm) as error_value,
                'Dữ liệu đã tồn tại trong hệ thống' as error_message,
                CURRENT_TIMESTAMP as created_at,
                CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi_norm, '|MaThung:', sr.ma_thung_norm) as original_data
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            AND EXISTS (
                SELECT 1 FROM case_detail cd
                JOIN unit u ON cd.unit_id = u.id
                JOIN box b ON cd.box_id = b.id
                WHERE u.code = sr.ma_don_vi_norm
                AND b.code = sr.ma_thung_norm
                AND cd.doc_date = sr.ngay_chung_tu_norm::date
                AND cd.quantity = sr.so_luong_tap_norm::integer
            )
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.debug("Found {} records duplicate with existing data for JobId: {}", affected, jobId);
    }
    
    /**
     * Move valid records to staging_valid table
     */
    private long moveValidRecordsToStagingValid(String jobId) {
        log.debug("Moving valid records to staging_valid for JobId: {}", jobId);
        
        String sql = """
            INSERT INTO staging_valid (
                job_id, row_num, created_at,
                kho_vpbank_norm, ma_don_vi_norm, trach_nhiem_ban_giao, loai_chung_tu_norm,
                ngay_chung_tu_norm, ten_tap, so_luong_tap_norm,
                ngay_phai_ban_giao_norm, ngay_ban_giao_norm,
                tinh_trang_that_lac_norm, tinh_trang_khong_hoan_tra_norm,
                trang_thai_case_pdm_norm, ghi_chu_case_pdm, ma_thung_norm,
                thoi_han_luu_tru_norm, ngay_nhap_kho_vpbank_norm, ngay_chuyen_kho_crown_norm,
                khu_vuc_norm, hang_norm, cot_norm,
                tinh_trang_thung_norm, trang_thai_thung_norm, luu_y
            )
            SELECT 
                sr.job_id, sr.row_num, CURRENT_TIMESTAMP,
                sr.kho_vpbank_norm, sr.ma_don_vi_norm, sr.trach_nhiem_ban_giao, sr.loai_chung_tu_norm,
                sr.ngay_chung_tu_norm, sr.ten_tap, sr.so_luong_tap_norm::integer,
                NULLIF(sr.ngay_phai_ban_giao, ''), NULLIF(sr.ngay_ban_giao, ''),
                sr.tinh_trang_that_lac, sr.tinh_trang_khong_hoan_tra,
                sr.trang_thai_case_pdm, sr.ghi_chu_case_pdm, sr.ma_thung_norm,
                sr.thoi_han_luu_tru_norm::integer, NULLIF(sr.ngay_nhap_kho_vpbank, ''), NULLIF(sr.ngay_chuyen_kho_crown, ''),
                sr.khu_vuc_norm, NULLIF(sr.hang_norm, '')::integer, NULLIF(sr.cot_norm, '')::integer,
                sr.tinh_trang_thung, sr.trang_thai_thung, sr.luu_y
            FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            """;
        
        int affected = jdbcTemplate.update(sql, jobId);
        log.info("Moved {} valid records to staging_valid for JobId: {}", affected, jobId);
        
        return affected;
    }
    
    /**
     * Inner class cho kết quả validation
     */
    @lombok.Data
    @lombok.Builder
    private static class ValidationResult {
        private Long validRows;
        private Long errorRows;
    }
}
