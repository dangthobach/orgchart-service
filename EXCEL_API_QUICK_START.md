# Excel API Quick Start Guide

**Version:** 2.0 (New Architecture)
**Status:** ✅ Production Ready

---

## 🚀 Quick Start (30 seconds)

### Reading Excel

```java
@Autowired
private ExcelFacade excelFacade;

// Read small file (< 100K records)
List<User> users = excelFacade.readExcel(inputStream, User.class);

// Read large file (100K+ records) with batch processing
excelFacade.readExcel(inputStream, User.class, batch -> {
    userRepository.saveAll(batch);
});
```

### Writing Excel

```java
@Autowired
private ExcelFacade excelFacade;

// Write to file (automatic strategy selection)
excelFacade.writeExcel("output.xlsx", users);

// Write to bytes (< 50K records only)
byte[] bytes = excelFacade.writeExcelToBytes(users);
```

**Done!** That's all you need for 90% of use cases.

---

## 📚 Complete Examples

### Example 1: Simple Import

```java
@Service
@RequiredArgsConstructor
public class UserImportService {

    private final ExcelFacade excelFacade;
    private final UserRepository userRepository;

    public int importUsers(InputStream inputStream) {
        List<User> users = excelFacade.readExcel(inputStream, User.class);
        userRepository.saveAll(users);
        return users.size();
    }
}
```

### Example 2: Large File Import (1M+ records)

```java
@Service
@RequiredArgsConstructor
public class LargeImportService {

    private final ExcelFacade excelFacade;
    private final UserRepository userRepository;

    public void importLargeFile(InputStream inputStream) {
        AtomicInteger count = new AtomicInteger(0);

        var result = excelFacade.readLargeFile(inputStream, User.class, batch -> {
            userRepository.saveAll(batch);
            count.addAndGet(batch.size());
            System.out.println("Processed: " + count.get());
        });

        System.out.println("Total: " + result.getProcessedRecords());
        System.out.println("Speed: " + result.getRecordsPerSecond() + " rec/sec");
    }
}
```

### Example 3: Export to Excel

```java
@Service
@RequiredArgsConstructor
public class UserExportService {

    private final ExcelFacade excelFacade;
    private final UserRepository userRepository;

    public void exportUsers(String fileName) {
        List<User> users = userRepository.findAll();
        excelFacade.writeExcel(fileName, users);
        System.out.println("Exported " + users.size() + " users");
    }
}
```

### Example 4: Custom Configuration

```java
@Service
@RequiredArgsConstructor
public class CustomImportService {

    private final ExcelFacade excelFacade;

    public void importWithValidation(InputStream inputStream) {
        ExcelConfig config = ExcelConfig.builder()
            .batchSize(10000)
            .enableProgressTracking(true)
            .strictValidation(true)
            .requiredFields("name", "email")
            .uniqueFields("email")
            .build();

        excelFacade.readExcelWithConfig(inputStream, User.class, config, batch -> {
            // Process validated batch
        });
    }
}
```

---

## 🎯 Which Method to Use?

### Reading

| File Size | Method | Example |
|-----------|--------|---------|
| < 100K records | `readExcel()` | `List<User> users = excelFacade.readExcel(inputStream, User.class);` |
| < 50K records | `readSmallFile()` | `List<User> users = excelFacade.readSmallFile(inputStream, User.class);` |
| 100K - 2M records | `readLargeFile()` | `excelFacade.readLargeFile(inputStream, User.class, batch -> {...});` |
| Custom config | `readExcelWithConfig()` | `excelFacade.readExcelWithConfig(inputStream, User.class, config, batch -> {...});` |

### Writing

| File Size | Method | Example |
|-----------|--------|---------|
| Any size | `writeExcel()` | `excelFacade.writeExcel("output.xlsx", users);` |
| < 50K records | `writeSmallFile()` | `excelFacade.writeSmallFile("output.xlsx", users);` |
| 500K - 2M records | `writeLargeFile()` | `excelFacade.writeLargeFile("output.xlsx", users);` |
| To bytes | `writeExcelToBytes()` | `byte[] bytes = excelFacade.writeExcelToBytes(users);` |
| Custom config | `writeExcelWithConfig()` | `excelFacade.writeExcelWithConfig("output.xlsx", users, config);` |

---

## 💡 Best Practices

### ✅ Do This

```java
// ✅ Use dependency injection
@Autowired
private ExcelFacade excelFacade;

// ✅ Use batch processing for large files
excelFacade.readLargeFile(inputStream, User.class, batch -> {
    repository.saveAll(batch);
});

// ✅ Let automatic strategy selection work
excelFacade.writeExcel("output.xlsx", users);

// ✅ Use try-with-resources
try (InputStream inputStream = new FileInputStream("data.xlsx")) {
    List<User> users = excelFacade.readExcel(inputStream, User.class);
}
```

### ❌ Don't Do This

```java
// ❌ Don't use static ExcelUtil directly (old API)
List<User> users = ExcelUtil.processExcel(inputStream, User.class);

// ❌ Don't read large files into memory
List<User> millionUsers = excelFacade.readExcel(inputStream, User.class); // OOM!

// ❌ Don't write large data to bytes
byte[] bytes = excelFacade.writeExcelToBytes(millionUsers); // OOM!

// ❌ Don't forget to close streams
InputStream inputStream = new FileInputStream("data.xlsx");
List<User> users = excelFacade.readExcel(inputStream, User.class);
// Stream not closed!
```

---

## 🧪 Testing

### Unit Test (with Mocks)

```java
@Test
public void testUserImport() {
    // Arrange
    ExcelReader<User> mockReader = mock(ExcelReader.class);
    List<User> testUsers = Arrays.asList(new User("John"));

    when(mockReader.readAll(any(), eq(User.class)))
        .thenReturn(testUsers);

    UserImportService service = new UserImportService(mockReader, userRepository);

    // Act
    int count = service.importUsers(inputStream);

    // Assert
    assertEquals(1, count);
    verify(mockReader).readAll(inputStream, User.class);
}
```

### Integration Test

```java
@SpringBootTest
public class ExcelIntegrationTest {

    @Autowired
    private ExcelFacade excelFacade;

    @Test
    public void testRealExcelFile() throws Exception {
        // Arrange
        InputStream inputStream = new FileInputStream("src/test/resources/test-users.xlsx");

        // Act
        List<User> users = excelFacade.readExcel(inputStream, User.class);

        // Assert
        assertNotNull(users);
        assertEquals(10, users.size());
        assertEquals("John Doe", users.get(0).getName());
    }
}
```

---

## 🔄 Migration from Old API

### Before (ExcelUtil - Old)

```java
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    .build();

List<User> users = ExcelUtil.processExcel(inputStream, User.class, config);
```

### After (ExcelFacade - New)

```java
@Autowired
private ExcelFacade excelFacade;

List<User> users = excelFacade.readExcel(inputStream, User.class);
```

**Benefits:**
- ✅ Dependency injection (testable)
- ✅ Simpler method names
- ✅ Same performance
- ✅ Easier to mock

---

## 📖 Complete Method Reference

### ExcelFacade Reading Methods

```java
// Read all records (< 100K)
List<T> readExcel(InputStream inputStream, Class<T> beanClass)

// Read with batch processing
ProcessingResult readExcel(InputStream inputStream, Class<T> beanClass, Consumer<List<T>> batchProcessor)

// Read with custom config
ProcessingResult readExcelWithConfig(InputStream inputStream, Class<T> beanClass, ExcelConfig config, Consumer<List<T>> batchProcessor)

// Convenience methods
List<T> readSmallFile(InputStream inputStream, Class<T> beanClass)
ProcessingResult readLargeFile(InputStream inputStream, Class<T> beanClass, Consumer<List<T>> batchProcessor)
```

### ExcelFacade Writing Methods

```java
// Write to file (automatic strategy)
void writeExcel(String fileName, List<T> data)

// Write to bytes (< 50K records)
byte[] writeExcelToBytes(List<T> data)

// Write with custom config
void writeExcelWithConfig(String fileName, List<T> data, ExcelConfig config)

// Convenience methods
void writeSmallFile(String fileName, List<T> data)
void writeLargeFile(String fileName, List<T> data)
```

---

## ⚡ Performance Tips

### Reading

1. **Use batch processing** for files > 100K records
2. **Enable progress tracking** for visibility: `.enableProgressTracking(true)`
3. **Enable memory monitoring** for safety: `.enableMemoryMonitoring(true)`
4. **Tune batch size**: Default 5000, increase for faster processing

### Writing

1. **Let automatic strategy work** - don't force a strategy
2. **Disable auto-sizing** for large files: `.disableAutoSizing(true)`
3. **Consider CSV** for very large files (10x+ faster)
4. **Use streaming** (automatic for > 1M cells)

---

## 🆘 Troubleshooting

### OutOfMemoryError

```java
// ❌ Problem: Reading too much into memory
List<User> users = excelFacade.readExcel(inputStream, User.class);

// ✅ Solution: Use batch processing
excelFacade.readLargeFile(inputStream, User.class, batch -> {
    repository.saveAll(batch);
});
```

### Slow Performance

```java
// ✅ Solution 1: Increase batch size
ExcelConfig config = ExcelConfig.builder()
    .batchSize(10000)  // Default 5000
    .build();

// ✅ Solution 2: Disable auto-sizing
ExcelConfig config = ExcelConfig.builder()
    .disableAutoSizing(true)  // Major speedup for large files
    .build();

// ✅ Solution 3: Use CSV for very large files
ExcelConfig config = ExcelConfig.builder()
    .preferCSVForLargeData(true)
    .build();
```

---

## 📞 Need Help?

- **Documentation:** See `PHASE1_IMPLEMENTATION_COMPLETE.md`
- **Architecture:** See `EXCELUTIL_REVIEW_AND_REFACTOR_PLAN.md`
- **Config Review:** See `TRUE_STREAMING_CONFIG_REVIEW.md`
- **Project Guide:** See `CLAUDE.md`

---

**Quick Start:** Just inject `ExcelFacade` and call `readExcel()` or `writeExcel()`. That's it!
