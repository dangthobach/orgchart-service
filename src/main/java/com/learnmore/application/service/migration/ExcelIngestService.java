package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.domain.migration.MigrationJob;
import com.learnmore.domain.migration.StagingRaw;
import com.learnmore.infrastructure.repository.MigrationJobRepository;
import com.learnmore.infrastructure.repository.StagingRawRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service xử lý Pha 1: Ingest và Staging Excel data
 * - Đọc file Excel bằng streaming
 * - Chuẩn hóa dữ liệu 
 * - Lưu vào staging_raw table
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelIngestService {
    
    private final MigrationJobRepository migrationJobRepository;
    private final StagingRawRepository stagingRawRepository;
    
    /**
     * Bắt đầu quá trình ingest Excel file
     */
    @Transactional
    public MigrationResultDTO startIngestProcess(InputStream inputStream, String filename, String createdBy) {
        
        // Tạo job ID unique
        String jobId = generateJobId();
        log.info("Starting Excel ingest process. JobId: {}, Filename: {}", jobId, filename);
        
        // Tạo migration job record
        MigrationJob migrationJob = MigrationJob.builder()
                .jobId(jobId)
                .filename(filename)
                .status("STARTED")
                .currentPhase("INGEST")
                .progressPercent(0.0)
                .createdAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .createdBy(createdBy)
                .build();
        
        migrationJobRepository.save(migrationJob);
        
        try {
            // Thực hiện ingest
            IngestResult result = performIngest(inputStream, jobId);
            
            // Cập nhật job status
            migrationJob.setStatus("INGESTING_COMPLETED");
            migrationJob.setCurrentPhase("INGEST_COMPLETED");
            migrationJob.setTotalRows(result.getTotalRows());
            migrationJob.setProcessedRows(result.getProcessedRows());
            migrationJob.setProgressPercent(100.0);
            migrationJob.setProcessingTimeMs(result.getProcessingTimeMs());
            
            migrationJobRepository.save(migrationJob);
            
            log.info("Excel ingest completed successfully. JobId: {}, ProcessedRows: {}", 
                    jobId, result.getProcessedRows());
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("INGESTING_COMPLETED")
                    .filename(filename)
                    .totalRows(result.getTotalRows())
                    .processedRows(result.getProcessedRows())
                    .currentPhase("INGEST_COMPLETED")
                    .progressPercent(100.0)
                    .startedAt(migrationJob.getStartedAt())
                    .ingestTimeMs(result.getProcessingTimeMs())
                    .build();
                    
        } catch (Exception e) {
            log.error("Excel ingest failed. JobId: {}, Error: {}", jobId, e.getMessage(), e);
            
            // Cập nhật job failed
            migrationJob.setStatus("FAILED");
            migrationJob.setCurrentPhase("INGEST_FAILED");
            migrationJob.setErrorMessage(e.getMessage());
            migrationJobRepository.save(migrationJob);
            
            return MigrationResultDTO.builder()
                    .jobId(jobId)
                    .status("FAILED")
                    .filename(filename)
                    .currentPhase("INGEST_FAILED")
                    .errorMessage(e.getMessage())
                    .startedAt(migrationJob.getStartedAt())
                    .build();
        }
    }
    
    /**
     * Thực hiện quá trình ingest sử dụng ExcelUtil streaming
     */
    private IngestResult performIngest(InputStream inputStream, String jobId) throws Exception {
        
        long startTime = System.currentTimeMillis();
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(0);
        
        // Cấu hình Excel processing cho streaming
        ExcelConfig config = ExcelConfig.builder()
                .batchSize(5000) // Process in batches of 5000 records
                .memoryThreshold(500) // 500MB memory limit
                .parallelProcessing(false) // Sequential for data integrity
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .useStreamingParser(false) // Use traditional processor for this phase
                .strictValidation(false) // Skip strict validation in ingest phase
                .build();
        
        // Batch processor để lưu vào staging_raw
        List<StagingRaw> batchBuffer = new ArrayList<>();
        
        // Sử dụng processExcelStreaming từ ExcelUtil
        ExcelUtil.processExcelStreaming(inputStream, ExcelRowDTO.class, config, batch -> {
            
            // Convert ExcelRowDTO to StagingRaw entities
            List<StagingRaw> stagingEntities = convertToStagingRaw(batch, jobId);
            batchBuffer.addAll(stagingEntities);
            
            // Lưu batch khi đạt kích thước định sẵn
            if (batchBuffer.size() >= config.getBatchSize()) {
                saveBatch(batchBuffer, jobId);
                processedCount.addAndGet(batchBuffer.size());
                batchBuffer.clear();
                
                log.debug("Processed batch for JobId: {}, Total processed: {}", 
                         jobId, processedCount.get());
            }
            
            totalCount.addAndGet(batch.size());
        });
        
        // Lưu batch cuối cùng
        if (!batchBuffer.isEmpty()) {
            saveBatch(batchBuffer, jobId);
            processedCount.addAndGet(batchBuffer.size());
        }
        
        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;
        
        log.info("Ingest completed. JobId: {}, Total: {}, Processed: {}, Time: {}ms", 
                jobId, totalCount.get(), processedCount.get(), processingTime);
        
        return IngestResult.builder()
                .totalRows((long) totalCount.get())
                .processedRows((long) processedCount.get())
                .processingTimeMs(processingTime)
                .build();
    }
    
    /**
     * Convert ExcelRowDTO list to StagingRaw entities
     */
    private List<StagingRaw> convertToStagingRaw(List<ExcelRowDTO> excelRows, String jobId) {
        
        List<StagingRaw> stagingEntities = new ArrayList<>();
        
        for (ExcelRowDTO row : excelRows) {
            try {
                // Normalize data
                row.normalize();
                
                // Create StagingRaw entity
                StagingRaw stagingRaw = StagingRaw.builder()
                        .jobId(jobId)
                        .rowNum(row.getRowNumber())
                        .sheetName("Sheet1") // Default sheet name
                        .createdAt(LocalDateTime.now())
                        
                        // Raw data
                        .khoVpbank(row.getKhoVpbank())
                        .maDonVi(row.getMaDonVi())
                        .trachNhiemBanGiao(row.getTrachNhiemBanGiao())
                        .loaiChungTu(row.getLoaiChungTu())
                        .ngayChungTu(row.getNgayChungTu())
                        .tenTap(row.getTenTap())
                        .soLuongTap(row.getSoLuongTap() != null ? row.getSoLuongTap().toString() : null)
                        .ngayPhaiBanGiao(row.getNgayPhaiBanGiao())
                        .ngayBanGiao(row.getNgayBanGiao())
                        .tinhTrangThatLac(row.getTinhTrangThatLac())
                        .tinhTrangKhongHoanTra(row.getTinhTrangKhongHoanTra())
                        .trangThaiCasePdm(row.getTrangThaiCasePdm())
                        .ghiChuCasePdm(row.getGhiChuCasePdm())
                        .maThung(row.getMaThung())
                        .thoiHanLuuTru(row.getThoiHanLuuTru() != null ? row.getThoiHanLuuTru().toString() : null)
                        .ngayNhapKhoVpbank(row.getNgayNhapKhoVpbank())
                        .ngayChuyenKhoCrown(row.getNgayChuyenKhoCrown())
                        .khuVuc(row.getKhuVuc())
                        .hang(row.getHang() != null ? row.getHang().toString() : null)
                        .cot(row.getCot() != null ? row.getCot().toString() : null)
                        .tinhTrangThung(row.getTinhTrangThung())
                        .trangThaiThung(row.getTrangThaiThung())
                        .luuY(row.getLuuY())
                        
                        // Normalized data
                        .khoVpbankNorm(normalizeString(row.getKhoVpbank()))
                        .maDonViNorm(normalizeString(row.getMaDonVi()))
                        .loaiChungTuNorm(normalizeString(row.getLoaiChungTu()))
                        .ngayChungTuNorm(normalizeDateString(row.getNgayChungTu()))
                        .soLuongTapNorm(row.getSoLuongTap() != null ? row.getSoLuongTap().toString() : null)
                        .maThungNorm(normalizeString(row.getMaThung()))
                        .thoiHanLuuTruNorm(row.getThoiHanLuuTru() != null ? row.getThoiHanLuuTru().toString() : null)
                        .khuVucNorm(normalizeString(row.getKhuVuc()))
                        .hangNorm(row.getHang() != null ? row.getHang().toString() : null)
                        .cotNorm(row.getCot() != null ? row.getCot().toString() : null)
                        
                        .build();
                
                stagingEntities.add(stagingRaw);
                
            } catch (Exception e) {
                log.warn("Failed to convert row {} to StagingRaw: {}", row.getRowNumber(), e.getMessage());
                
                // Create error record
                StagingRaw errorRecord = StagingRaw.builder()
                        .jobId(jobId)
                        .rowNum(row.getRowNumber())
                        .createdAt(LocalDateTime.now())
                        .parseErrors("Conversion error: " + e.getMessage())
                        .build();
                
                stagingEntities.add(errorRecord);
            }
        }
        
        return stagingEntities;
    }
    
    /**
     * Lưu batch vào database
     */
    @Transactional
    private void saveBatch(List<StagingRaw> batch, String jobId) {
        try {
            stagingRawRepository.saveAll(batch);
            log.debug("Saved batch of {} records for JobId: {}", batch.size(), jobId);
        } catch (Exception e) {
            log.error("Failed to save batch for JobId: {}, Error: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to save staging data batch", e);
        }
    }
    
    /**
     * Normalize string values
     */
    private String normalizeString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
    
    /**
     * Normalize date string to yyyy-MM-dd format
     */
    private String normalizeDateString(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Handle different date formats
            String cleaned = dateStr.trim();
            
            // dd/MM/yyyy format
            if (cleaned.matches("\\d{2}/\\d{2}/\\d{4}")) {
                String[] parts = cleaned.split("/");
                return String.format("%s-%s-%s", parts[2], parts[1], parts[0]);
            }
            
            // yyyy-MM-dd format (already normalized)
            if (cleaned.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return cleaned;
            }
            
            return cleaned; // Return as-is for other formats
            
        } catch (Exception e) {
            log.warn("Failed to normalize date string: {}", dateStr);
            return dateStr;
        }
    }
    
    /**
     * Generate unique job ID
     */
    private String generateJobId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "JOB_" + timestamp + "_" + uuid;
    }
    
    /**
     * Inner class cho kết quả ingest
     */
    @lombok.Data
    @lombok.Builder
    private static class IngestResult {
        private Long totalRows;
        private Long processedRows;
        private Long processingTimeMs;
    }
}
