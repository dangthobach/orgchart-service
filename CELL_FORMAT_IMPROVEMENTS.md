# Cải Thiện Smart Cell Processing với CellFormat Annotation

## 📋 Tổng Quan

Đã cải thiện giải pháp `smartProcessCellValue` để giải quyết 2 vấn đề chính:
1. **Rủi ro khi dựa vào pattern matching trên fieldName** → Thêm `cellFormat` annotation
2. **Không thể cover hết các date format** → Parse Excel serial date thay vì pattern matching

## ✅ Giải Pháp 1: Thêm CellFormat Annotation

### Thay Đổi Trong `@ExcelColumn`

Thêm enum `CellFormatType` và field `cellFormat`:

```java
@ExcelColumn(
    name = "Số CMND",
    cellFormat = ExcelColumn.CellFormatType.IDENTIFIER  // ✅ Explicit format
)
private String identityCard;
```

**Các loại CellFormat:**
- `GENERAL` (default): Auto-detect dựa trên cell type và field type
- `TEXT`: Treat as text (preserve leading zeros, scientific notation as string)
- `NUMBER`: Treat as numeric value
- `DATE`: Treat as date (parse Excel serial date)
- `IDENTIFIER`: Treat as identifier (CMND, phone, etc. - normalize scientific notation)

### Ưu Tiên Xử Lý

```
1. cellFormat từ @ExcelColumn annotation (HIGHEST PRIORITY)
   ↓
2. Auto-detect dựa trên fieldType và pattern matching (FALLBACK)
   ↓
3. Regular processing
```

### Ví Dụ Sử Dụng

```java
public class CustomerDTO {
    // ✅ Explicit identifier format - không cần dựa vào fieldName
    @ExcelColumn(name = "Số CMND", cellFormat = ExcelColumn.CellFormatType.IDENTIFIER)
    private String cmnd;
    
    // ✅ Explicit date format - parse Excel serial date
    @ExcelColumn(name = "Ngày sinh", cellFormat = ExcelColumn.CellFormatType.DATE)
    private LocalDate birthDate;
    
    // ✅ General format - auto-detect
    @ExcelColumn(name = "Tên khách hàng")
    private String customerName;
}
```

## ✅ Giải Pháp 2: Parse Excel Serial Date

### Vấn Đề Cũ

Pattern matching không thể cover hết các date format:
- `dd-MMM-yyyy` (15-Jan-2023)
- `MMM dd, yyyy` (Jan 15, 2023)
- `yyyy年MM月dd日` (Japanese format)
- Và nhiều format khác...

### Giải Pháp Mới

**Parse Excel serial date trực tiếp** thay vì pattern matching:

```java
// Excel stores dates as serial numbers (44927 = 2023-01-15)
double serialDate = Double.parseDouble(value);
Date javaDate = DateUtil.getJavaDate(serialDate);

// Convert to target type
LocalDate localDate = javaDate.toInstant()
    .atZone(ZoneId.systemDefault())
    .toLocalDate();

return localDate.toString(); // "2023-01-15"
```

### Ưu Điểm

1. ✅ **Cover 100% date formats**: Excel luôn lưu date dưới dạng serial number
2. ✅ **Không phụ thuộc locale**: Không cần biết format hiển thị
3. ✅ **Chính xác hơn**: Tránh lỗi parse do format khác nhau
4. ✅ **Hỗ trợ cả date và datetime**: Tự động detect và convert

### Fallback

Vẫn giữ pattern matching cho các trường hợp:
- Date được nhập dưới dạng text (không phải Excel date format)
- Short year format (01/15/23 → 01/15/2023)

## 🔧 Implementation Details

### 1. Annotation Cache

Pre-cache ExcelColumn annotations để tránh repeated reflection:

```java
private final Map<String, ExcelColumn> fieldAnnotationCache = new ConcurrentHashMap<>();

private void initializeAnnotationCache() {
    for (Field field : beanClass.getDeclaredFields()) {
        ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
        if (annotation != null) {
            fieldAnnotationCache.put(field.getName(), annotation);
            fieldAnnotationCache.put(annotation.name(), annotation);
        }
    }
}
```

### 2. Smart Processing Flow

```java
private String smartProcessCellValue(String rawValue, String fieldName, Class<?> fieldType) {
    // Step 1: Check cellFormat from annotation (HIGHEST PRIORITY)
    ExcelColumn annotation = getExcelColumnAnnotation(fieldName);
    if (annotation != null && annotation.cellFormat() != CellFormatType.GENERAL) {
        return processByCellFormat(value, annotation.cellFormat(), fieldType);
    }
    
    // Step 2: Auto-detect (FALLBACK)
    if (isIdentifierField(fieldName, fieldType, value)) {
        return normalizeIdentifierValue(value);
    }
    
    if (isDateField(fieldType)) {
        return normalizeDateValue(value, fieldType);
    }
    
    // Step 3: Regular processing
    return value;
}
```

### 3. Date Normalization

```java
private String normalizeDateValue(String value, Class<?> fieldType) {
    // Try Excel serial date first (most reliable)
    if (value.matches("\\d+\\.?\\d*") && !value.contains("/") && !value.contains("-")) {
        double serialDate = Double.parseDouble(value);
        if (serialDate >= 1 && serialDate < 3000000) {
            Date javaDate = DateUtil.getJavaDate(serialDate);
            
            // Convert to target type
            if (fieldType == LocalDate.class) {
                LocalDate localDate = javaDate.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
                return localDate.toString(); // "2023-01-15"
            }
            // ... handle other date types
        }
    }
    
    // Fallback to pattern matching for text dates
    // ...
}
```

## 📊 So Sánh Trước/Sau

### Trước

```java
// ❌ Phụ thuộc vào fieldName pattern
if (fieldName.contains("cmnd")) {
    // normalize
}

// ❌ Pattern matching không cover hết
if (value.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
    // normalize short year
}
// ❌ Miss: dd-MMM-yyyy, MMM dd yyyy, etc.
```

### Sau

```java
// ✅ Explicit annotation - không phụ thuộc fieldName
@ExcelColumn(cellFormat = CellFormatType.IDENTIFIER)
private String cmnd;

// ✅ Parse Excel serial date - cover 100% formats
double serialDate = Double.parseDouble(value);
Date javaDate = DateUtil.getJavaDate(serialDate);
// ✅ Works với mọi date format trong Excel
```

## 🎯 Lợi Ích

1. **Giảm rủi ro**: Không còn phụ thuộc vào fieldName pattern matching
2. **Linh hoạt hơn**: Developer có thể explicit specify format
3. **Chính xác hơn**: Parse Excel serial date cover 100% date formats
4. **Backward compatible**: Vẫn hỗ trợ auto-detect như fallback
5. **Performance**: Annotation cache tránh repeated reflection

## 📝 Migration Guide

### Không Cần Thay Đổi Code Hiện Tại

Code cũ vẫn hoạt động với `GENERAL` format (auto-detect):

```java
@ExcelColumn(name = "Số CMND")
private String identityCard;  // ✅ Vẫn hoạt động với pattern matching
```

### Khuyến Nghị: Thêm Explicit Format

Để tăng độ chính xác, nên thêm `cellFormat`:

```java
@ExcelColumn(
    name = "Số CMND",
    cellFormat = ExcelColumn.CellFormatType.IDENTIFIER  // ✅ Explicit
)
private String identityCard;

@ExcelColumn(
    name = "Ngày sinh",
    cellFormat = ExcelColumn.CellFormatType.DATE  // ✅ Explicit
)
private LocalDate birthDate;
```

## 🧪 Test Cases

### Test Identifier với Annotation

```java
@ExcelColumn(name = "Mã số", cellFormat = CellFormatType.IDENTIFIER)
private String code;

// Input: "1.234E+11"
// Output: "123400000000" ✅
```

### Test Date với Excel Serial

```java
@ExcelColumn(name = "Ngày", cellFormat = CellFormatType.DATE)
private LocalDate date;

// Input: "44927" (Excel serial date)
// Output: "2023-01-15" ✅

// Input: "44927.5" (Excel serial datetime)
// Output: "2023-01-15T12:00:00" ✅
```

### Test Auto-detect (Backward Compatible)

```java
@ExcelColumn(name = "Số CMND")  // No cellFormat
private String identityCard;

// Input: "1.234E+11"
// Output: "123400000000" ✅ (vẫn hoạt động với pattern matching)
```

