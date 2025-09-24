package com.learnmore.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/migration")
public class TestMigrationController {
    
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "message", "Migration controller is working!",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @PostMapping("/excel/upload-test")
    public ResponseEntity<Map<String, Object>> uploadTest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "system") String createdBy,
            @RequestParam(defaultValue = "0") int maxRows) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }
        
        try {
            // Wrap InputStream để hỗ trợ mark/reset
            java.io.InputStream inputStream = com.learnmore.application.utils.validation.ExcelDimensionValidator
                .wrapWithBuffer(file.getInputStream());
            
            // Test trực tiếp với ExcelUtil để tránh stream closed issue
            List<TestExcelData> excelData = com.learnmore.application.utils.ExcelUtil.processExcel(
                inputStream, 
                TestExcelData.class
            );
            
            return ResponseEntity.accepted()
                    .body(Map.of(
                        "message", "Excel processed successfully with ExcelUtil",
                        "filename", file.getOriginalFilename(),
                        "size", file.getSize(),
                        "createdBy", createdBy,
                        "maxRows", maxRows,
                        "recordsProcessed", excelData.size(),
                        "status", "COMPLETED",
                        "note", "Direct ExcelUtil processing test",
                        "sampleData", excelData.stream().limit(3).toList()
                    ));
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", "Failed to process Excel: " + e.getMessage(),
                        "filename", file.getOriginalFilename(),
                        "errorType", e.getClass().getSimpleName(),
                        "stackTrace", Arrays.toString(e.getStackTrace()).substring(0, Math.min(500, Arrays.toString(e.getStackTrace()).length()))
                    ));
        }
    }
    
    // Simple test data class với annotation đúng
    public static class TestExcelData {
        @com.learnmore.application.utils.ExcelColumn(name = "ma_don_vi")
        private String maDonVi;
        
        @com.learnmore.application.utils.ExcelColumn(name = "ma_thung")
        private String maThung;
        
        @com.learnmore.application.utils.ExcelColumn(name = "ngay_chung_tu")
        private String ngayChungTu;
        
        @com.learnmore.application.utils.ExcelColumn(name = "so_luong_tap")
        private Integer soLuongTap;
        
        @com.learnmore.application.utils.ExcelColumn(name = "kho_vpbank")
        private String khoVpbank;
        
        @com.learnmore.application.utils.ExcelColumn(name = "loai_chung_tu")
        private String loaiChungTu;
        
        // Getters and setters
        public String getMaDonVi() { return maDonVi; }
        public void setMaDonVi(String maDonVi) { this.maDonVi = maDonVi; }
        
        public String getMaThung() { return maThung; }
        public void setMaThung(String maThung) { this.maThung = maThung; }
        
        public String getNgayChungTu() { return ngayChungTu; }
        public void setNgayChungTu(String ngayChungTu) { this.ngayChungTu = ngayChungTu; }
        
        public Integer getSoLuongTap() { return soLuongTap; }
        public void setSoLuongTap(Integer soLuongTap) { this.soLuongTap = soLuongTap; }
        
        public String getKhoVpbank() { return khoVpbank; }
        public void setKhoVpbank(String khoVpbank) { this.khoVpbank = khoVpbank; }
        
        public String getLoaiChungTu() { return loaiChungTu; }
        public void setLoaiChungTu(String loaiChungTu) { this.loaiChungTu = loaiChungTu; }
        
        @Override
        public String toString() {
            return String.format("TestExcelData{maDonVi='%s', maThung='%s', ngayChungTu='%s', soLuongTap=%d, khoVpbank='%s', loaiChungTu='%s'}", 
                maDonVi, maThung, ngayChungTu, soLuongTap, khoVpbank, loaiChungTu);
        }
    }
}