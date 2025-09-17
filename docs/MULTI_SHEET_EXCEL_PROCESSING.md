# Multi-Sheet Excel Processing Documentation

## Tổng quan

ExcelUtil đã được mở rộng để hỗ trợ xử lý nhiều sheet trong cùng một file Excel, mỗi sheet có thể tương ứng với một POJO khác nhau. Tính năng này rất hữu ích cho:

- Import dữ liệu tổ chức phức tạp (Users, Roles, Teams, Permissions)
- Xử lý file Excel có cấu trúc đa dạng
- Batch processing với monitoring và validation

## Các phương thức chính

### 1. processMultiSheetExcel()

Xử lý nhiều sheet với các POJO khác nhau, trả về kết quả đầy đủ.

```java
Map<String, MultiSheetResult> processMultiSheetExcel(
    InputStream inputStream, 
    Map<String, Class<?>> sheetClassMap, 
    ExcelConfig config
)
```

**Tham số:**
- `inputStream`: Luồng dữ liệu Excel file
- `sheetClassMap`: Map định nghĩa sheet name -> POJO class
- `config`: Cấu hình xử lý Excel

**Trả về:**
- `Map<String, MultiSheetResult>`: Kết quả xử lý từng sheet

### 2. processMultiSheetExcelStreaming()

Xử lý nhiều sheet với streaming approach cho file lớn.

```java
void processMultiSheetExcelStreaming(
    InputStream inputStream,
    Map<String, SheetProcessorConfig> sheetProcessors,
    ExcelConfig config
)
```

**Tham số:**
- `inputStream`: Luồng dữ liệu Excel file
- `sheetProcessors`: Map định nghĩa sheet processor cho từng sheet
- `config`: Cấu hình xử lý Excel

## Cách sử dụng

### Ví dụ 1: Multi-Sheet Processing cơ bản

```java
// Định nghĩa mapping sheet -> POJO class
Map<String, Class<?>> sheetMapping = new HashMap<>();
sheetMapping.put("Users", UserData.class);
sheetMapping.put("Roles", RoleData.class);
sheetMapping.put("Teams", TeamData.class);

// Cấu hình
ExcelConfig config = ExcelConfig.builder()
    .batchSize(1000)
    .memoryThreshold(500)
    .enableProgressTracking(true)
    .enableMemoryMonitoring(true)
    .strictValidation(true)
    .requiredFields("id", "name")
    .build();

// Xử lý Excel file
try (InputStream inputStream = new FileInputStream("organization-data.xlsx")) {
    Map<String, ExcelUtil.MultiSheetResult> results = 
        ExcelUtil.processMultiSheetExcel(inputStream, sheetMapping, config);
    
    // Xử lý kết quả
    results.forEach((sheetName, result) -> {
        if (result.isSuccessful()) {
            System.out.println("Sheet " + sheetName + ": " + 
                result.getProcessedRecords() + " records processed");
            
            // Lấy dữ liệu
            List<?> data = result.getData();
            // Xử lý data...
            
        } else {
            System.err.println("Sheet " + sheetName + " has errors:");
            result.getErrors().forEach(System.err::println);
        }
    });
}
```

### Ví dụ 2: Streaming Multi-Sheet Processing

```java
// Định nghĩa processors cho từng sheet
Map<String, ExcelUtil.SheetProcessorConfig> sheetProcessors = new HashMap<>();

// User processor
Consumer<List<UserData>> userProcessor = batch -> {
    // Xử lý batch users (save to database, validate, etc.)
    userService.saveBatch(batch);
    System.out.println("Processed " + batch.size() + " users");
};
sheetProcessors.put("Users", 
    new ExcelUtil.SheetProcessorConfig(UserData.class, userProcessor));

// Role processor  
Consumer<List<RoleData>> roleProcessor = batch -> {
    roleService.saveBatch(batch);
    System.out.println("Processed " + batch.size() + " roles");
};
sheetProcessors.put("Roles", 
    new ExcelUtil.SheetProcessorConfig(RoleData.class, roleProcessor));

// Thực hiện streaming processing
try (InputStream inputStream = new FileInputStream("large-org-data.xlsx")) {
    ExcelUtil.processMultiSheetExcelStreaming(inputStream, sheetProcessors, config);
}
```

### Ví dụ 3: POJO Classes với ExcelColumn annotation

```java
public class UserData {
    @ExcelColumn("User ID")
    private String userId;
    
    @ExcelColumn("Full Name")
    private String fullName;
    
    @ExcelColumn("Email Address")
    private String email;
    
    @ExcelColumn("Department")
    private String department;
    
    @ExcelColumn("Manager ID")
    private String managerId;
    
    @ExcelColumn("Active Status")
    private Boolean active;
    
    // Constructors, getters, setters...
}

public class RoleData {
    @ExcelColumn("Role ID")
    private String roleId;
    
    @ExcelColumn("Role Name")
    private String roleName;
    
    @ExcelColumn("Description")
    private String description;
    
    @ExcelColumn("Permission Level")
    private Integer level;
    
    // Constructors, getters, setters...
}
```

## Kết quả và Error Handling

### MultiSheetResult Class

```java
public class MultiSheetResult {
    private List<?> data;           // Dữ liệu đã xử lý
    private List<String> errors;    // Danh sách lỗi
    private int processedRecords;   // Số bản ghi đã xử lý
    private String errorMessage;    // Thông báo lỗi chính
    
    public boolean hasErrors();     // Có lỗi không
    public boolean isSuccessful();  // Xử lý thành công không
}
```

### Error Handling

Hệ thống hỗ trợ nhiều cấp độ error handling:

1. **Sheet level errors**: Sheet không tồn tại, cấu trúc không đúng
2. **Row level errors**: Dữ liệu không hợp lệ, validation failed
3. **Field level errors**: Kiểu dữ liệu không đúng, required field thiếu

```java
// Kiểm tra và xử lý lỗi
ExcelUtil.MultiSheetResult result = results.get("Users");
if (!result.isSuccessful()) {
    if (result.getErrorMessage() != null) {
        // Lỗi chính (như sheet không tồn tại)
        System.err.println("Main error: " + result.getErrorMessage());
    }
    
    if (result.hasErrors()) {
        // Lỗi chi tiết từng row
        System.err.println("Detailed errors:");
        result.getErrors().forEach(error -> System.err.println("- " + error));
    }
}
```

## Cấu hình nâng cao

### ExcelConfig cho Multi-Sheet

```java
ExcelConfig config = ExcelConfig.builder()
    .batchSize(1000)                    // Kích thước batch
    .memoryThreshold(500)               // Ngưỡng memory (MB)
    .enableProgressTracking(true)       // Theo dõi tiến độ
    .enableMemoryMonitoring(true)       // Monitor memory
    .strictValidation(true)             // Validation nghiêm ngặt
    .failOnFirstError(false)            // Tiếp tục khi có lỗi
    .requiredFields("id", "name")       // Trường bắt buộc
    .uniqueFields("id", "email")        // Trường unique
    .parallelProcessing(true)           // Xử lý song song
    .threadPoolSize(4)                  // Số thread
    .maxErrorsBeforeAbort(1000)         // Max lỗi trước khi dừng
    .build();
```

## Performance và Monitoring

### Memory Monitoring

System tự động monitor memory usage và báo cảnh khi vượt ngưỡng:

```java
// Memory alerts sẽ được log khi memory usage > threshold
// Tự động trigger GC khi cần thiết
// Có thể configure memory threshold per sheet
```

### Progress Tracking

```java
// Progress sẽ được report theo interval cấu hình
// Log format: "Processed X of Y records (Z%)"
// Có ETA estimation cho large files
```

### Performance Statistics

```java
// Lấy thống kê performance
String stats = ExcelUtil.getPerformanceStatistics();
System.out.println(stats);

// Clear caches khi cần
ExcelUtil.clearCaches();
```

## Best Practices

### 1. Memory Management

- Sử dụng streaming approach cho files > 10MB
- Set memory threshold phù hợp với heap size
- Clear caches sau khi xử lý xong

### 2. Error Handling

- Luôn kiểm tra `isSuccessful()` trước khi xử lý data
- Log errors chi tiết cho debugging
- Sử dụng `failOnFirstError=false` cho production import

### 3. Validation

- Định nghĩa required fields và unique fields
- Sử dụng custom validation rules khi cần
- Validate business logic trong batch processor

### 4. Performance Tuning

- Điều chỉnh batch size theo memory available
- Enable parallel processing cho multi-core systems
- Use appropriate thread pool size
- Monitor memory usage với large datasets

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**: Giảm batch size, tăng heap size, hoặc sử dụng streaming
2. **Sheet not found**: Kiểm tra tên sheet trong mapping
3. **Column mapping issues**: Đảm bảo ExcelColumn annotation đúng với header Excel
4. **Type conversion errors**: Kiểm tra data type compatibility
5. **Validation failures**: Review validation rules và data quality

### Debugging Tips

- Enable debug logging cho ExcelUtil package
- Use performance statistics để identify bottlenecks
- Monitor memory usage patterns
- Check error messages chi tiết trong MultiSheetResult