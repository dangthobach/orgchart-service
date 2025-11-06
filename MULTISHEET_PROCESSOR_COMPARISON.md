# Phân Tích So Sánh: MultiSheetProcessor vs TrueStreamingMultiSheetProcessor

## 📋 Tổng Quan

Hai class này có **mục đích và trách nhiệm khác nhau**, nhưng đang được sử dụng cùng nhau trong workflow. Phân tích này sẽ làm rõ sự khác biệt và đề xuất giải pháp tối ưu.

---

## 🔍 Phân Tích Chi Tiết

### 1. **MultiSheetProcessor** (Service Layer - Orchestration)

**Location:** `com.learnmore.application.service.multisheet.MultiSheetProcessor`

**Mục đích:**
- ✅ **Business Workflow Orchestration**: Quản lý toàn bộ quy trình Ingest → Validate → Insert
- ✅ **Parallel Processing Management**: Điều phối xử lý song song nhiều sheets với ExecutorService
- ✅ **Status Tracking**: Theo dõi trạng thái từng sheet trong database (MigrationJobSheetEntity)
- ✅ **Transaction Management**: Quản lý transaction độc lập cho mỗi sheet
- ✅ **Error Handling & Retry**: Retry logic, optimistic locking, timeout handling

**Đặc điểm:**
```java
@Service  // Spring-managed service
public class MultiSheetProcessor {
    private final SheetIngestService ingestService;
    private final SheetValidationService validationService;
    private final SheetInsertService insertService;
    private final ExcelFacade excelFacade;  // ✅ Uses ExcelFacade
    
    // ExecutorService for parallel processing
    private ExecutorService currentExecutor;
}
```

**Responsibilities:**
1. **Workflow Orchestration**: Ingest → Validate → Insert
2. **Parallel Execution**: Quản lý thread pool và future tracking
3. **Database Operations**: Status updates, transaction management
4. **Configuration**: Load từ SheetMigrationConfig
5. **Error Recovery**: Retry, rollback, status tracking

**Dependencies:**
- Spring Framework (Service, Transaction, Retry)
- Database (JPA Repositories)
- Business Services (IngestService, ValidationService, InsertService)

---

### 2. **TrueStreamingMultiSheetProcessor** (Low-level Utility - Excel Reading)

**Location:** `com.learnmore.application.utils.sax.TrueStreamingMultiSheetProcessor`

**Mục đích:**
- ✅ **Excel Reading Only**: Chỉ đọc Excel file và convert thành DTOs
- ✅ **True Streaming SAX**: Xử lý multiple sheets với SAX streaming (memory efficient)
- ✅ **Type Mapping**: Map sheet name → DTO class (`Map<String, Class<?>>`)
- ✅ **Batch Processing**: Gọi batch processor cho mỗi batch của từng sheet

**Đặc điểm:**
```java
// Plain Java class (no Spring annotations)
public class TrueStreamingMultiSheetProcessor {
    private final Map<String, Class<?>> sheetClassMap;
    private final Map<String, Consumer<List<?>>> sheetProcessors;
    private final ExcelConfig config;
    
    // NO business logic, NO database operations
}
```

**Responsibilities:**
1. **Excel Parsing**: Đọc Excel file với SAX streaming
2. **Sheet Iteration**: Process từng sheet trong workbook
3. **DTO Conversion**: Convert Excel rows → DTO objects
4. **Batch Callback**: Gọi consumer cho mỗi batch
5. **Metrics Collection**: Return ProcessingResult với statistics

**Dependencies:**
- Apache POI (OPCPackage, XSSFReader, SAX)
- TrueStreamingSAXProcessor (low-level)
- ExcelConfig (configuration only)

**NO Dependencies on:**
- ❌ Spring Framework
- ❌ Database
- ❌ Business Logic
- ❌ Services

---

## 📊 So Sánh Trực Tiếp

| Aspect | MultiSheetProcessor | TrueStreamingMultiSheetProcessor |
|--------|-------------------|--------------------------------|
| **Layer** | Service Layer | Utility Layer |
| **Purpose** | Business Workflow | Excel Reading |
| **Spring** | ✅ @Service | ❌ Plain Java |
| **Database** | ✅ JPA Repositories | ❌ No DB |
| **Transactions** | ✅ @Transactional | ❌ No |
| **Parallel Processing** | ✅ ExecutorService | ❌ Sequential |
| **Status Tracking** | ✅ Database | ❌ No |
| **Retry Logic** | ✅ @Retryable | ❌ No |
| **Excel Reading** | ❌ Delegates to ExcelFacade | ✅ Direct SAX |
| **DTO Mapping** | ✅ Loads from config | ✅ Receives map |
| **Business Logic** | ✅ Ingest/Validate/Insert | ❌ No |
| **Error Handling** | ✅ Comprehensive | ❌ Basic exceptions |

---

## 🔄 Kiến Trúc Hiện Tại

```
MultiSheetProcessor (Service)
  │
  ├─> processWithExcelFacadeParallel()
  │     │
  │     ├─> buildSheetClassMap() → Map<sheetName, DTO.class>
  │     ├─> buildSheetProcessors() → Map<sheetName, Consumer>
  │     │
  │     └─> ExcelFacade.readMultiSheet()
  │           │
  │           └─> TrueStreamingMultiSheetProcessor.processTrueStreaming()
  │                 │
  │                 ├─> Process Sheet 1 → HopDongDTO
  │                 ├─> Process Sheet 2 → CifDTO
  │                 └─> Process Sheet 3 → TapDTO
  │
  └─> Parallel Processing (Post-Ingest)
        ├─> Thread 1: Validate + Insert Sheet 1
        ├─> Thread 2: Validate + Insert Sheet 2
        └─> Thread 3: Validate + Insert Sheet 3
```

**Current Flow:**
1. **MultiSheetProcessor** orchestrates workflow
2. **ExcelFacade** provides unified API
3. **TrueStreamingMultiSheetProcessor** does actual Excel reading
4. **MultiSheetProcessor** handles validation/insertion in parallel

---

## ⚠️ Vấn Đề Hiện Tại

### 1. **Duplication Risk**

**MultiSheetProcessor** có method `processInParallelFromMemory()` (legacy) không dùng ExcelFacade:
```java
// Legacy approach - không dùng ExcelFacade
private List<SheetProcessResult> processInParallelFromMemory(...) {
    // Uses SheetIngestService directly (SAX duplicate logic)
}
```

**TrueStreamingMultiSheetProcessor** cũng implement SAX processing.

### 2. **Separation of Concerns**

✅ **GOOD**: Hiện tại đã tách biệt rõ ràng:
- MultiSheetProcessor: Business logic
- TrueStreamingMultiSheetProcessor: Technical concern (Excel reading)

### 3. **Parallel Processing**

**Current limitation:**
- Excel reading: Sequential (TrueStreamingMultiSheetProcessor)
- Post-processing: Parallel (MultiSheetProcessor)

**Could be optimized:**
- Parallel Excel reading nếu cần (nhưng phức tạp vì OPCPackage sharing)

---

## ✅ Giải Pháp Đề Xuất

### **Option 1: Giữ Nguyên (RECOMMENDED)**

**Rationale:**
- ✅ **Separation of Concerns**: Rõ ràng, dễ maintain
- ✅ **Single Responsibility**: Mỗi class có một trách nhiệm
- ✅ **Reusability**: TrueStreamingMultiSheetProcessor có thể dùng ở nhiều nơi
- ✅ **Testability**: Dễ test từng layer riêng

**Architecture:**
```
MultiSheetProcessor (Orchestration)
  └─> ExcelFacade (Unified API)
      └─> TrueStreamingMultiSheetProcessor (Excel Reading)
```

### **Option 2: Merge vào MultiSheetProcessor (NOT RECOMMENDED)**

**Vấn đề:**
- ❌ Vi phạm Single Responsibility Principle
- ❌ TrueStreamingMultiSheetProcessor không thể reuse
- ❌ Mixing business logic với technical concerns
- ❌ Khó test và maintain

### **Option 3: Tối Ưu Hiện Tại (RECOMMENDED)**

**Cải thiện:**
1. **Remove legacy methods** trong MultiSheetProcessor
2. **Always use ExcelFacade** thay vì direct SAX
3. **Add parallel reading support** trong TrueStreamingMultiSheetProcessor (optional, future)

---

## 🎯 Kết Luận

### **Có thể dùng chung giải pháp không?**

**Câu trả lời: KHÔNG NÊN merge, nhưng NÊN optimize:**

1. **Giữ Separation of Concerns:**
   - `MultiSheetProcessor`: Business orchestration
   - `TrueStreamingMultiSheetProcessor`: Technical Excel reading

2. **Tối ưu Integration:**
   - MultiSheetProcessor → ExcelFacade → TrueStreamingMultiSheetProcessor
   - Đây là pattern tốt, nên giữ

3. **Cleanup:**
   - Remove legacy methods không dùng ExcelFacade
   - Ensure all paths use ExcelFacade

### **Recommendation:**

✅ **KEEP CURRENT ARCHITECTURE** (với optimizations):
- MultiSheetProcessor: Orchestration layer
- ExcelFacade: Unified API layer  
- TrueStreamingMultiSheetProcessor: Low-level utility

✅ **OPTIMIZATIONS:**
1. Remove `processInParallelFromMemory()` legacy method
2. Remove `processSheetFromMemory()` nếu không dùng
3. Ensure all code paths use ExcelFacade
4. Add parallel Excel reading support (future enhancement)

---

## 📝 Next Steps

1. ✅ Phân tích hoàn tất
2. ⏳ Cleanup legacy methods
3. ⏳ Ensure all paths use ExcelFacade
4. ⏳ Add parallel reading support (optional)

