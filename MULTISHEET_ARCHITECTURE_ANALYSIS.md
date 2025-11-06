# Phân Tích Kiến Trúc Multi-Sheet Processing

## 📋 Tổng Quan

Codebase hiện tại có **nhiều lớp xử lý multi-sheet** với các mục đích và implementation khác nhau, gây ra:
- **Code duplication** (logic SAX processing bị duplicate)
- **Inconsistency** (mỗi class dùng approach khác nhau)
- **Maintenance overhead** (phải maintain nhiều implementations)
- **Confusion** (không rõ khi nào dùng class nào)

---

## 🔍 Phân Tích Chi Tiết

### 1. **MultiSheetProcessor** (Service Layer - Workflow Orchestration)

**Location:** `com.learnmore.application.service.multisheet.MultiSheetProcessor`

**Mục đích:**
- ✅ **Workflow orchestration**: Quản lý toàn bộ quy trình Ingest → Validate → Insert
- ✅ **Parallel processing**: Điều phối xử lý song song nhiều sheets
- ✅ **Status tracking**: Theo dõi trạng thái từng sheet trong database
- ✅ **Transaction management**: Quản lý transaction độc lập cho mỗi sheet
- ✅ **Error handling**: Retry logic, optimistic locking

**Đặc điểm:**
- Service-level, Spring-managed
- Sử dụng `SheetIngestService`, `SheetValidationService`, `SheetInsertService`
- **KHÔNG** sử dụng `ExcelFacade` hay `ReadStrategy`
- Tự quản lý parallel execution với `ExecutorService`

**Use case:**
- Migration jobs với workflow phức tạp
- Cần tracking status trong database
- Cần transaction isolation giữa các sheets

---

### 2. **MultiSheetReadStrategy** (Strategy Pattern - Excel Reading)

**Location:** `com.learnmore.application.excel.strategy.impl.MultiSheetReadStrategy`

**Mục đích:**
- ✅ **Strategy pattern**: Part of ExcelFacade strategy selection
- ✅ **Generic Excel reading**: Dùng cho mọi use case đọc Excel
- ❌ **CHƯA HOÀN THIỆN**: Hiện tại chỉ process sheet đầu tiên (TODO comment)

**Đặc điểm:**
- Strategy implementation, Spring-managed
- Được sử dụng bởi `ExcelReadingService` → `ExcelFacade`
- **API limitation**: Chỉ nhận `Class<T>` (single class), không support `Map<String, Class<?>>`
- **Hiện tại**: Fallback về single sheet processing

**Use case:**
- Generic Excel reading với multiple sheets
- Cần strategy pattern cho flexibility
- **NHƯNG**: Chưa implement đầy đủ multi-sheet support

---

### 3. **TrueStreamingMultiSheetProcessor** (Low-level Utility)

**Location:** `com.learnmore.application.utils.sax.TrueStreamingMultiSheetProcessor`

**Mục đích:**
- ✅ **True streaming SAX**: Xử lý multiple sheets với SAX streaming
- ✅ **Support multiple classes**: Nhận `Map<String, Class<?>>` để map sheet → class
- ✅ **Low memory**: Chỉ giữ batch trong memory

**Đặc điểm:**
- Low-level utility class
- Không có business logic
- Được sử dụng bởi `ExcelFacade.readMultiSheet()`
- **Không được dùng** bởi `MultiSheetProcessor` hay `SheetIngestService`

**Use case:**
- Low-level Excel processing với multiple sheets
- Cần true streaming với minimal memory
- Support different POJO classes per sheet

---

### 4. **SheetIngestService** (Service Layer - Data Ingestion)

**Location:** `com.learnmore.application.service.multisheet.SheetIngestService`

**Mục đích:**
- ✅ **Ingest single sheet**: Đọc 1 sheet và lưu vào staging table
- ✅ **Business logic**: Column mapping, business key generation, batch insert
- ❌ **Code duplication**: Tự implement SAX processing thay vì dùng `ExcelFacade`

**Đặc điểm:**
- Service-level, Spring-managed
- **Tự implement SAX processing** (duplicate logic với `TrueStreamingSAXProcessor`)
- Không sử dụng `ExcelFacade` hay `MultiSheetReadStrategy`
- Có custom `IngestHandler` để batch insert vào database

**Use case:**
- Migration workflow: Ingest → Validate → Insert
- Cần business logic (mapping, normalization)
- **NHƯNG**: Duplicate SAX logic nên cần refactor

---

## ⚠️ Vấn Đề Hiện Tại

### 1. **Code Duplication**

```
SheetIngestService.ingestSheetFromMemory()
  └─> Tự implement SAX processing với OPCPackage, XSSFReader
  
TrueStreamingMultiSheetProcessor.processTrueStreaming()
  └─> Cũng dùng OPCPackage, XSSFReader, SAX processing
  
TrueStreamingSAXProcessor.processExcelStreamTrue()
  └─> Cũng dùng SAX processing
```

**Impact:**
- Bug fix phải update ở nhiều nơi
- Performance optimization không consistent
- Memory leak risk ở nhiều implementations

---

### 2. **Inconsistent API**

**MultiSheetReadStrategy:**
```java
// Chỉ support single Class<T>
execute(InputStream, Class<T>, ExcelConfig, Consumer<List<T>>)
```

**TrueStreamingMultiSheetProcessor:**
```java
// Support Map<String, Class<?>>
processTrueStreaming(InputStream, Map<String, Class<?>>, ...)
```

**SheetIngestService:**
```java
// Không dùng strategy, tự implement
ingestSheetFromMemory(InputStream, SheetConfig)
```

---

### 3. **MultiSheetReadStrategy Chưa Hoàn Thiện**

```java
// Line 97: TODO comment
log.info("Multi-sheet processing: Using first/default sheet only 
         (full multi-sheet API requires sheet-class mapping)");
```

**Vấn đề:**
- Strategy được đăng ký nhưng không thực sự process multiple sheets
- Gây confusion khi được selected nhưng chỉ process sheet đầu tiên

---

### 4. **Architecture Mismatch**

```
MultiSheetProcessor (Workflow)
  └─> SheetIngestService (SAX duplicate)
      └─> ❌ Không dùng ExcelFacade

ExcelFacade (Excel operations)
  └─> ExcelReadingService
      └─> ReadStrategySelector
          └─> MultiSheetReadStrategy (incomplete)
              └─> TrueStreamingSAXProcessor (single sheet)
```

**Vấn đề:**
- `MultiSheetProcessor` không tận dụng `ExcelFacade` infrastructure
- `MultiSheetReadStrategy` không kết nối với `TrueStreamingMultiSheetProcessor`

---

## ✅ Giải Pháp Đề Xuất

### **Kiến Trúc Thống Nhất**

```
┌─────────────────────────────────────────────────────────┐
│  MultiSheetProcessor (Workflow Orchestration)          │
│  - Ingest → Validate → Insert workflow                  │
│  - Parallel/Sequential execution                        │
│  - Status tracking                                      │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  ExcelFacade (Unified Excel API)                        │
│  - readMultiSheet() for multi-sheet reading             │
│  - Strategy pattern for flexibility                     │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  TrueStreamingMultiSheetProcessor (Low-level)           │
│  - Single source of truth for SAX processing           │
│  - True streaming, minimal memory                       │
└─────────────────────────────────────────────────────────┘
```

### **Refactoring Plan**

#### **Phase 1: Hoàn Thiện MultiSheetReadStrategy**

1. **Extend API** để support `Map<String, Class<?>>`
2. **Delegate** đến `TrueStreamingMultiSheetProcessor`
3. **Aggregate results** từ multiple sheets

#### **Phase 2: Refactor SheetIngestService**

1. **Remove duplicate SAX logic**
2. **Use ExcelFacade.readMultiSheet()** thay vì tự implement
3. **Wrap batch processor** để insert vào staging table

#### **Phase 3: Unified Multi-Sheet Service (Optional)**

1. Tạo `UnifiedMultiSheetService` nếu cần
2. Consolidate common logic
3. Provide consistent API

---

## 📊 So Sánh Trước & Sau

### **Trước Refactor:**

| Class | Purpose | SAX Logic | Strategy Usage |
|-------|---------|-----------|----------------|
| MultiSheetProcessor | Workflow | ❌ No | ❌ No |
| MultiSheetReadStrategy | Excel Reading | ❌ Incomplete | ✅ Yes |
| TrueStreamingMultiSheetProcessor | Low-level | ✅ Yes | ❌ No |
| SheetIngestService | Ingestion | ✅ Duplicate | ❌ No |

### **Sau Refactor:**

| Class | Purpose | SAX Logic | Strategy Usage |
|-------|---------|-----------|----------------|
| MultiSheetProcessor | Workflow | ❌ No | ✅ Yes (via ExcelFacade) |
| MultiSheetReadStrategy | Excel Reading | ✅ Complete | ✅ Yes |
| TrueStreamingMultiSheetProcessor | Low-level | ✅ Single source | ❌ No |
| SheetIngestService | Ingestion | ❌ Removed | ✅ Yes (via ExcelFacade) |

---

## 🎯 Kết Luận

1. **MultiSheetProcessor** và **MultiSheetReadStrategy** phục vụ **mục đích khác nhau**:
   - `MultiSheetProcessor`: Workflow orchestration (business logic)
   - `MultiSheetReadStrategy`: Excel reading strategy (technical concern)

2. **Vấn đề chính**: 
   - Code duplication (SAX logic)
   - Incomplete implementation (MultiSheetReadStrategy)
   - Architecture mismatch (không dùng ExcelFacade)

3. **Giải pháp**:
   - Hoàn thiện `MultiSheetReadStrategy`
   - Refactor `SheetIngestService` để dùng `ExcelFacade`
   - Consolidate SAX logic vào `TrueStreamingMultiSheetProcessor`

---

## 📝 Next Steps

1. ✅ Phân tích hoàn tất
2. ⏳ Refactor MultiSheetReadStrategy
3. ⏳ Refactor SheetIngestService
4. ⏳ Testing & Validation

