# ExcelConfig Migration Quick Reference

**Version:** 2.0 | **Date:** 2025-10-03

---

## ⚡ Quick Fix Guide

### Compilation Error → Quick Fix

| Error | Quick Fix |
|-------|-----------|
| `Cannot resolve method 'useStreamingParser'` | **Remove line** - Streaming always on |
| `Cannot resolve method 'enableDataTypeCache'` | **Remove line** - Caching always on |
| `Cannot resolve method 'enableReflectionCache'` | **Remove line** - Caching always on |
| `Cannot resolve method 'enableMemoryGC'` | **Remove line** - Auto GC always on |
| `Cannot resolve method 'enableRangeValidation'` | **Replace** with field-specific validation |
| `Cannot resolve method 'minValue'` | **Replace** with `NumericRangeValidator` |
| `Cannot resolve method 'maxValue'` | **Replace** with `NumericRangeValidator` |

---

## 🔄 Before → After Examples

### Example 1: Simple Config

```java
// ❌ BEFORE
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    .useStreamingParser(true)
    .enableDataTypeCache(true)
    .enableReflectionCache(true)
    .build();

// ✅ AFTER
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    // Streaming & caching always enabled
    .build();
```

### Example 2: Range Validation

```java
// ❌ BEFORE
ExcelConfig config = ExcelConfig.builder()
    .enableRangeValidation(true)
    .minValue(0.0)
    .maxValue(100.0)
    .build();

// ✅ AFTER
ExcelConfig config = ExcelConfig.builder()
    .addFieldValidation("score", new NumericRangeValidator(0.0, 100.0))
    .addFieldValidation("percentage", new NumericRangeValidator(0.0, 100.0))
    .build();
```

### Example 3: Progress Tracking (Now Works!)

```java
// ⚠️ BEFORE - Had no effect
ExcelConfig config = ExcelConfig.builder()
    .enableProgressTracking(false)  // Ignored (bug)
    .build();

// ✅ AFTER - Actually works
ExcelConfig config = ExcelConfig.builder()
    .enableProgressTracking(false)  // Now respected!
    .build();
```

### Example 4: Large File Config

```java
// ❌ BEFORE
ExcelConfig config = ExcelConfig.builder()
    .batchSize(10000)
    .memoryThreshold(1024)
    .useStreamingParser(true)
    .enableDataTypeCache(true)
    .enableReflectionCache(true)
    .enableMemoryGC(true)
    .enableProgressTracking(true)
    .progressReportInterval(50000)
    .build();

// ✅ AFTER
ExcelConfig config = ExcelConfig.builder()
    .batchSize(10000)
    .memoryThreshold(1024)
    .enableProgressTracking(true)
    .progressReportInterval(50000)
    .build();
```

---

## 🎯 Common Patterns

### Pattern 1: Minimal Config

```java
// Simplest possible config
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    .build();
// Everything else uses smart defaults
```

### Pattern 2: Production Config

```java
ExcelConfig config = ExcelConfig.builder()
    .batchSize(8000)
    .memoryThreshold(512)
    .enableProgressTracking(true)
    .enableMemoryMonitoring(true)
    .progressReportInterval(25000)
    .strictValidation(true)
    .maxErrorsBeforeAbort(100)
    .build();
```

### Pattern 3: Silent Processing (No Logs)

```java
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    .enableProgressTracking(false)  // ✅ Now works!
    .enableMemoryMonitoring(false)
    .build();
```

### Pattern 4: Field-Specific Validation

```java
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    .addFieldValidation("age", new NumericRangeValidator(0.0, 120.0))
    .addFieldValidation("salary", new NumericRangeValidator(0.0, 1000000.0))
    .addFieldValidation("email", new EmailValidator())
    .requiredFields("name", "email", "age")
    .uniqueFields("email", "employeeId")
    .build();
```

---

## 🐛 Bug Fixes

### Fixed: Progress Tracking

```java
// ⚠️ BEFORE: Always logged every 10K records (bug)
// Even if enableProgressTracking=false

// ✅ AFTER: Respects config
ExcelConfig config = ExcelConfig.builder()
    .enableProgressTracking(false)  // Actually disables logs now
    .build();
```

### Fixed: Configurable Progress Interval

```java
// ⚠️ BEFORE: Always 10,000 records (hardcoded)

// ✅ AFTER: Configurable
ExcelConfig config = ExcelConfig.builder()
    .enableProgressTracking(true)
    .progressReportInterval(5000)    // Log every 5K records
    .build();
```

---

## ❓ FAQ

### Q: Will my code break?
**A:** Only if you use removed fields. Compilation errors will show exactly what to fix.

### Q: Is performance affected?
**A:** No. Zero performance impact. Caching and streaming work exactly the same.

### Q: Do I need to migrate immediately?
**A:** No rush. Fix compilation errors when you next modify the code.

### Q: Why were fields removed?
**A:** They had zero effect. Caching and streaming are always enabled internally.

### Q: How do I disable streaming?
**A:** You can't. TrueStreamingSAXProcessor always uses SAX for efficiency.

### Q: How do I disable caching?
**A:** You can't. Caching is critical for performance and is always on.

### Q: What about range validation?
**A:** Use field-specific `ValidationRule` instead of global config:
```java
.addFieldValidation("field", new NumericRangeValidator(min, max))
```

---

## 🔍 Verification

### Check Your Config is Clean

```java
// ✅ GOOD - No removed fields
ExcelConfig config = ExcelConfig.builder()
    .batchSize(5000)
    .memoryThreshold(512)
    .enableProgressTracking(true)
    .enableMemoryMonitoring(true)
    .build();

// ❌ BAD - Uses removed fields (won't compile)
ExcelConfig config = ExcelConfig.builder()
    .useStreamingParser(true)        // ❌ Removed
    .enableDataTypeCache(true)       // ❌ Removed
    .enableReflectionCache(true)     // ❌ Removed
    .build();
```

---

## 📚 Full Documentation

- **Quick Reference:** This file
- **Analysis:** `EXCELCONFIG_REFACTOR_ANALYSIS.md`
- **Complete Guide:** `EXCELCONFIG_REFACTOR_COMPLETE.md`
- **Summary:** `REFACTORING_SUMMARY.md`

---

## 🆘 Need Help?

1. Check compilation errors - they show exactly what to fix
2. Search for the field name in this document
3. Apply the "Before → After" example
4. Remove the line or replace with field-specific validation

**Most common fix:** Just remove the line - field had no effect anyway!

---

**Version:** 2.0 | **Status:** Production Ready ✅
