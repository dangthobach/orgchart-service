package com.learnmore.application.utils.factory;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.config.ExcelConfigValidator;
import com.learnmore.application.utils.factory.ExcelFactory.ExcelProcessor;
import com.learnmore.application.utils.factory.ExcelFactory.ProcessingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ExcelFactory functionality
 */
public class ExcelFactoryTest {
    
    @Test
    public void testConfigProfiles() {
        // Test development profile
        ExcelConfig devConfig = ExcelFactory.Profiles.development();
        assertTrue(devConfig.isStrictValidation());
        assertTrue(devConfig.isFailOnFirstError());
        assertEquals(100, devConfig.getBatchSize());
        assertEquals(10, devConfig.getMaxErrorsBeforeAbort());
        
        // Test staging profile  
        ExcelConfig stagingConfig = ExcelFactory.Profiles.staging();
        assertTrue(stagingConfig.isStrictValidation());
        assertFalse(stagingConfig.isFailOnFirstError());
        assertEquals(5000, stagingConfig.getBatchSize());
        assertEquals(100, stagingConfig.getMaxErrorsBeforeAbort());
        
        // Test production profile  
        ExcelConfig prodConfig = ExcelFactory.Profiles.production();
        assertFalse(prodConfig.isStrictValidation());
        assertFalse(prodConfig.isFailOnFirstError());
        assertEquals(10000, prodConfig.getBatchSize());
        assertEquals(500, prodConfig.getMaxErrorsBeforeAbort());
        // REMOVED assertions: isUseStreamingParser, isEnableReflectionCache, isEnableDataTypeCache
        // These fields removed - caching always enabled, streaming always used
        
        // Test batch profile
        ExcelConfig batchConfig = ExcelFactory.Profiles.batch();
        assertEquals(50000, batchConfig.getBatchSize());
        assertEquals(2000, batchConfig.getMemoryThresholdMB());
        assertTrue(batchConfig.isParallelProcessing());
        assertTrue(batchConfig.isForceStreamingMode());
    }
    
    @Test
    public void testConfigValidation() {
        // Valid config
        ExcelConfig validConfig = ExcelConfig.builder()
            .batchSize(1000)
            .memoryThreshold(500)
            .threadPoolSize(4)
            .progressReportInterval(1000)
            .maxErrorsBeforeAbort(100)
            .cellCountThresholdForSXSSF(1000000)
            .maxCellsForXSSF(500000)
            .csvThreshold(2000000)
            .sxssfRowAccessWindowSize(1000)
            .build();
        
        ExcelConfigValidator.ValidationResult result = 
            ExcelConfigValidator.validate(validConfig);
        assertTrue(result.isValid(), "Valid config should pass validation");
        assertTrue(result.getErrors().isEmpty(), "Should have no errors");
        
        // Invalid config
        ExcelConfig invalidConfig = ExcelConfig.builder()
            .batchSize(-1)  // Invalid
            .memoryThreshold(0)  // Invalid
            .threadPoolSize(-1) // Invalid when parallel processing enabled
            .progressReportInterval(-1) // Invalid when progress tracking enabled
            .maxErrorsBeforeAbort(-1) // Invalid
            .cellCountThresholdForSXSSF(-1) // Invalid
            .maxCellsForXSSF(-1) // Invalid
            .csvThreshold(-1) // Invalid
            .parallelProcessing(true) // Makes threadPoolSize validation fail
            .enableProgressTracking(true) // Makes progressReportInterval validation fail
            .build();
        
        result = ExcelConfigValidator.validate(invalidConfig);
        assertFalse(result.isValid(), "Invalid config should fail validation");
        assertFalse(result.getErrors().isEmpty(), "Should have errors");
        
        // Print errors for debugging
        result.getErrors().forEach(System.out::println);
    }
    
    @Test
    public void testConfigValidationWarnings() {
        // Config with warnings but no errors
        ExcelConfig warningConfig = ExcelConfig.builder()
            .batchSize(150000) // Warning: too large
            .memoryThreshold(999999) // Warning: exceeds heap
            .threadPoolSize(100) // Warning: too many threads
            .sxssfRowAccessWindowSize(50) // Warning: too small
            .forceStreamingMode(true)
            .disableAutoSizing(true)
            .autoSizeColumns(true) // Warning: conflict
            .build();
        
        ExcelConfigValidator.ValidationResult result = 
            ExcelConfigValidator.validate(warningConfig);
        assertTrue(result.isValid(), "Config with only warnings should be valid");
        assertFalse(result.getWarnings().isEmpty(), "Should have warnings");
        
        // Print warnings
        result.getWarnings().forEach(warning -> 
            System.out.println("Warning: " + warning));
    }
    
    @Test
    public void testProcessorPresets() {
        // Test small file preset
        ExcelProcessor<ExcelRowDTO> smallProcessor = 
            ExcelFactory.Presets.smallFile(ExcelRowDTO.class);
        assertNotNull(smallProcessor);
        assertEquals("IN_MEMORY", smallProcessor.getStatistics().getStrategy());
        
        // Test medium file preset
        ExcelProcessor<ExcelRowDTO> mediumProcessor = 
            ExcelFactory.Presets.mediumFile(ExcelRowDTO.class);
        assertNotNull(mediumProcessor);
        assertEquals("XSSF", mediumProcessor.getStatistics().getStrategy());
        
        // Test large file preset
        ExcelProcessor<ExcelRowDTO> largeProcessor = 
            ExcelFactory.Presets.largeFile(ExcelRowDTO.class);
        assertNotNull(largeProcessor);
        assertEquals("SXSSF", largeProcessor.getStatistics().getStrategy());
        
        // Test extra large file preset
        ExcelProcessor<ExcelRowDTO> extraLargeProcessor = 
            ExcelFactory.Presets.extraLargeFile(ExcelRowDTO.class);
        assertNotNull(extraLargeProcessor);
        assertEquals("SAX_STREAMING", extraLargeProcessor.getStatistics().getStrategy());
        
        // Test low memory preset
        ExcelProcessor<ExcelRowDTO> lowMemoryProcessor = 
            ExcelFactory.Presets.lowMemory(ExcelRowDTO.class);
        assertNotNull(lowMemoryProcessor);
        assertEquals("SAX_STREAMING", lowMemoryProcessor.getStatistics().getStrategy());
        
        // Test high performance preset
        ExcelProcessor<ExcelRowDTO> perfProcessor = 
            ExcelFactory.Presets.highPerformance(ExcelRowDTO.class);
        assertNotNull(perfProcessor);
        assertEquals("PARALLEL_SAX", perfProcessor.getStatistics().getStrategy());
    }
    
    @Test
    public void testStrategySelection() {
        // Test all strategies have proper description
        for (ProcessingStrategy strategy : ProcessingStrategy.values()) {
            assertNotNull(strategy.toString(), "Strategy name should not be null");
            assertNotNull(strategy.getDescription(), "Strategy description should not be null");
            assertFalse(strategy.getDescription().isEmpty(), "Strategy description should not be empty");
            
            System.out.println(strategy + ": " + strategy.getDescription());
        }
    }
    
    @Test
    public void testExplicitStrategyCreation() {
        // Test creating processor with explicit strategy
        ExcelConfig config = ExcelConfig.builder()
            .batchSize(1000)
            .build();
        
        ExcelProcessor<ExcelRowDTO> processor = 
            ExcelFactory.createProcessor(ProcessingStrategy.SAX_STREAMING, ExcelRowDTO.class, config);
        
        assertNotNull(processor);
        assertEquals("SAX_STREAMING", processor.getStatistics().getStrategy());
    }
    
    @Test
    public void testImmutableConfigCreation() {
        // Test making immutable copy
        ExcelConfig original = ExcelConfig.builder()
            .batchSize(1000)
            .memoryThreshold(500)
            .jobId("test-job")
            .build();
        
        ExcelConfig immutable = ExcelConfigValidator.makeImmutable(original);
        assertNotNull(immutable);
        assertEquals(original.getBatchSize(), immutable.getBatchSize());
        assertEquals(original.getMemoryThresholdMB(), immutable.getMemoryThresholdMB());
        assertEquals(original.getJobId(), immutable.getJobId());
    }
    
    @Test
    public void testRecommendedConfigGeneration() {
        // Test recommended config for different file sizes and environments
        
        // Small file, development
        ExcelConfig smallDev = ExcelConfigValidator.getRecommendedConfig(5000, "development");
        assertEquals(1000, smallDev.getBatchSize());
        assertTrue(smallDev.isStrictValidation());
        assertTrue(smallDev.isFailOnFirstError());
        
        // Medium file, staging
        ExcelConfig mediumStaging = ExcelConfigValidator.getRecommendedConfig(50000, "staging");
        assertEquals(5000, mediumStaging.getBatchSize());
        assertTrue(mediumStaging.isStrictValidation());
        assertFalse(mediumStaging.isFailOnFirstError());
        
        // Large file, production
        ExcelConfig largeProd = ExcelConfigValidator.getRecommendedConfig(500000, "production");
        assertEquals(10000, largeProd.getBatchSize());
        assertFalse(largeProd.isStrictValidation());
        // REMOVED: isUseStreamingParser, isEnableReflectionCache - always enabled
        
        // Extra large file, production
        ExcelConfig extraLargeProd = ExcelConfigValidator.getRecommendedConfig(2000000, "production");
        assertEquals(50000, extraLargeProd.getBatchSize());
        assertTrue(extraLargeProd.isForceStreamingMode());
        assertTrue(extraLargeProd.isMinimizeMemoryFootprint());
    }
    
    @Test
    public void testProcessingStatistics() {
        // Test ProcessingStatistics functionality
        ExcelFactory.ProcessingStatistics stats = new ExcelFactory.ProcessingStatistics();
        
        stats.setStrategy("TEST");
        stats.setProcessedRows(1000);
        stats.setErrorRows(10);
        stats.setProcessingTimeMs(5000);
        stats.setRecordsPerSecond(200.0);
        
        assertEquals("TEST", stats.getStrategy());
        assertEquals(1000, stats.getProcessedRows());
        assertEquals(10, stats.getErrorRows());
        assertEquals(5000, stats.getProcessingTimeMs());
        assertEquals(200.0, stats.getRecordsPerSecond(), 0.01);
        
        // Test toString
        String statsString = stats.toString();
        assertNotNull(statsString);
        assertTrue(statsString.contains("TEST"));
        assertTrue(statsString.contains("1000"));
        
        System.out.println("Statistics: " + statsString);
    }
}