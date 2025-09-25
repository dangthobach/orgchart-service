package com.learnmore.benchmark;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.utils.reflection.MethodHandleMapper;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * Simple benchmark to compare reflection vs MethodHandle performance
 */
public class SimpleBenchmark {
    
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 100000;
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Simple Benchmark...");
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println("=".repeat(50));
        
        // Prepare test data
        ExcelRowDTO sample = new ExcelRowDTO();
        sample.setRowNum(1);
        sample.setKhoVpbank("VPB001");
        sample.setMaDonVi("DV001");
        sample.setLoaiChungTu("CT001");
        
        // Test reflection approach
        long reflectionTime = benchmarkReflection(sample);
        System.out.println("Reflection approach: " + reflectionTime + " ns/operation");
        
        // Test MethodHandle approach  
        long methodHandleTime = benchmarkMethodHandle(sample);
        System.out.println("MethodHandle approach: " + methodHandleTime + " ns/operation");
        
        // Calculate improvement
        double improvement = (double) reflectionTime / methodHandleTime;
        System.out.println("Performance improvement: " + String.format("%.2fx", improvement));
        
        System.out.println("=".repeat(50));
        System.out.println("Benchmark completed successfully!");
    }
    
    private static long benchmarkReflection(ExcelRowDTO sample) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            createWithReflection();
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            ExcelRowDTO dto = createWithReflection();
            setFieldWithReflection(dto, "rowNum", i);
            setFieldWithReflection(dto, "khoVpbank", "VPB" + i);
        }
        long endTime = System.nanoTime();
        
        return (endTime - startTime) / BENCHMARK_ITERATIONS;
    }
    
    private static long benchmarkMethodHandle(ExcelRowDTO sample) throws Exception {
        // Warmup
        MethodHandleMapper<ExcelRowDTO> mapper = MethodHandleMapper.of(ExcelRowDTO.class);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            mapper.createInstance();
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            ExcelRowDTO dto = mapper.createInstance();
            mapper.setFieldValue(dto, "rowNum", i);
            mapper.setFieldValue(dto, "khoVpbank", "VPB" + i);
        }
        long endTime = System.nanoTime();
        
        return (endTime - startTime) / BENCHMARK_ITERATIONS;
    }
    
    private static ExcelRowDTO createWithReflection() throws Exception {
        return ExcelRowDTO.class.getDeclaredConstructor().newInstance();
    }
    
    private static void setFieldWithReflection(ExcelRowDTO dto, String fieldName, Object value) throws Exception {
        Field field = ExcelRowDTO.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(dto, value);
    }
}