package com.learnmore.application.utils.mapper;

import com.learnmore.application.dto.User;
import com.learnmore.application.service.MockDataGenerator;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;

import java.util.List;

/**
 * Standalone Performance Benchmark comparing MethodHandle vs CachedReflection mappers
 * Tests performance, timing, and memory management without Spring Boot context
 * 
 * @author AI Assistant
 * @version 1.0
 */
public class StandaloneMapperPerformanceBenchmark {
    
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    public static void main(String[] args) {
        System.out.println("🚀 STARTING COMPREHENSIVE MAPPER PERFORMANCE BENCHMARK");
        System.out.println("=" + "=".repeat(70));
        
        // Warm up JVM
        System.out.println("⏳ Warming up JVM...");
        warmupJVM();
        
        // Test different dataset sizes
        int[] testSizes = {1000, 5000, 10000, 50000};
        
        for (int size : testSizes) {
            System.out.println("\n📊 TESTING WITH " + size + " RECORDS");
            System.out.println("-".repeat(50));
            
            runBenchmarkForSize(size);
            
            // Force garbage collection between tests
            System.gc();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("\n✅ BENCHMARK COMPLETED");
        System.out.println("=" + "=".repeat(70));
    }
    
    private static void warmupJVM() {
        MockDataGenerator mockDataGenerator = new MockDataGenerator();
        List<User> warmupData = mockDataGenerator.generateUsers(100);
        
        AdvancedExcelMapper<User> methodHandleMapper = AdvancedExcelMapper.of(User.class);
        CachedReflectionMapper<User> cachedReflectionMapper = CachedReflectionMapper.of(User.class);
        
        // Warmup both mappers
        for (int i = 0; i < 50; i++) {
            methodHandleMapper.beanToArray(warmupData.get(i % warmupData.size()));
            cachedReflectionMapper.beanToArray(warmupData.get(i % warmupData.size()));
        }
        
        System.gc();
    }
    
    private static void runBenchmarkForSize(int size) {
        // Generate test data
        MockDataGenerator mockDataGenerator = new MockDataGenerator();
        List<User> testData = mockDataGenerator.generateUsers(size);
        
        System.out.println("✅ Generated " + size + " test records");
        
        // Test MethodHandle approach
        BenchmarkResult methodHandleResult = benchmarkMethodHandles(testData);
        
        // Force garbage collection
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Test CachedReflection approach
        BenchmarkResult cachedReflectionResult = benchmarkCachedReflection(testData);
        
        // Compare results
        compareResults(methodHandleResult, cachedReflectionResult, size);
    }
    
    private static BenchmarkResult benchmarkMethodHandles(List<User> testData) {
        System.out.println("\n🔬 Testing MethodHandle Mapper");
        
        AdvancedExcelMapper<User> mapper = AdvancedExcelMapper.of(User.class);
        
        // Record initial memory state
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        MemoryUsage beforeNonHeap = memoryBean.getNonHeapMemoryUsage();
        
        long startTime = System.nanoTime();
        long totalMappingTime = 0;
        List<Object[]> results = new ArrayList<>();
        
        // Perform mapping with individual timing
        for (User user : testData) {
            long mappingStart = System.nanoTime();
            
            Object[] row = mapper.beanToArray(user);
            
            long mappingEnd = System.nanoTime();
            totalMappingTime += (mappingEnd - mappingStart);
            
            results.add(row);
        }
        
        long endTime = System.nanoTime();
        
        // Record final memory state
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        MemoryUsage afterNonHeap = memoryBean.getNonHeapMemoryUsage();
        
        // Calculate metrics
        long totalTime = endTime - startTime;
        double throughput = (double) testData.size() / (totalTime / 1_000_000_000.0);
        double avgMappingTime = (double) totalMappingTime / testData.size() / 1_000_000.0; // Convert to milliseconds
        
        long heapMemoryUsed = afterHeap.getUsed() - beforeHeap.getUsed();
        long nonHeapMemoryUsed = afterNonHeap.getUsed() - beforeNonHeap.getUsed();
        
        // Get performance stats
        AdvancedExcelMapper.PerformanceStats stats = mapper.getPerformanceStats();
        String performanceStats = String.format(
            "MethodHandle Stats: records=%d, avgTime=%.2fns, usesMethodHandles=%s",
            stats.totalMappedRecords, stats.avgMappingTimeNs, stats.usesMethodHandles
        );
        
        BenchmarkResult result = new BenchmarkResult(
            "MethodHandle",
            testData.size(),
            totalTime,
            totalMappingTime,
            throughput,
            avgMappingTime,
            heapMemoryUsed,
            nonHeapMemoryUsed,
            performanceStats
        );
        
        printBenchmarkResult(result);
        return result;
    }
    
    private static BenchmarkResult benchmarkCachedReflection(List<User> testData) {
        System.out.println("\n🔬 Testing CachedReflection Mapper");
        
        CachedReflectionMapper<User> mapper = CachedReflectionMapper.of(User.class);
        
        // Record initial memory state
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        MemoryUsage beforeNonHeap = memoryBean.getNonHeapMemoryUsage();
        
        long startTime = System.nanoTime();
        long totalMappingTime = 0;
        List<Object[]> results = new ArrayList<>();
        
        // Perform mapping with individual timing
        for (User user : testData) {
            long mappingStart = System.nanoTime();
            
            Object[] row = mapper.beanToArray(user);
            
            long mappingEnd = System.nanoTime();
            totalMappingTime += (mappingEnd - mappingStart);
            
            results.add(row);
        }
        
        long endTime = System.nanoTime();
        
        // Record final memory state
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        MemoryUsage afterNonHeap = memoryBean.getNonHeapMemoryUsage();
        
        // Calculate metrics
        long totalTime = endTime - startTime;
        double throughput = (double) testData.size() / (totalTime / 1_000_000_000.0);
        double avgMappingTime = (double) totalMappingTime / testData.size() / 1_000_000.0; // Convert to milliseconds
        
        long heapMemoryUsed = afterHeap.getUsed() - beforeHeap.getUsed();
        long nonHeapMemoryUsed = afterNonHeap.getUsed() - beforeNonHeap.getUsed();
        
        // Get performance stats
        CachedReflectionMapper.PerformanceStats stats = mapper.getPerformanceStats();
        String performanceStats = String.format(
            "CachedReflection Stats: records=%d, avgTime=%.2fns, cacheHits=%d, cacheMisses=%d",
            stats.totalMappedRecords, stats.avgMappingTimeNs, stats.cacheHits, stats.cacheMisses
        );
        
        BenchmarkResult result = new BenchmarkResult(
            "CachedReflection",
            testData.size(),
            totalTime,
            totalMappingTime,
            throughput,
            avgMappingTime,
            heapMemoryUsed,
            nonHeapMemoryUsed,
            performanceStats
        );
        
        printBenchmarkResult(result);
        return result;
    }
    

    
    private static void printBenchmarkResult(BenchmarkResult result) {
        System.out.println("📈 Results for " + result.mapperName + ":");
        System.out.println("   • Total Time: " + formatDuration(result.totalTime));
        System.out.println("   • Avg Mapping Time: " + String.format("%.4f ms", result.avgMappingTime));
        System.out.println("   • Throughput: " + String.format("%.0f records/sec", result.throughput));
        System.out.println("   • Heap Memory Used: " + formatBytes(result.heapMemoryUsed));
        System.out.println("   • Non-Heap Memory Used: " + formatBytes(result.nonHeapMemoryUsed));
        System.out.println("   • Performance Stats: " + result.performanceStats);
    }
    
    private static void compareResults(BenchmarkResult methodHandle, BenchmarkResult cachedReflection, int dataSize) {
        System.out.println("\n🔍 COMPARISON ANALYSIS");
        System.out.println("-".repeat(30));
        
        // Performance comparison
        double speedRatio = (double) cachedReflection.totalTime / methodHandle.totalTime;
        double throughputRatio = methodHandle.throughput / cachedReflection.throughput;
        long memoryDifference = methodHandle.heapMemoryUsed - cachedReflection.heapMemoryUsed;
        
        System.out.println("📊 Performance Metrics:");
        if (speedRatio > 1.05) {
            System.out.println("   ✅ MethodHandle is " + String.format("%.1f%%", (speedRatio - 1) * 100) + " FASTER");
        } else if (speedRatio < 0.95) {
            System.out.println("   ❌ MethodHandle is " + String.format("%.1f%%", (1 - speedRatio) * 100) + " SLOWER");
        } else {
            System.out.println("   ⚖️ Performance is SIMILAR (within 5%)");
        }
        
        System.out.println("   • Throughput Ratio (MH/CR): " + String.format("%.2fx", throughputRatio));
        System.out.println("   • Time Difference: " + formatDuration(methodHandle.totalTime - cachedReflection.totalTime));
        
        System.out.println("\n💾 Memory Usage:");
        if (memoryDifference > 0) {
            System.out.println("   • MethodHandle uses " + formatBytes(memoryDifference) + " MORE memory");
        } else if (memoryDifference < 0) {
            System.out.println("   • MethodHandle uses " + formatBytes(-memoryDifference) + " LESS memory");
        } else {
            System.out.println("   • Memory usage is SIMILAR");
        }
        
        // Recommendations
        System.out.println("\n💡 RECOMMENDATIONS for " + dataSize + " records:");
        if (dataSize < 5000) {
            if (speedRatio > 1.1) {
                System.out.println("   ✅ Use MethodHandle for small datasets - significant performance gain");
            } else {
                System.out.println("   ⚖️ Either approach works well for small datasets");
            }
        } else if (dataSize < 20000) {
            if (speedRatio > 1.05) {
                System.out.println("   ✅ MethodHandle recommended for medium datasets");
            } else {
                System.out.println("   🔄 CachedReflection may be more stable for medium datasets");
            }
        } else {
            if (memoryDifference < 0 && speedRatio > 1.0) {
                System.out.println("   ✅ MethodHandle excellent for large datasets - better performance and memory");
            } else {
                System.out.println("   🔄 Consider CachedReflection for large datasets if memory is a concern");
            }
        }
    }
    
    private static String formatDuration(long nanos) {
        if (nanos < 1_000_000) {
            return String.format("%.2f μs", nanos / 1000.0);
        } else if (nanos < 1_000_000_000) {
            return String.format("%.2f ms", nanos / 1_000_000.0);
        } else {
            return String.format("%.2f s", nanos / 1_000_000_000.0);
        }
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    private static class BenchmarkResult {
        final String mapperName;
        final int recordCount;
        final long totalTime;
        final long totalMappingTime;
        final double throughput;
        final double avgMappingTime;
        final long heapMemoryUsed;
        final long nonHeapMemoryUsed;
        final String performanceStats;
        
        BenchmarkResult(String mapperName, int recordCount, long totalTime, long totalMappingTime,
                       double throughput, double avgMappingTime, long heapMemoryUsed, 
                       long nonHeapMemoryUsed, String performanceStats) {
            this.mapperName = mapperName;
            this.recordCount = recordCount;
            this.totalTime = totalTime;
            this.totalMappingTime = totalMappingTime;
            this.throughput = throughput;
            this.avgMappingTime = avgMappingTime;
            this.heapMemoryUsed = heapMemoryUsed;
            this.nonHeapMemoryUsed = nonHeapMemoryUsed;
            this.performanceStats = performanceStats;
        }
    }
}