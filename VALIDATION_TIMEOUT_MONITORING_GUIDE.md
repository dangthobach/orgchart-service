# Advanced Validation Monitoring & Timeout Handling Guide

## 🎯 Mục Tiêu

Giải pháp **monitoring chi tiết** và **timeout handling** cho SheetValidationService để:
- ✅ Theo dõi real-time từng bước validation
- ✅ Phát hiện timeout tự động (5 phút/bước)
- ✅ Identify bottleneck performance
- ✅ Log comprehensive metrics
- ✅ Cancel timeout tasks ngay lập tức

---

## 🔧 Implementation Details

### 1. Timeout Configuration

```java
// SheetValidationService.java

// Timeout cho mỗi validation step
private static final long STEP_TIMEOUT_SECONDS = 300; // 5 phút

// Timeout tổng cho toàn bộ validation
private static final long TOTAL_TIMEOUT_SECONDS = 1800; // 30 phút

// Threshold để warning slow step
private static final long SLOW_THRESHOLD_MS = 10000; // 10 giây
```

### 2. ExecutorService cho Timeout Handling

```java
// Daemon thread pool cho timeout monitoring
private final ExecutorService timeoutExecutor = Executors.newCachedThreadPool(r -> {
    Thread thread = new Thread(r);
    thread.setDaemon(true);
    thread.setName("validation-timeout-handler");
    return thread;
});
```

**Why CachedThreadPool?**
- Tự động scale threads khi cần
- Daemon threads không block JVM shutdown
- Perfect cho short-lived timeout tasks

### 3. Core Monitoring Method

```java
private <T extends Number> T executeWithTimeoutAndMonitoring(
        String stepName,
        Supplier<T> validationStep,
        List<ValidationStepMetrics> stepMetrics,
        String jobId,
        String sheetName) throws TimeoutException {
    
    long stepStartTime = System.currentTimeMillis();
    log.info("🔄 [{}] Starting step: {}", sheetName, stepName);

    // Submit task with Future
    Future<T> future = timeoutExecutor.submit(() -> validationStep.get());

    try {
        // Wait with timeout
        T result = future.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        long duration = System.currentTimeMillis() - stepStartTime;
        
        // Record success metrics
        stepMetrics.add(ValidationStepMetrics.builder()
                .stepName(stepName)
                .durationMs(duration)
                .rowsProcessed(result.longValue())
                .success(true)
                .build());
        
        // Slow warning
        if (duration > SLOW_THRESHOLD_MS) {
            log.warn("⚠️ [{}] Step '{}' took {}ms (slow!)", 
                     sheetName, stepName, duration);
        }
        
        return result;
        
    } catch (TimeoutException e) {
        future.cancel(true); // ⭐ Cancel task immediately
        
        stepMetrics.add(ValidationStepMetrics.builder()
                .stepName(stepName)
                .durationMs(System.currentTimeMillis() - stepStartTime)
                .success(false)
                .errorMessage("Timeout after " + STEP_TIMEOUT_SECONDS + "s")
                .build());
        
        throw new TimeoutException("Step '" + stepName + "' timed out");
    }
}
```

---

## 📊 Monitoring Output Examples

### Example 1: Success Case (All Steps Pass)

```log
2024-11-04 10:30:00.123 INFO  🔄 [HOPD] Starting step: Required Fields Validation
2024-11-04 10:30:01.373 INFO  ✅ [HOPD] Step 'Required Fields Validation' completed in 1250ms, processed 1500 rows

2024-11-04 10:30:01.374 INFO  🔄 [HOPD] Starting step: Date Format Validation
2024-11-04 10:30:02.264 INFO  ✅ [HOPD] Step 'Date Format Validation' completed in 890ms, processed 200 rows

2024-11-04 10:30:02.265 INFO  🔄 [HOPD] Starting step: Numeric Field Validation
2024-11-04 10:30:02.715 INFO  ✅ [HOPD] Step 'Numeric Field Validation' completed in 450ms, processed 100 rows

2024-11-04 10:30:02.716 INFO  🔄 [HOPD] Starting step: Enum Values Validation
2024-11-04 10:30:03.036 INFO  ✅ [HOPD] Step 'Enum Values Validation' completed in 320ms, processed 50 rows

2024-11-04 10:30:03.037 INFO  🔄 [HOPD] Starting step: Duplicate In File Check
2024-11-04 10:30:05.137 INFO  ✅ [HOPD] Step 'Duplicate In File Check' completed in 2100ms, processed 800 rows

2024-11-04 10:30:05.138 INFO  🔄 [HOPD] Starting step: Duplicate With DB Check
2024-11-04 10:30:08.338 INFO  ✅ [HOPD] Step 'Duplicate With DB Check' completed in 3200ms, processed 500 rows

2024-11-04 10:30:08.339 INFO  🔄 [HOPD] Starting step: Master Reference Validation
2024-11-04 10:30:17.239 INFO  ✅ [HOPD] Step 'Master Reference Validation' completed in 8900ms, processed 2000 rows

2024-11-04 10:30:17.240 INFO  🔄 [HOPD] Starting step: Move Valid Records
2024-11-04 10:30:18.630 INFO  ✅ [HOPD] Step 'Move Valid Records' completed in 1390ms, processed 48500 rows

2024-11-04 10:30:18.631 INFO  📊 ========== VALIDATION PERFORMANCE SUMMARY ==========
2024-11-04 10:30:18.631 INFO  Sheet: HOPD, JobId: JOB-20241104-123
2024-11-04 10:30:18.631 INFO  Total Duration: 18500ms (18.5 seconds)
2024-11-04 10:30:18.631 INFO  Valid Rows: 48500, Error Rows: 3150
2024-11-04 10:30:18.631 INFO  ────────────────────────────────────────────────────
2024-11-04 10:30:18.632 INFO  ✅ Step: Required Fields Validation   | Duration:  1250ms | Rows:     1500 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ✅ Step: Date Format Validation       | Duration:   890ms | Rows:      200 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ✅ Step: Numeric Field Validation     | Duration:   450ms | Rows:      100 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ✅ Step: Enum Values Validation       | Duration:   320ms | Rows:       50 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ✅ Step: Duplicate In File Check      | Duration:  2100ms | Rows:      800 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ✅ Step: Duplicate With DB Check      | Duration:  3200ms | Rows:      500 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ✅ Step: Master Reference Validation  | Duration:  8900ms | Rows:     2000 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ✅ Step: Move Valid Records           | Duration:  1390ms | Rows:    48500 | Status: SUCCESS
2024-11-04 10:30:18.632 INFO  ════════════════════════════════════════════════════
2024-11-04 10:30:18.632 INFO  🐌 BOTTLENECK: 'Master Reference Validation' took 8900ms (48.1% of total time)
```

### Example 2: Timeout Case (Step Exceeds 5 Minutes)

```log
2024-11-04 10:30:00.123 INFO  🔄 [HOPD] Starting step: Master Reference Validation
2024-11-04 10:35:00.125 ERROR ⏱️ TIMEOUT: [HOPD] Step 'Master Reference Validation' exceeded timeout of 300s
2024-11-04 10:35:00.126 ERROR ❌ TIMEOUT: Validation step timed out for sheet 'HOPD': Step 'Master Reference Validation' timed out

2024-11-04 10:35:00.127 INFO  📊 ========== VALIDATION PERFORMANCE SUMMARY ==========
2024-11-04 10:35:00.127 INFO  Sheet: HOPD, JobId: JOB-20241104-123
2024-11-04 10:35:00.127 INFO  Total Duration: 300002ms (300.0 seconds)
2024-11-04 10:35:00.127 INFO  Valid Rows: 0, Error Rows: 3150
2024-11-04 10:35:00.127 INFO  ────────────────────────────────────────────────────
2024-11-04 10:35:00.127 INFO  ✅ Step: Required Fields Validation   | Duration:  1250ms | Rows:     1500 | Status: SUCCESS
2024-11-04 10:35:00.127 INFO  ✅ Step: Date Format Validation       | Duration:   890ms | Rows:      200 | Status: SUCCESS
2024-11-04 10:35:00.127 INFO  ✅ Step: Numeric Field Validation     | Duration:   450ms | Rows:      100 | Status: SUCCESS
2024-11-04 10:35:00.127 INFO  ✅ Step: Enum Values Validation       | Duration:   320ms | Rows:       50 | Status: SUCCESS
2024-11-04 10:35:00.127 INFO  ✅ Step: Duplicate In File Check      | Duration:  2100ms | Rows:      800 | Status: SUCCESS
2024-11-04 10:35:00.127 INFO  ✅ Step: Duplicate With DB Check      | Duration:  3200ms | Rows:      500 | Status: SUCCESS
2024-11-04 10:35:00.127 INFO  ❌ Step: Master Reference Validation  | Duration: 300002ms | Rows:        0 | Status: FAILED - Timeout after 300 seconds
2024-11-04 10:35:00.127 INFO  ════════════════════════════════════════════════════
2024-11-04 10:35:00.127 INFO  🐌 BOTTLENECK: 'Master Reference Validation' took 300002ms (100.0% of total time)
```

### Example 3: Slow Warning (Step > 10 seconds)

```log
2024-11-04 10:30:08.339 INFO  🔄 [HOPD] Starting step: Master Reference Validation
2024-11-04 10:30:23.579 WARN  ⚠️ [HOPD] Step 'Master Reference Validation' took 15240ms (slow!)
2024-11-04 10:30:23.580 INFO  ✅ [HOPD] Step 'Master Reference Validation' completed in 15240ms, processed 2000 rows
```

---

## 🔍 Performance Summary Components

### ValidationStepMetrics Class

```java
@Data
@Builder
private static class ValidationStepMetrics {
    private String stepName;        // Tên bước
    private long durationMs;         // Duration (milliseconds)
    private long rowsProcessed;      // Số rows xử lý (errors hoặc valid)
    private boolean success;         // Success/Fail flag
    private String errorMessage;     // Error message nếu fail
}
```

### Summary Report Structure

```
📊 ========== VALIDATION PERFORMANCE SUMMARY ==========
Sheet: {sheetName}, JobId: {jobId}
Total Duration: {totalMs}ms ({totalSec} seconds)
Valid Rows: {validCount}, Error Rows: {errorCount}
────────────────────────────────────────────────────
✅/❌ Step: {stepName} | Duration: {ms}ms | Rows: {count} | Status: {status}
...
════════════════════════════════════════════════════
🐌 BOTTLENECK: '{slowestStep}' took {ms}ms ({percentage}% of total time)
```

---

## 🚨 Troubleshooting Scenarios

### Scenario 1: Timeout at "Master Reference Validation"

**Log Pattern:**
```log
⏱️ TIMEOUT: [HOPD] Step 'Master Reference Validation' exceeded timeout of 300s
```

**Diagnosis:**
```sql
-- 1. Check master table size
SELECT 
    schemaname, tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
    n_live_tup as row_count
FROM pg_stat_user_tables
WHERE tablename LIKE 'master_%'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- 2. Check missing indexes
SELECT 
    t.tablename,
    i.indexname,
    i.indexdef
FROM pg_indexes i
JOIN pg_tables t ON i.tablename = t.tablename
WHERE t.schemaname = 'public'
    AND t.tablename LIKE 'master_%';

-- 3. Check query execution plan
EXPLAIN (ANALYZE, BUFFERS)
SELECT temp.*
FROM temp_ref_ma_nhan_vien temp
LEFT JOIN master_nhan_vien master ON temp.ma_nhan_vien = master.ma_nhan_vien
WHERE master.ma_nhan_vien IS NULL;
```

**Solutions:**
```sql
-- Create missing index
CREATE INDEX CONCURRENTLY idx_master_nhan_vien_code 
    ON master_nhan_vien(ma_nhan_vien)
    WHERE deleted_at IS NULL;

-- Vacuum and analyze
VACUUM ANALYZE master_nhan_vien;
```

### Scenario 2: Slow "Duplicate In File Check"

**Log Pattern:**
```log
⚠️ [HOPD] Step 'Duplicate In File Check' took 45000ms (slow!)
```

**Diagnosis:**
```sql
-- Check duplicate count
SELECT 
    ma_don_vi, ma_nhan_vien, 
    COUNT(*) as dup_count
FROM staging_raw_hopd
WHERE job_id = 'JOB-20241104-123'
GROUP BY ma_don_vi, ma_nhan_vien
HAVING COUNT(*) > 1
ORDER BY COUNT(*) DESC
LIMIT 10;

-- Check index usage
SELECT * FROM pg_stat_user_indexes 
WHERE schemaname = 'public' 
    AND tablename = 'staging_raw_hopd';
```

**Solutions:**
```sql
-- Create index for duplicate check
CREATE INDEX idx_staging_raw_hopd_dup_check
    ON staging_raw_hopd(job_id, ma_don_vi, ma_nhan_vien);

-- Analyze table
ANALYZE staging_raw_hopd;
```

### Scenario 3: Overall Validation Timeout (> 30 min)

**Log Pattern:**
```log
⚠️ Sheet 'HOPD' validation took 1850000ms (exceeded threshold of 1800000ms)
```

**Solutions:**

**Option 1: Increase Timeout**
```java
// Tăng timeout cho datasets lớn
private static final long TOTAL_TIMEOUT_SECONDS = 3600; // 1 giờ
```

**Option 2: Split File**
```bash
# Split Excel file thành nhiều phần nhỏ
# Process 100K rows mỗi file
```

**Option 3: Optimize Queries**
```sql
-- Run performance indexes script
psql -U username -d database -f scripts/performance_indexes.sql
```

---

## 📈 Real-Time Monitoring via Logs

### Using grep to Monitor Progress

```bash
# Terminal 1: Follow validation progress
tail -f logs/application.log | grep "🔄\|✅\|⚠️\|❌"

# Terminal 2: Monitor only timeouts
tail -f logs/application.log | grep "⏱️\|TIMEOUT"

# Terminal 3: Monitor bottlenecks
tail -f logs/application.log | grep "BOTTLENECK"
```

### PowerShell Monitoring

```powershell
# Real-time monitoring
Get-Content logs\application.log -Wait | Select-String "🔄|✅|⚠️|❌|TIMEOUT|BOTTLENECK"

# Filter only slow steps
Get-Content logs\application.log -Wait | Select-String "slow!"

# Count steps completed
(Get-Content logs\application.log | Select-String "✅").Count
```

---

## 🎯 Performance Tuning Guide

### 1. Identify Bottleneck

```log
🐌 BOTTLENECK: 'Master Reference Validation' took 8900ms (48.1% of total time)
```

**Action:** Focus optimization on this step first

### 2. Check Query Plan

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
-- Copy query from log at line: "Validating master references"
SELECT ...
```

### 3. Add Missing Indexes

```sql
-- Based on EXPLAIN output
CREATE INDEX CONCURRENTLY idx_staging_raw_hopd_master_ref
    ON staging_raw_hopd(job_id, ma_nhan_vien)
    WHERE job_id IS NOT NULL;
```

### 4. Verify Improvement

```log
-- Before optimization
✅ Step: Master Reference Validation  | Duration:  8900ms

-- After optimization
✅ Step: Master Reference Validation  | Duration:   890ms  (10x faster!)
```

---

## 📊 Expected Performance Benchmarks

### Small Dataset (10K rows)

| Step | Expected Duration | Warning Threshold |
|------|------------------|-------------------|
| Required Fields | < 500ms | > 2s |
| Date Formats | < 300ms | > 1s |
| Numeric Fields | < 200ms | > 1s |
| Enum Values | < 200ms | > 1s |
| Duplicate In File | < 800ms | > 3s |
| Duplicate With DB | < 1s | > 5s |
| Master References | < 2s | > 10s |
| Move Valid | < 500ms | > 2s |
| **TOTAL** | **< 5s** | **> 30s** |

### Medium Dataset (100K rows)

| Step | Expected Duration | Warning Threshold |
|------|------------------|-------------------|
| Required Fields | < 1s | > 5s |
| Date Formats | < 800ms | > 3s |
| Numeric Fields | < 500ms | > 2s |
| Enum Values | < 500ms | > 2s |
| Duplicate In File | < 2s | > 10s |
| Duplicate With DB | < 3s | > 15s |
| Master References | < 5s | > 30s |
| Move Valid | < 1.5s | > 5s |
| **TOTAL** | **< 15s** | **> 1 min** |

### Large Dataset (1M rows)

| Step | Expected Duration | Warning Threshold |
|------|------------------|-------------------|
| Required Fields | < 3s | > 15s |
| Date Formats | < 2s | > 10s |
| Numeric Fields | < 1.5s | > 8s |
| Enum Values | < 1.5s | > 8s |
| Duplicate In File | < 8s | > 40s |
| Duplicate With DB | < 10s | > 60s |
| Master References | < 15s | > 90s |
| Move Valid | < 5s | > 20s |
| **TOTAL** | **< 50s** | **> 5 min** |

---

## 📞 Alert Configuration

### Setup Email Alerts for Timeout

```java
@Component
public class ValidationAlertService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    public void sendTimeoutAlert(String jobId, String sheetName, String stepName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("ops-team@company.com");
        message.setSubject("⏱️ Validation Timeout Alert");
        message.setText(String.format(
            "Validation timeout detected!\n\n" +
            "JobId: %s\n" +
            "Sheet: %s\n" +
            "Step: %s\n" +
            "Time: %s\n\n" +
            "Please investigate immediately.",
            jobId, sheetName, stepName, LocalDateTime.now()
        ));
        mailSender.send(message);
    }
}
```

### Integrate with Slack

```java
@Component
public class SlackNotifier {
    
    @Value("${slack.webhook.url}")
    private String webhookUrl;
    
    public void notifyTimeout(String jobId, String sheetName, String stepName) {
        RestTemplate restTemplate = new RestTemplate();
        
        Map<String, Object> payload = Map.of(
            "text", "⏱️ *Validation Timeout Alert*",
            "attachments", List.of(Map.of(
                "color", "danger",
                "fields", List.of(
                    Map.of("title", "JobId", "value", jobId, "short", true),
                    Map.of("title", "Sheet", "value", sheetName, "short", true),
                    Map.of("title", "Step", "value", stepName, "short", false)
                )
            ))
        );
        
        restTemplate.postForEntity(webhookUrl, payload, String.class);
    }
}
```

---

## ✅ Summary

### Features Implemented

| Feature | Status | Description |
|---------|--------|-------------|
| **Step Timeout** | ✅ | 5 minutes per step |
| **Total Timeout** | ✅ | 30 minutes overall |
| **Real-time Logging** | ✅ | Progress logs with emoji |
| **Slow Warnings** | ✅ | Alert for steps > 10s |
| **Performance Summary** | ✅ | Detailed metrics report |
| **Bottleneck Detection** | ✅ | Auto-identify slowest step |
| **Task Cancellation** | ✅ | Cancel on timeout |
| **Error Tracking** | ✅ | Record failed steps |

### Benefits

- 🎯 **Visibility**: Know exactly which step is running
- ⏱️ **Timeout Protection**: Prevent indefinite hangs
- 🐌 **Bottleneck Discovery**: Optimize slowest steps first
- 📊 **Performance Metrics**: Data-driven optimization
- 🚨 **Early Warning**: Detect issues before timeout

---

**Author**: Performance Optimization Team  
**Date**: November 4, 2024  
**Version**: 2.0  
**Status**: ✅ Production Ready
