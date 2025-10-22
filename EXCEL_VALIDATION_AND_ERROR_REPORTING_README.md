# Excel Validation và Error Reporting

## Tổng quan

Hệ thống đã được bổ sung tính năng validation và báo cáo lỗi cho Excel migration. Khi đọc Excel file, hệ thống sẽ:

1. **Validate dữ liệu** theo các quy tắc business
2. **Lưu thông tin lỗi** vào database với 2 cột mới: `errorMessage` và `errorCode`
3. **Tạo file lỗi Excel** với nội dung giống file upload + 2 cột lỗi
4. **Cung cấp API** để download file lỗi

## Các thay đổi chính

### 1. Entity StagingRaw - Thêm 2 cột lỗi

```java
// Thông tin lỗi validation
@Column(name = "error_message", length = 4000)
private String errorMessage;

@Column(name = "error_code", length = 1000)
private String errorCode;
```

### 2. ExcelValidationService - Service validation

- **Required fields validation**: Kiểm tra các trường bắt buộc
- **Data format validation**: Kiểm tra định dạng ngày tháng, số
- **Business rules validation**: Kiểm tra logic nghiệp vụ

**Các loại lỗi được validate:**
- `REQUIRED_MA_DON_VI`: Mã đơn vị không được để trống
- `REQUIRED_LOAI_CHUNG_TU`: Loại chứng từ không được để trống
- `REQUIRED_NGAY_CHUNG_TU`: Ngày chứng từ không được để trống
- `REQUIRED_MA_THUNG`: Mã thùng không được để trống
- `INVALID_DATE_FORMAT`: Định dạng ngày không đúng
- `INVALID_SO_LUONG_TAP`: Số lượng tập không hợp lệ
- `INVALID_DATE_LOGIC`: Logic ngày tháng không hợp lệ
- `INVALID_MA_DON_VI_LENGTH`: Độ dài mã đơn vị không hợp lệ
- `INVALID_MA_THUNG_LENGTH`: Độ dài mã thùng không hợp lệ

### 3. ExcelRowWithErrorDTO - DTO cho file lỗi

```java
@Data
@Builder
public class ExcelRowWithErrorDTO {
    // Tất cả fields từ ExcelRowDTO
    private String khoVpbank;
    private String maDonVi;
    // ... các fields khác
    
    // 2 cột lỗi mới
    private String errorMessage;
    private String errorCode;
}
```

### 4. ExcelIngestService - Cập nhật validation

- **Inline validation**: Validate trong quá trình convert dữ liệu
- **Error counting**: Đếm số lượng bản ghi lỗi
- **Error file generation**: Tạo file Excel lỗi

### 5. MigrationController - API endpoints mới

#### Download file lỗi
```http
GET /migration/job/{jobId}/errors/download
```

**Response**: File Excel (.xlsx) chứa:
- Tất cả dữ liệu từ file gốc
- 2 cột bổ sung: `errorMessage` và `errorCode`

#### Thống kê lỗi
```http
GET /migration/job/{jobId}/errors/stats
```

**Response**:
```json
{
    "jobId": "JOB_20241201120000_abc123",
    "hasErrors": true,
    "errorCount": 15,
    "errorFileAvailable": true
}
```

## Cách sử dụng

### 1. Upload Excel file

```http
POST /migration/excel/upload
Content-Type: multipart/form-data

file: [Excel file]
createdBy: "user123"
maxRows: 1000
```

**Response**:
```json
{
    "jobId": "JOB_20241201120000_abc123",
    "status": "INGESTING_COMPLETED",
    "totalRows": 1000,
    "processedRows": 1000,
    "errorRows": 15,
    "validRows": 985,
    "currentPhase": "INGEST_COMPLETED",
    "progressPercent": 100.0
}
```

### 2. Kiểm tra có lỗi không

```http
GET /migration/job/{jobId}/errors/stats
```

### 3. Download file lỗi

```http
GET /migration/job/{jobId}/errors/download
```

**Response**: File Excel với:
- Tất cả dữ liệu từ file gốc
- 2 cột bổ sung: `errorMessage` và `errorCode`
- Chỉ chứa các dòng có lỗi

## Database Schema

### Bảng staging_raw - Thêm 2 cột

```sql
ALTER TABLE staging_raw 
ADD COLUMN error_message VARCHAR(4000),
ADD COLUMN error_code VARCHAR(1000);

-- Index cho performance
CREATE INDEX idx_staging_raw_errors ON staging_raw(job_id, error_message);
```

## Performance Impact

- **Validation overhead**: ~5-10% thời gian xử lý
- **Memory usage**: Không tăng đáng kể
- **Database storage**: +2 cột per record
- **File generation**: Chỉ khi có lỗi

## Error Codes Reference

| Error Code | Description |
|------------|-------------|
| `REQUIRED_MA_DON_VI` | Mã đơn vị không được để trống |
| `REQUIRED_LOAI_CHUNG_TU` | Loại chứng từ không được để trống |
| `REQUIRED_NGAY_CHUNG_TU` | Ngày chứng từ không được để trống |
| `REQUIRED_MA_THUNG` | Mã thùng không được để trống |
| `INVALID_DATE_FORMAT` | Định dạng ngày không đúng |
| `INVALID_SO_LUONG_TAP` | Số lượng tập không hợp lệ |
| `INVALID_THOI_HAN_LUU_TRU` | Thời hạn lưu trữ không hợp lệ |
| `INVALID_HANG` | Hàng không hợp lệ |
| `INVALID_COT` | Cột không hợp lệ |
| `INVALID_DATE_LOGIC` | Logic ngày tháng không hợp lệ |
| `INVALID_MA_DON_VI_LENGTH` | Độ dài mã đơn vị không hợp lệ |
| `INVALID_MA_THUNG_LENGTH` | Độ dài mã thùng không hợp lệ |
| `CONVERSION_ERROR` | Lỗi chuyển đổi dữ liệu |

## Migration Notes

1. **Database migration**: Cần chạy ALTER TABLE để thêm 2 cột mới
2. **Backward compatibility**: Các API cũ vẫn hoạt động bình thường
3. **Error handling**: Lỗi validation không làm dừng quá trình ingest
4. **File format**: File lỗi có cùng format với file upload + 2 cột lỗi

## Testing

### Test validation rules
```bash
# Upload file với dữ liệu lỗi
curl -X POST "http://localhost:8080/migration/excel/upload" \
  -F "file=@test-data-with-errors.xlsx" \
  -F "createdBy=testuser"

# Kiểm tra lỗi
curl "http://localhost:8080/migration/job/{jobId}/errors/stats"

# Download file lỗi
curl "http://localhost:8080/migration/job/{jobId}/errors/download" \
  -o errors.xlsx
```

### Test data examples

**File Excel với lỗi:**
- Dòng 2: Thiếu mã đơn vị → `REQUIRED_MA_DON_VI`
- Dòng 3: Ngày không đúng format → `INVALID_DATE_FORMAT`
- Dòng 4: Số lượng âm → `INVALID_SO_LUONG_TAP`

**Expected error file:**
- Chứa 3 dòng lỗi
- Có 2 cột `errorMessage` và `errorCode`
- Có thể mở bằng Excel để xem chi tiết lỗi
