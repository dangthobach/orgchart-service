# 🚀 Async Migration API - Hướng Dẫn Sử Dụng

## 📋 Tổng Quan

**Phase 1 Implementation Complete** ✅

Hệ thống đã được nâng cấp để hỗ trợ **async processing** - xử lý migration trong background thread, client nhận response ngay lập tức và poll progress.

---

## 🔄 Workflow Mới

### **ASYNC MODE (Khuyến nghị)**

```
┌─────────────┐
│   CLIENT    │
└──────┬──────┘
       │ 1. POST /upload?async=true
       │ (Upload file Excel)
       ↓
┌──────────────────────────┐
│  CONTROLLER              │
│  - Validate file         │
│  - Save to disk          │
│  - Submit to async queue │ → Trả về HTTP 202 (< 500ms)
└──────┬───────────────────┘
       │
       ↓ (Job ID: JOB-20251105-123)
       
CLIENT nhận response:
{
  "jobId": "JOB-20251105-123",
  "status": "STARTED",
  "progressUrl": "/api/migration/multisheet/JOB-20251105-123/progress",
  "cancelUrl": "/api/migration/multisheet/JOB-20251105-123/cancel"
}

       │
       │ 2. Background processing (10-30 phút)
       ↓
┌──────────────────────────┐
│  ASYNC THREAD POOL       │
│  - Parse Excel           │
│  - Validate sheets       │
│  - Insert to DB          │
│  - Update progress       │
└──────┬───────────────────┘
       │
       │ 3. Client polls progress
       ↓
┌──────────────────────────┐
│  GET /progress           │ → { overallStatus: "STARTED", overallProgress: 45% }
│  (Every 2-5 seconds)     │
└──────────────────────────┘
```

---

## 🎯 API Endpoints

### **1. Upload & Start Migration (ASYNC)**

```http
POST /api/migration/multisheet/upload?async=true
Content-Type: multipart/form-data

file: employees.xlsx
```

**Response (HTTP 202 Accepted):**
```json
{
  "jobId": "JOB-20251105-001",
  "status": "STARTED",
  "message": "Migration job submitted successfully. Use progress endpoint to track status.",
  "originalFilename": "employees.xlsx",
  "filePath": "/home/user/excel-uploads/JOB-20251105-001_1730803200000.xlsx",
  "fileSize": 2048576,
  "uploadedAt": "2025-11-05T10:30:00",
  "async": true,
  "excelInfo": {
    "valid": true,
    "totalSheets": 3,
    "sheetNames": ["HOPD", "CIF", "TAP"]
  },
  "progressUrl": "/api/migration/multisheet/JOB-20251105-001/progress",
  "sheetsUrl": "/api/migration/multisheet/JOB-20251105-001/sheets",
  "cancelUrl": "/api/migration/multisheet/JOB-20251105-001/cancel"
}
```

---

### **2. Get Progress (Polling)**

```http
GET /api/migration/multisheet/{jobId}/progress
```

**Response:**
```json
{
  "jobId": "JOB-20251105-001",
  "overallStatus": "STARTED",
  "isRunning": true,
  "totalSheets": 3,
  "pendingSheets": 0,
  "inProgressSheets": 1,
  "completedSheets": 2,
  "failedSheets": 0,
  "overallProgress": 66.67,
  "totalIngestedRows": 150000,
  "totalValidRows": 148500,
  "totalErrorRows": 1500,
  "totalInsertedRows": 100000,
  "currentSheet": "TAP",
  "currentPhase": "VALIDATING"
}
```

**Overall Status Values:**
- `PENDING` - Job chưa bắt đầu
- `STARTED` - Đang xử lý
- `COMPLETED` - Hoàn thành thành công (tất cả sheets OK)
- `COMPLETED_WITH_ERRORS` - Hoàn thành nhưng có sheet fail
- `FAILED` - Job fail hoàn toàn
- `CANCELLED` - Đã bị cancel
- `NOT_FOUND` - Không tìm thấy job

---

### **3. Get Overall Job Status**

```http
GET /api/migration/multisheet/{jobId}/status
```

**Response:**
```json
{
  "jobId": "JOB-20251105-001",
  "overallStatus": "STARTED",
  "isRunning": true,
  "canCancel": true
}
```

---

### **4. Cancel Job**

```http
DELETE /api/migration/multisheet/{jobId}/cancel
```

**Response:**
```json
{
  "jobId": "JOB-20251105-001",
  "cancelled": true,
  "message": "Job cancellation initiated. Processing will stop gracefully."
}
```

---

### **5. Get System Info**

```http
GET /api/migration/multisheet/system/info
```

**Response:**
```json
{
  "runningJobsCount": 3,
  "timestamp": "2025-11-05T10:35:00"
}
```

---

## 💻 Client Implementation Example

### **JavaScript (Polling Pattern)**

```javascript
async function uploadAndTrack(file) {
  // 1. Upload file
  const formData = new FormData();
  formData.append('file', file);
  
  const uploadResponse = await fetch('/api/migration/multisheet/upload?async=true', {
    method: 'POST',
    body: formData
  });
  
  if (uploadResponse.status !== 202) {
    throw new Error('Upload failed');
  }
  
  const data = await uploadResponse.json();
  const jobId = data.jobId;
  
  console.log('✅ Job submitted:', jobId);
  
  // 2. Poll progress every 3 seconds
  const pollInterval = setInterval(async () => {
    const progressResponse = await fetch(`/api/migration/multisheet/${jobId}/progress`);
    const progress = await progressResponse.json();
    
    console.log(`Progress: ${progress.overallProgress}% - Status: ${progress.overallStatus}`);
    
    // Update UI
    updateProgressBar(progress.overallProgress);
    updateStatusText(progress.overallStatus);
    updateSheetInfo(progress);
    
    // Check if completed
    if (['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED'].includes(progress.overallStatus)) {
      clearInterval(pollInterval);
      console.log('✅ Job finished:', progress.overallStatus);
      handleCompletion(progress);
    }
  }, 3000); // Poll every 3 seconds
}

function updateProgressBar(percent) {
  document.getElementById('progress-bar').style.width = percent + '%';
  document.getElementById('progress-text').textContent = Math.round(percent) + '%';
}

function updateStatusText(status) {
  const statusIcons = {
    'PENDING': '⏳',
    'STARTED': '🔄',
    'COMPLETED': '✅',
    'COMPLETED_WITH_ERRORS': '⚠️',
    'FAILED': '❌',
    'CANCELLED': '🛑'
  };
  
  document.getElementById('status').textContent = statusIcons[status] + ' ' + status;
}
```

---

### **React Hook Example**

```javascript
import { useState, useEffect } from 'react';

function useMigrationJob(jobId) {
  const [progress, setProgress] = useState(null);
  const [isComplete, setIsComplete] = useState(false);

  useEffect(() => {
    if (!jobId || isComplete) return;

    const interval = setInterval(async () => {
      const response = await fetch(`/api/migration/multisheet/${jobId}/progress`);
      const data = await response.json();
      
      setProgress(data);

      if (['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED'].includes(data.overallStatus)) {
        setIsComplete(true);
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [jobId, isComplete]);

  const cancel = async () => {
    await fetch(`/api/migration/multisheet/${jobId}/cancel`, { method: 'DELETE' });
  };

  return { progress, isComplete, cancel };
}

// Usage
function MigrationDashboard() {
  const [jobId, setJobId] = useState(null);
  const { progress, isComplete, cancel } = useMigrationJob(jobId);

  const handleUpload = async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch('/api/migration/multisheet/upload?async=true', {
      method: 'POST',
      body: formData
    });
    
    const data = await response.json();
    setJobId(data.jobId);
  };

  return (
    <div>
      <input type="file" onChange={e => handleUpload(e.target.files[0])} />
      
      {progress && (
        <div>
          <ProgressBar value={progress.overallProgress} />
          <Status status={progress.overallStatus} />
          <SheetInfo sheets={progress} />
          {!isComplete && <button onClick={cancel}>Cancel Job</button>}
        </div>
      )}
    </div>
  );
}
```

---

## 🔧 Configuration

### **Thread Pool Settings** (MigrationAsyncConfig.java)

```java
@Bean("migrationExecutor")
public Executor migrationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);        // 2 concurrent jobs
    executor.setMaxPoolSize(5);         // Max 5 concurrent jobs
    executor.setQueueCapacity(100);     // Queue up to 100 jobs
    executor.setThreadNamePrefix("migration-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
}
```

**Tuning:**
- `corePoolSize`: Số job chạy đồng thời (mặc định: 2)
- `maxPoolSize`: Số job tối đa (mặc định: 5)
- `queueCapacity`: Số job đợi trong queue (mặc định: 100)

---

## ⚠️ Backward Compatibility

### **SYNC MODE** (Deprecated)

```http
POST /api/migration/multisheet/upload?async=false
```

- ⚠️ **Không khuyến nghị**: HTTP request sẽ block 10-30 phút
- ⚠️ **Risk**: Gateway timeout, thread pool exhaustion
- ✅ **Use case**: Testing, small files (<10K rows)

---

## 🎯 Best Practices

### **Client-Side**

1. **Always use async=true** cho production
2. **Poll interval**: 2-5 giây (không quá nhanh)
3. **Timeout**: Set client timeout >= 5 phút cho polling
4. **Error handling**: Xử lý `FAILED`, `CANCELLED` status
5. **Cancel button**: Cho phép user cancel job đang chạy

### **Server-Side**

1. **Monitor running jobs**: Dùng `/system/info` để check load
2. **Set thread pool limits**: Tránh overload database
3. **Cleanup old jobs**: Xóa jobs > 7 ngày (TODO: implement)

---

## 📊 Performance

### **Async Mode**
- ✅ Response time: < 500ms
- ✅ Client timeout: None (polling)
- ✅ Concurrent jobs: 2-5 (configurable)
- ✅ Memory: Efficient (background processing)

### **Sync Mode**
- ❌ Response time: 10-30 phút
- ❌ Client timeout: Gateway (30-60s)
- ❌ Concurrent requests: Limited by HTTP threads
- ❌ Memory: High (multiple blocking requests)

---

## 🚧 Future Enhancements (Phase 2+)

- [ ] **WebSocket**: Real-time push updates (thay vì polling)
- [ ] **Server-Sent Events (SSE)**: Streaming progress
- [ ] **Job Priority Queue**: High/Low priority jobs
- [ ] **Job Retry**: Auto-retry failed jobs
- [ ] **Job History**: Persist job results > 7 days
- [ ] **Rate Limiting**: Per-user job limits
- [ ] **Batch Cancellation**: Cancel multiple jobs

---

## 📝 Testing

### **cURL Examples**

```bash
# 1. Upload async
curl -X POST http://localhost:8080/api/migration/multisheet/upload?async=true \
  -F "file=@employees.xlsx"

# Response:
# {
#   "jobId": "JOB-20251105-001",
#   "status": "STARTED",
#   ...
# }

# 2. Check progress
curl http://localhost:8080/api/migration/multisheet/JOB-20251105-001/progress

# 3. Cancel job
curl -X DELETE http://localhost:8080/api/migration/multisheet/JOB-20251105-001/cancel

# 4. Check system info
curl http://localhost:8080/api/migration/multisheet/system/info
```

---

## ❓ Troubleshooting

### **Job stuck in STARTED status**

```bash
# Check if job is still running
curl http://localhost:8080/api/migration/multisheet/{jobId}/status

# If stuck, try cancel
curl -X DELETE http://localhost:8080/api/migration/multisheet/{jobId}/cancel
```

### **Too many concurrent jobs**

```bash
# Check running jobs count
curl http://localhost:8080/api/migration/multisheet/system/info

# If > 5, wait or increase thread pool size
```

### **Progress not updating**

- Check database connection
- Verify `migration_job_sheet` table has records
- Check application logs for errors

---

## 📚 Related Documentation

- [EXCEL_UPLOAD_API_GUIDE.md](EXCEL_UPLOAD_API_GUIDE.md) - Upload API details
- [VALIDATION_TIMEOUT_MONITORING_GUIDE.md](VALIDATION_TIMEOUT_MONITORING_GUIDE.md) - Timeout monitoring
- [MULTISHEET_MIGRATION_README.md](MULTISHEET_MIGRATION_README.md) - Architecture overview

---

**✅ Phase 1 Complete - Async Processing Enabled!**

*Next: Phase 2 - WebSocket real-time updates (optional)*
