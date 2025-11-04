package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.application.dto.migration.ValidationStepStatus;
import com.learnmore.domain.migration.*;
import com.learnmore.infrastructure.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service xử lý Pha 2: Validation dữ liệu với step tracking chi tiết
 * - Validate dữ liệu bắt buộc, format, enum
 * - Check duplicate trong file
 * - Validate tham chiếu với master tables
 * - Check duplicate với dữ liệu cũ trong DB
 * - Track từng step để monitor progress và detect hang
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceWithTracking {

    private final MigrationJobRepository migrationJobRepository;
    private final StagingRawRepository stagingRawRepository;
    private final StagingValidRepository stagingValidRepository;
    private final StagingErrorRepository stagingErrorRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ValidationStepTracker stepTracker;

    /**
     * Bắt đầu quá trình validation với step tracking
     */
    @Transactional
    public MigrationResultDTO startValidation(String jobId) {

        log.info("Starting validation process with tracking for JobId: {}", jobId);

        // Initialize step tracking
        stepTracker.initializeTracking(jobId);

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

            // Thực hiện validation bằng SQL set-based operations với tracking
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

            // Log detailed report
            log.info("Validation completed for JobId: {}, Valid: {}, Errors: {}, Time: {}ms",
                    jobId, result.getValidRows(), result.getErrorRows(), processingTime);
            log.info("\n{}", stepTracker.getDetailedReport(jobId));

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
            log.error("\n{}", stepTracker.getDetailedReport(jobId));

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
     * Thực hiện validation sử dụng SQL set-based operations với tracking
     */
    private ValidationResult performValidation(String jobId) {

        log.info("Performing set-based validation with tracking for JobId: {}", jobId);

        // Step 1: Validate required fields
        validateRequiredFields(jobId);

        // Check for timeout
        stepTracker.checkTimeouts(jobId);

        // Step 2: Validate date formats and business rules
        validateDateFormats(jobId);

        // Check for timeout
        stepTracker.checkTimeouts(jobId);

        // Step 3: Validate numeric fields
        validateNumericFields(jobId);

        // Check for timeout
        stepTracker.checkTimeouts(jobId);

        // Step 4: Check duplicates within file
        checkDuplicatesInFile(jobId);

        // Check for timeout
        stepTracker.checkTimeouts(jobId);

        // Step 5: Validate references with master tables
        validateMasterReferences(jobId);

        // Check for timeout
        stepTracker.checkTimeouts(jobId);

        // Step 6: Check duplicates with existing data
        checkDuplicatesWithExistingData(jobId);

        // Check for timeout
        stepTracker.checkTimeouts(jobId);

        // Step 7: Move valid records to staging_valid
        long validRows = moveValidRecordsToStagingValid(jobId);

        // Final timeout check
        stepTracker.checkTimeouts(jobId);

        // Get error count
        long errorRows = stagingErrorRepository.countByJobId(jobId);

        return ValidationResult.builder()
                .validRows(validRows)
                .errorRows(errorRows)
                .build();
    }

    /**
     * Step 1: Validate required fields
     */
    private void validateRequiredFields(String jobId) {
        stepTracker.markStepStarted(jobId, "VALIDATE_REQUIRED_FIELDS");

        try {
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
            stepTracker.markStepCompleted(jobId, "VALIDATE_REQUIRED_FIELDS", affected);

        } catch (Exception e) {
            stepTracker.markStepFailed(jobId, "VALIDATE_REQUIRED_FIELDS", e.getMessage());
            throw new RuntimeException("Failed to validate required fields", e);
        }
    }

    /**
     * Step 2: Validate date formats
     */
    private void validateDateFormats(String jobId) {
        stepTracker.markStepStarted(jobId, "VALIDATE_DATE_FORMATS");

        try {
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
            stepTracker.markStepCompleted(jobId, "VALIDATE_DATE_FORMATS", affected);

        } catch (Exception e) {
            stepTracker.markStepFailed(jobId, "VALIDATE_DATE_FORMATS", e.getMessage());
            throw new RuntimeException("Failed to validate date formats", e);
        }
    }

    /**
     * Step 3: Validate numeric fields
     */
    private void validateNumericFields(String jobId) {
        stepTracker.markStepStarted(jobId, "VALIDATE_NUMERIC_FIELDS");

        try {
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
            stepTracker.markStepCompleted(jobId, "VALIDATE_NUMERIC_FIELDS", affected);

        } catch (Exception e) {
            stepTracker.markStepFailed(jobId, "VALIDATE_NUMERIC_FIELDS", e.getMessage());
            throw new RuntimeException("Failed to validate numeric fields", e);
        }
    }

    /**
     * Step 4: Check duplicates within file
     */
    private void checkDuplicatesInFile(String jobId) {
        stepTracker.markStepStarted(jobId, "CHECK_DUPLICATES_IN_FILE");

        try {
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
            stepTracker.markStepCompleted(jobId, "CHECK_DUPLICATES_IN_FILE", affected);

        } catch (Exception e) {
            stepTracker.markStepFailed(jobId, "CHECK_DUPLICATES_IN_FILE", e.getMessage());
            throw new RuntimeException("Failed to check duplicates in file", e);
        }
    }

    /**
     * Step 5: Validate master references
     */
    private void validateMasterReferences(String jobId) {
        stepTracker.markStepStarted(jobId, "VALIDATE_MASTER_REFERENCES");

        try {
            // Note: This would call multiple sub-validations
            // For simplicity, just count total errors added
            int totalAffected = 0;

            // Warehouse validation
            String warehouseSql = """
                INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
                SELECT
                    sr.job_id, sr.row_num, 'REF_NOT_FOUND' as error_type, 'kho_vpbank' as error_field,
                    sr.kho_vpbank_norm as error_value, 'Kho VPBank không tồn tại' as error_message,
                    CURRENT_TIMESTAMP as created_at,
                    CONCAT('Row:', sr.row_num, '|MaDV:', sr.ma_don_vi_norm, '|MaThung:', sr.ma_thung_norm) as original_data
                FROM staging_raw sr
                WHERE sr.job_id = ? AND sr.parse_errors IS NULL AND sr.kho_vpbank_norm IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
                AND NOT EXISTS (SELECT 1 FROM warehouse w WHERE w.code = sr.kho_vpbank_norm AND w.is_active = true)
                """;
            totalAffected += jdbcTemplate.update(warehouseSql, jobId);

            stepTracker.markStepCompleted(jobId, "VALIDATE_MASTER_REFERENCES", totalAffected);

        } catch (Exception e) {
            stepTracker.markStepFailed(jobId, "VALIDATE_MASTER_REFERENCES", e.getMessage());
            throw new RuntimeException("Failed to validate master references", e);
        }
    }

    /**
     * Step 6: Check duplicates with existing data
     */
    private void checkDuplicatesWithExistingData(String jobId) {
        stepTracker.markStepStarted(jobId, "CHECK_DUPLICATES_WITH_DB");

        try {
            String sql = """
                INSERT INTO staging_error (job_id, row_num, error_type, error_field, error_value, error_message, created_at, original_data)
                SELECT
                    sr.job_id, sr.row_num, 'DUP_IN_DB' as error_type, 'business_key' as error_field,
                    CONCAT(sr.ma_don_vi_norm, '_', sr.ma_thung_norm) as error_value,
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
                    LIMIT 1
                )
                """;

            int affected = jdbcTemplate.update(sql, jobId);
            stepTracker.markStepCompleted(jobId, "CHECK_DUPLICATES_WITH_DB", affected);

        } catch (Exception e) {
            stepTracker.markStepFailed(jobId, "CHECK_DUPLICATES_WITH_DB", e.getMessage());
            throw new RuntimeException("Failed to check duplicates with DB", e);
        }
    }

    /**
     * Step 7: Move valid records to staging_valid
     * This is the step that often hangs with 100k+ records
     */
    private long moveValidRecordsToStagingValid(String jobId) {
        stepTracker.markStepStarted(jobId, "MOVE_VALID_RECORDS");

        try {
            log.info("Moving valid records to staging_valid for JobId: {} - This may take several minutes for large datasets", jobId);

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
            stepTracker.markStepCompleted(jobId, "MOVE_VALID_RECORDS", affected);

            log.info("Successfully moved {} valid records to staging_valid for JobId: {}", affected, jobId);
            return affected;

        } catch (Exception e) {
            stepTracker.markStepFailed(jobId, "MOVE_VALID_RECORDS", e.getMessage());
            throw new RuntimeException("Failed to move valid records to staging_valid", e);
        }
    }

    /**
     * Get validation progress for a job
     */
    public List<ValidationStepStatus> getValidationProgress(String jobId) {
        return stepTracker.getAllSteps(jobId);
    }

    /**
     * Get current step being executed
     */
    public ValidationStepStatus getCurrentStep(String jobId) {
        return stepTracker.getCurrentStep(jobId);
    }

    /**
     * Get progress summary
     */
    public String getProgressSummary(String jobId) {
        return stepTracker.getProgressSummary(jobId);
    }

    /**
     * Get detailed report
     */
    public String getDetailedReport(String jobId) {
        return stepTracker.getDetailedReport(jobId);
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
