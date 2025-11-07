# Phân Tích Cách Check CellType Trong TrueStreamingSAXProcessor

## 🔍 Tình Trạng Hiện Tại

### ❌ **KHÔNG có direct CellType check**

Trong `TrueStreamingSAXProcessor.java`, code **KHÔNG check CellType trực tiếp** từ Cell object vì:

1. **SAX Streaming Mode**: Sử dụng `XSSFSheetXMLHandler` với SAX parser
2. **Callback chỉ cung cấp String**: Method `cell()` chỉ nhận `formattedValue` (String), không có Cell object
3. **Không có access đến CellType**: Apache POI SAX streaming không expose CellType trong callback

### 📝 Code Hiện Tại

```java
@Override
public void cell(String cellReference, String formattedValue, 
                org.apache.poi.xssf.usermodel.XSSFComment comment) {
    // ❌ Chỉ có formattedValue (String), không có CellType
    // ❌ Không có Cell object để gọi getCellType()
    
    processDataCell(colIndex, formattedValue);
}
```

## ✅ Cách Code Hiện Tại Detect Cell Type

Thay vì check CellType, code sử dụng **3-layer detection strategy**:

### **Layer 1: cellFormat từ @ExcelColumn Annotation** (HIGHEST PRIORITY)

```java
// ✅ Step 1: Check cellFormat from annotation
ExcelColumn annotation = getExcelColumnAnnotation(fieldName);
if (annotation != null && annotation.cellFormat() != CellFormatType.GENERAL) {
    return processByCellFormat(value, annotation.cellFormat(), fieldType);
}
```

**Ví dụ:**
```java
@ExcelColumn(name = "Số CMND", cellFormat = CellFormatType.IDENTIFIER)
private String identityCard;  // ✅ Explicit format
```

### **Layer 2: Auto-detect dựa trên fieldType** (FALLBACK)

```java
// ✅ Step 2: Auto-detect based on fieldType
if (isIdentifierField(fieldName, fieldType, value)) {
    return normalizeIdentifierValue(value);
}

if (isDateField(fieldType)) {
    return normalizeDateValue(value, fieldType);
}
```

**Logic:**
- `isIdentifierField()`: Check fieldType == String.class + pattern matching trên fieldName
- `isDateField()`: Check fieldType == LocalDate.class || LocalDateTime.class || Date.class

### **Layer 3: Pattern Matching trên Value** (LAST RESORT)

```java
// ✅ Step 3: Value-based detection
if (looksLikeIdentifierValue(value)) {
    // Detect scientific notation, long numbers, etc.
}
```

## 📊 So Sánh: SAX Streaming vs DOM Model

### **DOM Model (Traditional)**
```java
Cell cell = row.getCell(colIndex);
CellType cellType = cell.getCellType();  // ✅ Direct access

switch (cellType) {
    case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
            // Date
        } else {
            // Number
        }
        break;
    case STRING:
        // Text
        break;
    // ...
}
```

### **SAX Streaming (Current)**
```java
// ❌ Không có Cell object
// ❌ Không có CellType
// ✅ Chỉ có formattedValue (String)

// Phải detect dựa trên:
// 1. Annotation (@ExcelColumn.cellFormat)
// 2. Field type (Java field type)
// 3. Value pattern matching
```

## 🔧 Nếu Muốn Có CellType Information

### **Option 1: Custom XSSFSheetXMLHandler** (Phức tạp)

Có thể extend `XSSFSheetXMLHandler` để capture thêm thông tin:

```java
// Custom handler để capture cell type
public class CustomXSSFSheetXMLHandler extends XSSFSheetXMLHandler {
    private CellType currentCellType;
    
    @Override
    public void cell(String cellReference, String formattedValue, XSSFComment comment) {
        // Try to detect cell type from XML attributes
        // (Phức tạp, cần parse XML manually)
    }
}
```

**Nhược điểm:**
- Phức tạp, cần parse XML manually
- Không được Apache POI hỗ trợ chính thức
- Performance overhead

### **Option 2: Sử dụng StylesTable** (Có thể)

Có thể check cell format từ `StylesTable`:

```java
// Get cell style index from XML
int styleIndex = getCellStyleIndex(cellReference);
CellStyle cellStyle = stylesTable.getStyleAt(styleIndex);
short dataFormat = cellStyle.getDataFormat();

// Check if it's a date format
if (DateUtil.isADateFormat(dataFormat, cellStyle.getDataFormatString())) {
    // Likely a date cell
}
```

**Nhược điểm:**
- Chỉ biết format, không biết CellType (NUMERIC vs STRING)
- Phức tạp để implement trong SAX streaming

### **Option 3: Giữ nguyên approach hiện tại** (RECOMMENDED) ✅

**Ưu điểm:**
- ✅ Đơn giản, dễ maintain
- ✅ Performance tốt (không cần parse thêm)
- ✅ Đã có annotation-based approach (explicit, reliable)
- ✅ Backward compatible với auto-detect

## 📝 Kết Luận

### **Hiện Tại:**
- ❌ **KHÔNG check CellType trực tiếp** (không có access trong SAX streaming)
- ✅ **Detect dựa trên**: Annotation → FieldType → Pattern Matching

### **Recommendation:**
Giữ nguyên approach hiện tại vì:
1. SAX streaming không cung cấp CellType
2. Annotation-based approach (`cellFormat`) đã giải quyết vấn đề
3. Auto-detect fallback đã cover hầu hết cases
4. Performance tốt hơn DOM model

### **Nếu cần CellType:**
- Sử dụng DOM model thay vì SAX streaming (trade-off: memory usage)
- Hoặc implement custom handler (phức tạp, không recommended)

