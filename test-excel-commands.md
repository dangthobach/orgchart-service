# Excel Migration API Test Commands

## 1. CURL Commands

### Upload Excel Async (Basic)
```bash
curl -X POST "http://localhost:8080/api/migration/excel/upload-async" \
  -F "file=@test-data.xlsx" \
  -F "createdBy=admin" \
  -F "maxRows=1000"
```

### Upload Excel Async (Detailed with headers)
```bash
curl -X POST "http://localhost:8080/api/migration/excel/upload-async" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@test-data.xlsx" \
  -F "createdBy=admin" \
  -F "maxRows=1000" \
  -v
```

### Upload Excel Async (No row limit)
```bash
curl -X POST "http://localhost:8080/api/migration/excel/upload-async" \
  -F "file=@test-data.xlsx" \
  -F "createdBy=system" \
  -F "maxRows=0"
```

### Get Job Status
```bash
curl -X GET "http://localhost:8080/api/migration/job/{jobId}/status" \
  -H "Accept: application/json"
```

### Get System Metrics
```bash
curl -X GET "http://localhost:8080/api/migration/system/metrics" \
  -H "Accept: application/json"
```

### Cleanup Job Data
```bash
curl -X DELETE "http://localhost:8080/api/migration/job/{jobId}/cleanup?keepErrors=true" \
  -H "Accept: application/json"
```

## 2. HTTPie Commands

### Upload Excel Async (Basic)
```bash
http --form POST localhost:8080/api/migration/excel/upload-async \
  file@test-data.xlsx \
  createdBy=admin \
  maxRows:=1000
```

### Upload Excel Async (With custom filename)
```bash
http --form POST localhost:8080/api/migration/excel/upload-async \
  file@"C:\path\to\your\excel-file.xlsx" \
  createdBy=admin \
  maxRows:=1000
```

### Upload Excel Async (No row limit)
```bash
http --form POST localhost:8080/api/migration/excel/upload-async \
  file@test-data.xlsx \
  createdBy=system \
  maxRows:=0
```

### Get Job Status
```bash
http GET localhost:8080/api/migration/job/{jobId}/status
```

### Get System Metrics
```bash
http GET localhost:8080/api/migration/system/metrics
```

### Cleanup Job Data
```bash
http DELETE localhost:8080/api/migration/job/{jobId}/cleanup \
  keepErrors==true
```

## 3. PowerShell (Invoke-RestMethod)

### Upload Excel Async
```powershell
$filePath = "C:\path\to\your\test-data.xlsx"
$uri = "http://localhost:8080/api/migration/excel/upload-async"

$form = @{
    file = Get-Item -Path $filePath
    createdBy = "admin"
    maxRows = 1000
}

Invoke-RestMethod -Uri $uri -Method Post -Form $form
```

### Get Job Status
```powershell
$jobId = "your-job-id-here"
$uri = "http://localhost:8080/api/migration/job/$jobId/status"

Invoke-RestMethod -Uri $uri -Method Get
```

## 4. Test Files Creation

### Create Simple Test Excel (Using PowerShell)
```powershell
# Install ImportExcel module if not already installed
# Install-Module -Name ImportExcel -Force

$testData = @(
    [PSCustomObject]@{
        "ma_don_vi" = "DV001"
        "ma_thung" = "TH001"
        "ngay_chung_tu" = "2024-01-01"
        "so_luong_tap" = 100
        "kho_vpbank" = "Kho HCM"
        "loai_chung_tu" = "Hợp đồng"
    },
    [PSCustomObject]@{
        "ma_don_vi" = "DV002" 
        "ma_thung" = "TH002"
        "ngay_chung_tu" = "2024-01-02"
        "so_luong_tap" = 50
        "kho_vpbank" = "Kho HN"
        "loai_chung_tu" = "Chứng từ"
    }
)

$testData | Export-Excel -Path "test-data.xlsx" -WorksheetName "Sheet1" -AutoSize
```

## 5. Response Examples

### Successful Async Upload Response
```json
{
    "message": "Migration started successfully",
    "filename": "test-data.xlsx",
    "status": "PROCESSING",
    "note": "Use /status endpoint to check progress"
}
```

### Job Status Response
```json
{
    "jobId": "job-12345",
    "status": "COMPLETED",
    "totalRows": 1000,
    "validRows": 950,
    "errorRows": 50,
    "startTime": "2024-01-01T10:00:00",
    "endTime": "2024-01-01T10:05:00",
    "duration": "5 minutes"
}
```

## 6. Error Handling Examples

### Invalid File Format
```bash
curl -X POST "http://localhost:8080/api/migration/excel/upload-async" \
  -F "file=@test.txt" \
  -F "createdBy=admin"
```

Response:
```json
{
    "error": "Invalid file format. Only .xlsx and .xls files are supported"
}
```

### Empty File
```bash
curl -X POST "http://localhost:8080/api/migration/excel/upload-async" \
  -F "file=@empty.xlsx" \
  -F "createdBy=admin"
```

Response:
```json
{
    "error": "File is empty"
}
```