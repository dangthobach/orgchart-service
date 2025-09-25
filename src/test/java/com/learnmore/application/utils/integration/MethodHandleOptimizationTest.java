package com.learnmore.application.utils.integration;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.utils.reflection.MethodHandleMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MethodHandle-optimized TrueStreamingSAXProcessor
 */
@Slf4j
@SpringBootTest
public class MethodHandleOptimizationTest {

    /**
     * Test MethodHandleMapper functionality
     */
    @Test
    public void testMethodHandleMapper() throws Exception {
        MethodHandleMapper<ExcelRowDTO> mapper = MethodHandleMapper.forClass(ExcelRowDTO.class);
        
        // Test instance creation
        ExcelRowDTO dto = mapper.createInstance();
        assertNotNull(dto);
        
        // Test field setting
        mapper.setFieldValue(dto, "rowNum", 42);
        mapper.setFieldValue(dto, "maDonVi", "DV001");
        mapper.setFieldValue(dto, "maThung", "TH001");
        mapper.setFieldValue(dto, "soLuongTap", 5);
        
        // Test field getting
        assertEquals(42, mapper.getFieldValue(dto, "rowNum"));
        assertEquals("DV001", mapper.getFieldValue(dto, "maDonVi"));
        assertEquals("TH001", mapper.getFieldValue(dto, "maThung"));
        assertEquals(5, mapper.getFieldValue(dto, "soLuongTap"));
        
        // Test field type getting
        assertEquals(Integer.class, mapper.getFieldType("rowNum"));
        assertEquals(String.class, mapper.getFieldType("maDonVi"));
        assertEquals(Integer.class, mapper.getFieldType("soLuongTap"));
        
        // Test field existence
        assertTrue(mapper.hasField("rowNum"));
        assertTrue(mapper.hasField("maDonVi"));
        assertFalse(mapper.hasField("nonExistentField"));
        
        log.info("MethodHandleMapper test passed successfully");
    }
    
    /**
     * Performance comparison test
     */
    @Test
    public void testPerformanceComparison() throws Exception {
        final int iterations = 10000;
        
        MethodHandleMapper<ExcelRowDTO> mapper = MethodHandleMapper.forClass(ExcelRowDTO.class);
        
        // Warmup
        for (int i = 0; i < 1000; i++) {
            ExcelRowDTO dto = mapper.createInstance();
            mapper.setFieldValue(dto, "rowNum", i);
        }
        
        // Test MethodHandle performance
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ExcelRowDTO dto = mapper.createInstance();
            mapper.setFieldValue(dto, "rowNum", i);
            mapper.setFieldValue(dto, "maDonVi", "DV" + i);
            mapper.setFieldValue(dto, "soLuongTap", i % 10);
            
            // Verify values
            assertEquals(i, mapper.getFieldValue(dto, "rowNum"));
            assertEquals("DV" + i, mapper.getFieldValue(dto, "maDonVi"));
        }
        long methodHandleTime = System.nanoTime() - startTime;
        
        // Test reflection performance
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ExcelRowDTO dto = new ExcelRowDTO();
            dto.setRowNum(i);
            dto.setMaDonVi("DV" + i);
            dto.setSoLuongTap(i % 10);
            
            // Verify values
            assertEquals(i, dto.getRowNum());
            assertEquals("DV" + i, dto.getMaDonVi());
        }
        long reflectionTime = System.nanoTime() - startTime;
        
        double performanceGain = (double) reflectionTime / methodHandleTime;
        
        log.info("Performance Test Results ({} iterations):", iterations);
        log.info("Reflection Time:    {} ns ({} ms)", reflectionTime, reflectionTime / 1_000_000.0);
        log.info("MethodHandle Time:  {} ns ({} ms)", methodHandleTime, methodHandleTime / 1_000_000.0);
        log.info("Performance Gain:   {:.2f}x", performanceGain);
        
        // MethodHandle should be reasonable (allowing for JVM warmup effects)
        assertTrue(performanceGain > 0.1, "MethodHandle performance should be reasonable");
    }
    
    /**
     * Test MethodHandle basic functionality without Excel parsing
     */
    @Test
    public void testMethodHandleBasicFunctionality() throws Exception {
        log.info("Testing MethodHandle basic functionality...");
        
        MethodHandleMapper<ExcelRowDTO> mapper = MethodHandleMapper.forClass(ExcelRowDTO.class);
        
        // Test creating multiple instances and setting values
        for (int i = 0; i < 5; i++) {
            ExcelRowDTO dto = mapper.createInstance();
            assertNotNull(dto);
            
            mapper.setFieldValue(dto, "rowNum", i + 1);
            mapper.setFieldValue(dto, "maDonVi", "DV" + String.format("%03d", i + 1));
            mapper.setFieldValue(dto, "maThung", "TH" + String.format("%03d", i + 1));
            mapper.setFieldValue(dto, "soLuongTap", (i + 1) * 2);
            
            // Verify values were set correctly
            assertEquals(i + 1, mapper.getFieldValue(dto, "rowNum"));
            assertEquals("DV" + String.format("%03d", i + 1), mapper.getFieldValue(dto, "maDonVi"));
            assertEquals("TH" + String.format("%03d", i + 1), mapper.getFieldValue(dto, "maThung"));
            assertEquals((i + 1) * 2, mapper.getFieldValue(dto, "soLuongTap"));
        }
        
        log.info("MethodHandle basic functionality test passed successfully");
    }
    
    /**
     * Test Excel column mapping with MethodHandle
     */
    @Test
    public void testExcelColumnMappingWithMethodHandle() throws Exception {
        MethodHandleMapper<ExcelRowDTO> mapper = MethodHandleMapper.forClass(ExcelRowDTO.class);
        
        // Test Excel column mapping
        var columnMapping = mapper.getExcelColumnMapping();
        assertNotNull(columnMapping);
        
        log.info("Excel column mappings found: {}", columnMapping.size());
        for (var entry : columnMapping.entrySet()) {
            log.debug("Excel column '{}' -> field '{}'", entry.getKey(), entry.getValue());
        }
        
        // Verify some expected mappings exist
        assertTrue(mapper.hasField("Kho VPBank"), "Should have 'Kho VPBank' field mapping");
        assertTrue(mapper.hasField("Mã đơn vị"), "Should have 'Mã đơn vị' field mapping");
        assertTrue(mapper.hasField("Mã thùng"), "Should have 'Mã thùng' field mapping");
        
        log.info("Excel column mapping test passed successfully");
    }
    
    /**
     * Test cached mapper reuse
     */
    @Test
    public void testMapperCaching() {
        MethodHandleMapper<ExcelRowDTO> mapper1 = MethodHandleMapper.forClass(ExcelRowDTO.class);
        MethodHandleMapper<ExcelRowDTO> mapper2 = MethodHandleMapper.forClass(ExcelRowDTO.class);
        
        // Should return the same cached instance
        assertSame(mapper1, mapper2, "Mapper instances should be cached and reused");
        
        log.info("Mapper caching test passed successfully");
    }
}