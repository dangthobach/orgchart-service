package com.learnmore.application.utils.sax;

import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.converter.TypeConverter;
import com.learnmore.application.utils.reflection.MethodHandleMapper;
import com.learnmore.application.utils.validation.ValidationRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * True streaming SAX processor - không tích lũy kết quả trong memory
 * Đẩy từng batch trực tiếp vào batchProcessor để xử lý ngay
 */
@Slf4j
public class TrueStreamingSAXProcessor<T> {
    
    private final Class<T> beanClass;
    private final ExcelConfig config;
    private final List<ValidationRule> validationRules;
    private final TypeConverter typeConverter;
    private final Consumer<List<T>> batchProcessor;
    private final MethodHandleMapper<T> methodHandleMapper;
    
    // Cache for ExcelColumn annotations per field name
    private final Map<String, ExcelColumn> fieldAnnotationCache = new ConcurrentHashMap<>();

    
    // Statistics
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final long startTime;
    
    public TrueStreamingSAXProcessor(Class<T> beanClass, ExcelConfig config, 
                                   List<ValidationRule> validationRules, 
                                   Consumer<List<T>> batchProcessor) {
        this.beanClass = beanClass;
        this.config = config;
        this.validationRules = validationRules != null ? validationRules : new ArrayList<>();
        this.typeConverter = TypeConverter.getInstance();
        this.batchProcessor = batchProcessor;
        this.methodHandleMapper = MethodHandleMapper.forClass(beanClass);
        this.startTime = System.currentTimeMillis();
        
        // Pre-cache ExcelColumn annotations for all fields
        initializeAnnotationCache();
        
        log.info("Initialized TrueStreamingSAXProcessor with MethodHandle optimization for class: {}", 
                 beanClass.getSimpleName());
    }
    
    /**
     * Pre-cache ExcelColumn annotations for all fields to avoid repeated reflection
     */
    private void initializeAnnotationCache() {
        try {
            for (Field field : beanClass.getDeclaredFields()) {
                ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                if (annotation != null) {
                    fieldAnnotationCache.put(field.getName(), annotation);
                    // Also cache by Excel column name
                    if (!annotation.name().isEmpty()) {
                        fieldAnnotationCache.put(annotation.name(), annotation);
                    }
                }
            }
            log.debug("Cached {} ExcelColumn annotations for class {}", 
                     fieldAnnotationCache.size(), beanClass.getSimpleName());
        } catch (Exception e) {
            log.warn("Failed to initialize annotation cache: {}", e.getMessage());
        }
    }
    
    /**
     * Get ExcelColumn annotation for a field name
     */
    private ExcelColumn getExcelColumnAnnotation(String fieldName) {
        return fieldAnnotationCache.get(fieldName);
    }
    
    /**
     * Process Excel với true streaming - không tích lũy kết quả
     */
    public ProcessingResult processExcelStreamTrue(InputStream inputStream) throws Exception {
        
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            org.apache.poi.xssf.model.SharedStringsTable sharedStringsTable = 
                (org.apache.poi.xssf.model.SharedStringsTable) xssfReader.getSharedStringsTable();
            StylesTable stylesTable = xssfReader.getStylesTable();
            
            // True streaming content handler - xử lý từng batch ngay
            TrueStreamingContentHandler contentHandler = new TrueStreamingContentHandler();
            
            // Setup SAX parser with proper date formatting
            XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            
            // Create DataFormatter with proper date formatting
            DataFormatter dataFormatter = new DataFormatter();
            dataFormatter.setUseCachedValuesForFormulaCells(false);
            
            XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                stylesTable, sharedStringsTable, contentHandler, dataFormatter, false
            );
            xmlReader.setContentHandler(sheetHandler);
            
            // Process first sheet với true streaming
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (sheetIterator.hasNext()) {
                try (InputStream sheetStream = sheetIterator.next()) {
                    xmlReader.parse(new InputSource(sheetStream));
                }
            }
            
            // Flush remaining batch
            contentHandler.flushRemainingBatch();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Throw if no data rows were processed
        if (totalProcessed.get() == 0) {
            throw new RuntimeException("Tập không có dữ liệu");
        }
        
        return new ProcessingResult(
            totalProcessed.get(), 
            totalErrors.get(), 
            processingTime
        );
    }
    
    /**
     * Process a single sheet stream with provided shared resources (styles, strings, formatter)
     * This is used by TrueStreamingMultiSheetProcessor to avoid reopening OPCPackage for each sheet
     */
    public ProcessingResult processSheetStream(
            InputStream sheetStream,
            StylesTable stylesTable,
            org.apache.poi.xssf.model.SharedStringsTable sharedStringsTable,
            DataFormatter dataFormatter) throws Exception {
        
        // True streaming content handler - xử lý từng batch ngay
        TrueStreamingContentHandler contentHandler = new TrueStreamingContentHandler();
        
        // Setup SAX parser
        XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        
        XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
            stylesTable, sharedStringsTable, contentHandler, dataFormatter, false
        );
        xmlReader.setContentHandler(sheetHandler);
        
        // Process sheet stream directly
        xmlReader.parse(new InputSource(sheetStream));
        
        // Flush remaining batch
        contentHandler.flushRemainingBatch();
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Throw if no data rows were processed
        if (totalProcessed.get() == 0) {
            throw new RuntimeException("Tập không có dữ liệu");
        }
        
        return new ProcessingResult(
            totalProcessed.get(), 
            totalErrors.get(), 
            processingTime
        );
    }
    
    // fieldMapping removed; MethodHandleMapper handles both Excel column names and direct field names
    
    /**
     * True streaming content handler - xử lý batch ngay, không tích lũy
     */
    private class TrueStreamingContentHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        
        private final List<T> currentBatch = new ArrayList<>();
        private final Map<String, Integer> headerMapping = new HashMap<>();
        private final Set<String> seenUniqueValues = new HashSet<>();
        private final AtomicLong errorCount = new AtomicLong(0);
        private Object currentInstance;
        private int currentRowNum = 0;
        private boolean headerProcessed = false;
        private boolean rowHasValue = false;
        
        @Override
        public void startRow(int rowNum) {
            this.currentRowNum = rowNum;
            
            // Skip rows before start row
            if (rowNum < config.getStartRow()) {
                return;
            }
            
            // Create new instance for data rows using MethodHandle (5x faster)
            if (headerProcessed) {
                rowHasValue = false;
                try {
                    currentInstance = methodHandleMapper.createInstance();
                    
                    // Set rowNum if field exists
                    @SuppressWarnings("unchecked")
                    T typedInstance = (T) currentInstance;
                    if (methodHandleMapper.hasField("rowNum")) {
                        methodHandleMapper.setFieldValue(typedInstance, "rowNum", rowNum + 1);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to create instance for row {}: {}", rowNum, e.getMessage());
                    totalErrors.incrementAndGet();
                }
            }
        }
        
        @Override
        public void cell(String cellReference, String formattedValue, 
                        org.apache.poi.xssf.usermodel.XSSFComment comment) {
            if (currentRowNum < config.getStartRow()) {
                return;
            }
            
            int colIndex = getColumnIndex(cellReference);
            
            // Process header row
            if (currentRowNum == config.getStartRow() && !headerProcessed) {
                if (formattedValue != null && !formattedValue.trim().isEmpty()) {
                    headerMapping.put(formattedValue.trim(), colIndex);
                }
                return;
            }
            
            // Process data rows
            if (headerProcessed && currentInstance != null) {
                processDataCell(colIndex, formattedValue);
            }
        }
        
        @Override
        public void endRow(int rowNum) {
            // Mark header as processed
            if (rowNum == config.getStartRow() && !headerProcessed) {
                headerProcessed = true;
                log.debug("Header processed with {} columns", headerMapping.size());
                return;
            }

            // Process completed data row
            if (headerProcessed && currentInstance != null) {
                try {
                    // Skip completely empty data rows
                    if (!rowHasValue) {
                        log.debug("Skipping empty row {}", rowNum);
                        currentInstance = null;
                        return;
                    }
                    // ✅ INLINE maxRows VALIDATION (during streaming, NO buffering)
                    if (config.getMaxRows() > 0) {
                        int dataRowsProcessed = (int) totalProcessed.get() + 1; // +1 for current row
                        if (dataRowsProcessed > config.getMaxRows()) {
                            throw new RuntimeException(String.format(
                                "Số lượng bản ghi trong file (%d) vượt quá giới hạn cho phép (%d). " +
                                "Vui lòng chia nhỏ file hoặc tăng giới hạn xử lý.",
                                dataRowsProcessed, config.getMaxRows()));
                        }
                    }

                    // Run validations
                    runValidations(currentInstance, rowNum);

                    // Add to current batch
                    @SuppressWarnings("unchecked")
                    T typedInstance = (T) currentInstance;
                    currentBatch.add(typedInstance);
                    totalProcessed.incrementAndGet();

                    // Process batch khi đủ size
                    if (currentBatch.size() >= config.getBatchSize()) {
                        processBatch();
                    }

                    // Progress tracking - respects config.enableProgressTracking and configurable interval
                    if (config.isEnableProgressTracking() &&
                        totalProcessed.get() % config.getProgressReportInterval() == 0) {
                        log.info("Processed {} rows in streaming mode", totalProcessed.get());
                    }

                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    log.warn("Error processing row {}: {}", rowNum, e.getMessage());
                    // Re-throw if it's a maxRows violation (don't continue processing)
                    if (e.getMessage() != null && e.getMessage().contains("vượt quá giới hạn")) {
                        throw new RuntimeException(e);
                    }
                }

                currentInstance = null;
            }
        }
        
        /**
         * Process current batch và clear ngay để tiếp tục streaming
         */
        private void processBatch() {
            if (!currentBatch.isEmpty() && batchProcessor != null) {
                try {
                    // Tạo copy để xử lý
                    List<T> batchToProcess = new ArrayList<>(currentBatch);
                    
                    // Process batch ngay lập tức
                    batchProcessor.accept(batchToProcess);
                    
                    // Clear batch để tiếp tục streaming
                    currentBatch.clear();
                    
                    log.debug("Processed batch of {} records", batchToProcess.size());
                    
                } catch (Exception e) {
                    log.error("Error processing batch: {}", e.getMessage(), e);
                    totalErrors.addAndGet(currentBatch.size());
                    currentBatch.clear();
                }
            }
        }
        
        /**
         * Flush remaining batch cuối file
         */
        public void flushRemainingBatch() {
            if (!currentBatch.isEmpty()) {
                log.info("Flushing final batch of {} records", currentBatch.size());
                processBatch();
            }
        }
        
        private void processDataCell(int colIndex, String formattedValue) {
            // Find field by column index
            String fieldName = findFieldNameByColumnIndex(colIndex);
            if (fieldName == null) {
                return;
            }

            try {
                // Get field type using MethodHandle mapper
                Class<?> fieldType = methodHandleMapper.getFieldType(fieldName);
                if (fieldType != null) {
                    // Convert and set value using MethodHandle (5x faster)
                    if (formattedValue != null && !formattedValue.trim().isEmpty()) {
                        rowHasValue = true;
                    }

                    // ✅ SMART PROCESSING: Auto-detect cell type and normalize
                    String processedValue = smartProcessCellValue(formattedValue, fieldName, fieldType);
                    Object convertedValue = typeConverter.convert(processedValue, fieldType);

                    @SuppressWarnings("unchecked")
                    T typedInstance = (T) currentInstance;
                    methodHandleMapper.setFieldValue(typedInstance, fieldName, convertedValue);
                }

            } catch (Exception e) {
                log.debug("Failed to set field {} with value '{}': {}", fieldName, formattedValue, e.getMessage());
            }
        }

        /**
         * ✅ SMART CELL PROCESSING: Auto-detect and normalize cell values
         * Priority: cellFormat from annotation > fieldType > pattern matching
         * Handles:
         * 1. Identity numbers (scientific notation → plain string)
         * 2. Phone numbers (preserve leading zeros)
         * 3. Date formats (Excel serial date parsing)
         * 4. Regular numbers
         */
        private String smartProcessCellValue(String rawValue, String fieldName, Class<?> fieldType) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return rawValue;
            }

            String value = rawValue.trim();

            // ✅ Step 1: Check cellFormat from @ExcelColumn annotation (highest priority)
            ExcelColumn annotation = getExcelColumnAnnotation(fieldName);
            if (annotation != null && annotation.cellFormat() != ExcelColumn.CellFormatType.GENERAL) {
                return processByCellFormat(value, annotation.cellFormat(), fieldType);
            }

            // ✅ Step 2: Auto-detect based on fieldType and patterns (fallback)
            if (isIdentifierField(fieldName, fieldType, value)) {
                return normalizeIdentifierValue(value);
            }

            if (isDateField(fieldType)) {
                return normalizeDateValue(value, fieldType);
            }

            // ✅ Step 3: Regular processing for numbers, booleans, etc.
            return value;
        }
        
        /**
         * Process cell value based on explicit cellFormat from annotation
         */
        private String processByCellFormat(String value, ExcelColumn.CellFormatType cellFormat, Class<?> fieldType) {
            switch (cellFormat) {
                case IDENTIFIER:
                case TEXT:
                    // Normalize scientific notation and preserve format
                    return normalizeIdentifierValue(value);
                    
                case DATE:
                    // Parse Excel serial date
                    return normalizeDateValue(value, fieldType);
                    
                case NUMBER:
                    // Return as-is for numeric processing
                    return value;
                    
                case GENERAL:
                default:
                    // Should not reach here, but return as-is
                    return value;
            }
        }

        /**
         * Check if field is an identifier (should be treated as String)
         * Uses normalized field name matching and value-based detection as fallback
         */
        private boolean isIdentifierField(String fieldName, Class<?> fieldType, String value) {
            if (fieldType != String.class) {
                return false;  // Only String fields can be identifiers
            }

            // ✅ Step 1: Normalize field name (remove diacritics, spaces, convert to lowercase)
            String normalizedFieldName = normalizeFieldName(fieldName);

            // ✅ Step 2: Check patterns on normalized name
            boolean matchesPattern = normalizedFieldName.contains("identity") ||
                                   normalizedFieldName.contains("identitycard") ||
                                   normalizedFieldName.contains("cmnd") ||
                                   normalizedFieldName.contains("cccd") ||
                                   normalizedFieldName.contains("passport") ||
                                   normalizedFieldName.contains("phone") ||
                                   normalizedFieldName.contains("phonenumber") ||
                                   normalizedFieldName.contains("mobile") ||
                                   normalizedFieldName.contains("tax") ||
                                   normalizedFieldName.contains("taxcode") ||
                                   normalizedFieldName.contains("mst") ||
                                   normalizedFieldName.contains("account") ||
                                   normalizedFieldName.contains("accountnumber") ||
                                   normalizedFieldName.contains("code") ||
                                   (normalizedFieldName.contains("number") && normalizedFieldName.contains("card"));

            if (matchesPattern) {
                return true;
            }

            // ✅ Step 3: Fallback - Value-based detection
            // If fieldName doesn't match but value looks like an identifier
            if (value != null && !value.trim().isEmpty()) {
                return looksLikeIdentifierValue(value);
            }

            return false;
        }

        /**
         * Normalize field name: remove diacritics (Vietnamese accents), spaces, convert to lowercase
         * Example: "Số CMND" → "socmnd"
         */
        private String normalizeFieldName(String fieldName) {
            if (fieldName == null || fieldName.isEmpty()) {
                return "";
            }

            // Remove diacritics (Vietnamese accents)
            String normalized = Normalizer.normalize(fieldName, Normalizer.Form.NFD);
            normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

            // Convert to lowercase and remove spaces
            normalized = normalized.toLowerCase().replaceAll("\\s+", "");

            return normalized;
        }

        /**
         * Check if a value looks like an identifier based on its characteristics
         * Used as fallback when fieldName doesn't match identifier patterns
         */
        private boolean looksLikeIdentifierValue(String value) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }

            String trimmed = value.trim();

            // ✅ Check for scientific notation (strong indicator of identifier)
            if (trimmed.contains("E") || trimmed.contains("e")) {
                try {
                    java.math.BigDecimal bd = new java.math.BigDecimal(trimmed);
                    // If it's a large integer in scientific notation, likely an identifier
                    if (bd.scale() == 0 && bd.precision() > 9) {
                        log.debug("Detected identifier by scientific notation: {}", value);
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a number, continue
                }
            }

            // ✅ Check for long numeric strings (likely identifiers like CMND, phone)
            // CMND: 9-12 digits, Phone: 10-11 digits, Tax code: 10-13 digits
            if (trimmed.matches("\\d{9,15}")) {
                log.debug("Detected identifier by length pattern: {}", value);
                return true;
            }

            // ✅ Check for values with trailing ".0" (Excel number formatting issue)
            if (trimmed.matches("\\d+\\.0+")) {
                log.debug("Detected identifier by .0 pattern: {}", value);
                return true;
            }

            return false;
        }

        /**
         * Normalize identifier values
         * Converts scientific notation back to plain string
         */
        private String normalizeIdentifierValue(String value) {
            // ✅ Detect scientific notation (e.g., "1.234567E+11")
            if (value.contains("E") || value.contains("e")) {
                try {
                    java.math.BigDecimal bd = new java.math.BigDecimal(value);
                    String plainString = bd.toPlainString();

                    // Remove decimal point if it's ".0"
                    if (plainString.endsWith(".0")) {
                        plainString = plainString.substring(0, plainString.length() - 2);
                    }

                    log.debug("Normalized identifier: {} → {}", value, plainString);
                    return plainString;

                } catch (NumberFormatException e) {
                    log.warn("Failed to normalize identifier value: {}", value);
                    return value;
                }
            }

            // ✅ Remove trailing ".0" from values like "123456.0"
            if (value.matches("\\d+\\.0+")) {
                return value.substring(0, value.indexOf('.'));
            }

            return value;
        }

        /**
         * Check if field is a date type
         */
        private boolean isDateField(Class<?> fieldType) {
            return fieldType == java.time.LocalDate.class ||
                   fieldType == java.time.LocalDateTime.class ||
                   fieldType == java.util.Date.class;
        }

        /**
         * Normalize date values - Parse Excel serial date to ISO format
         * This approach handles ALL date formats by converting Excel serial date to actual date
         * instead of pattern matching which cannot cover all formats
         */
        private String normalizeDateValue(String value, Class<?> fieldType) {
            // ✅ Step 1: Try to parse as Excel serial date (most reliable method)
            // Excel serial dates are numbers (integer or decimal for time)
            if (value.matches("\\d+\\.?\\d*") && !value.contains("/") && !value.contains("-")) {
                try {
                    double serialDate = Double.parseDouble(value);
                    // Excel dates typically range from 1 to 2958465 (year 1900 to 9999)
                    if (serialDate >= 1 && serialDate < 3000000) {
                        // Convert Excel serial date to Java Date
                        Date javaDate = DateUtil.getJavaDate(serialDate);
                        
                        // Convert to target type format
                        if (fieldType == LocalDate.class) {
                            LocalDate localDate = javaDate.toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate();
                            String isoDate = localDate.toString(); // yyyy-MM-dd
                            log.debug("Converted Excel serial date {} → {}", value, isoDate);
                            return isoDate;
                        } else if (fieldType == java.time.LocalDateTime.class) {
                            java.time.LocalDateTime localDateTime = javaDate.toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime();
                            String isoDateTime = localDateTime.toString(); // yyyy-MM-ddTHH:mm:ss
                            log.debug("Converted Excel serial date {} → {}", value, isoDateTime);
                            return isoDateTime;
                        } else if (fieldType == java.util.Date.class || fieldType == java.sql.Date.class) {
                            // Return ISO format string for Date type
                            LocalDate localDate = javaDate.toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate();
                            String isoDate = localDate.toString();
                            log.debug("Converted Excel serial date {} → {}", value, isoDate);
                            return isoDate;
                        } else {
                            // Unknown date type, return serial date as-is for TypeConverter to handle
                            log.debug("Detected Excel serial date for unknown date type: {}", value);
                            return value;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse Excel serial date '{}': {}", value, e.getMessage());
                    // Not a valid Excel serial date, continue to pattern matching
                }
            }

            // ✅ Step 2: Fallback - Handle common text date formats (for backward compatibility)
            // Only handle obvious short year formats
            if (value.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
                String[] parts = value.split("/");
                if (parts.length == 3) {
                    String month = parts[0];
                    String day = parts[1];
                    String year = parts[2];

                    // Convert 2-digit year to 4-digit year
                    int yearInt = Integer.parseInt(year);
                    if (yearInt <= 30) {
                        year = "20" + year;  // 00-30 → 2000-2030
                    } else {
                        year = "19" + year;  // 31-99 → 1931-1999
                    }

                    String normalized = month + "/" + day + "/" + year;
                    log.debug("Normalized short year date: {} → {}", value, normalized);
                    return normalized;
                }
            }

            // ✅ Step 3: Handle dd-MM-yy format
            if (value.matches("\\d{1,2}-\\d{1,2}-\\d{2}")) {
                String[] parts = value.split("-");
                if (parts.length == 3) {
                    String day = parts[0];
                    String month = parts[1];
                    String year = parts[2];

                    int yearInt = Integer.parseInt(year);
                    if (yearInt <= 30) {
                        year = "20" + year;
                    } else {
                        year = "19" + year;
                    }

                    return day + "/" + month + "/" + year;
                }
            }

            // ✅ Step 4: Handle dd-MMM-yyyy format (e.g., "15-Jan-2023", "15-January-2023")
            if (value.matches("\\d{1,2}-[A-Za-z]+-\\d{4}")) {
                String[] parts = value.split("-");
                if (parts.length == 3) {
                    String day = parts[0];
                    String monthName = parts[1];
                    String year = parts[2];
                    
                    String monthNumber = parseMonthName(monthName);
                    if (monthNumber != null) {
                        String normalized = day + "/" + monthNumber + "/" + year;
                        log.debug("Normalized date format dd-MMM-yyyy: {} → {}", value, normalized);
                        return normalized;
                    }
                }
            }

            // ✅ Step 5: Handle dd-MMM-yy format (e.g., "15-Jan-23", "15-January-23")
            if (value.matches("\\d{1,2}-[A-Za-z]+-\\d{2}")) {
                String[] parts = value.split("-");
                if (parts.length == 3) {
                    String day = parts[0];
                    String monthName = parts[1];
                    String year = parts[2];
                    
                    String monthNumber = parseMonthName(monthName);
                    if (monthNumber != null) {
                        // Convert 2-digit year to 4-digit year
                        int yearInt = Integer.parseInt(year);
                        if (yearInt <= 30) {
                            year = "20" + year;  // 00-30 → 2000-2030
                        } else {
                            year = "19" + year;  // 31-99 → 1931-1999
                        }
                        
                        String normalized = day + "/" + monthNumber + "/" + year;
                        log.debug("Normalized date format dd-MMM-yy: {} → {}", value, normalized);
                        return normalized;
                    }
                }
            }

            // Return as-is for TypeConverter to handle with its formatters
            return value;
        }
        
        /**
         * Parse month name (English or Vietnamese) to month number (01-12)
         * Supports both full names and abbreviations
         */
        private String parseMonthName(String monthName) {
            if (monthName == null || monthName.trim().isEmpty()) {
                return null;
            }
            
            String normalized = monthName.trim().toLowerCase();
            
            // English month names (full and abbreviated)
            Map<String, String> englishMonths = new HashMap<>();
            englishMonths.put("january", "01");
            englishMonths.put("jan", "01");
            englishMonths.put("february", "02");
            englishMonths.put("feb", "02");
            englishMonths.put("march", "03");
            englishMonths.put("mar", "03");
            englishMonths.put("april", "04");
            englishMonths.put("apr", "04");
            englishMonths.put("may", "05");
            englishMonths.put("june", "06");
            englishMonths.put("jun", "06");
            englishMonths.put("july", "07");
            englishMonths.put("jul", "07");
            englishMonths.put("august", "08");
            englishMonths.put("aug", "08");
            englishMonths.put("september", "09");
            englishMonths.put("sep", "09");
            englishMonths.put("sept", "09");
            englishMonths.put("october", "10");
            englishMonths.put("oct", "10");
            englishMonths.put("november", "11");
            englishMonths.put("nov", "11");
            englishMonths.put("december", "12");
            englishMonths.put("dec", "12");
            
            // Vietnamese month names (full and abbreviated)
            Map<String, String> vietnameseMonths = new HashMap<>();
            vietnameseMonths.put("tháng một", "01");
            vietnameseMonths.put("tháng 1", "01");
            vietnameseMonths.put("tháng hai", "02");
            vietnameseMonths.put("tháng 2", "02");
            vietnameseMonths.put("tháng ba", "03");
            vietnameseMonths.put("tháng 3", "03");
            vietnameseMonths.put("tháng tư", "04");
            vietnameseMonths.put("tháng 4", "04");
            vietnameseMonths.put("tháng năm", "05");
            vietnameseMonths.put("tháng 5", "05");
            vietnameseMonths.put("tháng sáu", "06");
            vietnameseMonths.put("tháng 6", "06");
            vietnameseMonths.put("tháng bảy", "07");
            vietnameseMonths.put("tháng 7", "07");
            vietnameseMonths.put("tháng tám", "08");
            vietnameseMonths.put("tháng 8", "08");
            vietnameseMonths.put("tháng chín", "09");
            vietnameseMonths.put("tháng 9", "09");
            vietnameseMonths.put("tháng mười", "10");
            vietnameseMonths.put("tháng 10", "10");
            vietnameseMonths.put("tháng mười một", "11");
            vietnameseMonths.put("tháng 11", "11");
            vietnameseMonths.put("tháng mười hai", "12");
            vietnameseMonths.put("tháng 12", "12");
            
            // Check English months first
            String monthNumber = englishMonths.get(normalized);
            if (monthNumber != null) {
                return monthNumber;
            }
            
            // Check Vietnamese months
            monthNumber = vietnameseMonths.get(normalized);
            if (monthNumber != null) {
                return monthNumber;
            }
            
            // Try to parse as number (01-12)
            try {
                int monthInt = Integer.parseInt(normalized);
                if (monthInt >= 1 && monthInt <= 12) {
                    return String.format("%02d", monthInt);
                }
            } catch (NumberFormatException ignored) {
                // Not a number
            }
            
            log.debug("Failed to parse month name: {}", monthName);
            return null;
        }
        
        /**
         * Find actual field name by column index
         * Always returns the actual Java field name, not Excel column name
         */
        private String findFieldNameByColumnIndex(int colIndex) {
            for (Map.Entry<String, Integer> entry : headerMapping.entrySet()) {
                if (entry.getValue().equals(colIndex)) {
                    String headerName = entry.getKey();
                    
                    // Step 1: Check if headerName is a direct field name (camelCase pattern)
                    if (methodHandleMapper.hasField(headerName) && isFieldNamePattern(headerName)) {
                        return headerName;
                    }
                    
                    // Step 2: Resolve Excel column name to actual field name via annotation
                    String actualFieldName = resolveExcelColumnToFieldName(headerName);
                    if (actualFieldName != null && methodHandleMapper.hasField(actualFieldName)) {
                        return actualFieldName;
                    }
                    
                    // Step 3: Fallback to headerName if mapper knows it (for backward compatibility)
                    if (methodHandleMapper.hasField(headerName)) {
                        return headerName;
                    }
                }
            }
            return null;
        }
        
        /**
         * Check if a string matches Java field name pattern (camelCase, no spaces, no special chars)
         */
        private boolean isFieldNamePattern(String name) {
            if (name == null || name.isEmpty()) {
                return false;
            }
            // Field names are typically camelCase, no spaces, no special characters except underscore
            return name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
        }
        
        /**
         * Resolve Excel column name to actual Java field name by checking ExcelColumn annotations
         */
        private String resolveExcelColumnToFieldName(String excelColumnName) {
            try {
                for (Field field : beanClass.getDeclaredFields()) {
                    ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                    if (annotation != null && excelColumnName.equals(annotation.name())) {
                        return field.getName();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to resolve Excel column '{}' to field name: {}", excelColumnName, e.getMessage());
            }
            return null;
        }
        
        private void runValidations(Object instance, int rowNum) {
            try {
                // Required fields validation
                for (String requiredField : config.getRequiredFields()) {
                    if (methodHandleMapper.hasField(requiredField)) {
                        @SuppressWarnings("unchecked")
                        T typedInstance = (T) instance;
                        Object value = methodHandleMapper.getFieldValue(typedInstance, requiredField);
                        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                            log.warn("Required field '{}' is empty at row {}", requiredField, rowNum);
                            errorCount.incrementAndGet();
                        }
                    }
                }
                
                // Unique fields validation (simple memory-based check for current batch)
                for (String uniqueField : config.getUniqueFields()) {
                    if (methodHandleMapper.hasField(uniqueField)) {
                        @SuppressWarnings("unchecked")
                        T typedInstance = (T) instance;
                        Object value = methodHandleMapper.getFieldValue(typedInstance, uniqueField);
                        if (value != null) {
                            String key = uniqueField + ":" + value.toString();
                            if (seenUniqueValues.contains(key)) {
                                log.warn("Duplicate value '{}' for unique field '{}' at row {}", 
                                        value, uniqueField, rowNum);
                                errorCount.incrementAndGet();
                            } else {
                                seenUniqueValues.add(key);
                            }
                        }
                    }
                }
                
                // Custom field validation rules
                for (Map.Entry<String, ValidationRule> entry : config.getFieldValidationRules().entrySet()) {
                    String fieldName = entry.getKey();
                    ValidationRule rule = entry.getValue();
                    if (methodHandleMapper.hasField(fieldName)) {
                        @SuppressWarnings("unchecked")
                        T typedInstance = (T) instance;
                        Object value = methodHandleMapper.getFieldValue(typedInstance, fieldName);
                        if (value != null) {
                            var result = rule.validate(fieldName, value, rowNum, 0);
                            if (!result.isValid()) {
                                log.warn("Validation failed for field '{}' with value '{}' at row {}: {}", 
                                        fieldName, value, rowNum, result.getErrorMessage());
                                errorCount.incrementAndGet();
                            }
                        }
                    }
                }
                
                // Global validation rules
                for (ValidationRule rule : config.getGlobalValidationRules()) {
                    var result = rule.validate("global", instance, rowNum, 0);
                    if (!result.isValid()) {
                        log.warn("Global validation failed for instance at row {}: {}", rowNum, result.getErrorMessage());
                        errorCount.incrementAndGet();
                    }
                }
                
            } catch (Exception e) {
                log.error("Validation error at row {}: {}", rowNum, e.getMessage());
                errorCount.incrementAndGet();
            }
        }
        
        private int getColumnIndex(String cellReference) {
            // Extract column index from cell reference (e.g., "A1" -> 0, "B1" -> 1)
            String colRef = cellReference.replaceAll("\\d", "");
            int colIndex = 0;
            for (int i = 0; i < colRef.length(); i++) {
                colIndex = colIndex * 26 + (colRef.charAt(i) - 'A' + 1);
            }
            return colIndex - 1;
        }
    }
    
    /**
     * Result class for true streaming processing
     */
    public static class ProcessingResult {
        private final long processedRecords;
        private final long errorCount;
        private final long processingTimeMs;
        
        public ProcessingResult(long processedRecords, long errorCount, long processingTimeMs) {
            this.processedRecords = processedRecords;
            this.errorCount = errorCount;
            this.processingTimeMs = processingTimeMs;
        }
        
        public long getProcessedRecords() { return processedRecords; }
        public long getErrorCount() { return errorCount; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public double getRecordsPerSecond() { 
            return processingTimeMs > 0 ? (processedRecords * 1000.0) / processingTimeMs : 0; 
        }
        
        @Override
        public String toString() {
            return String.format("ProcessingResult{processed=%d, errors=%d, time=%dms, rate=%.1f rec/sec}", 
                    processedRecords, errorCount, processingTimeMs, getRecordsPerSecond());
        }
    }
}