# Excel Migration Service

Hệ thống migration dữ liệu Excel với hiệu năng cao, hỗ trợ xử lý 1-2 triệu bản ghi với kiến trúc 4 pha:

## Kiến trúc

### Pha 1: Ingest & Staging
- Đọc Excel file bằng streaming để tối ưu bộ nhớ
- Chuẩn hóa dữ liệu và lưu vào `staging_raw`
- Batch processing 5,000 records/lần

### Pha 2: Validation
- Validate dữ liệu bắt buộc, format, enum
- Check duplicate trong file và với DB
- Validate tham chiếu master tables
- Sử dụng SQL set-based operations

### Pha 3: Apply Data
- Insert vào master tables theo thứ tự phụ thuộc
- Bulk insert để tối ưu hiệu năng
- Đảm bảo idempotent và data consistency

### Pha 4: Monitor & Reconcile
- Đối soát dữ liệu giữa staging và master
- Báo cáo thống kê và metrics
- Cleanup staging data

## Cấu trúc Database

### Master Tables
- `warehouse` - Kho VPBank
- `unit` - Đơn vị
- `doc_type` - Loại chứng từ  
- `status` - Các trạng thái (CASE_PDM, BOX_STATUS, BOX_STATE, etc.)
- `location` - Vị trí trong kho (khu vực, hàng, cột)
- `retention_period` - Thời hạn lưu trữ
- `box` - Thùng chứa hồ sơ
- `case_detail` - Chi tiết case (bảng chính)

### Staging Tables
- `staging_raw` - Dữ liệu thô từ Excel
- `staging_valid` - Dữ liệu đã validate
- `staging_error` - Lỗi validation
- `migration_job` - Theo dõi job migration

## API Endpoints

### Upload và Migration
```bash
# Upload Excel đồng bộ
POST /api/migration/excel/upload
Content-Type: multipart/form-data
- file: Excel file (.xlsx, .xls)
- createdBy: User thực hiện (optional)

# Upload Excel bất đồng bộ  
POST /api/migration/excel/upload-async
Content-Type: multipart/form-data
- file: Excel file
- createdBy: User thực hiện (optional)
```

### Monitoring
```bash
# Kiểm tra trạng thái job
GET /api/migration/job/{jobId}/status

# Metrics hệ thống
GET /api/migration/system/metrics
```

### Manual Phase Execution (Debug)
```bash
# Chỉ thực hiện ingest
POST /api/migration/excel/ingest-only

# Chỉ validation
POST /api/migration/job/{jobId}/validate

# Chỉ apply data
POST /api/migration/job/{jobId}/apply

# Chỉ reconciliation
POST /api/migration/job/{jobId}/reconcile
```

### Cleanup
```bash
# Cleanup staging data
DELETE /api/migration/job/{jobId}/cleanup?keepErrors=true
```

## Cấu trúc Excel File

File Excel phải có các cột sau (theo thứ tự):

| Cột | Tên | Bắt buộc | Ghi chú |
|-----|-----|----------|---------|
| A | Kho VPBank | ✓ | Mã kho lưu trữ |
| B | Mã đơn vị | ✓ | Mã đơn vị chủ quản |
| C | Trách nhiệm bàn giao | | Bộ phận chịu trách nhiệm |
| D | Loại chứng từ | ✓ | Loại chứng từ tài liệu |
| E | Ngày chứng từ | ✓ | dd/MM/yyyy hoặc yyyy-MM-dd |
| F | Tên tập | | Tên tập/chồng hồ sơ |
| G | Số lượng tập | ✓ | Số nguyên dương |
| H | Ngày phải bàn giao | | dd/MM/yyyy hoặc yyyy-MM-dd |
| I | Ngày bàn giao | | dd/MM/yyyy hoặc yyyy-MM-dd |
| J | Tình trạng thất lạc | | Yes/No |
| K | Tình trạng không hoàn trả | | Yes/No |
| L | Trạng thái case PDM | | Trạng thái case |
| M | Ghi chú case PDM | | Ghi chú |
| N | Mã thùng | ✓ | Mã thùng chứa hồ sơ |
| O | Thời hạn lưu trữ | ✓ | Số năm (số nguyên) |
| P | Ngày nhập kho VPBank | | dd/MM/yyyy hoặc yyyy-MM-dd |
| Q | Ngày chuyển kho Crown | | dd/MM/yyyy hoặc yyyy-MM-dd |
| R | Khu vực | | Vị trí khu vực |
| S | Hàng | | Số hàng (số nguyên) |
| T | Cột | | Số cột (số nguyên) |
| U | Tình trạng thùng | | Hỏng/móp/mất nắp... |
| V | Trạng thái thùng | | Mã trạng thái |
| W | Lưu ý | | Ghi chú tự do |

## Ví dụ Sử dụng

### 1. Upload Excel đồng bộ
```bash
curl -X POST http://localhost:8080/api/migration/excel/upload \
  -F "file=@data.xlsx" \
  -F "createdBy=admin"
```

Response:
```json
{
  "jobId": "JOB_20241219143022_a1b2c3d4",
  "status": "COMPLETED",
  "filename": "data.xlsx",
  "totalRows": 100000,
  "processedRows": 100000,
  "validRows": 95000,
  "errorRows": 5000,
  "insertedRows": 95000,
  "currentPhase": "RECONCILIATION_COMPLETED",
  "progressPercent": 100.0,
  "processingTimeMs": 45000,
  "ingestTimeMs": 10000,
  "validationTimeMs": 15000,
  "applyTimeMs": 15000,
  "reconcileTimeMs": 5000,
  "avgProcessingRate": 2222.22
}
```

### 2. Upload Excel bất đồng bộ
```bash
curl -X POST http://localhost:8080/api/migration/excel/upload-async \
  -F "file=@large-data.xlsx" \
  -F "createdBy=admin"
```

Response:
```json
{
  "message": "Migration started successfully",
  "filename": "large-data.xlsx",
  "status": "PROCESSING",
  "note": "Use /status endpoint to check progress"
}
```

### 3. Kiểm tra trạng thái
```bash
curl http://localhost:8080/api/migration/job/JOB_20241219143022_a1b2c3d4/status
```

Response:
```json
{
  "jobId": "JOB_20241219143022_a1b2c3d4",
  "status": "VALIDATING",
  "filename": "data.xlsx",
  "totalRows": 100000,
  "processedRows": 100000,
  "validRows": 0,
  "errorRows": 0,
  "progressPercent": 45.0,
  "errorBreakdown": {
    "REQUIRED_MISSING": 100,
    "INVALID_DATE": 50,
    "DUP_IN_FILE": 25
  },
  "createdAt": "2024-12-19T14:30:22",
  "startedAt": "2024-12-19T14:30:25"
}
```

## Monitoring và Performance

### Memory Usage
- Streaming processing để tránh OutOfMemory
- Batch size: 5,000 records (configurable)
- Memory threshold: 500MB (configurable)

### Processing Rate
- Target: 2,000-3,000 records/second
- Depends on validation complexity và DB performance

### Error Handling
- Comprehensive validation với detailed error messages
- Error types: REQUIRED_MISSING, INVALID_DATE, DUP_IN_FILE, REF_NOT_FOUND, DUP_IN_DB
- Export errors ra Excel để user xử lý

### Logging
- Structured logging với jobId
- Performance metrics cho mỗi phase
- Memory usage tracking

## Configuration

Application properties:
```yaml
# Migration settings
migration:
  batch-size: 5000
  memory-threshold-mb: 500
  enable-progress-tracking: true
  enable-memory-monitoring: true
  parallel-processing: false
  strict-validation: true

# Async processing
spring:
  task:
    execution:
      pool:
        core-size: 2
        max-size: 5
        queue-capacity: 100
```

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**
   - Giảm batch-size
   - Tăng heap memory: `-Xmx4g`
   - Enable streaming parser

2. **Slow Performance**
   - Kiểm tra database connections
   - Optimize indexes trên master tables  
   - Reduce validation complexity

3. **Validation Errors**
   - Kiểm tra master data completeness
   - Validate Excel format trước upload
   - Check date formats

### Monitoring Queries

```sql
-- Job statistics
SELECT status, COUNT(*) FROM migration_job GROUP BY status;

-- Error breakdown
SELECT error_type, COUNT(*) FROM staging_error 
WHERE job_id = 'your_job_id' GROUP BY error_type;

-- Data consistency check  
SELECT 
  (SELECT COUNT(*) FROM staging_valid WHERE job_id = 'your_job_id') as valid_count,
  (SELECT COUNT(*) FROM case_detail WHERE created_at >= 'job_start_time') as inserted_count;
```

## Best Practices

1. **Data Preparation**
   - Validate Excel format trước upload
   - Remove empty rows/columns
   - Ensure master data completeness

2. **Performance Optimization**
   - Process during off-peak hours
   - Monitor system resources
   - Use appropriate batch sizes

3. **Error Management**  
   - Review error reports
   - Fix data issues incrementally
   - Re-run migration sau khi fix

4. **Monitoring**
   - Track processing rates
   - Monitor memory usage
   - Set up alerts cho failures
