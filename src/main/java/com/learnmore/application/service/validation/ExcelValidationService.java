package com.learnmore.application.service.validation;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
// import java.util.regex.Pattern; // Not used currently

/**
 * Service xử lý validation cho Excel data
 */
@Service
@Slf4j
public class ExcelValidationService {
    
    // Regex patterns cho validation (có thể sử dụng trong tương lai)
    // private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    // private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,11}$");
    // private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2})$");
    
    /**
     * Validate một Excel row và trả về danh sách lỗi
     * 
     * @param row Excel row cần validate
     * @return ValidationResult chứa thông tin lỗi
     */
    public ValidationResult validateRow(ExcelRowDTO row) {
        List<String> errorMessages = new ArrayList<>();
        List<String> errorCodes = new ArrayList<>();
        
        // Validate required fields
        validateRequiredFields(row, errorMessages, errorCodes);
        
        // Validate data formats
        validateDataFormats(row, errorMessages, errorCodes);
        
        // Validate business rules
        validateBusinessRules(row, errorMessages, errorCodes);
        
        return ValidationResult.builder()
                .hasErrors(!errorMessages.isEmpty())
                .errorMessage(String.join(", ", errorMessages))
                .errorCode(String.join(", ", errorCodes))
                .build();
    }
    
    /**
     * Validate các trường bắt buộc
     */
    private void validateRequiredFields(ExcelRowDTO row, List<String> errorMessages, List<String> errorCodes) {
        if (row.getMaDonVi() == null || row.getMaDonVi().trim().isEmpty()) {
            errorMessages.add("Mã đơn vị không được để trống");
            errorCodes.add("REQUIRED_MA_DON_VI");
        }
        
        if (row.getLoaiChungTu() == null || row.getLoaiChungTu().trim().isEmpty()) {
            errorMessages.add("Loại chứng từ không được để trống");
            errorCodes.add("REQUIRED_LOAI_CHUNG_TU");
        }
        
        if (row.getNgayChungTu() == null || row.getNgayChungTu().trim().isEmpty()) {
            errorMessages.add("Ngày chứng từ không được để trống");
            errorCodes.add("REQUIRED_NGAY_CHUNG_TU");
        }
        
        if (row.getMaThung() == null || row.getMaThung().trim().isEmpty()) {
            errorMessages.add("Mã thùng không được để trống");
            errorCodes.add("REQUIRED_MA_THUNG");
        }
    }
    
    /**
     * Validate định dạng dữ liệu
     */
    private void validateDataFormats(ExcelRowDTO row, List<String> errorMessages, List<String> errorCodes) {
        // Validate ngày chứng từ
        if (row.getNgayChungTu() != null && !row.getNgayChungTu().trim().isEmpty()) {
            if (!isValidDate(row.getNgayChungTu())) {
                errorMessages.add("Ngày chứng từ không đúng định dạng (dd/MM/yyyy hoặc yyyy-MM-dd)");
                errorCodes.add("INVALID_DATE_FORMAT");
            }
        }
        
        // Validate ngày phải bàn giao
        if (row.getNgayPhaiBanGiao() != null && !row.getNgayPhaiBanGiao().trim().isEmpty()) {
            if (!isValidDate(row.getNgayPhaiBanGiao())) {
                errorMessages.add("Ngày phải bàn giao không đúng định dạng (dd/MM/yyyy hoặc yyyy-MM-dd)");
                errorCodes.add("INVALID_DATE_FORMAT");
            }
        }
        
        // Validate ngày bàn giao
        if (row.getNgayBanGiao() != null && !row.getNgayBanGiao().trim().isEmpty()) {
            if (!isValidDate(row.getNgayBanGiao())) {
                errorMessages.add("Ngày bàn giao không đúng định dạng (dd/MM/yyyy hoặc yyyy-MM-dd)");
                errorCodes.add("INVALID_DATE_FORMAT");
            }
        }
        
        // Validate ngày nhập kho VPBank
        if (row.getNgayNhapKhoVpbank() != null && !row.getNgayNhapKhoVpbank().trim().isEmpty()) {
            if (!isValidDate(row.getNgayNhapKhoVpbank())) {
                errorMessages.add("Ngày nhập kho VPBank không đúng định dạng (dd/MM/yyyy hoặc yyyy-MM-dd)");
                errorCodes.add("INVALID_DATE_FORMAT");
            }
        }
        
        // Validate ngày chuyển kho Crown
        if (row.getNgayChuyenKhoCrown() != null && !row.getNgayChuyenKhoCrown().trim().isEmpty()) {
            if (!isValidDate(row.getNgayChuyenKhoCrown())) {
                errorMessages.add("Ngày chuyển kho Crown không đúng định dạng (dd/MM/yyyy hoặc yyyy-MM-dd)");
                errorCodes.add("INVALID_DATE_FORMAT");
            }
        }
        
        // Validate số lượng tập
        if (row.getSoLuongTap() != null && row.getSoLuongTap() < 0) {
            errorMessages.add("Số lượng tập phải lớn hơn hoặc bằng 0");
            errorCodes.add("INVALID_SO_LUONG_TAP");
        }
        
        // Validate thời hạn lưu trữ
        if (row.getThoiHanLuuTru() != null && row.getThoiHanLuuTru() < 0) {
            errorMessages.add("Thời hạn lưu trữ phải lớn hơn hoặc bằng 0");
            errorCodes.add("INVALID_THOI_HAN_LUU_TRU");
        }
        
        // Validate hàng
        if (row.getHang() != null && row.getHang() < 0) {
            errorMessages.add("Hàng phải lớn hơn hoặc bằng 0");
            errorCodes.add("INVALID_HANG");
        }
        
        // Validate cột
        if (row.getCot() != null && row.getCot() < 0) {
            errorMessages.add("Cột phải lớn hơn hoặc bằng 0");
            errorCodes.add("INVALID_COT");
        }
    }
    
    /**
     * Validate business rules
     */
    private void validateBusinessRules(ExcelRowDTO row, List<String> errorMessages, List<String> errorCodes) {
        // Validate ngày bàn giao phải sau ngày phải bàn giao
        if (row.getNgayPhaiBanGiao() != null && row.getNgayBanGiao() != null 
            && !row.getNgayPhaiBanGiao().trim().isEmpty() && !row.getNgayBanGiao().trim().isEmpty()) {
            
            try {
                LocalDate ngayPhaiBanGiao = parseDate(row.getNgayPhaiBanGiao());
                LocalDate ngayBanGiao = parseDate(row.getNgayBanGiao());
                
                if (ngayBanGiao.isBefore(ngayPhaiBanGiao)) {
                    errorMessages.add("Ngày bàn giao phải sau hoặc bằng ngày phải bàn giao");
                    errorCodes.add("INVALID_DATE_LOGIC");
                }
            } catch (Exception e) {
                // Date parsing errors đã được xử lý ở validateDataFormats
            }
        }
        
        // Validate mã đơn vị format (ví dụ: phải có ít nhất 3 ký tự)
        if (row.getMaDonVi() != null && row.getMaDonVi().trim().length() < 3) {
            errorMessages.add("Mã đơn vị phải có ít nhất 3 ký tự");
            errorCodes.add("INVALID_MA_DON_VI_LENGTH");
        }
        
        // Validate mã thùng format (ví dụ: phải có ít nhất 2 ký tự)
        if (row.getMaThung() != null && row.getMaThung().trim().length() < 2) {
            errorMessages.add("Mã thùng phải có ít nhất 2 ký tự");
            errorCodes.add("INVALID_MA_THUNG_LENGTH");
        }
    }
    
    /**
     * Kiểm tra định dạng ngày tháng
     */
    private boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return true; // Empty date is valid (optional field)
        }
        
        try {
            parseDate(dateStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Parse date string thành LocalDate
     */
    private LocalDate parseDate(String dateStr) {
        String cleaned = dateStr.trim();
        
        // Try dd/MM/yyyy format
        if (cleaned.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        
        // Try yyyy-MM-dd format
        if (cleaned.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        
        throw new DateTimeParseException("Invalid date format", dateStr, 0);
    }
    
    /**
     * Inner class cho kết quả validation
     */
    @lombok.Data
    @lombok.Builder
    public static class ValidationResult {
        private boolean hasErrors;
        private String errorMessage;
        private String errorCode;
    }
}
