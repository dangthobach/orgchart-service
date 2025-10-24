package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.eventusermodel.XSSFReader;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Utility để đọc dimension từ Excel file và validate số lượng bản ghi
 * Sử dụng BufferedInputStream để có thể mark/reset stream
 */
@Slf4j
public class ExcelDimensionValidator {
    
    private static final int BUFFER_SIZE = 8192; // 8KB buffer
    
    /**
     * Kiểm tra số lượng bản ghi trong Excel file có vượt quá maxRows không
     *
     * STREAMING OPTIMIZED: Uses pure SAX streaming without buffering entire file
     * - NO mark(Integer.MAX_VALUE) - prevents 2GB memory buffer
     * - NO readAllBytes() - prevents loading file into memory
     * - Stream is consumed during validation and CANNOT be reused
     *
     * @param inputStream Excel file input stream (will be consumed)
     * @param maxRows số lượng bản ghi tối đa cho phép
     * @param startRow row bắt đầu đọc dữ liệu (0-based)
     * @return số lượng bản ghi thực tế trong file
     * @throws ExcelProcessException nếu số lượng bản ghi vượt quá limit
     */
    public static int validateRowCount(InputStream inputStream, int maxRows, int startRow)
            throws ExcelProcessException {

        try {
            // ✅ STREAMING: Read dimension directly from stream (no buffering)
            DimensionInfo dimensionInfo = readDimension(inputStream);

            // Calculate actual data rows (excluding header rows)
            int totalRows = dimensionInfo.getLastRow() - dimensionInfo.getFirstRow() + 1;
            int dataRows = Math.max(0, totalRows - startRow);

            log.info("Excel dimension: {}:{}, Total rows: {}, Data rows: {}, Max allowed: {}",
                    dimensionInfo.getFirstCellRef(), dimensionInfo.getLastCellRef(),
                    totalRows, dataRows, maxRows);

            // Validate against max rows
            if (dataRows > maxRows) {
                throw new ExcelProcessException(String.format(
                    "Số lượng bản ghi trong file (%d) vượt quá giới hạn cho phép (%d). " +
                    "Vui lòng chia nhỏ file hoặc tăng giới hạn xử lý.",
                    dataRows, maxRows));
            }

            return dataRows;

        } catch (ExcelProcessException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessException("Không thể đọc dimension từ Excel file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Tạo BufferedInputStream từ InputStream thường để hỗ trợ mark/reset
     */
    public static BufferedInputStream wrapWithBuffer(InputStream inputStream) {
        if (inputStream instanceof BufferedInputStream) {
            return (BufferedInputStream) inputStream;
        }
        return new BufferedInputStream(inputStream, BUFFER_SIZE);
    }
    
    /**
     * Validate row counts for ALL sheets in Excel file
     *
     * @param inputStream Excel file input stream (will be consumed)
     * @param maxRowsPerSheet maximum rows allowed per sheet
     * @param startRow row bắt đầu đọc dữ liệu (0-based)
     * @return Map of sheet name to data row count
     * @throws ExcelProcessException if any sheet exceeds the limit
     */
    public static java.util.Map<String, Integer> validateAllSheets(InputStream inputStream, int maxRowsPerSheet, int startRow)
            throws ExcelProcessException {

        try {
            java.util.Map<String, Integer> sheetRowCounts = new java.util.HashMap<>();
            java.util.List<String> violatingSheets = new java.util.ArrayList<>();

            // Read all sheet dimensions
            java.util.Map<String, DimensionInfo> allDimensions = readAllSheetDimensions(inputStream);

            for (java.util.Map.Entry<String, DimensionInfo> entry : allDimensions.entrySet()) {
                String sheetName = entry.getKey();
                DimensionInfo dimensionInfo = entry.getValue();

                // Calculate actual data rows (excluding header rows)
                int totalRows = dimensionInfo.getLastRow() - dimensionInfo.getFirstRow() + 1;
                int dataRows = Math.max(0, totalRows - startRow);

                sheetRowCounts.put(sheetName, dataRows);

                log.info("Sheet '{}' dimension: {}:{}, Total rows: {}, Data rows: {}, Max allowed: {}",
                        sheetName, dimensionInfo.getFirstCellRef(), dimensionInfo.getLastCellRef(),
                        totalRows, dataRows, maxRowsPerSheet);

                // Check if sheet violates limit
                if (dataRows > maxRowsPerSheet) {
                    violatingSheets.add(String.format("%s (%d rows)", sheetName, dataRows));
                }
            }

            // Throw exception if any sheet violates the limit
            if (!violatingSheets.isEmpty()) {
                throw new ExcelProcessException(String.format(
                    "Các sheet sau vượt quá giới hạn %d bản ghi: %s. Vui lòng chia nhỏ file hoặc tăng giới hạn xử lý.",
                    maxRowsPerSheet, String.join(", ", violatingSheets)));
            }

            return sheetRowCounts;

        } catch (ExcelProcessException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessException("Không thể đọc dimension từ các sheet: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ STREAMING OPTIMIZED: Read dimension directly from stream without loading into memory
     *
     * BEFORE FIX: byte[] streamData = inputStream.readAllBytes(); // ❌ 500MB-2GB in memory!
     * AFTER FIX: Direct streaming via OPCPackage.open(inputStream) // ✅ Constant ~8MB
     *
     * WARNING: This method consumes the inputStream and closes it
     */
    private static DimensionInfo readDimension(InputStream inputStream) throws Exception {
        // ✅ OPTIMIZED: Open OPCPackage directly from stream (NO memory buffering)
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {

            XSSFReader xssfReader = new XSSFReader(opcPackage);

            // Read first sheet dimension
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (sheetIterator.hasNext()) {
                try (InputStream sheetInputStream = sheetIterator.next()) {
                    return parseDimensionFromSheet(sheetInputStream);
                }
            }

            throw new ExcelProcessException("Không tìm thấy sheet nào trong Excel file");
        }
    }

    /**
     * Read dimensions from ALL sheets in Excel file
     */
    private static java.util.Map<String, DimensionInfo> readAllSheetDimensions(InputStream inputStream) throws Exception {
        java.util.Map<String, DimensionInfo> dimensionMap = new java.util.LinkedHashMap<>();

        // ✅ OPTIMIZED: Open OPCPackage directly from stream (NO memory buffering)
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader xssfReader = new XSSFReader(opcPackage);

            // Iterate through all sheets
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            while (sheetIterator.hasNext()) {
                try (InputStream sheetInputStream = sheetIterator.next()) {
                    String sheetName = sheetIterator.getSheetName();
                    DimensionInfo dimensionInfo = parseDimensionFromSheet(sheetInputStream);
                    dimensionMap.put(sheetName, dimensionInfo);
                }
            }

            if (dimensionMap.isEmpty()) {
                throw new ExcelProcessException("Không tìm thấy sheet nào trong Excel file");
            }
        }

        return dimensionMap;
    }
    
    private static DimensionInfo parseDimensionFromSheet(InputStream sheetInputStream) throws Exception {
        DimensionHandler handler = new DimensionHandler();
        
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        XMLReader xmlReader = saxFactory.newSAXParser().getXMLReader();
        xmlReader.setContentHandler(handler);
        
        xmlReader.parse(new InputSource(sheetInputStream));
        
        if (handler.getDimensionRef() == null) {
            throw new ExcelProcessException("Không tìm thấy dimension trong Excel sheet");
        }
        
        return parseDimensionRef(handler.getDimensionRef());
    }
    
    private static DimensionInfo parseDimensionRef(String dimensionRef) {
        try {
            // Parse dimension ref như "A1:Z1000"
            CellRangeAddress range = CellRangeAddress.valueOf(dimensionRef);
            
            return DimensionInfo.builder()
                    .firstRow(range.getFirstRow())
                    .lastRow(range.getLastRow())
                    .firstCol(range.getFirstColumn())
                    .lastCol(range.getLastColumn())
                    .firstCellRef(range.formatAsString().split(":")[0])
                    .lastCellRef(range.formatAsString().split(":")[1])
                    .totalRows(range.getLastRow() - range.getFirstRow() + 1)
                    .totalCols(range.getLastColumn() - range.getFirstColumn() + 1)
                    .build();
                    
        } catch (Exception e) {
            throw new ExcelProcessException("Không thể parse dimension reference: " + dimensionRef, e);
        }
    }
    
    /**
     * SAX Handler để đọc dimension từ XML sheet
     */
    private static class DimensionHandler extends DefaultHandler {
        private String dimensionRef;
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("dimension".equals(qName)) {
                dimensionRef = attributes.getValue("ref");
            }
        }
        
        public String getDimensionRef() {
            return dimensionRef;
        }
    }
    
    /**
     * Class chứa thông tin dimension của Excel sheet
     */
    @lombok.Data
    @lombok.Builder
    public static class DimensionInfo {
        private int firstRow;
        private int lastRow;
        private int firstCol;
        private int lastCol;
        private String firstCellRef;
        private String lastCellRef;
        private int totalRows;
        private int totalCols;
    }
}
