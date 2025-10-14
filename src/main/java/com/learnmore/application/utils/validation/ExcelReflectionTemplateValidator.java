package com.learnmore.application.utils.validation;

import com.learnmore.application.utils.ExcelColumn;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator tận dụng @ExcelColumn annotation và reflection để kiểm tra template
 * Tích hợp với ExcelFacade và hệ thống hiện có
 */
@Slf4j
public class ExcelReflectionTemplateValidator {
    
    private final Class<?> targetClass;
    private final Map<String, Field> excelFields;
    private final List<ExcelColumnInfo> columnInfos;
    
    public ExcelReflectionTemplateValidator(Class<?> targetClass) {
        this.targetClass = targetClass;
        this.excelFields = extractExcelFields(targetClass);
        this.columnInfos = buildColumnInfos();
        
        log.info("Created ExcelReflectionTemplateValidator for {} with {} columns", 
                targetClass.getSimpleName(), excelFields.size());
    }
    
    /**
     * Validate file Excel theo template được định nghĩa bởi @ExcelColumn annotations
     */
    public TemplateValidationResult validate(InputStream inputStream) {
        log.info("Starting Excel template validation for class: {}", targetClass.getSimpleName());
        
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            // 1. Validate số lượng sheet
            validateSheetCount(workbook, errors);
            
            // 2. Validate từng sheet
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                validateSheet(sheet, errors, warnings);
            }
            
        } catch (Exception e) {
            log.error("Error during Excel template validation", e);
            errors.add(ValidationError.of(
                "FILE_ERROR", 
                "Không thể đọc file Excel: " + e.getMessage(),
                0, 0, "FILE", "FileReadError"
            ));
        }
        
        boolean isValid = errors.isEmpty();
        log.info("Excel template validation completed. Valid: {}, Errors: {}, Warnings: {}", 
                isValid, errors.size(), warnings.size());
        
        // Tạo template definition từ reflection
        ExcelTemplateDefinition templateDefinition = buildTemplateDefinitionFromReflection();
        
        return new TemplateValidationResult(isValid, errors, warnings, templateDefinition);
    }
    
    /**
     * Extract các field có @ExcelColumn annotation
     */
    private Map<String, Field> extractExcelFields(Class<?> clazz) {
        Map<String, Field> fields = new LinkedHashMap<>();
        
        // Get all fields including inherited ones
        List<Field> allFields = getAllFields(clazz);
        
        for (Field field : allFields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null) {
                String columnName = annotation.name().isEmpty() ? field.getName() : annotation.name();
                field.setAccessible(true);
                fields.put(columnName, field);
                
                log.debug("Found Excel column: {} -> field: {}", columnName, field.getName());
            }
        }
        
        return fields;
    }
    
    /**
     * Build column information từ reflection
     */
    private List<ExcelColumnInfo> buildColumnInfos() {
        return excelFields.entrySet().stream()
                .map(entry -> {
                    String columnName = entry.getKey();
                    Field field = entry.getValue();
                    ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                    
                    return ExcelColumnInfo.builder()
                            .columnName(columnName)
                            .field(field)
                            .annotation(annotation)
                            .dataType(annotation.dataType())
                            .required(annotation.required())
                            .maxLength(annotation.maxLength() > 0 ? annotation.maxLength() : null)
                            .pattern(annotation.pattern().isEmpty() ? null : annotation.pattern())
                            .description(annotation.description().isEmpty() ? null : annotation.description())
                            .example(annotation.example().isEmpty() ? null : annotation.example())
                            .position(annotation.position().isEmpty() ? null : annotation.position())
                            .build();
                })
                .sorted(Comparator.comparing(info -> info.getColumnName()))
                .collect(Collectors.toList());
    }
    
    /**
     * Validate số lượng sheet
     */
    private void validateSheetCount(Workbook workbook, List<ValidationError> errors) {
        int sheetCount = workbook.getNumberOfSheets();
        
        if (sheetCount < 1) {
            errors.add(ValidationError.of(
                "SHEET_COUNT_MIN", 
                "File phải có ít nhất 1 sheet, hiện tại có " + sheetCount + " sheet",
                0, 0, "SHEET", "SheetCountValidation"
            ));
        }
        
        if (sheetCount > 1) {
            errors.add(ValidationError.of(
                "SHEET_COUNT_MAX", 
                "File chỉ được có 1 sheet, hiện tại có " + sheetCount + " sheet",
                0, 0, "SHEET", "SheetCountValidation"
            ));
        }
    }
    
    /**
     * Validate một sheet
     */
    private void validateSheet(Sheet sheet, List<ValidationError> errors, List<ValidationWarning> warnings) {
        String sheetName = sheet.getSheetName();
        log.debug("Validating sheet: {}", sheetName);
        
        // 1. Validate header
        validateHeader(sheet, errors, warnings);
        
        // 2. Validate cấu trúc dữ liệu
        validateDataStructure(sheet, errors, warnings);
        
        // 3. Validate số dòng dữ liệu
        validateDataRowCount(sheet, errors, warnings);
    }
    
    /**
     * Validate header của sheet
     */
    private void validateHeader(Sheet sheet, List<ValidationError> errors, List<ValidationWarning> warnings) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            errors.add(ValidationError.of(
                "HEADER_MISSING", 
                "Không tìm thấy dòng header",
                1, 0, "HEADER", "HeaderValidation"
            ));
            return;
        }
        
        // Lấy danh sách header từ Excel
        List<String> excelHeaders = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            String headerValue = cell != null ? getCellValueAsString(cell) : "";
            excelHeaders.add(headerValue.trim());
        }
        
        // Validate header bắt buộc
        validateRequiredHeaders(excelHeaders, errors);
        
        // Validate thứ tự header
        validateHeaderOrder(excelHeaders, warnings);
        
        // Validate header không được phép
        validateUnexpectedHeaders(excelHeaders, warnings);
    }
    
    /**
     * Validate các header bắt buộc
     */
    private void validateRequiredHeaders(List<String> excelHeaders, List<ValidationError> errors) {
        Set<String> requiredColumnNames = getRequiredColumnNames();
        Set<String> excelHeaderSet = new HashSet<>(excelHeaders);
        
        for (String requiredColumn : requiredColumnNames) {
            if (!excelHeaderSet.contains(requiredColumn)) {
                errors.add(ValidationError.of(
                    "REQUIRED_HEADER_MISSING", 
                    String.format("Thiếu cột bắt buộc: '%s'", requiredColumn),
                    1, 0, "HEADER", "RequiredHeaderValidation"
                ));
            }
        }
    }
    
    /**
     * Validate thứ tự header
     */
    private void validateHeaderOrder(List<String> excelHeaders, List<ValidationWarning> warnings) {
        List<String> expectedOrder = columnInfos.stream()
                .map(ExcelColumnInfo::getColumnName)
                .collect(Collectors.toList());
        
        int lastExpectedIndex = -1;
        for (String expectedColumn : expectedOrder) {
            int currentIndex = excelHeaders.indexOf(expectedColumn);
            if (currentIndex != -1) {
                if (currentIndex < lastExpectedIndex) {
                    warnings.add(ValidationWarning.of(
                        "HEADER_ORDER_WARNING", 
                        String.format("Cột '%s' không đúng thứ tự. Nên đặt ở vị trí %d", 
                            expectedColumn, lastExpectedIndex + 1),
                        1, currentIndex, "HEADER", "HeaderOrderValidation"
                    ));
                }
                lastExpectedIndex = currentIndex;
            }
        }
    }
    
    /**
     * Validate các header không được phép
     */
    private void validateUnexpectedHeaders(List<String> excelHeaders, List<ValidationWarning> warnings) {
        Set<String> allowedHeaders = excelFields.keySet();
        
        for (int i = 0; i < excelHeaders.size(); i++) {
            String header = excelHeaders.get(i);
            if (!header.isEmpty() && !allowedHeaders.contains(header)) {
                warnings.add(ValidationWarning.of(
                    "UNEXPECTED_HEADER", 
                    String.format("Cột '%s' không có trong template", header),
                    1, i, "HEADER", "UnexpectedHeaderValidation"
                ));
            }
        }
    }
    
    /**
     * Validate cấu trúc dữ liệu
     */
    private void validateDataStructure(Sheet sheet, List<ValidationError> errors, List<ValidationWarning> warnings) {
        int firstDataRow = 1; // Header ở dòng 0
        
        // Lấy header để map cột
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return;
        
        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String headerValue = getCellValueAsString(cell).trim();
                if (!headerValue.isEmpty()) {
                    columnIndexMap.put(headerValue, i);
                }
            }
        }
        
        // Validate từng dòng dữ liệu
        for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            // Kiểm tra dòng có dữ liệu không
            boolean hasData = false;
            for (int i = 0; i < row.getLastCellNum(); i++) {
                Cell cell = row.getCell(i);
                if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                    hasData = true;
                    break;
                }
            }
            
            if (hasData) {
                validateDataRow(row, rowIndex + 1, columnIndexMap, errors, warnings);
            }
        }
    }
    
    /**
     * Validate một dòng dữ liệu
     */
    private void validateDataRow(Row row, int rowNumber, Map<String, Integer> columnIndexMap, 
                                List<ValidationError> errors, List<ValidationWarning> warnings) {
        
        for (ExcelColumnInfo columnInfo : columnInfos) {
            String columnName = columnInfo.getColumnName();
            Integer columnIndex = columnIndexMap.get(columnName);
            
            if (columnIndex == null) continue; // Header không tồn tại, đã được validate ở trên
            
            Cell cell = row.getCell(columnIndex);
            String cellValue = cell != null ? getCellValueAsString(cell) : "";
            
            // Validate giá trị bắt buộc
            if (columnInfo.isRequired() && cellValue.trim().isEmpty()) {
                errors.add(ValidationError.of(
                    "REQUIRED_FIELD_EMPTY", 
                    String.format("Cột '%s' là bắt buộc nhưng đang trống", columnName),
                    rowNumber, columnIndex, cellValue, "RequiredFieldValidation"
                ));
            }
            
            // Validate giá trị cell theo annotation
            if (!cellValue.trim().isEmpty()) {
                validateCellValue(cellValue, columnInfo, rowNumber, columnIndex, errors, warnings);
            }
        }
    }
    
    /**
     * Validate giá trị cell theo annotation
     */
    private void validateCellValue(String cellValue, ExcelColumnInfo columnInfo, 
                                  int rowNumber, int columnIndex, List<ValidationError> errors, 
                                  List<ValidationWarning> warnings) {
        
        ExcelColumn annotation = columnInfo.getAnnotation();
        
        // Validate độ dài
        if (annotation.maxLength() > 0 && cellValue.length() > annotation.maxLength()) {
            errors.add(ValidationError.of(
                "FIELD_TOO_LONG", 
                String.format("Cột '%s' vượt quá độ dài cho phép (%d ký tự)", 
                    columnInfo.getColumnName(), annotation.maxLength()),
                rowNumber, columnIndex, cellValue, "LengthValidation"
            ));
        }
        
        // Validate pattern
        if (!annotation.pattern().isEmpty() && !cellValue.matches(annotation.pattern())) {
            errors.add(ValidationError.of(
                "FIELD_PATTERN_INVALID", 
                String.format("Cột '%s' không đúng định dạng. Ví dụ: %s", 
                    columnInfo.getColumnName(), annotation.example()),
                rowNumber, columnIndex, cellValue, "PatternValidation"
            ));
        }
        
        // Validate kiểu dữ liệu
        validateDataType(cellValue, columnInfo, rowNumber, columnIndex, errors, warnings);
    }
    
    /**
     * Validate kiểu dữ liệu
     */
    private void validateDataType(String cellValue, ExcelColumnInfo columnInfo, 
                                 int rowNumber, int columnIndex, List<ValidationError> errors, 
                                 List<ValidationWarning> warnings) {
        
        ExcelColumn.ColumnType dataType = columnInfo.getDataType();
        
        switch (dataType) {
            case STRING:
                // String validation đã được xử lý ở các bước trước
                break;
                
            case INTEGER:
                try {
                    Integer.parseInt(cellValue);
                } catch (NumberFormatException e) {
                    errors.add(ValidationError.of(
                        "INVALID_INTEGER", 
                        String.format("Cột '%s' phải là số nguyên", columnInfo.getColumnName()),
                        rowNumber, columnIndex, cellValue, "DataTypeValidation"
                    ));
                }
                break;
                
            case DECIMAL:
                try {
                    Double.parseDouble(cellValue);
                } catch (NumberFormatException e) {
                    errors.add(ValidationError.of(
                        "INVALID_DECIMAL", 
                        String.format("Cột '%s' phải là số thập phân", columnInfo.getColumnName()),
                        rowNumber, columnIndex, cellValue, "DataTypeValidation"
                    ));
                }
                break;
                
            case DATE:
                // Có thể mở rộng để validate format ngày tháng
                break;
                
            case EMAIL:
                if (!cellValue.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    errors.add(ValidationError.of(
                        "INVALID_EMAIL", 
                        String.format("Cột '%s' phải là email hợp lệ", columnInfo.getColumnName()),
                        rowNumber, columnIndex, cellValue, "DataTypeValidation"
                    ));
                }
                break;
                
            case PHONE:
                // Có thể mở rộng để validate format số điện thoại
                break;
                
            case BOOLEAN:
                if (!cellValue.equalsIgnoreCase("true") && !cellValue.equalsIgnoreCase("false") &&
                    !cellValue.equalsIgnoreCase("yes") && !cellValue.equalsIgnoreCase("no")) {
                    warnings.add(ValidationWarning.of(
                        "INVALID_BOOLEAN", 
                        String.format("Cột '%s' nên là true/false hoặc yes/no", columnInfo.getColumnName()),
                        rowNumber, columnIndex, cellValue, "DataTypeValidation"
                    ));
                }
                break;
                
            case CUSTOM:
                // Custom validation sẽ được xử lý bởi custom validator
                break;
        }
    }
    
    /**
     * Validate số dòng dữ liệu
     */
    private void validateDataRowCount(Sheet sheet, List<ValidationError> errors, List<ValidationWarning> warnings) {
        int dataRowCount = 0;
        
        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Bỏ qua header row
            Row row = sheet.getRow(i);
            if (row != null) {
                boolean hasData = false;
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                        hasData = true;
                        break;
                    }
                }
                if (hasData) {
                    dataRowCount++;
                }
            }
        }
        
        if (dataRowCount < 1) {
            errors.add(ValidationError.of(
                "INSUFFICIENT_DATA_ROWS", 
                String.format("File phải có ít nhất 1 dòng dữ liệu, hiện tại có %d dòng", dataRowCount),
                0, 0, "DATA", "DataRowCountValidation"
            ));
        }
    }
    
    /**
     * Lấy giá trị cell dưới dạng string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    /**
     * Lấy tất cả fields bao gồm inherited
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        
        return fields;
    }
    
    /**
     * Lấy tên các cột bắt buộc
     */
    private Set<String> getRequiredColumnNames() {
        return columnInfos.stream()
                .filter(ExcelColumnInfo::isRequired)
                .map(ExcelColumnInfo::getColumnName)
                .collect(Collectors.toSet());
    }
    
    /**
     * Tạo template definition từ reflection
     */
    public ExcelTemplateDefinition buildTemplateDefinitionFromReflection() {
        List<ExcelTemplateDefinition.ColumnDefinition> requiredColumns = new ArrayList<>();
        List<ExcelTemplateDefinition.ColumnDefinition> optionalColumns = new ArrayList<>();
        
        for (ExcelColumnInfo columnInfo : columnInfos) {
            ExcelTemplateDefinition.ColumnDefinition columnDef = ExcelTemplateDefinition.ColumnDefinition.builder()
                    .columnName(columnInfo.getColumnName())
                    .columnPosition(columnInfo.getPosition())
                    .dataType(convertColumnType(columnInfo.getDataType()))
                    .required(columnInfo.isRequired())
                    .maxLength(columnInfo.getMaxLength())
                    .pattern(columnInfo.getPattern())
                    .description(columnInfo.getDescription())
                    .example(columnInfo.getExample())
                    .build();
            
            if (columnInfo.isRequired()) {
                requiredColumns.add(columnDef);
            } else {
                optionalColumns.add(columnDef);
            }
        }
        
        return ExcelTemplateDefinition.builder()
                .templateName(targetClass.getSimpleName())
                .description("Template generated from " + targetClass.getSimpleName() + " class")
                .version("1.0")
                .minSheets(1)
                .maxSheets(1)
                .headerRowCount(1)
                .minDataRows(1)
                .maxDataRows(Integer.MAX_VALUE)
                .requiredColumns(requiredColumns)
                .optionalColumns(optionalColumns)
                .build();
    }
    
    /**
     * Convert ColumnType từ annotation sang template definition
     */
    private ExcelTemplateDefinition.ColumnType convertColumnType(ExcelColumn.ColumnType annotationType) {
        switch (annotationType) {
            case STRING: return ExcelTemplateDefinition.ColumnType.STRING;
            case INTEGER: return ExcelTemplateDefinition.ColumnType.INTEGER;
            case DECIMAL: return ExcelTemplateDefinition.ColumnType.DECIMAL;
            case DATE: return ExcelTemplateDefinition.ColumnType.DATE;
            case BOOLEAN: return ExcelTemplateDefinition.ColumnType.BOOLEAN;
            case EMAIL: return ExcelTemplateDefinition.ColumnType.EMAIL;
            case PHONE: return ExcelTemplateDefinition.ColumnType.PHONE;
            case CUSTOM: return ExcelTemplateDefinition.ColumnType.CUSTOM;
            default: return ExcelTemplateDefinition.ColumnType.STRING;
        }
    }
    
    /**
     * Thông tin cột từ reflection
     */
    @lombok.Data
    @lombok.Builder
    private static class ExcelColumnInfo {
        private String columnName;
        private Field field;
        private ExcelColumn annotation;
        private ExcelColumn.ColumnType dataType;
        private boolean required;
        private Integer maxLength;
        private String pattern;
        private String description;
        private String example;
        private String position;
    }
}
