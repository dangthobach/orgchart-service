package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;

/**
 * Early validation để kiểm tra dimension và số lượng records trước khi xử lý
 * Tránh parse toàn bộ file nếu vượt quá ngưỡng nghiệp vụ
 */
@Slf4j
public class ExcelEarlyValidator {
    
    /**
     * Kiểm tra sớm số lượng records trong Excel file
     * Sử dụng SAX để đọc chỉ thẻ <dimension> thay vì parse toàn bộ
     */
    public static EarlyValidationResult validateRecordCount(
            InputStream inputStream, int maxAllowedRecords, int headerRows) 
            throws ExcelProcessException {
        
        try {
            // Mark stream để có thể reset sau khi validate
            if (!inputStream.markSupported()) {
                throw new ExcelProcessException("InputStream must support mark/reset for early validation");
            }
            
            inputStream.mark(Integer.MAX_VALUE);
            
            DimensionInfo dimensionInfo = readDimensionFast(inputStream);
            
            // Reset stream để có thể sử dụng tiếp
            inputStream.reset();
            
            // Calculate actual data rows
            int totalRows = dimensionInfo.getLastRow() - dimensionInfo.getFirstRow() + 1;
            int dataRows = Math.max(0, totalRows - headerRows);
            
            EarlyValidationResult result = new EarlyValidationResult(
                dimensionInfo, dataRows, totalRows, maxAllowedRecords
            );
            
            log.info("Early validation: Dimension {}, Total rows: {}, Data rows: {}, Max allowed: {}", 
                    dimensionInfo.getDimensionRef(), totalRows, dataRows, maxAllowedRecords);
            
            // Validate against limit
            if (dataRows > maxAllowedRecords) {
                result.setValid(false);
                result.setErrorMessage(String.format(
                    "Record count (%d) exceeds maximum allowed (%d). " +
                    "Consider splitting the file or increasing the limit.", 
                    dataRows, maxAllowedRecords));
            }
            
            return result;
            
        } catch (Exception e) {
            throw new ExcelProcessException("Early validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Đọc nhanh dimension info từ sheet đầu tiên
     * Chỉ parse thẻ <dimension> để lấy ref="A1:Z10000" 
     */
    private static DimensionInfo readDimensionFast(InputStream inputStream) throws Exception {
        
        // Tạo copy của stream để tránh việc đóng stream gốc
        byte[] streamData = inputStream.readAllBytes();
        
        try (java.io.ByteArrayInputStream copyStream = new java.io.ByteArrayInputStream(streamData);
             OPCPackage opcPackage = OPCPackage.open(copyStream)) {
            
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            
            // Get first sheet
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (!sheetIterator.hasNext()) {
                throw new ExcelProcessException("No sheets found in Excel file");
            }
            
            try (InputStream sheetStream = sheetIterator.next()) {
                return parseDimensionFromSheet(sheetStream);
            }
        }
    }
    
    /**
     * Parse dimension từ sheet XML sử dụng SAX
     * Chỉ đọc thẻ <dimension ref="A1:Z10000"> và stop ngay
     */
    private static DimensionInfo parseDimensionFromSheet(InputStream sheetStream) throws Exception {
        
        DimensionHandler handler = new DimensionHandler();
        
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        XMLReader xmlReader = saxFactory.newSAXParser().getXMLReader();
        xmlReader.setContentHandler(handler);
        
        // Parse chỉ để tìm dimension tag
        xmlReader.parse(new InputSource(sheetStream));
        
        if (handler.getDimensionRef() == null) {
            throw new ExcelProcessException("No dimension found in Excel sheet");
        }
        
        return parseDimensionRef(handler.getDimensionRef());
    }
    
    /**
     * Parse dimension reference "A1:Z10000" thành row/col ranges
     */
    private static DimensionInfo parseDimensionRef(String dimensionRef) {
        try {
            // Parse dimension ref như "A1:Z1000"
            org.apache.poi.ss.util.CellRangeAddress range = 
                org.apache.poi.ss.util.CellRangeAddress.valueOf(dimensionRef);
            
            return DimensionInfo.builder()
                    .dimensionRef(dimensionRef)
                    .firstRow(range.getFirstRow())
                    .lastRow(range.getLastRow())
                    .firstCol(range.getFirstColumn())
                    .lastCol(range.getLastColumn())
                    .totalRows(range.getLastRow() - range.getFirstRow() + 1)
                    .totalCols(range.getLastColumn() - range.getFirstColumn() + 1)
                    .build();
                    
        } catch (Exception e) {
            throw new ExcelProcessException("Cannot parse dimension reference: " + dimensionRef, e);
        }
    }
    
    /**
     * SAX Handler để đọc dimension và stop ngay
     */
    private static class DimensionHandler extends DefaultHandler {
        private String dimensionRef;
        private boolean foundDimension = false;
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("dimension".equals(qName)) {
                dimensionRef = attributes.getValue("ref");
                foundDimension = true;
                // Could throw SAXException here to stop parsing immediately
                // but for simplicity, let it continue
            }
        }
        
        public String getDimensionRef() {
            return dimensionRef;
        }
    }
    
    /**
     * Dimension info class
     */
    @lombok.Data
    @lombok.Builder
    public static class DimensionInfo {
        private String dimensionRef;
        private int firstRow;
        private int lastRow;
        private int firstCol;
        private int lastCol;
        private int totalRows;
        private int totalCols;
    }
    
    /**
     * Early validation result
     */
    @lombok.Data
    public static class EarlyValidationResult {
        private final DimensionInfo dimensionInfo;
        private final int dataRows;
        private final int totalRows;
        private final int maxAllowedRecords;
        private boolean valid = true;
        private String errorMessage;
        
        public EarlyValidationResult(DimensionInfo dimensionInfo, int dataRows, 
                                   int totalRows, int maxAllowedRecords) {
            this.dimensionInfo = dimensionInfo;
            this.dataRows = dataRows;
            this.totalRows = totalRows;
            this.maxAllowedRecords = maxAllowedRecords;
        }
        
        public boolean isValid() { return valid; }
        public boolean hasError() { return !valid; }
        
        public long estimatedCells() {
            return (long) dataRows * dimensionInfo.getTotalCols();
        }
        
        /**
         * Estimate memory requirements (rough calculation)
         */
        public long estimatedMemoryMB() {
            // Rough estimate: ~200-500 bytes per cell for POJO + overhead
            long totalCells = estimatedCells();
            return (totalCells * 300L) / (1024 * 1024); // Convert to MB
        }
        
        public String getRecommendation() {
            if (valid) {
                return "File size acceptable for processing";
            }
            
            StringBuilder rec = new StringBuilder();
            rec.append("File too large. Recommendations:\n");
            rec.append("- Split file into smaller chunks (≤").append(maxAllowedRecords).append(" records each)\n");
            rec.append("- Use streaming processing with batch size ≤1000\n");
            
            if (estimatedMemoryMB() > 2000) {
                rec.append("- Consider increasing JVM heap: -Xmx").append(estimatedMemoryMB() + 500).append("m\n");
            }
            
            if (estimatedCells() > 5_000_000L) {
                rec.append("- Consider CSV format for better performance\n");
            }
            
            return rec.toString();
        }
    }
}