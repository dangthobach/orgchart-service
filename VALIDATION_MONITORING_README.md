# Validation Step Monitoring System

## Tổng quan

Hệ thống monitoring này giúp theo dõi chi tiết từng step trong quá trình validation của migration process. Nó giải quyết vấn đề:
- **Không biết step nào đang chạy** khi validation bị treo
- **Không biết step nào bị hang** với 100k+ records
- **Không có timeout detection** cho các query chạy quá lâu
- **Không có performance metrics** để tối ưu

## Kiến trúc

### 1. ValidationStepStatus (DTO)
- Lưu trữ thông tin chi tiết của từng step
- Trạng thái: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `TIMEOUT`
- Track thời gian bắt đầu, kết thúc, duration, số rows affected

### 2. ValidationStepTracker (Component)
- Quản lý tracking cho tất cả các job
- 7 steps được track:
  1. `VALIDATE_REQUIRED_FIELDS` - Validate trường bắt buộc
  2. `VALIDATE_DATE_FORMATS` - Validate format ngày tháng
  3. `VALIDATE_NUMERIC_FIELDS` - Validate trường số
  4. `CHECK_DUPLICATES_IN_FILE` - Check trùng trong file
  5. `VALIDATE_MASTER_REFERENCES` - Validate tham chiếu master tables
  6. `CHECK_DUPLICATES_WITH_DB` - Check trùng với DB
  7. `MOVE_VALID_RECORDS` - Di chuyển records hợp lệ (step hay bị treo nhất)

### 3. ValidationServiceWithTracking (Service)
- Wrapper của ValidationService gốc
- Tự động track từng step
- Detect timeout và log chi tiết

### 4. ValidationMonitoringController (REST API)
- Cung cấp endpoints để query progress real-time

## Cấu hình Timeout

```java
// Trong ValidationStepTracker.java
DEFAULT_STEP_TIMEOUT_MS = 5 * 60 * 1000;           // 5 phút cho các step thông thường
MOVE_VALID_RECORDS_TIMEOUT_MS = 15 * 60 * 1000;   // 15 phút cho step MOVE_VALID_RECORDS
```

Bạn có thể điều chỉnh timeout này tùy theo môi trường và số lượng records.

## API Endpoints

### 1. Get All Validation Steps
```http
GET /api/migration/validation/{jobId}/steps
```

**Response:**
```json
{
  "jobId": "JOB-20251029-001",
  "totalSteps": 7,
  "overallProgress": 42.85,
  "steps": [
    {
      "jobId": "JOB-20251029-001",
      "stepNumber": 1,
      "stepName": "VALIDATE_REQUIRED_FIELDS",
      "stepDescription": "Validate required fields (mandatory columns)",
      "status": "COMPLETED",
      "startTime": "2025-10-29T09:38:50",
      "endTime": "2025-10-29T09:38:52",
      "durationMs": 2340,
      "affectedRows": 150,
      "progressPercent": 0.0
    },
    {
      "jobId": "JOB-20251029-001",
      "stepNumber": 7,
      "stepName": "MOVE_VALID_RECORDS",
      "stepDescription": "Move valid records to staging_valid table",
      "status": "IN_PROGRESS",
      "startTime": "2025-10-29T09:38:52",
      "durationMs": null,
      "affectedRows": null,
      "progressPercent": 0.0
    }
  ]
}
```

### 2. Get Current Step (Step đang chạy)
```http
GET /api/migration/validation/{jobId}/current
```

**Response:**
```json
{
  "jobId": "JOB-20251029-001",
  "currentStep": {
    "stepNumber": 7,
    "stepName": "MOVE_VALID_RECORDS",
    "stepDescription": "Move valid records to staging_valid table",
    "status": "IN_PROGRESS",
    "startTime": "2025-10-29T09:38:52"
  },
  "elapsedMs": 125000,
  "elapsedSeconds": 125.0
}
```

**Giải thích:**
- Nếu `elapsedSeconds` > 300 (5 phút) cho step thông thường: có thể bị treo
- Nếu `elapsedSeconds` > 900 (15 phút) cho MOVE_VALID_RECORDS: có thể bị treo

### 3. Get Progress Summary
```http
GET /api/migration/validation/{jobId}/summary
```

**Response:**
```json
{
  "jobId": "JOB-20251029-001",
  "overallProgress": 85.71,
  "totalSteps": 7,
  "completedSteps": 6,
  "failedSteps": 0,
  "timeoutSteps": 0,
  "inProgressSteps": 1,
  "pendingSteps": 0,
  "currentStepNumber": 7,
  "currentStepName": "MOVE_VALID_RECORDS",
  "currentStepDescription": "Move valid records to staging_valid table",
  "currentStepElapsedMs": 125000,
  "currentStepElapsedSeconds": 125.0
}
```

### 4. Get Detailed Report
```http
GET /api/migration/validation/{jobId}/report
```

**Response:**
```json
{
  "jobId": "JOB-20251029-001",
  "timestamp": "2025-10-29T10:15:30",
  "report": "=== Validation Step Report for JobId: JOB-20251029-001 ===\n\nStep 1: VALIDATE_REQUIRED_FIELDS\n  Description: Validate required fields (mandatory columns)\n  Status: COMPLETED\n  Start Time: 2025-10-29T09:38:50\n  End Time: 2025-10-29T09:38:52\n  Duration: 2340ms (2.34s)\n  Affected Rows: 150\n\n..."
}
```

### 5. Check Timeouts
```http
POST /api/migration/validation/{jobId}/check-timeout
```

**Response:**
```json
{
  "jobId": "JOB-20251029-001",
  "timeoutSteps": 1,
  "message": "Found 1 step(s) that exceeded timeout"
}
```

### 6. Get Performance Metrics
```http
GET /api/migration/validation/{jobId}/performance
```

**Response:**
```json
{
  "jobId": "JOB-20251029-001",
  "totalDurationMs": 180000,
  "totalDurationSeconds": 180.0,
  "performanceData": [
    {
      "stepNumber": 1,
      "stepName": "VALIDATE_REQUIRED_FIELDS",
      "durationMs": 2340,
      "durationSeconds": 2.34,
      "affectedRows": 150,
      "rowsPerSecond": 64.1
    },
    {
      "stepNumber": 7,
      "stepName": "MOVE_VALID_RECORDS",
      "durationMs": 125000,
      "durationSeconds": 125.0,
      "affectedRows": 99850,
      "rowsPerSecond": 798.8
    }
  ]
}
```

### 7. Get Specific Step Status
```http
GET /api/migration/validation/{jobId}/step/MOVE_VALID_RECORDS
```

## Cách sử dụng trong thực tế

### Scenario 1: Validation bị treo với 100k records

**Bước 1: Check current step**
```bash
curl http://localhost:8080/api/migration/validation/JOB-20251029-001/current
```

Output cho biết step đang chạy:
```json
{
  "currentStep": {
    "stepName": "MOVE_VALID_RECORDS",
    "status": "IN_PROGRESS"
  },
  "elapsedSeconds": 450.0  // Đã chạy 7.5 phút
}
```

**Bước 2: Check database locks**

Từ output trên, bạn biết đang bị treo ở step `MOVE_VALID_RECORDS`. Giờ check locks trong PostgreSQL:

```sql
SELECT
    pid,
    usename,
    application_name,
    query_start,
    state,
    wait_event_type,
    wait_event,
    query
FROM pg_stat_activity
WHERE query LIKE '%staging_valid%'
AND state = 'active';
```

**Bước 3: Get performance metrics**
```bash
curl http://localhost:8080/api/migration/validation/JOB-20251029-001/performance
```

Xem tốc độ xử lý của các step trước đó để estimate thời gian còn lại.

### Scenario 2: Monitor progress trong quá trình validation

**Polling script (bash):**
```bash
#!/bin/bash
JOB_ID="JOB-20251029-001"

while true; do
    echo "=== $(date) ==="
    curl -s "http://localhost:8080/api/migration/validation/$JOB_ID/summary" | jq '.'
    sleep 10  # Check mỗi 10 giây
done
```

**Polling script (PowerShell):**
```powershell
$jobId = "JOB-20251029-001"

while ($true) {
    Write-Host "=== $(Get-Date) ===" -ForegroundColor Green
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/migration/validation/$jobId/summary"
    $response | ConvertTo-Json -Depth 3

    if ($response.currentStepName) {
        Write-Host "Current: $($response.currentStepName) - Elapsed: $($response.currentStepElapsedSeconds)s" -ForegroundColor Yellow
    }

    Start-Sleep -Seconds 10
}
```

### Scenario 3: Detect và xử lý timeout

**Auto-check timeout:**
```bash
#!/bin/bash
JOB_ID="JOB-20251029-001"

# Trigger timeout check
curl -X POST "http://localhost:8080/api/migration/validation/$JOB_ID/check-timeout"

# Get report
curl -s "http://localhost:8080/api/migration/validation/$JOB_ID/report"
```

Nếu có timeout, log sẽ cho biết:
```
Step 7: MOVE_VALID_RECORDS
  Status: TIMEOUT
  Error: Step timeout after 950000ms (limit: 900000ms)
```

## Cách sử dụng ValidationServiceWithTracking

### Option 1: Thay thế ValidationService trong MigrationOrchestrationService

```java
@Service
@RequiredArgsConstructor
public class MigrationOrchestrationService {

    // Thay vì inject ValidationService
    // private final ValidationService validationService;

    // Inject ValidationServiceWithTracking
    private final ValidationServiceWithTracking validationService;

    // Code khác không cần thay đổi
}
```

### Option 2: Sử dụng song song để test

```java
@Service
@RequiredArgsConstructor
public class MigrationOrchestrationService {

    private final ValidationService validationService;
    private final ValidationServiceWithTracking validationServiceWithTracking;

    public MigrationResultDTO performFullMigration(...) {
        // Dùng service có tracking
        MigrationResultDTO validationResult = validationServiceWithTracking.startValidation(jobId);
        // ...
    }
}
```

## Troubleshooting Guide

### Problem: Step MOVE_VALID_RECORDS bị treo

**Dấu hiệu:**
- `elapsedSeconds` > 900 (15 phút)
- Status vẫn là `IN_PROGRESS`

**Nguyên nhân thường gặp:**
1. **Database locks** - Query đang chờ lock từ transaction khác
2. **Missing indexes** - Subquery `NOT EXISTS` chạy full table scan
3. **Too many records** - INSERT 100k+ rows trong 1 transaction

**Giải pháp:**

1. **Check locks:**
```sql
SELECT * FROM pg_locks l
JOIN pg_stat_activity a ON l.pid = a.pid
WHERE l.granted = false;
```

2. **Kill blocking query:**
```sql
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE pid = <blocking_pid>;
```

3. **Optimize query - Thêm batch processing:**

Thay vì INSERT toàn bộ 100k records một lúc, chia nhỏ thành batch:

```java
private long moveValidRecordsToStagingValid(String jobId) {
    final int BATCH_SIZE = 5000;
    long totalInserted = 0;

    // Count total valid records
    String countSql = """
        SELECT COUNT(*) FROM staging_raw sr
        WHERE sr.job_id = ?
        AND sr.parse_errors IS NULL
        AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
        """;

    Long totalRecords = jdbcTemplate.queryForObject(countSql, Long.class, jobId);

    // Process in batches
    for (int offset = 0; offset < totalRecords; offset += BATCH_SIZE) {
        String sql = """
            INSERT INTO staging_valid (...)
            SELECT ... FROM staging_raw sr
            WHERE sr.job_id = ?
            AND sr.parse_errors IS NULL
            AND NOT EXISTS (SELECT 1 FROM staging_error se WHERE se.job_id = sr.job_id AND se.row_num = sr.row_num)
            LIMIT ? OFFSET ?
            """;

        int inserted = jdbcTemplate.update(sql, jobId, BATCH_SIZE, offset);
        totalInserted += inserted;

        log.info("Inserted batch: {}/{} records", offset + inserted, totalRecords);
    }

    return totalInserted;
}
```

### Problem: Timeout configuration không phù hợp

**Điều chỉnh timeout:**

Edit file `ValidationStepTracker.java`:

```java
// Cho môi trường dev với data nhỏ
private static final long DEFAULT_STEP_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
private static final long MOVE_VALID_RECORDS_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

// Cho môi trường production với 100k+ records
private static final long DEFAULT_STEP_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes
private static final long MOVE_VALID_RECORDS_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
```

### Problem: Memory usage cao khi track nhiều jobs

**Cleanup tracking data:**

Thêm scheduled job để cleanup:

```java
@Scheduled(fixedDelay = 3600000) // Every 1 hour
public void cleanupCompletedJobs() {
    // Remove tracking data for jobs completed > 1 hour ago
    stepTracker.cleanupTracking(oldJobId);
}
```

## Best Practices

1. **Always check current step** trước khi kết luận validation bị treo
2. **Monitor elapsedSeconds** để detect sớm các vấn đề
3. **Check database locks** khi step chạy quá timeout
4. **Review performance metrics** sau mỗi lần migration để optimize
5. **Adjust timeout values** phù hợp với môi trường và data size
6. **Implement batch processing** cho step MOVE_VALID_RECORDS nếu có > 50k records

## Tích hợp với Frontend

Frontend có thể poll API để hiển thị progress bar:

```javascript
async function monitorValidation(jobId) {
    const interval = setInterval(async () => {
        const response = await fetch(`/api/migration/validation/${jobId}/summary`);
        const data = await response.json();

        // Update progress bar
        updateProgressBar(data.overallProgress);

        // Show current step
        updateCurrentStep(data.currentStepName, data.currentStepElapsedSeconds);

        // Stop polling khi hoàn thành
        if (data.completedSteps === data.totalSteps) {
            clearInterval(interval);
        }

        // Alert nếu timeout
        if (data.timeoutSteps > 0) {
            alert('Validation bị timeout! Kiểm tra logs.');
            clearInterval(interval);
        }
    }, 5000); // Poll mỗi 5 giây
}
```

## Logging

Khi validation complete, hệ thống tự động log detailed report:

```
2025-10-29 10:15:30 INFO  - Validation completed for JobId: JOB-20251029-001, Valid: 99850, Errors: 150, Time: 180000ms
2025-10-29 10:15:30 INFO  -
=== Validation Step Report for JobId: JOB-20251029-001 ===

Step 1: VALIDATE_REQUIRED_FIELDS
  Description: Validate required fields (mandatory columns)
  Status: COMPLETED
  Start Time: 2025-10-29T09:38:50
  End Time: 2025-10-29T09:38:52
  Duration: 2340ms (2.34s)
  Affected Rows: 150

Step 2: VALIDATE_DATE_FORMATS
  Description: Validate date formats and business rules
  Status: COMPLETED
  Duration: 3100ms (3.10s)
  Affected Rows: 0

...

Step 7: MOVE_VALID_RECORDS
  Description: Move valid records to staging_valid table
  Status: COMPLETED
  Duration: 125000ms (125.00s)
  Affected Rows: 99850

Overall Progress: 100.0%
```

## Kết luận

Với hệ thống monitoring này, bạn có thể:
- ✅ **Biết chính xác step nào đang chạy** khi validation bị treo
- ✅ **Track thời gian thực thi** của từng step
- ✅ **Detect timeout** tự động
- ✅ **Get performance metrics** để optimize
- ✅ **Monitor real-time** qua REST API
- ✅ **Debug dễ dàng** với detailed reports

Hệ thống đặc biệt hữu ích khi xử lý **100k+ records** và cần troubleshooting vấn đề database locks.
