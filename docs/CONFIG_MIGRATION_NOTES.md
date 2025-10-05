# Excel Config Migration Notes

Date: 2025-10-05

This note helps migrate usage to the new config-aware Excel APIs and understand behavior changes after refactorings.

## Summary of Changes

- New config-aware read/write APIs via `ExcelFacade` and services
  - `ExcelFacade.readExcel(InputStream, Class<T>, ExcelConfig)` returns `List<T>` with config applied
  - `ExcelFacade.readExcelWithConfig(InputStream, Class<T>, ExcelConfig, Consumer<List<T>>)` for batch streaming
  - `ExcelFacade.readMultiSheet(...)` for true streaming across multiple sheets
  - `ExcelWritingService` adds strategy-based implementations for `writeToBytes` and position-aware `writeWithPosition`

- Builders
  - `ExcelReaderBuilder` and `ExcelWriterBuilder` construct `ExcelConfig` internally; call terminal ops that pass built config to services
  - Fixed `ExcelConfig.Builder.progressReportInterval(long)` to set the value correctly

- Monitoring and errors
  - `MemoryMonitor` and `ErrorTracker` are wired into read paths (batch callback wrapper)
  - `ExcelConfig` fields `enableMemoryMonitoring`, `memoryThresholdMB`, `maxErrorsBeforeAbort`, `failOnFirstError`, `strictValidation` now take effect in streaming read paths

- Multi-sheet
  - `TrueStreamingMultiSheetProcessor` exposes per-sheet results; available through `ExcelFacade.readMultiSheet(...)`

- Removal of legacy util
  - `ExcelUtil` removed. All callers should use `ExcelFacade` or services/strategies

## Migration Guide

### Reading (in-memory list)

Before:
```java
List<User> users = excelFacade.readExcel(inputStream, User.class);
```

After (with config):
```java
ExcelConfig config = ExcelConfig.builder()
    .batchSize(10_000)
    .enableProgressTracking(true)
    .progressReportInterval(50_000)
    .enableMemoryMonitoring(true)
    .memoryThreshold(512)
    .build();

List<User> users = excelFacade.readExcel(inputStream, User.class, config);
```

### Reading (streaming batches)

Before (ExcelUtil):
```java
// Deprecated
// ExcelUtil.processExcelTrueStreaming(inputStream, User.class, config, batchProcessor);
```

After:
```java
excelFacade.readExcelWithConfig(inputStream, User.class, config, batch -> {
    userRepository.saveAll(batch);
});
```

### Reading (multi-sheet)

```java
Map<String, Class<?>> sheetClassMap = Map.of(
    "Users", User.class,
    "Orders", Order.class
);

Map<String, java.util.function.Consumer<List<?>>> sheetProcessors = Map.of(
    "Users", (List<?> batch) -> userService.saveAll((List<User>) batch),
    "Orders", (List<?> batch) -> orderService.saveAll((List<Order>) batch)
);

Map<String, TrueStreamingSAXProcessor.ProcessingResult> results =
    excelFacade.readMultiSheet(inputStream, sheetClassMap, sheetProcessors, config);
```

### Writing (file)

Before:
```java
excelFacade.writeExcel("output.xlsx", data);
```

After (position-aware):
```java
ExcelConfig config = ExcelConfig.builder().disableAutoSizing(true).build();
excelFacade.writer(data) /* or use service directly */;
// Service path with explicit position
excelWritingService.writeWithPosition("output.xlsx", data, 1, 0, config);
```

### Writing (in-memory bytes)

```java
byte[] bytes = excelWritingService.writeToBytes(data);
```

Notes:
- Uses XSSF for small datasets, SXSSF for large datasets automatically

## Behavioral Notes

- `progressReportInterval` is honored (builder fix). Set `enableProgressTracking(true)` as needed
- Memory checks: when `enableMemoryMonitoring` is true and `jobId` is set in config, monitoring runs during batch processing; warnings are logged on high usage/threshold breach
- Error handling: `failOnFirstError` throws immediately; `maxErrorsBeforeAbort` stops processing after threshold

## API Reference (delta)

- Added
  - `ExcelFacade#readExcel(InputStream, Class<T>, ExcelConfig)`
  - `ExcelFacade#readMultiSheet(InputStream, Map<String,Class<?>>, Map<String,Consumer<List<?>>>, ExcelConfig)`
  - `ExcelWritingService#writeToBytes(List<T>)` (strategy-based)
  - `ExcelWritingService#writeWithPosition(String, List<T>, int, int, ExcelConfig)` (position-aware)

- Changed
  - `ExcelReaderBuilder` and `ExcelWriterBuilder` use `ExcelConfig.Builder` internally; call `buildConfig()` to inspect final config if needed

- Removed
  - `ExcelUtil` (and all direct usages)

## Common Migration Patterns

1) From ExcelUtil (streaming) to Facade + config
```java
// Old
// ExcelUtil.processExcelTrueStreaming(inputStream, User.class, cfg, proc);

// New
excelFacade.readExcelWithConfig(inputStream, User.class, cfg, proc);
```

2) From ad-hoc writes to strategy-based writes
```java
// Use facade for simple writes
excelFacade.writeExcel("out.xlsx", data);

// Use service for position-aware and bytes
excelWritingService.writeWithPosition("out.xlsx", data, 2, 1, cfg);
byte[] bytes = excelWritingService.writeToBytes(data);
```

## Versioning

- Breaking changes: removal of `ExcelUtil`
- Backward-compatible: previous `ExcelFacade` methods remain; new overloads added

## Checklist

- Replace `ExcelUtil` imports with `ExcelFacade`/services
- For small-file returns, prefer `readExcel(..., config)` when tuning needed
- For large-file streaming, use `readExcelWithConfig(..., batchProcessor)`
- For multi-sheet, use `readMultiSheet(...)`
- For writing bytes/position, use `ExcelWritingService` methods
