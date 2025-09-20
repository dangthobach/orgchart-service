package com.learnmore.application.utils.mapper;

import com.learnmore.application.dto.User;
import com.learnmore.application.service.MockDataGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Boot Test for Mapper Performance Benchmark
 * Comparing MethodHandle vs CachedReflection approaches
 */
@SpringBootTest(classes = com.learnmore.OrgchartServiceApplication.class)
public class MapperPerformanceBenchmarkTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MapperPerformanceBenchmarkTest.class);
    
    @Autowired
    private MockDataGenerator mockDataGenerator;
    
    // Test parameters
    private static final int[] TEST_SIZES = {100, 1000, 10000, 50000};
    private static final int WARMUP_ITERATIONS = 5;
    private static final int BENCHMARK_ITERATIONS = 10;
    
    // Memory monitoring
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    @Test
    public void runComprehensiveMapperBenchmark() {
        logger.info("=== COMPREHENSIVE MAPPER PERFORMANCE BENCHMARK ===");
        logger.info("Comparing MethodHandle vs CachedReflection approaches");
        logger.info("Test sizes: {}", java.util.Arrays.toString(TEST_SIZES));
        logger.info("Warmup: {}, Benchmark: {} iterations", WARMUP_ITERATIONS, BENCHMARK_ITERATIONS);
        
        // Initialize mappers
        AdvancedExcelMapper<User> methodHandleMapper = AdvancedExcelMapper.of(User.class);
        CachedReflectionMapper<User> cachedReflectionMapper = CachedReflectionMapper.of(User.class);
        
        logger.info("Mappers initialized:");
        logger.info("  MethodHandle: {}", methodHandleMapper.getPerformanceStats());
        logger.info("  CachedReflection: {}", cachedReflectionMapper.getPerformanceStats());
        
        // Memory baseline
        recordMemoryBaseline();
        
        logger.info("\n" + "-".repeat(100));
        logger.info("PERFORMANCE COMPARISON RESULTS");
        logger.info("-".repeat(100));
        logger.info(String.format("%-10s | %-15s | %-15s | %-15s | %-12s | %-12s | %-12s", 
                   "Size", "MethodHandle(ms)", "CachedRefl(ms)", "Memory(MB)", "MH Improv%", "MH Ratio", "Winner"));
        logger.info("-".repeat(100));
        
        List<BenchmarkResult> results = new ArrayList<>();
        
        for (int testSize : TEST_SIZES) {
            BenchmarkResult result = runSizeBenchmark(testSize, methodHandleMapper, cachedReflectionMapper);
            results.add(result);
            
            // Log result row
            logger.info(String.format("%-10d | %-15.2f | %-15.2f | %-15.1f | %-11.1f%% | %-11.2fx | %-12s", 
                       result.testSize, 
                       result.methodHandleTime, 
                       result.cachedReflectionTime,
                       result.memoryUsageMB,
                       result.methodHandleImprovement,
                       result.performanceRatio,
                       result.winner));
        }
        
        // Final analysis
        printComprehensiveAnalysis(results, methodHandleMapper, cachedReflectionMapper);
    }
    
    private BenchmarkResult runSizeBenchmark(int testSize,
                                           AdvancedExcelMapper<User> methodHandleMapper,
                                           CachedReflectionMapper<User> cachedReflectionMapper) {
        
        logger.debug("Generating {} test records...", testSize);
        List<User> testData = mockDataGenerator.generateUsers(testSize);
        
        // Memory before test
        System.gc();
        long memoryBefore = getUsedMemory();
        
        // Warmup both approaches
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            benchmarkMethodHandleMapping(testData, methodHandleMapper, true);
            benchmarkCachedReflectionMapping(testData, cachedReflectionMapper, true);
        }
        
        // Clear any warmup artifacts
        System.gc();
        
        // Benchmark MethodHandle approach
        long methodHandleTime = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            methodHandleTime += benchmarkMethodHandleMapping(testData, methodHandleMapper, false);
        }
        double avgMethodHandleTime = methodHandleTime / (double) BENCHMARK_ITERATIONS / 1_000_000.0; // Convert to ms
        
        // Benchmark CachedReflection approach  
        long cachedReflectionTime = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            cachedReflectionTime += benchmarkCachedReflectionMapping(testData, cachedReflectionMapper, false);
        }
        double avgCachedReflectionTime = cachedReflectionTime / (double) BENCHMARK_ITERATIONS / 1_000_000.0; // Convert to ms
        
        // Memory after test
        System.gc();
        long memoryAfter = getUsedMemory();
        double memoryUsageMB = (memoryAfter - memoryBefore) / (1024.0 * 1024.0);
        
        // Calculate performance metrics
        double methodHandleImprovement = ((avgCachedReflectionTime - avgMethodHandleTime) / avgCachedReflectionTime) * 100;
        double performanceRatio = avgCachedReflectionTime / avgMethodHandleTime;
        String winner = avgMethodHandleTime < avgCachedReflectionTime ? "MethodHandle" : "CachedRefl";
        
        // Detailed logging for larger datasets
        if (testSize >= 10000) {
            logger.debug("Detailed analysis for {} records:", testSize);
            logger.debug("  MethodHandle: {:.2f}ms ({:.0f} records/ms)", 
                        avgMethodHandleTime, testSize / avgMethodHandleTime);
            logger.debug("  CachedReflection: {:.2f}ms ({:.0f} records/ms)", 
                        avgCachedReflectionTime, testSize / avgCachedReflectionTime);
            logger.debug("  Memory usage: {:.1f}MB", memoryUsageMB);
            logger.debug("  Performance gain: {:.1f}% faster with {}", 
                        Math.abs(methodHandleImprovement), winner);
        }
        
        return new BenchmarkResult(
            testSize, avgMethodHandleTime, avgCachedReflectionTime,
            memoryUsageMB, methodHandleImprovement, performanceRatio, winner
        );
    }
    
    /**
     * Benchmark MethodHandle-based mapping
     */
    private long benchmarkMethodHandleMapping(List<User> testData, 
                                             AdvancedExcelMapper<User> mapper, 
                                             boolean isWarmup) {
        long startTime = System.nanoTime();
        
        for (User user : testData) {
            // Test comprehensive read/write operations
            Object[] values = mapper.beanToArray(user);
            User reconstructed = mapper.arrayToBean(values);
            
            // Additional field access operations
            mapper.getFieldValue(user, "First Name");
            mapper.getFieldValue(user, "Email");
            mapper.setFieldValue(reconstructed, "Department", "Engineering");
            
            // Verify data integrity (only during actual benchmark)
            if (!isWarmup && !user.getFirstName().equals(reconstructed.getFirstName())) {
                throw new RuntimeException("MethodHandle mapping verification failed");
            }
        }
        
        return System.nanoTime() - startTime;
    }
    
    /**
     * Benchmark CachedReflection-based mapping
     */
    private long benchmarkCachedReflectionMapping(List<User> testData, 
                                                 CachedReflectionMapper<User> mapper, 
                                                 boolean isWarmup) {
        long startTime = System.nanoTime();
        
        for (User user : testData) {
            // Test comprehensive read/write operations
            Object[] values = mapper.beanToArray(user);
            User reconstructed = mapper.arrayToBean(values);
            
            // Additional field access operations
            mapper.getFieldValue(user, "First Name");
            mapper.getFieldValue(user, "Email");
            mapper.setFieldValue(reconstructed, "Department", "Engineering");
            
            // Verify data integrity (only during actual benchmark)
            if (!isWarmup && !user.getFirstName().equals(reconstructed.getFirstName())) {
                throw new RuntimeException("CachedReflection mapping verification failed");
            }
        }
        
        return System.nanoTime() - startTime;
    }
    
    /**
     * Print comprehensive analysis of benchmark results
     */
    private void printComprehensiveAnalysis(List<BenchmarkResult> results,
                                          AdvancedExcelMapper<User> methodHandleMapper,
                                          CachedReflectionMapper<User> cachedReflectionMapper) {
        
        logger.info("\n" + "=".repeat(80));
        logger.info("COMPREHENSIVE ANALYSIS");
        logger.info("=".repeat(80));
        
        // Performance statistics
        var methodHandleStats = methodHandleMapper.getPerformanceStats();
        var cachedReflectionStats = cachedReflectionMapper.getPerformanceStats();
        
        logger.info("\nFINAL MAPPER STATISTICS:");
        logger.info("  MethodHandle: {}", methodHandleStats);
        logger.info("  CachedReflection: {}", cachedReflectionStats);
        
        // Performance analysis by dataset size
        logger.info("\nPERFORMANCE ANALYSIS BY DATASET SIZE:");
        long methodHandleWins = results.stream().mapToLong(r -> "MethodHandle".equals(r.winner) ? 1 : 0).sum();
        long cachedReflectionWins = results.size() - methodHandleWins;
        
        logger.info("  MethodHandle wins: {}/{} test sizes", methodHandleWins, results.size());
        logger.info("  CachedReflection wins: {}/{} test sizes", cachedReflectionWins, results.size());
        
        // Average improvement analysis
        double avgImprovement = results.stream()
            .mapToDouble(r -> r.methodHandleImprovement)
            .average()
            .orElse(0.0);
        
        logger.info("  Average MethodHandle improvement: {:.1f}%", avgImprovement);
        
        // Memory analysis
        double avgMemoryUsage = results.stream()
            .mapToDouble(r -> r.memoryUsageMB)
            .average()
            .orElse(0.0);
        
        logger.info("  Average memory usage per test: {:.1f}MB", avgMemoryUsage);
        
        // Cache efficiency analysis
        logger.info("\nCACHE EFFICIENCY ANALYSIS:");
        logger.info("  CachedReflection cache hit ratio: {:.1f}%", 
                   cachedReflectionStats.cacheHitRatio * 100);
        logger.info("  Cache hits: {}, misses: {}", 
                   cachedReflectionStats.cacheHits, cachedReflectionStats.cacheMisses);
        
        // Field-level analysis
        logger.info("\nFIELD-LEVEL PERFORMANCE:");
        cachedReflectionStats.fieldStats.stream()
            .limit(5) // Show first 5 fields
            .forEach(stat -> logger.info("  {}", stat));
        
        // Recommendations
        logger.info("\nRECOMMENDATIONS:");
        
        if (avgImprovement > 5) {
            logger.info("✓ MethodHandle approach shows consistent {:.1f}% improvement", avgImprovement);
            logger.info("✓ Recommended for production use with medium to large datasets");
            logger.info("✓ JVM optimization benefits become significant with repeated operations");
        } else if (avgImprovement < -5) {
            logger.info("✓ CachedReflection approach shows {:.1f}% better performance", -avgImprovement);
            logger.info("✓ Consider using CachedReflection for this specific use case");
            logger.info("✓ Method caching overhead is effectively amortized");
        } else {
            logger.info("⚖ Performance difference is marginal ({:.1f}%)", Math.abs(avgImprovement));
            logger.info("⚖ Both approaches are viable - consider other factors:");
            logger.info("   - Code maintainability and readability");
            logger.info("   - JVM version compatibility");
            logger.info("   - Development team expertise");
        }
        
        // Final memory report
        printMemoryReport();
    }
    
    /**
     * Record memory baseline
     */
    private void recordMemoryBaseline() {
        System.gc();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        logger.info("Memory baseline - Used: {}MB, Max: {}MB", 
                   heapUsage.getUsed() / (1024 * 1024),
                   heapUsage.getMax() / (1024 * 1024));
    }
    
    /**
     * Get current used memory
     */
    private long getUsedMemory() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * Print final memory report
     */
    private void printMemoryReport() {
        System.gc();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        logger.info("\nFINAL MEMORY REPORT:");
        logger.info("  Heap - Used: {}MB, Committed: {}MB, Max: {}MB", 
                   heapUsage.getUsed() / (1024 * 1024),
                   heapUsage.getCommitted() / (1024 * 1024),
                   heapUsage.getMax() / (1024 * 1024));
        logger.info("  Non-Heap - Used: {}MB, Committed: {}MB", 
                   nonHeapUsage.getUsed() / (1024 * 1024),
                   nonHeapUsage.getCommitted() / (1024 * 1024));
        
        // Cache statistics
        var methodHandleCache = AdvancedExcelMapper.getCacheStats();
        var cachedReflectionCache = CachedReflectionMapper.getCacheStats();
        
        logger.info("  Mapper caches - MethodHandle: {}, CachedReflection: {}", 
                   methodHandleCache.get("cached_classes"),
                   cachedReflectionCache.get("cached_classes"));
    }
    
    /**
     * Benchmark result data structure
     */
    private static class BenchmarkResult {
        final int testSize;
        final double methodHandleTime;
        final double cachedReflectionTime;
        final double memoryUsageMB;
        final double methodHandleImprovement;
        final double performanceRatio;
        final String winner;
        
        BenchmarkResult(int testSize, double methodHandleTime, double cachedReflectionTime,
                       double memoryUsageMB, double methodHandleImprovement, 
                       double performanceRatio, String winner) {
            this.testSize = testSize;
            this.methodHandleTime = methodHandleTime;
            this.cachedReflectionTime = cachedReflectionTime;
            this.memoryUsageMB = memoryUsageMB;
            this.methodHandleImprovement = methodHandleImprovement;
            this.performanceRatio = performanceRatio;
            this.winner = winner;
        }
    }
}