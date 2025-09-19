package com.learnmore.application.utils.strategy;

import com.learnmore.application.utils.config.ExcelConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Chiến lược lựa chọn phương pháp ghi Excel dựa trên ngưỡng thực tế
 * Áp dụng quy tắc: ≤2M ô → XSSF, >2M ô → SXSSF, >5M ô → khuyến nghị CSV
 */
@Slf4j
public class ExcelWriteStrategy {
    
    /**
     * Enum định nghĩa các chiến lược ghi file
     */
    public enum WriteMode {
        XSSF_TRADITIONAL,    // XSSF Workbook truyền thống (≤1.5M ô)
        SXSSF_STREAMING,     // SXSSF Streaming (>1.5M ô)
        CSV_STREAMING,       // CSV streaming (>5M ô hoặc yêu cầu đặc biệt)
        MULTI_SHEET_SPLIT    // Chia nhỏ thành nhiều sheet
    }
    
    /**
     * Quyết định chiến lược ghi dựa trên kích thước dữ liệu
     * 
     * @param dataSize số lượng bản ghi
     * @param columnCount số lượng cột
     * @param config cấu hình Excel
     * @return chiến lược ghi phù hợp
     */
    public static WriteMode determineWriteStrategy(int dataSize, int columnCount, ExcelConfig config) {
        long totalCells = (long) dataSize * columnCount;
        
        log.debug("Determining write strategy for {} rows × {} cols = {} cells", 
                dataSize, columnCount, totalCells);
        
        // Kiểm tra bắt buộc streaming mode
        if (config.isForceStreamingMode()) {
            log.info("Force streaming mode enabled - using SXSSF");
            return WriteMode.SXSSF_STREAMING;
        }
        
        // Kiểm tra ngưỡng CSV (>5M ô)
        if (config.isPreferCSVForLargeData() && totalCells > config.getCsvThreshold()) {
            log.info("Large dataset ({} cells > {} threshold) - recommending CSV streaming", 
                    totalCells, config.getCsvThreshold());
            return WriteMode.CSV_STREAMING;
        }
        
        // Áp dụng quy tắc ngưỡng thực tế
        if (totalCells <= config.getMaxCellsForXSSF()) {
            // ≤1.5M ô → XSSF ổn định với heap 1-2GB
            log.info("Small dataset ({} cells ≤ {}) - using traditional XSSF", 
                    totalCells, config.getMaxCellsForXSSF());
            return WriteMode.XSSF_TRADITIONAL;
            
        } else if (totalCells <= config.getCellCountThresholdForSXSSF()) {
            // 1.5M - 2M ô → SXSSF an toàn hơn
            log.info("Medium dataset ({} cells) - using SXSSF streaming for safety", totalCells);
            return WriteMode.SXSSF_STREAMING;
            
        } else if (dataSize > 500_000) {
            // >500k hàng → chia multi-sheet
            log.info("Very large row count ({} rows) - suggesting multi-sheet split", dataSize);
            return WriteMode.MULTI_SHEET_SPLIT;
            
        } else {
            // >2M ô → SXSSF bắt buộc
            log.info("Large dataset ({} cells > {} threshold) - using SXSSF streaming", 
                    totalCells, config.getCellCountThresholdForSXSSF());
            return WriteMode.SXSSF_STREAMING;
        }
    }
    
    /**
     * Tính toán số sheet cần thiết cho multi-sheet split
     */
    public static int calculateOptimalSheetCount(int dataSize, ExcelConfig config) {
        int maxRowsPerSheet = 500_000; // An toàn cho SXSSF
        return (int) Math.ceil((double) dataSize / maxRowsPerSheet);
    }
    
    /**
     * Tính toán kích thước cửa sổ hàng tối ưu cho SXSSF
     * Dựa trên quy mô dữ liệu và heap available
     */
    public static int calculateOptimalWindowSize(int dataSize, int columnCount, ExcelConfig config) {
        long totalCells = (long) dataSize * columnCount;
        
        // Điều chỉnh window size dựa trên kích thước dữ liệu
        if (totalCells > 10_000_000L) {
            return 100; // Cửa sổ nhỏ cho dữ liệu rất lớn
        } else if (totalCells > 5_000_000L) {
            return 250; // Cửa sổ trung bình
        } else {
            return config.getSxssfRowAccessWindowSize(); // Sử dụng config mặc định (500)
        }
    }
    
    /**
     * Kiểm tra format file có phù hợp với kích thước dữ liệu không
     */
    public static void validateFileFormat(String fileName, int dataSize, int columnCount, ExcelConfig config) {
        if (fileName.toLowerCase().endsWith(".xls")) {
            if (!config.isAllowXLSFormat()) {
                throw new IllegalArgumentException("XLS format is disabled in configuration");
            }
            
            if (dataSize > config.getMaxRowsForXLS()) {
                throw new IllegalArgumentException(
                    String.format("Data size (%d rows) exceeds XLS limit (%d rows). Use .xlsx format instead.", 
                            dataSize, config.getMaxRowsForXLS()));
            }
            
            if (columnCount > config.getMaxColsForXLS()) {
                throw new IllegalArgumentException(
                    String.format("Column count (%d) exceeds XLS limit (%d cols). Use .xlsx format instead.", 
                            columnCount, config.getMaxColsForXLS()));
            }
        }
    }
    
    /**
     * Đưa ra khuyến nghị tối ưu hóa dựa trên kích thước dữ liệu
     */
    public static String getOptimizationRecommendation(int dataSize, int columnCount, ExcelConfig config) {
        long totalCells = (long) dataSize * columnCount;
        StringBuilder recommendation = new StringBuilder();
        
        WriteMode strategy = determineWriteStrategy(dataSize, columnCount, config);
        
        recommendation.append("Strategy: ").append(strategy).append("\n");
        
        switch (strategy) {
            case XSSF_TRADITIONAL:
                recommendation.append("- Kích thước vừa phải, XSSF ổn định\n");
                recommendation.append("- Khuyến nghị: -Xmx 1-2GB\n");
                break;
                
            case SXSSF_STREAMING:
                int windowSize = calculateOptimalWindowSize(dataSize, columnCount, config);
                recommendation.append("- Sử dụng SXSSF streaming với windowSize=").append(windowSize).append("\n");
                recommendation.append("- Khuyến nghị: -Xmx 1.5-2GB+, đảm bảo dung lượng đĩa\n");
                break;
                
            case CSV_STREAMING:
                recommendation.append("- Dữ liệu rất lớn, khuyến nghị CSV streaming\n");
                recommendation.append("- CSV nhẹ hơn, xử lý nhanh hơn Excel\n");
                break;
                
            case MULTI_SHEET_SPLIT:
                int sheetCount = calculateOptimalSheetCount(dataSize, config);
                recommendation.append("- Chia thành ").append(sheetCount).append(" sheets\n");
                recommendation.append("- Mỗi sheet ≤500k hàng để đảm bảo performance\n");
                break;
        }
        
        // Cảnh báo về performance
        if (totalCells > 10_000_000L) {
            recommendation.append("⚠️ Cảnh báo: Dữ liệu rất lớn (>10M ô), có thể xử lý chậm\n");
        }
        
        if (dataSize > 1_000_000) {
            recommendation.append("💡 Gợi ý: Cân nhắc chia nhỏ file đầu vào hoặc xử lý batch\n");
        }
        
        return recommendation.toString();
    }
}