package com.learnmore.benchmark;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.utils.reflection.MethodHandleMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Simple performance test to compare MethodHandle vs Reflection performance
 * Run with: mvn test -Dtest=MethodHandleBenchmark
 */
public class MethodHandleBenchmark {
    
    private ExcelRowDTO dto;
    private Field reflectionField;
    private Constructor<ExcelRowDTO> reflectionConstructor;
    private MethodHandleMapper<ExcelRowDTO> methodHandleMapper;
    
    public void setup() throws Exception {
        dto = new ExcelRowDTO();
        
        // Setup reflection components
        reflectionField = ExcelRowDTO.class.getDeclaredField("rowNum");
        reflectionField.setAccessible(true);
        reflectionConstructor = ExcelRowDTO.class.getDeclaredConstructor();
        reflectionConstructor.setAccessible(true);
        
        // Setup MethodHandle components
        methodHandleMapper = MethodHandleMapper.forClass(ExcelRowDTO.class);
    }
    
    public void testReflectionFieldSet() throws Exception {
        reflectionField.set(dto, 100);
    }
    
    public void testMethodHandleFieldSet() {
        methodHandleMapper.setFieldValue(dto, "rowNum", 100);
    }
    
    public Object testReflectionFieldGet() throws Exception {
        return reflectionField.get(dto);
    }
    
    public Object testMethodHandleFieldGet() {
        return methodHandleMapper.getFieldValue(dto, "rowNum");
    }
    
    public ExcelRowDTO testReflectionCreate() throws Exception {
        return reflectionConstructor.newInstance();
    }
    
    public ExcelRowDTO testMethodHandleCreate() {
        return methodHandleMapper.createInstance();
    }
    
    /**
     * Simulate real-world Excel processing scenario
     */
    public ExcelRowDTO testCompleteRowProcessingReflection() throws Exception {
        ExcelRowDTO row = reflectionConstructor.newInstance();
        
        // Set multiple fields like in real Excel processing
        Field maDonVi = ExcelRowDTO.class.getDeclaredField("maDonVi");
        Field maThung = ExcelRowDTO.class.getDeclaredField("maThung");
        Field rowNum = ExcelRowDTO.class.getDeclaredField("rowNum");
        Field soLuongTap = ExcelRowDTO.class.getDeclaredField("soLuongTap");
        
        maDonVi.setAccessible(true);
        maThung.setAccessible(true);
        rowNum.setAccessible(true);
        soLuongTap.setAccessible(true);
        
        maDonVi.set(row, "DV001");
        maThung.set(row, "TH001");
        rowNum.set(row, 1);
        soLuongTap.set(row, 5);
        
        return row;
    }
    
    public ExcelRowDTO testCompleteRowProcessingMethodHandle() {
        ExcelRowDTO row = methodHandleMapper.createInstance();
        
        // Set multiple fields using MethodHandle
        methodHandleMapper.setFieldValue(row, "maDonVi", "DV001");
        methodHandleMapper.setFieldValue(row, "maThung", "TH001");
        methodHandleMapper.setFieldValue(row, "rowNum", 1);
        methodHandleMapper.setFieldValue(row, "soLuongTap", 5);
        
        return row;
    }
    
    /**
     * Run simple performance comparison
     */
    public static void main(String[] args) throws Exception {
        MethodHandleBenchmark benchmark = new MethodHandleBenchmark();
        benchmark.setup();
        
        // Warmup
        for (int i = 0; i < 10000; i++) {
            benchmark.testReflectionFieldSet();
            benchmark.testMethodHandleFieldSet();
        }
        
        // Test reflection
        long startTime = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            benchmark.testReflectionFieldSet();
        }
        long reflectionTime = System.nanoTime() - startTime;
        
        // Test MethodHandle
        startTime = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            benchmark.testMethodHandleFieldSet();
        }
        long methodHandleTime = System.nanoTime() - startTime;
        
        System.out.println("=== Performance Comparison (100,000 operations) ===");
        System.out.printf("Reflection Time:    %,d ns (%.2f ms)%n", reflectionTime, reflectionTime / 1_000_000.0);
        System.out.printf("MethodHandle Time:  %,d ns (%.2f ms)%n", methodHandleTime, methodHandleTime / 1_000_000.0);
        System.out.printf("Performance Gain:   %.2fx faster%n", (double) reflectionTime / methodHandleTime);
        
        // Test complete row processing
        startTime = System.nanoTime();
        for (int i = 0; i < 50000; i++) {
            benchmark.testCompleteRowProcessingReflection();
        }
        long reflectionRowTime = System.nanoTime() - startTime;
        
        startTime = System.nanoTime();
        for (int i = 0; i < 50000; i++) {
            benchmark.testCompleteRowProcessingMethodHandle();
        }
        long methodHandleRowTime = System.nanoTime() - startTime;
        
        System.out.println("\n=== Complete Row Processing (50,000 operations) ===");
        System.out.printf("Reflection Time:    %,d ns (%.2f ms)%n", reflectionRowTime, reflectionRowTime / 1_000_000.0);
        System.out.printf("MethodHandle Time:  %,d ns (%.2f ms)%n", methodHandleRowTime, methodHandleRowTime / 1_000_000.0);
        System.out.printf("Performance Gain:   %.2fx faster%n", (double) reflectionRowTime / methodHandleRowTime);
        
        // Estimated throughput for 1M records
        double reflectionThroughput = 1_000_000.0 / (reflectionRowTime / 1_000_000_000.0);
        double methodHandleThroughput = 1_000_000.0 / (methodHandleRowTime / 1_000_000_000.0);
        
        System.out.println("\n=== Estimated Throughput (1M records) ===");
        System.out.printf("Reflection:         %,.0f records/second%n", reflectionThroughput * 20);
        System.out.printf("MethodHandle:       %,.0f records/second%n", methodHandleThroughput * 20);
    }
}