# Fake Data API Documentation

## Tổng quan

API Fake Data được thiết kế để tạo dữ liệu giả (fake data) cho 3 entity chính: **User**, **Role**, và **Permission**. Dữ liệu được xuất ra file Excel với 3 sheet riêng biệt, sử dụng WriteStrategy pattern để tối ưu hiệu suất.

## Tính năng chính

- ✅ Tạo fake data cho 3 entity: User, Role, Permission
- ✅ Xuất dữ liệu ra file Excel với 3 sheet
- ✅ Sử dụng Java Faker để tạo dữ liệu thực tế
- ✅ Đảm bảo tính duy nhất của các trường quan trọng
- ✅ Sử dụng WriteStrategy pattern cho hiệu suất tối ưu
- ✅ API RESTful với validation đầy đủ
- ✅ Hỗ trợ tùy chỉnh số lượng records

## Cấu trúc dữ liệu

### User Entity
- **ID**: Unique identifier (format: USR-YYYYMMDD-NNNNNNNN)
- **Identity Card**: Số CMND/CCCD (12 chữ số, unique)
- **First Name**: Tên
- **Last Name**: Họ
- **Email**: Email (dựa trên tên, unique)
- **Phone Number**: Số điện thoại
- **Birth Date**: Ngày sinh (18-65 tuổi)
- **Salary**: Lương (20M-200M VND)
- **Department**: Phòng ban
- **Created At**: Thời gian tạo

### Role Entity
- **ID**: Unique identifier (format: ROLE-XXXXXXXX)
- **Name**: Tên vai trò (unique)
- **Description**: Mô tả
- **Is Active**: Trạng thái hoạt động
- **Created At**: Thời gian tạo
- **Updated At**: Thời gian cập nhật

### Permission Entity
- **ID**: Unique identifier (format: PERM-XXXXXXXX)
- **Name**: Tên quyền
- **Code**: Mã quyền (unique, format: TYPE_RESOURCE)
- **Description**: Mô tả
- **Type**: Loại quyền (READ, WRITE, DELETE, EXECUTE, CREATE, UPDATE, VIEW, MANAGE)
- **Resource**: Tài nguyên
- **Is Active**: Trạng thái hoạt động
- **Created At**: Thời gian tạo
- **Updated At**: Thời gian cập nhật

## API Endpoints

### 1. Tạo fake data với số lượng mặc định

**POST** `/api/fake-data/generate`

Tạo fake data với số lượng mặc định:
- Users: 1000
- Roles: 100
- Permissions: 100

**Response:**
```json
{
  "success": true,
  "message": "Fake data generated and exported successfully",
  "fileName": "fake_data_20241201_120000.xlsx",
  "userCount": 1000,
  "roleCount": 100,
  "permissionCount": 100,
  "timestamp": 1701234567890
}
```

### 2. Tạo fake data với số lượng user tùy chỉnh

**POST** `/api/fake-data/generate/users/{userCount}`

**Parameters:**
- `userCount` (path): Số lượng users (1-1,000,000)
- Roles: 100 (mặc định)
- Permissions: 100 (mặc định)

**Example:**
```
POST /api/fake-data/generate/users/5000
```

**Response:**
```json
{
  "success": true,
  "message": "Fake data generated and exported successfully",
  "fileName": "fake_data_20241201_120000.xlsx",
  "userCount": 5000,
  "roleCount": 100,
  "permissionCount": 100,
  "timestamp": 1701234567890
}
```

### 3. Tạo fake data với số lượng tùy chỉnh cho tất cả entity

**POST** `/api/fake-data/generate/custom`

**Parameters:**
- `userCount` (query): Số lượng users (1-1,000,000)
- `roleCount` (query): Số lượng roles (1-10,000)
- `permissionCount` (query): Số lượng permissions (1-10,000)

**Example:**
```
POST /api/fake-data/generate/custom?userCount=2000&roleCount=50&permissionCount=75
```

**Response:**
```json
{
  "success": true,
  "message": "Fake data generated and exported successfully",
  "fileName": "fake_data_20241201_120000.xlsx",
  "userCount": 2000,
  "roleCount": 50,
  "permissionCount": 75,
  "timestamp": 1701234567890
}
```

### 4. Lấy thống kê generation

**GET** `/api/fake-data/stats`

**Response:**
```json
{
  "success": true,
  "message": "Generation statistics retrieved successfully",
  "stats": {
    "uniqueIds": 1000,
    "uniqueIdentityCards": 1000,
    "departments": ["Engineering", "Sales", "Marketing", "HR", "Finance"]
  },
  "timestamp": 1701234567890
}
```

### 5. Xóa cache

**POST** `/api/fake-data/clear-cache`

**Response:**
```json
{
  "success": true,
  "message": "Generation caches cleared successfully",
  "timestamp": 1701234567890
}
```

### 6. Health check

**GET** `/api/fake-data/health`

**Response:**
```json
{
  "success": true,
  "message": "Fake data service is healthy",
  "service": "FakeDataController",
  "timestamp": 1701234567890
}
```

## Cấu trúc file Excel

File Excel được tạo sẽ có 3 sheet:

### Sheet 1: User
- Chứa dữ liệu users với đầy đủ các trường
- Header được format với style đẹp
- Auto-size columns
- Freeze header pane

### Sheet 2: Role
- Chứa dữ liệu roles với đầy đủ các trường
- Tương tự format như sheet User

### Sheet 3: Permission
- Chứa dữ liệu permissions với đầy đủ các trường
- Tương tự format như sheet User

## Validation Rules

### User Count
- Minimum: 1
- Maximum: 1,000,000
- Error message: "User count must be at least 1" hoặc "User count cannot exceed 1,000,000"

### Role Count
- Minimum: 1
- Maximum: 10,000
- Error message: "Role count must be at least 1" hoặc "Role count cannot exceed 10,000"

### Permission Count
- Minimum: 1
- Maximum: 10,000
- Error message: "Permission count must be at least 1" hoặc "Permission count cannot exceed 10,000"

## Error Handling

### 400 Bad Request
```json
{
  "success": false,
  "message": "Invalid user count: User count must be at least 1",
  "timestamp": 1701234567890
}
```

### 500 Internal Server Error
```json
{
  "success": false,
  "message": "Failed to generate fake data: [Error details]",
  "timestamp": 1701234567890
}
```

## Performance

### WriteStrategy Selection
- **XSSF**: Cho files nhỏ (< 1M cells)
- **SXSSF**: Cho files trung bình (1M - 5M cells)
- **CSV**: Cho files lớn (> 5M cells)

### Memory Optimization
- Sử dụng SXSSF với window size 1000
- Batch processing với size 1000
- Auto cleanup temp files

### Generation Speed
- Users: ~1000 records/second
- Roles: ~10000 records/second
- Permissions: ~10000 records/second

## Usage Examples

### cURL Examples

```bash
# Tạo fake data mặc định
curl -X POST http://localhost:8080/api/fake-data/generate

# Tạo fake data với 5000 users
curl -X POST http://localhost:8080/api/fake-data/generate/users/5000

# Tạo fake data tùy chỉnh
curl -X POST "http://localhost:8080/api/fake-data/generate/custom?userCount=2000&roleCount=50&permissionCount=75"

# Lấy thống kê
curl -X GET http://localhost:8080/api/fake-data/stats

# Xóa cache
curl -X POST http://localhost:8080/api/fake-data/clear-cache

# Health check
curl -X GET http://localhost:8080/api/fake-data/health
```

### JavaScript Examples

```javascript
// Tạo fake data mặc định
fetch('/api/fake-data/generate', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  }
})
.then(response => response.json())
.then(data => {
  console.log('Generated file:', data.fileName);
  console.log('User count:', data.userCount);
});

// Tạo fake data tùy chỉnh
fetch('/api/fake-data/generate/custom?userCount=2000&roleCount=50&permissionCount=75', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  }
})
.then(response => response.json())
.then(data => {
  console.log('Generated file:', data.fileName);
});
```

## Technical Details

### Dependencies
- Spring Boot 3.x
- Apache POI (Excel processing)
- Java Faker (Data generation)
- Lombok (Code generation)

### Architecture
- **Controller**: `FakeDataController` - REST API endpoints
- **Service**: `FakeDataService` - Business logic
- **Generator**: `MockDataGenerator` - Data generation
- **Strategy**: `MultiSheetWriteStrategy` - Excel writing
- **DTOs**: `User`, `Role`, `Permission` - Data models

### Configuration
- ExcelConfig với builder pattern
- Multi-sheet support
- Memory optimization settings
- Performance tuning parameters

## Troubleshooting

### Common Issues

1. **Out of Memory Error**
   - Giảm số lượng records
   - Sử dụng endpoint clear-cache
   - Tăng heap size của JVM

2. **File Generation Failed**
   - Kiểm tra disk space
   - Kiểm tra permissions
   - Xem logs để biết chi tiết lỗi

3. **Slow Generation**
   - Sử dụng số lượng records nhỏ hơn
   - Kiểm tra system resources
   - Sử dụng CSV format cho files lớn

### Logs
- Tất cả operations được log với level INFO
- Error details được log với level ERROR
- Performance metrics được log với timing

## Future Enhancements

- [ ] Support cho thêm entity types
- [ ] Custom field mapping
- [ ] Template-based generation
- [ ] Real-time progress tracking
- [ ] Batch processing với queue
- [ ] Export to other formats (CSV, JSON)
- [ ] Data validation rules
- [ ] Custom data generators




