# Enhanced Excel Template Validation System

Hệ thống validation template Excel tận dụng `@ExcelColumn` annotation và ExcelFacade hiện có.

## Tổng quan

Hệ thống mới tận dụng tối đa cơ sở hạ tầng hiện có:

### ✅ **Tận dụng ExcelFacade:**
- **Streaming processing** - Xử lý file lớn hiệu quả
- **ReflectionCache** - Cache reflection để tăng performance
- **ExcelColumnMapper** - Mapping tự động từ annotation
- **TrueStreamingSAXProcessor** - SAX-based processing

### ✅ **Mở rộng @ExcelColumn:**
- **Validation attributes** - required, maxLength, pattern, dataType
- **Metadata** - description, example, position
- **Type safety** - ColumnType enum với STRING, INTEGER, DECIMAL, DATE, EMAIL, PHONE, BOOLEAN, CUSTOM

## Cấu trúc hệ thống

```
src/main/java/com/learnmore/
├── application/
│   ├── service/
│   │   └── EnhancedExcelTemplateValidationService.java
│   ├── utils/
│   │   ├── ExcelColumn.java (enhanced)
│   │   └── validation/
│   │       └── ExcelReflectionTemplateValidator.java
│   └── dto/
│       ├── User.java (enhanced annotations)
│       └── migration/
│           └── ExcelRowDTO.java (enhanced annotations)
└── controller/
    └── EnhancedExcelTemplateValidationController.java
```

## Cách sử dụng

### 1. Validate file Excel theo class

```bash
# Validate file User
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/user" \
  -F "file=@user-data.xlsx"

# Validate file Migration
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/migration" \
  -F "file=@migration-data.xlsx"

# Validate với class tùy chỉnh
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User"
```

### 2. Validate và đọc dữ liệu

```bash
# Validate và đọc dữ liệu
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate-and-read" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User"

# Validate và đọc theo batch
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate-and-read-batch" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User" \
  -F "batchSize=1000"
```

### 3. Kiểm tra nhanh

```bash
# Kiểm tra header
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/header" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User"

# Kiểm tra cấu trúc
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/structure" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User"
```

### 4. Thông tin class

```bash
# Lấy danh sách class có sẵn
curl -X GET "http://localhost:8080/api/excel/enhanced-template/classes"

# Lấy thông tin chi tiết class
curl -X GET "http://localhost:8080/api/excel/enhanced-template/classes/com.learnmore.application.dto.User"
```

## Enhanced @ExcelColumn Annotation

### Cú pháp mới:

```java
@ExcelColumn(
    name = "Column Name",           // Tên cột trong Excel
    required = true,                // Có bắt buộc không
    maxLength = 50,                 // Độ dài tối đa
    dataType = ColumnType.STRING,   // Kiểu dữ liệu
    pattern = "\\d+",               // Pattern regex
    description = "Mô tả cột",      // Mô tả
    example = "Ví dụ",              // Ví dụ giá trị
    position = "A"                  // Vị trí cột
)
private String fieldName;
```

### Các kiểu dữ liệu:

```java
public enum ColumnType {
    STRING,     // Chuỗi ký tự
    INTEGER,    // Số nguyên
    DECIMAL,    // Số thập phân
    DATE,       // Ngày tháng
    BOOLEAN,    // True/False
    EMAIL,      // Email
    PHONE,      // Số điện thoại
    CUSTOM      // Tùy chỉnh
}
```

## Ví dụ sử dụng trong DTO

### User.java

```java
@Entity
public class User {
    
    @ExcelColumn(name = "ID", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING, 
                description = "Mã định danh người dùng", example = "USER001", position = "A")
    private String id;
    
    @ExcelColumn(name = "Email", required = true, maxLength = 100, dataType = ExcelColumn.ColumnType.EMAIL,
                description = "Email", example = "user@example.com", position = "E")
    private String email;
    
    @ExcelColumn(name = "Birth Date", required = false, dataType = ExcelColumn.ColumnType.DATE,
                pattern = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}",
                description = "Ngày sinh", example = "01/01/1990", position = "G")
    private LocalDate birthDate;
    
    @ExcelColumn(name = "Salary", required = false, dataType = ExcelColumn.ColumnType.DECIMAL,
                description = "Lương", example = "5000000", position = "H")
    private Double salary;
}
```

### ExcelRowDTO.java (Migration)

```java
public class ExcelRowDTO {
    
    @ExcelColumn(name = "Kho VPBank", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING,
                description = "Mã kho lưu trữ", example = "VPB001", position = "A")
    private String khoVpbank;
    
    @ExcelColumn(name = "Ngày chứng từ", required = true, dataType = ExcelColumn.ColumnType.DATE,
                pattern = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}",
                description = "Ngày chứng từ (dd/MM/yyyy hoặc yyyy-MM-dd)", example = "01/01/2024", position = "E")
    private String ngayChungTu;
    
    @ExcelColumn(name = "Số lượng tập", required = true, dataType = ExcelColumn.ColumnType.INTEGER,
                description = "Số nguyên dương", example = "5", position = "G")
    private Integer soLuongTap;
}
```

## Sử dụng trong Java code

### 1. Validate đơn giản

```java
@RestController
public class MyController {
    
    @Autowired
    private EnhancedExcelTemplateValidationService validationService;
    
    @PostMapping("/upload-excel")
    public ResponseEntity<String> uploadExcel(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file Excel
            TemplateValidationResult result = validationService.validateUserExcel(
                file.getInputStream());
            
            if (result.isValid()) {
                return ResponseEntity.ok("File Excel hợp lệ!");
            } else {
                StringBuilder errorMessage = new StringBuilder("File Excel không hợp lệ:\n");
                result.getErrors().forEach(error -> 
                    errorMessage.append("- ").append(error.getMessage()).append("\n"));
                
                return ResponseEntity.badRequest().body(errorMessage.toString());
            }
            
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body("Lỗi khi đọc file: " + e.getMessage());
        }
    }
}
```

### 2. Validate và đọc dữ liệu

```java
@PostMapping("/upload-and-process")
public ResponseEntity<String> uploadAndProcess(@RequestParam("file") MultipartFile file) {
    try {
        // Validate và đọc dữ liệu
        EnhancedExcelTemplateValidationService.ExcelValidationAndReadResult<User> result = 
            validationService.validateAndReadExcel(file.getInputStream(), User.class);
        
        if (result.isValid()) {
            List<User> users = result.getData();
            userRepository.saveAll(users);
            
            return ResponseEntity.ok("Đã xử lý thành công " + users.size() + " records");
        } else {
            return ResponseEntity.badRequest().body("File không hợp lệ");
        }
        
    } catch (IOException e) {
        return ResponseEntity.internalServerError()
            .body("Lỗi khi đọc file: " + e.getMessage());
    }
}
```

### 3. Validate và đọc theo batch

```java
@PostMapping("/upload-and-process-batch")
public ResponseEntity<String> uploadAndProcessBatch(@RequestParam("file") MultipartFile file) {
    try {
        // Validate và đọc dữ liệu theo batch
        EnhancedExcelTemplateValidationService.ExcelValidationAndBatchReadResult<User> result = 
            validationService.validateAndReadExcelBatch(
                file.getInputStream(), 
                User.class, 
                batch -> userRepository.saveAll(batch));
        
        if (result.isValid()) {
            ProcessingResult processingResult = result.getProcessingResult();
            return ResponseEntity.ok(String.format(
                "Đã xử lý thành công %d records trong %d ms", 
                processingResult.getTotalRecords(), 
                processingResult.getProcessingTimeMs()));
        } else {
            return ResponseEntity.badRequest().body("File không hợp lệ");
        }
        
    } catch (IOException e) {
        return ResponseEntity.internalServerError()
            .body("Lỗi khi đọc file: " + e.getMessage());
    }
}
```

## So sánh với hệ thống cũ

### ✅ **Ưu điểm của hệ thống mới:**

1. **Tận dụng cơ sở hạ tầng hiện có:**
   - ExcelFacade với streaming processing
   - ReflectionCache để tăng performance
   - ExcelColumnMapper đã được tối ưu

2. **Annotation-based validation:**
   - Không cần tạo template definition thủ công
   - Tự động từ @ExcelColumn annotations
   - Dễ bảo trì và mở rộng

3. **Type safety:**
   - ColumnType enum
   - Compile-time checking
   - IDE support

4. **Performance:**
   - Cache validator per class
   - Streaming processing
   - Zero memory accumulation

5. **Integration:**
   - Tích hợp với ExcelFacade
   - Validate và đọc dữ liệu trong một lần
   - Batch processing support

### 📊 **Performance comparison:**

| Aspect | Old System | Enhanced System |
|--------|------------|-----------------|
| Template Definition | Manual | Auto from annotations |
| Memory Usage | High (load all) | Low (streaming) |
| Performance | Medium | High (cached reflection) |
| Maintenance | High | Low (annotation-based) |
| Type Safety | Low | High (enum types) |
| Integration | Separate | Integrated with ExcelFacade |

## Migration Guide

### 1. Cập nhật @ExcelColumn annotations

```java
// Cũ
@ExcelColumn(name = "ID")
private String id;

// Mới
@ExcelColumn(name = "ID", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING,
            description = "Mã định danh", example = "ID001", position = "A")
private String id;
```

### 2. Cập nhật service calls

```java
// Cũ
TemplateValidationResult result = templateValidationService.validateExcel(inputStream, "migration");

// Mới
TemplateValidationResult result = enhancedValidationService.validateMigrationExcel(inputStream);
```

### 3. Cập nhật controller endpoints

```java
// Cũ
@PostMapping("/validate")
public ResponseEntity<TemplateValidationResult> validate(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("template") String templateName)

// Mới
@PostMapping("/validate")
public ResponseEntity<TemplateValidationResult> validate(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("className") String className)
```

## Troubleshooting

### Lỗi thường gặp

1. **"Class không tồn tại"**: Kiểm tra tên class có đúng không
2. **"File Excel không được để trống"**: Đảm bảo file được upload đúng cách
3. **"Không thể đọc file"**: Kiểm tra định dạng file (.xlsx, .xls)
4. **"Thiếu cột bắt buộc"**: Kiểm tra header có đúng annotation không

### Debug

```bash
# Bật debug logging
logging.level.com.learnmore.application.utils.validation=DEBUG
logging.level.com.learnmore.application.service.EnhancedExcelTemplateValidationService=INFO

# Kiểm tra class info
curl -X GET "http://localhost:8080/api/excel/enhanced-template/classes/com.learnmore.application.dto.User"
```

## Kết luận

Hệ thống Enhanced Excel Template Validation tận dụng tối đa cơ sở hạ tầng hiện có, cung cấp:

- ✅ **Performance cao** - Streaming processing + cached reflection
- ✅ **Dễ sử dụng** - Annotation-based validation
- ✅ **Type safety** - Enum types + compile-time checking
- ✅ **Tích hợp tốt** - Với ExcelFacade hiện có
- ✅ **Mở rộng dễ dàng** - Chỉ cần thêm annotation attributes

Hệ thống mới giữ nguyên tất cả tính năng của hệ thống cũ nhưng hiệu quả và dễ sử dụng hơn nhiều.
