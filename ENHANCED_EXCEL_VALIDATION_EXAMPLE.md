# Ví dụ sử dụng Enhanced Excel Template Validation

## 1. Validate file User Excel

```bash
# Upload file Excel và validate theo User class
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/user" \
  -F "file=@user-data.xlsx"
```

**Response thành công:**
```json
{
  "valid": true,
  "errors": [],
  "warnings": [],
  "templateDefinition": {
    "templateName": "User",
    "description": "Template generated from User class",
    "version": "1.0",
    "requiredColumns": [
      {
        "columnName": "ID",
        "dataType": "STRING",
        "required": true,
        "maxLength": 50,
        "description": "Mã định danh người dùng",
        "example": "USER001",
        "position": "A"
      },
      {
        "columnName": "Email",
        "dataType": "EMAIL",
        "required": true,
        "maxLength": 100,
        "description": "Email",
        "example": "user@example.com",
        "position": "E"
      }
    ]
  }
}
```

**Response có lỗi:**
```json
{
  "valid": false,
  "errors": [
    {
      "code": "REQUIRED_HEADER_MISSING",
      "message": "Thiếu cột bắt buộc: 'ID'",
      "rowNumber": 1,
      "columnNumber": 0,
      "cellValue": "HEADER",
      "validationType": "RequiredHeaderValidation"
    },
    {
      "code": "INVALID_EMAIL",
      "message": "Cột 'Email' phải là email hợp lệ",
      "rowNumber": 2,
      "columnNumber": 4,
      "cellValue": "invalid-email",
      "validationType": "DataTypeValidation"
    }
  ],
  "warnings": [
    {
      "code": "HEADER_ORDER_WARNING",
      "message": "Cột 'First Name' không đúng thứ tự. Nên đặt ở vị trí 3",
      "rowNumber": 1,
      "columnNumber": 2,
      "cellValue": "First Name",
      "validationType": "HeaderOrderValidation",
      "severity": "MEDIUM"
    }
  ]
}
```

## 2. Validate file Migration Excel

```bash
# Upload file Excel và validate theo ExcelRowDTO class
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/migration" \
  -F "file=@migration-data.xlsx"
```

## 3. Validate với class tùy chỉnh

```bash
# Upload file Excel và validate với class tùy chỉnh
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User"
```

## 4. Validate và đọc dữ liệu

```bash
# Validate và đọc dữ liệu
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate-and-read" \
  -F "file=@user-data.xlsx" \
  -F "className=com.learnmore.application.dto.User"
```

**Response:**
```json
{
  "valid": true,
  "message": "File Excel hợp lệ và đã đọc thành công",
  "dataCount": 1000,
  "validationResult": {
    "valid": true,
    "errors": [],
    "warnings": []
  }
}
```

## 5. Validate và đọc theo batch

```bash
# Validate và đọc dữ liệu theo batch
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate-and-read-batch" \
  -F "file=@large-user-data.xlsx" \
  -F "className=com.learnmore.application.dto.User" \
  -F "batchSize=1000"
```

**Response:**
```json
{
  "valid": true,
  "message": "File Excel hợp lệ và đã đọc thành công theo batch",
  "totalRecords": 50000,
  "processingResult": {
    "totalRecords": 50000,
    "processedRecords": 50000,
    "errorCount": 0,
    "processingTimeMs": 2500,
    "memoryUsedMB": 45.2
  },
  "validationResult": {
    "valid": true,
    "errors": [],
    "warnings": []
  }
}
```

## 6. Kiểm tra nhanh header

```bash
# Chỉ kiểm tra header có đúng không
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/header" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User"
```

**Response:**
```json
{
  "valid": true,
  "message": "Header hợp lệ"
}
```

## 7. Kiểm tra nhanh cấu trúc

```bash
# Chỉ kiểm tra cấu trúc file có đúng không
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate/structure" \
  -F "file=@data.xlsx" \
  -F "className=com.learnmore.application.dto.User"
```

## 8. Lấy thông tin class

```bash
# Lấy danh sách class có sẵn
curl -X GET "http://localhost:8080/api/excel/enhanced-template/classes"
```

**Response:**
```json
{
  "User": "com.learnmore.application.dto.User",
  "ExcelRowDTO": "com.learnmore.application.dto.migration.ExcelRowDTO"
}
```

```bash
# Lấy thông tin chi tiết class User
curl -X GET "http://localhost:8080/api/excel/enhanced-template/classes/com.learnmore.application.dto.User"
```

**Response:**
```json
{
  "templateName": "User",
  "description": "Template generated from User class",
  "version": "1.0",
  "requiredColumnCount": 5,
  "optionalColumnCount": 5,
  "totalColumnCount": 10,
  "minDataRows": 1,
  "maxDataRows": 2147483647
}
```

## 9. Sử dụng trong Java code

### Controller đơn giản

```java
@RestController
public class UserController {
    
    @Autowired
    private EnhancedExcelTemplateValidationService validationService;
    
    @PostMapping("/users/upload")
    public ResponseEntity<String> uploadUsers(@RequestParam("file") MultipartFile file) {
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

### Controller với xử lý dữ liệu

```java
@RestController
public class UserController {
    
    @Autowired
    private EnhancedExcelTemplateValidationService validationService;
    
    @Autowired
    private UserRepository userRepository;
    
    @PostMapping("/users/upload-and-save")
    public ResponseEntity<String> uploadAndSaveUsers(@RequestParam("file") MultipartFile file) {
        try {
            // Validate và đọc dữ liệu
            EnhancedExcelTemplateValidationService.ExcelValidationAndReadResult<User> result = 
                validationService.validateAndReadExcel(file.getInputStream(), User.class);
            
            if (result.isValid()) {
                List<User> users = result.getData();
                userRepository.saveAll(users);
                
                return ResponseEntity.ok("Đã lưu thành công " + users.size() + " users");
            } else {
                return ResponseEntity.badRequest().body("File không hợp lệ");
            }
            
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body("Lỗi khi đọc file: " + e.getMessage());
        }
    }
}
```

### Controller với batch processing

```java
@RestController
public class UserController {
    
    @Autowired
    private EnhancedExcelTemplateValidationService validationService;
    
    @Autowired
    private UserRepository userRepository;
    
    @PostMapping("/users/upload-and-save-batch")
    public ResponseEntity<String> uploadAndSaveUsersBatch(@RequestParam("file") MultipartFile file) {
        try {
            // Validate và đọc dữ liệu theo batch
            EnhancedExcelTemplateValidationService.ExcelValidationAndBatchReadResult<User> result = 
                validationService.validateAndReadExcelBatch(
                    file.getInputStream(), 
                    User.class, 
                    batch -> {
                        userRepository.saveAll(batch);
                        log.info("Saved batch of {} users", batch.size());
                    });
            
            if (result.isValid()) {
                ProcessingResult processingResult = result.getProcessingResult();
                return ResponseEntity.ok(String.format(
                    "Đã xử lý thành công %d users trong %d ms", 
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
}
```

## 10. Tạo DTO class mới

### Ví dụ: Product.java

```java
package com.learnmore.application.dto;

import com.learnmore.application.utils.ExcelColumn;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
public class Product {
    
    @Id
    @ExcelColumn(name = "Product ID", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING,
                description = "Mã sản phẩm", example = "PROD001", position = "A")
    @Column(name = "product_id", nullable = false, unique = true)
    private String productId;
    
    @ExcelColumn(name = "Product Name", required = true, maxLength = 200, dataType = ExcelColumn.ColumnType.STRING,
                description = "Tên sản phẩm", example = "Laptop Dell XPS 13", position = "B")
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;
    
    @ExcelColumn(name = "Price", required = true, dataType = ExcelColumn.ColumnType.DECIMAL,
                description = "Giá sản phẩm", example = "25000000", position = "C")
    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;
    
    @ExcelColumn(name = "Category", required = true, maxLength = 100, dataType = ExcelColumn.ColumnType.STRING,
                description = "Danh mục sản phẩm", example = "Electronics", position = "D")
    @Column(name = "category", nullable = false, length = 100)
    private String category;
    
    @ExcelColumn(name = "Stock Quantity", required = true, dataType = ExcelColumn.ColumnType.INTEGER,
                description = "Số lượng tồn kho", example = "100", position = "E")
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;
    
    @ExcelColumn(name = "Description", required = false, maxLength = 1000, dataType = ExcelColumn.ColumnType.STRING,
                description = "Mô tả sản phẩm", example = "High-performance laptop", position = "F")
    @Column(name = "description", length = 1000)
    private String description;
    
    @ExcelColumn(name = "Created At", required = false, dataType = ExcelColumn.ColumnType.DATE,
                pattern = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}",
                description = "Ngày tạo", example = "01/01/2024", position = "G")
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

### Sử dụng Product class

```bash
# Validate file Product Excel
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate" \
  -F "file=@product-data.xlsx" \
  -F "className=com.learnmore.application.dto.Product"

# Validate và đọc dữ liệu Product
curl -X POST "http://localhost:8080/api/excel/enhanced-template/validate-and-read" \
  -F "file=@product-data.xlsx" \
  -F "className=com.learnmore.application.dto.Product"
```

## 11. Các loại lỗi thường gặp

### Lỗi cấu trúc
- `SHEET_COUNT_MIN`: Thiếu sheet
- `SHEET_COUNT_MAX`: Quá nhiều sheet
- `HEADER_MISSING`: Không có dòng header

### Lỗi header
- `REQUIRED_HEADER_MISSING`: Thiếu cột bắt buộc
- `HEADER_ORDER_WARNING`: Thứ tự cột không đúng
- `UNEXPECTED_HEADER`: Cột không được phép

### Lỗi dữ liệu
- `REQUIRED_FIELD_EMPTY`: Trường bắt buộc bị trống
- `FIELD_TOO_LONG`: Vượt quá độ dài cho phép
- `FIELD_PATTERN_INVALID`: Không đúng định dạng
- `INVALID_INTEGER`: Không phải số nguyên
- `INVALID_DECIMAL`: Không phải số thập phân
- `INVALID_EMAIL`: Email không hợp lệ

### Lỗi file
- `EMPTY_FILE`: File trống
- `INVALID_FILE_TYPE`: Không phải file Excel
- `FILE_READ_ERROR`: Không thể đọc file
- `CLASS_NOT_FOUND`: Class không tồn tại

## 12. Performance tips

1. **Sử dụng batch processing cho file lớn:**
   ```java
   // Thay vì đọc toàn bộ vào memory
   List<User> users = validationService.validateAndReadExcel(inputStream, User.class);
   
   // Sử dụng batch processing
   validationService.validateAndReadExcelBatch(inputStream, User.class, batch -> {
       userRepository.saveAll(batch);
   });
   ```

2. **Cache validator để tăng performance:**
   ```java
   // Validator được cache tự động per class
   ExcelReflectionTemplateValidator validator = new ExcelReflectionTemplateValidator(User.class);
   ```

3. **Sử dụng validation nhanh:**
   ```java
   // Chỉ kiểm tra header
   boolean headerValid = validationService.isHeaderValid(inputStream, User.class);
   
   // Chỉ kiểm tra cấu trúc
   boolean structureValid = validationService.isStructureValid(inputStream, User.class);
   ```

## 13. Debug và monitoring

### Bật debug logging

```yaml
# application.yml
logging:
  level:
    com.learnmore.application.utils.validation: DEBUG
    com.learnmore.application.service.EnhancedExcelTemplateValidationService: INFO
```

### Monitor performance

```java
@PostMapping("/users/upload-with-metrics")
public ResponseEntity<String> uploadWithMetrics(@RequestParam("file") MultipartFile file) {
    long startTime = System.currentTimeMillis();
    
    try {
        TemplateValidationResult result = validationService.validateUserExcel(
            file.getInputStream());
        
        long validationTime = System.currentTimeMillis() - startTime;
        
        if (result.isValid()) {
            return ResponseEntity.ok(String.format(
                "File hợp lệ. Validation time: %d ms", validationTime));
        } else {
            return ResponseEntity.badRequest().body(String.format(
                "File không hợp lệ. Validation time: %d ms", validationTime));
        }
        
    } catch (IOException e) {
        return ResponseEntity.internalServerError()
            .body("Lỗi khi đọc file: " + e.getMessage());
    }
}
```
