# ExcelUtil Review & Refactoring Plan

**Date:** 2025-10-03
**Current State:** 1452 lines, 29 public static methods
**Status:** Needs Refactoring

---

## 🔍 Current State Analysis

### Metrics

| Metric | Value | Assessment |
|--------|-------|------------|
| Lines of Code | 1,452 | ⚠️ Too large (God Object) |
| Public Methods | 29 | ⚠️ Too many responsibilities |
| Class Type | Utility (static) | ⚠️ Hard to test, no DI |
| Complexity | High | ⚠️ Multiple concerns mixed |
| Dependencies | 15+ | ⚠️ Tight coupling |

### Problems Identified

#### 1. **God Object Anti-Pattern** ⚠️
- Single class doing too much (reading, writing, validation, monitoring)
- 1,452 lines violates Single Responsibility Principle
- Hard to maintain and extend

#### 2. **Static Utility Hell** ⚠️
- All methods static - no dependency injection
- Hard to mock for testing
- No polymorphism or strategy pattern
- Cannot override behavior

#### 3. **Mixed Concerns** ⚠️
```java
// Reading Excel
processExcel()
processExcelTrueStreaming()
processMultiSheetExcelTrueStreaming()

// Writing Excel
writeToExcel()
writeToExcelBytes()
writeToExcelStreamingSXSSF()

// Async Processing
processExcelAsync()
processMultipleExcelFilesAsync()

// Parallel Processing
processExcelParallel()

// Validation Setup
setupValidationRules()

// Strategy Selection
determineOptimalWriteStrategy()

// Memory Monitoring
// Mixed into all methods
```

#### 4. **Poor Abstraction** ⚠️
- No interfaces - hard to swap implementations
- Concrete dependencies everywhere
- No Strategy Pattern for read/write strategies

#### 5. **Duplication** ⚠️
- Multiple similar methods with slight variations
- `processExcel()`, `processExcelToList()`, `processExcelTrueStreaming()`
- Copy-paste code patterns

#### 6. **Hard to Use** ⚠️
- Too many method overloads
- Unclear which method to use
- No fluent builder for common scenarios

---

## 🎯 Proposed Architecture (Hexagonal + Strategy Pattern)

### New Structure

```
application/
├── excel/
│   ├── ExcelFacade.java               // Simplified API entry point
│   ├── port/
│   │   ├── input/
│   │   │   ├── ExcelReader.java       // Interface
│   │   │   ├── ExcelWriter.java       // Interface
│   │   │   └── ExcelProcessor.java    // Interface
│   │   └── output/
│   │       └── ExcelStorage.java      // Interface for persistence
│   ├── service/
│   │   ├── ExcelReadingService.java   // Impl for reading
│   │   ├── ExcelWritingService.java   // Impl for writing
│   │   └── ExcelProcessingService.java // Orchestration
│   ├── strategy/
│   │   ├── read/
│   │   │   ├── ReadStrategy.java      // Interface
│   │   │   ├── StreamingReadStrategy.java
│   │   │   ├── StandardReadStrategy.java
│   │   │   └── ParallelReadStrategy.java
│   │   └── write/
│   │       ├── WriteStrategy.java     // Interface
│   │       ├── XSSFWriteStrategy.java
│   │       ├── SXSSFWriteStrategy.java
│   │       └── CSVWriteStrategy.java
│   ├── factory/
│   │   ├── ExcelReaderFactory.java
│   │   └── ExcelWriterFactory.java
│   └── builder/
│       ├── ExcelReaderBuilder.java    // Fluent API
│       └── ExcelWriterBuilder.java    // Fluent API
└── infrastructure/
    └── excel/
        └── TrueStreamingSAXProcessor.java // Keep as is
```

---

## 🔧 Refactoring Steps

### Phase 1: Extract Interfaces (Abstraction)

#### 1.1 Create Core Interfaces

```java
/**
 * Port: Excel reading interface
 */
public interface ExcelReader<T> {
    /**
     * Read Excel file and process in batches
     */
    ProcessingResult read(InputStream inputStream,
                         Class<T> beanClass,
                         Consumer<List<T>> batchProcessor);

    /**
     * Read Excel file and return all results
     */
    List<T> readAll(InputStream inputStream, Class<T> beanClass);
}

/**
 * Port: Excel writing interface
 */
public interface ExcelWriter<T> {
    /**
     * Write data to Excel file
     */
    void write(String fileName, List<T> data);

    /**
     * Write data to Excel bytes
     */
    byte[] writeToBytes(List<T> data);
}

/**
 * Port: Excel processing orchestration
 */
public interface ExcelProcessor<T> {
    /**
     * Process Excel with validation and transformations
     */
    ProcessingResult process(InputStream inputStream,
                            Class<T> beanClass,
                            Consumer<List<T>> batchProcessor);
}
```

#### 1.2 Create Strategy Interfaces

```java
/**
 * Strategy for reading Excel
 */
public interface ReadStrategy<T> {
    ProcessingResult execute(InputStream inputStream,
                           Class<T> beanClass,
                           ExcelConfig config,
                           Consumer<List<T>> batchProcessor) throws ExcelProcessException;

    String getName();
    boolean supports(ExcelConfig config);
}

/**
 * Strategy for writing Excel
 */
public interface WriteStrategy<T> {
    void execute(String fileName,
                List<T> data,
                ExcelConfig config) throws ExcelProcessException;

    String getName();
    boolean supports(int dataSize, long cellCount, ExcelConfig config);
}
```

---

### Phase 2: Extract Services (Single Responsibility)

#### 2.1 ExcelReadingService

```java
/**
 * Service responsible ONLY for reading Excel files
 */
@Service
public class ExcelReadingService<T> implements ExcelReader<T> {

    private final ReadStrategySelector strategySelector;
    private final ValidationService validationService;
    private final MemoryMonitoringService memoryMonitor;

    @Override
    public ProcessingResult read(InputStream inputStream,
                                Class<T> beanClass,
                                Consumer<List<T>> batchProcessor) {
        // Select optimal strategy
        ReadStrategy<T> strategy = strategySelector.selectStrategy(config);

        // Execute with monitoring
        return memoryMonitor.executeWithMonitoring(() ->
            strategy.execute(inputStream, beanClass, config, batchProcessor)
        );
    }

    @Override
    public List<T> readAll(InputStream inputStream, Class<T> beanClass) {
        List<T> results = new ArrayList<>();
        read(inputStream, beanClass, results::addAll);
        return results;
    }
}
```

#### 2.2 ExcelWritingService

```java
/**
 * Service responsible ONLY for writing Excel files
 */
@Service
public class ExcelWritingService<T> implements ExcelWriter<T> {

    private final WriteStrategySelector strategySelector;
    private final MemoryMonitoringService memoryMonitor;

    @Override
    public void write(String fileName, List<T> data) {
        // Select optimal strategy based on data size
        WriteStrategy<T> strategy = strategySelector.selectStrategy(data.size(), config);

        // Execute with monitoring
        memoryMonitor.executeWithMonitoring(() ->
            strategy.execute(fileName, data, config)
        );
    }

    @Override
    public byte[] writeToBytes(List<T> data) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Implementation
        return outputStream.toByteArray();
    }
}
```

#### 2.3 ExcelProcessingService (Orchestrator)

```java
/**
 * Orchestrator service that coordinates reading, validation, and processing
 */
@Service
public class ExcelProcessingService<T> implements ExcelProcessor<T> {

    private final ExcelReader<T> reader;
    private final ValidationService validationService;
    private final TransformationService transformationService;

    @Override
    public ProcessingResult process(InputStream inputStream,
                                   Class<T> beanClass,
                                   Consumer<List<T>> batchProcessor) {
        // Early validation
        validationService.validateFile(inputStream);

        // Read with validation
        return reader.read(inputStream, beanClass, batch -> {
            // Validate batch
            List<T> validatedBatch = validationService.validate(batch);

            // Transform if needed
            List<T> transformedBatch = transformationService.transform(validatedBatch);

            // Process
            batchProcessor.accept(transformedBatch);
        });
    }
}
```

---

### Phase 3: Implement Strategy Pattern

#### 3.1 Read Strategies

```java
/**
 * Streaming read strategy for large files
 */
@Component
public class StreamingReadStrategy<T> implements ReadStrategy<T> {

    @Override
    public ProcessingResult execute(InputStream inputStream,
                                   Class<T> beanClass,
                                   ExcelConfig config,
                                   Consumer<List<T>> batchProcessor) {
        TrueStreamingSAXProcessor<T> processor =
            new TrueStreamingSAXProcessor<>(beanClass, config, validationRules, batchProcessor);

        return processor.processExcelStreamTrue(inputStream);
    }

    @Override
    public boolean supports(ExcelConfig config) {
        return true; // Always support streaming
    }

    @Override
    public String getName() {
        return "STREAMING_SAX";
    }
}

/**
 * Parallel read strategy for multi-core processing
 */
@Component
public class ParallelReadStrategy<T> implements ReadStrategy<T> {

    private final ExecutorService executorService;

    @Override
    public ProcessingResult execute(...) {
        // Implement parallel batch processing
    }

    @Override
    public boolean supports(ExcelConfig config) {
        return config.isParallelProcessing() &&
               Runtime.getRuntime().availableProcessors() > 2;
    }

    @Override
    public String getName() {
        return "PARALLEL_BATCH";
    }
}
```

#### 3.2 Write Strategies

```java
/**
 * XSSF write strategy for small files
 */
@Component
public class XSSFWriteStrategy<T> implements WriteStrategy<T> {

    @Override
    public void execute(String fileName, List<T> data, ExcelConfig config) {
        // Use traditional XSSF workbook
        Workbook workbook = new XSSFWorkbook();
        // ... write logic
    }

    @Override
    public boolean supports(int dataSize, long cellCount, ExcelConfig config) {
        return cellCount <= 1_000_000;
    }

    @Override
    public String getName() {
        return "XSSF_STANDARD";
    }
}

/**
 * SXSSF streaming write strategy for large files
 */
@Component
public class SXSSFWriteStrategy<T> implements WriteStrategy<T> {

    @Override
    public void execute(String fileName, List<T> data, ExcelConfig config) {
        // Use streaming workbook
        SXSSFWorkbook workbook = new SXSSFWorkbook(config.getSxssfRowAccessWindowSize());
        // ... write logic
    }

    @Override
    public boolean supports(int dataSize, long cellCount, ExcelConfig config) {
        return cellCount > 1_000_000 && cellCount <= 5_000_000;
    }

    @Override
    public String getName() {
        return "SXSSF_STREAMING";
    }
}

/**
 * CSV write strategy for very large files
 */
@Component
public class CSVWriteStrategy<T> implements WriteStrategy<T> {

    @Override
    public void execute(String fileName, List<T> data, ExcelConfig config) {
        // Write to CSV for maximum performance
    }

    @Override
    public boolean supports(int dataSize, long cellCount, ExcelConfig config) {
        return cellCount > 5_000_000 || config.isPreferCSVForLargeData();
    }

    @Override
    public String getName() {
        return "CSV_STREAMING";
    }
}
```

---

### Phase 4: Create Fluent Builder API

```java
/**
 * Fluent builder for reading Excel files
 */
public class ExcelReaderBuilder<T> {

    private Class<T> beanClass;
    private ExcelConfig config;
    private Consumer<List<T>> batchProcessor;
    private final ExcelReadingService<T> readingService;

    public ExcelReaderBuilder<T> forClass(Class<T> beanClass) {
        this.beanClass = beanClass;
        return this;
    }

    public ExcelReaderBuilder<T> withConfig(ExcelConfig config) {
        this.config = config;
        return this;
    }

    public ExcelReaderBuilder<T> batchSize(int size) {
        this.config = config.toBuilder().batchSize(size).build();
        return this;
    }

    public ExcelReaderBuilder<T> onBatch(Consumer<List<T>> processor) {
        this.batchProcessor = processor;
        return this;
    }

    public ProcessingResult read(InputStream inputStream) {
        return readingService.read(inputStream, beanClass, batchProcessor);
    }

    public List<T> readAll(InputStream inputStream) {
        return readingService.readAll(inputStream, beanClass);
    }
}
```

---

### Phase 5: Create Simple Facade

```java
/**
 * Simplified facade for common Excel operations
 * Hides complexity, provides easy-to-use API
 */
@Component
public class ExcelFacade {

    private final ExcelReadingService<?> readingService;
    private final ExcelWritingService<?> writingService;
    private final ExcelProcessingService<?> processingService;

    // ========== Simple Read API ==========

    public <T> List<T> readExcel(InputStream inputStream, Class<T> beanClass) {
        return readingService.readAll(inputStream, beanClass);
    }

    public <T> ProcessingResult readExcel(InputStream inputStream,
                                         Class<T> beanClass,
                                         Consumer<List<T>> batchProcessor) {
        return readingService.read(inputStream, beanClass, batchProcessor);
    }

    // ========== Simple Write API ==========

    public <T> void writeExcel(String fileName, List<T> data) {
        writingService.write(fileName, data);
    }

    public <T> byte[] writeExcelToBytes(List<T> data) {
        return writingService.writeToBytes(data);
    }

    // ========== Fluent Builder API ==========

    public <T> ExcelReaderBuilder<T> read() {
        return new ExcelReaderBuilder<>(readingService);
    }

    public <T> ExcelWriterBuilder<T> write() {
        return new ExcelWriterBuilder<>(writingService);
    }
}
```

---

## 📝 Usage Examples (Before vs After)

### Before (Current - Hard to Use)

```java
// Reading - unclear which method to use
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    .enableProgressTracking(true)
    .build();

List<User> users = ExcelUtil.processExcel(inputStream, User.class, config);
// OR
ExcelUtil.processExcelTrueStreaming(inputStream, User.class, config, batch -> {
    // Process batch
});
// OR
ExcelUtil.processExcelToList(inputStream, User.class, config);
// Too many choices, unclear which is best
```

### After (Refactored - Easy to Use)

```java
// Option 1: Simple facade
List<User> users = excelFacade.readExcel(inputStream, User.class);

// Option 2: Fluent builder
ProcessingResult result = excelFacade.read()
    .forClass(User.class)
    .batchSize(5000)
    .enableProgressTracking()
    .onBatch(batch -> {
        // Process batch
    })
    .read(inputStream);

// Option 3: Direct service (DI friendly)
@Autowired
private ExcelReader<User> excelReader;

List<User> users = excelReader.readAll(inputStream, User.class);
```

---

## 🎯 Benefits After Refactoring

### 1. Clean Code ✅

| Aspect | Before | After |
|--------|--------|-------|
| Class Size | 1,452 lines | < 200 lines per class |
| Responsibilities | Many | Single per class |
| Testability | Hard (static) | Easy (DI, mocks) |
| Extensibility | Hard | Easy (new strategies) |

### 2. Design Patterns ✅

- **Strategy Pattern**: Pluggable read/write strategies
- **Facade Pattern**: Simple API for common use cases
- **Builder Pattern**: Fluent, readable configuration
- **Dependency Injection**: Testable, loosely coupled
- **Hexagonal Architecture**: Clear ports & adapters

### 3. Easy to Use ✅

```java
// Ultra-simple for beginners
List<User> users = excelFacade.readExcel(inputStream, User.class);

// Powerful for advanced users
ProcessingResult result = excelFacade.read()
    .forClass(User.class)
    .batchSize(5000)
    .withValidation(validator)
    .withTransformation(transformer)
    .onBatch(batchProcessor)
    .read(inputStream);
```

### 4. Easy to Extend ✅

```java
// Add new read strategy
@Component
public class CustomReadStrategy<T> implements ReadStrategy<T> {
    @Override
    public ProcessingResult execute(...) {
        // Custom logic
    }
}
// Automatically picked up by Spring and used when appropriate
```

### 5. Easy to Test ✅

```java
@Test
public void testExcelReading() {
    // Mock dependencies
    ExcelReader<User> mockReader = mock(ExcelReader.class);
    when(mockReader.readAll(any(), eq(User.class)))
        .thenReturn(testUsers);

    // Test easily
    List<User> result = mockReader.readAll(inputStream, User.class);

    // Verify
    assertEquals(10, result.size());
}
```

---

## 📊 Performance Impact

| Aspect | Before | After | Impact |
|--------|--------|-------|--------|
| Speed | X rec/sec | X rec/sec | ✅ Same (no regression) |
| Memory | Y MB | Y MB | ✅ Same |
| Strategy Selection | Manual/implicit | Automatic | ✅ Better |
| Code Size | 1,452 lines | ~1,500 lines (but organized) | ✅ Better structure |

**Conclusion:** Zero performance impact, much better code organization.

---

## 🚀 Implementation Plan

### Timeline: ~2 weeks

| Week | Phase | Tasks | Status |
|------|-------|-------|--------|
| 1 | Phase 1-2 | Create interfaces, extract services | 📋 Planned |
| 1 | Phase 3 | Implement strategy pattern | 📋 Planned |
| 2 | Phase 4-5 | Create builders and facade | 📋 Planned |
| 2 | Testing | Unit + integration tests | 📋 Planned |
| 2 | Migration | Update existing code | 📋 Planned |

### Migration Strategy

1. **Keep ExcelUtil as Deprecated Facade** (Non-Breaking)
   ```java
   @Deprecated
   public class ExcelUtil {
       private static final ExcelFacade facade = new ExcelFacade();

       @Deprecated
       public static <T> List<T> processExcel(...) {
           return facade.readExcel(...);
       }
   }
   ```

2. **Gradual Migration**
   - Phase 1: New code uses new API
   - Phase 2: Migrate existing code over time
   - Phase 3: Remove ExcelUtil after 1-2 months

---

## 🎯 Recommendations

### Immediate (This Sprint)
1. ✅ Create interfaces (ExcelReader, ExcelWriter)
2. ✅ Extract ExcelReadingService
3. ✅ Extract ExcelWritingService
4. ✅ Create simple facade

### Short-term (Next Sprint)
5. ✅ Implement Strategy Pattern
6. ✅ Create fluent builders
7. ✅ Write comprehensive tests
8. ✅ Update documentation

### Long-term (Next Month)
9. ✅ Migrate all usages to new API
10. ✅ Deprecate ExcelUtil
11. ✅ Remove ExcelUtil after grace period

---

## 📚 References

- **Clean Architecture**: Robert C. Martin
- **Design Patterns**: Gang of Four
- **Hexagonal Architecture**: Alistair Cockburn
- **SOLID Principles**: Robert C. Martin

---

**Conclusion:** Current ExcelUtil is a God Object anti-pattern. Refactoring to Hexagonal Architecture with Strategy Pattern will dramatically improve code quality, testability, and usability while maintaining performance.

**Status:** 📋 **Ready for Implementation**
