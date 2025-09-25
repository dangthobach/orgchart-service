package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ExcelWriter system
 */
class ExcelWriterSystemTest {
    
    private List<TestPerson> testData;
    private ExcelConfig basicConfig;
    
    @BeforeEach
    void setUp() {
        testData = generateTestData(100);
        basicConfig = ExcelConfig.builder()
            .batchSize(50)
            .flushInterval(25)
            .build();
    }
    
    @Nested
    @DisplayName("ExcelWriterFactory Tests")
    class ExcelWriterFactoryTest {
        
        @Test
        @DisplayName("Should create appropriate writer for small dataset")
        void shouldCreateInMemoryWriterForSmallDataset() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.createWriter(
                TestPerson.class, 5000, basicConfig);
            
            assertNotNull(writer);
            assertEquals("InMemoryExcelWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create streaming writer for medium dataset")
        void shouldCreateStreamingWriterForMediumDataset() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.createWriter(
                TestPerson.class, 50000, basicConfig);
            
            assertNotNull(writer);
            assertEquals("StreamingSXSSFWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create optimized writer for large dataset")
        void shouldCreateOptimizedWriterForLargeDataset() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.createWriter(
                TestPerson.class, 500000, basicConfig);
            
            assertNotNull(writer);
            assertEquals("OptimizedStreamingWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create parallel writer for very large dataset with parallel config")
        void shouldCreateParallelWriterForVeryLargeDataset() throws ExcelProcessException {
            ExcelConfig parallelConfig = ExcelConfig.builder()
                .parallelProcessing(true)
                .threadPoolSize(4)
                .build();
            
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.createWriter(
                TestPerson.class, 2000000, parallelConfig);
            
            assertNotNull(writer);
            assertEquals("ParallelExcelWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create writer with explicit strategy")
        void shouldCreateWriterWithExplicitStrategy() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.createWriter(
                ExcelWriterFactory.WritingStrategy.CSV_FALLBACK,
                TestPerson.class, 
                basicConfig);
            
            assertNotNull(writer);
            assertEquals("CSVFallbackWriter", writer.getStrategyName());
        }
    }
    
    @Nested
    @DisplayName("Writer Presets Tests")
    class WriterPresetsTest {
        
        @Test
        @DisplayName("Should create fast data export writer")
        void shouldCreateFastDataExportWriter() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.WriterPresets.fastDataExport(TestPerson.class);
            
            assertNotNull(writer);
            assertEquals("OptimizedStreamingWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create formatted report writer")
        void shouldCreateFormattedReportWriter() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.WriterPresets.formattedReport(TestPerson.class);
            
            assertNotNull(writer);
            assertEquals("StreamingSXSSFWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create high performance writer")
        void shouldCreateHighPerformanceWriter() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.WriterPresets.highPerformance(TestPerson.class);
            
            assertNotNull(writer);
            assertEquals("ParallelExcelWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create low memory writer")
        void shouldCreateLowMemoryWriter() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.WriterPresets.lowMemory(TestPerson.class);
            
            assertNotNull(writer);
            assertEquals("OptimizedStreamingWriter", writer.getStrategyName());
        }
        
        @Test
        @DisplayName("Should create small file writer")
        void shouldCreateSmallFileWriter() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = ExcelWriterFactory.WriterPresets.smallFile(TestPerson.class);
            
            assertNotNull(writer);
            assertEquals("InMemoryExcelWriter", writer.getStrategyName());
        }
    }
    
    @Nested
    @DisplayName("Individual Writer Tests")
    class IndividualWriterTest {
        
        @Test
        @DisplayName("InMemoryExcelWriter should write data successfully")
        void inMemoryWriterShouldWriteSuccessfully() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = new InMemoryExcelWriter<>(TestPerson.class, basicConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(testData, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(testData.size(), result.getTotalRowsWritten());
                assertTrue(baos.size() > 0);
                assertEquals("InMemoryExcelWriter", result.getStrategy());
            } catch (Exception e) {
                fail("Writing should not throw exception", e);
            }
        }
        
        @Test
        @DisplayName("StreamingSXSSFWriter should write data successfully")
        void streamingWriterShouldWriteSuccessfully() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = new StreamingSXSSFWriter<>(TestPerson.class, basicConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(testData, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(testData.size(), result.getTotalRowsWritten());
                assertTrue(baos.size() > 0);
                assertEquals("StreamingSXSSFWriter", result.getStrategy());
            } catch (Exception e) {
                fail("Writing should not throw exception", e);
            }
        }
        
        @Test
        @DisplayName("OptimizedStreamingWriter should write data successfully")
        void optimizedWriterShouldWriteSuccessfully() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = new OptimizedStreamingWriter<>(TestPerson.class, basicConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(testData, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(testData.size(), result.getTotalRowsWritten());
                assertTrue(baos.size() > 0);
                assertEquals("OptimizedStreamingWriter", result.getStrategy());
            } catch (Exception e) {
                fail("Writing should not throw exception", e);
            }
        }
        
        @Test
        @DisplayName("CSVFallbackWriter should write data successfully")
        void csvWriterShouldWriteSuccessfully() throws ExcelProcessException {
            ExcelWriter<TestPerson> writer = new CSVFallbackWriter<>(TestPerson.class, basicConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(testData, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(testData.size(), result.getTotalRowsWritten());
                assertTrue(baos.size() > 0);
                assertEquals("CSVFallbackWriter", result.getStrategy());
                
                // Verify CSV content
                String csvContent = baos.toString();
                assertTrue(csvContent.contains("name"));
                assertTrue(csvContent.contains("Person 1"));
            } catch (Exception e) {
                fail("Writing should not throw exception", e);
            }
        }
        
        @Test
        @DisplayName("ParallelExcelWriter should write data successfully")
        void parallelWriterShouldWriteSuccessfully() throws ExcelProcessException {
            ExcelConfig parallelConfig = ExcelConfig.builder()
                .parallelProcessing(true)
                .threadPoolSize(2)
                .build();
            
            ExcelWriter<TestPerson> writer = new ParallelExcelWriter<>(TestPerson.class, parallelConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(testData, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(testData.size(), result.getTotalRowsWritten());
                assertTrue(baos.size() > 0);
                assertEquals("ParallelExcelWriter", result.getStrategy());
            } catch (Exception e) {
                fail("Writing should not throw exception", e);
            }
        }
    }
    
    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTest {
        
        @Test
        @DisplayName("Should handle empty data list")
        void shouldHandleEmptyDataList() throws ExcelProcessException {
            List<TestPerson> emptyData = new ArrayList<>();
            ExcelWriter<TestPerson> writer = new InMemoryExcelWriter<>(TestPerson.class, basicConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(emptyData, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(0, result.getTotalRowsWritten());
            } catch (Exception e) {
                fail("Writing empty data should not throw exception", e);
            }
        }
        
        @Test
        @DisplayName("Should handle null values in data")
        void shouldHandleNullValuesInData() throws ExcelProcessException {
            List<TestPerson> dataWithNulls = new ArrayList<>();
            TestPerson personWithNulls = new TestPerson();
            personWithNulls.setId(1);
            personWithNulls.setName(null);
            personWithNulls.setEmail(null);
            dataWithNulls.add(personWithNulls);
            
            ExcelWriter<TestPerson> writer = new InMemoryExcelWriter<>(TestPerson.class, basicConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(dataWithNulls, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(1, result.getTotalRowsWritten());
            } catch (Exception e) {
                fail("Writing data with nulls should not throw exception", e);
            }
        }
        
        @Test
        @DisplayName("Writers should report reasonable performance metrics")
        void writersShouldReportPerformanceMetrics() throws ExcelProcessException {
            List<TestPerson> largeData = generateTestData(1000);
            ExcelWriter<TestPerson> writer = new OptimizedStreamingWriter<>(TestPerson.class, basicConfig);
            
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                WritingResult result = writer.write(largeData, baos);
                
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(1000, result.getTotalRowsWritten());
                assertTrue(result.getProcessingTimeMs() >= 0);
                assertTrue(result.getRowsPerSecond() >= 0);
                assertTrue(result.getCellsPerSecond() >= 0);
            } catch (Exception e) {
                fail("Performance test should not throw exception", e);
            }
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTest {
        
        @Test
        @DisplayName("Should throw exception for null bean class")
        void shouldThrowExceptionForNullBeanClass() {
            assertThrows(ExcelProcessException.class, () -> {
                new InMemoryExcelWriter<>(null, basicConfig);
            });
        }
        
        @Test
        @DisplayName("Should throw exception for null config")
        void shouldThrowExceptionForNullConfig() {
            assertThrows(ExcelProcessException.class, () -> {
                new InMemoryExcelWriter<>(TestPerson.class, null);
            });
        }
        
        @Test
        @DisplayName("Should handle class with no accessible fields")
        void shouldHandleClassWithNoAccessibleFields() {
            assertThrows(ExcelProcessException.class, () -> {
                new InMemoryExcelWriter<>(String.class, basicConfig);
            });
        }
    }
    
    // Helper methods
    
    private List<TestPerson> generateTestData(int count) {
        List<TestPerson> data = new ArrayList<>();
        
        for (int i = 1; i <= count; i++) {
            TestPerson person = new TestPerson();
            person.setId(i);
            person.setName("Person " + i);
            person.setEmail("person" + i + "@test.com");
            person.setAge(20 + (i % 50));
            person.setSalary(30000.0 + (i * 500));
            person.setActive(i % 2 == 0);
            person.setCreatedAt(LocalDateTime.now().minusDays(i));
            
            data.add(person);
        }
        
        return data;
    }
    
    @Data
    public static class TestPerson {
        private Integer id;
        private String name;
        private String email;
        private Integer age;
        private Double salary;
        private Boolean active;
        private LocalDateTime createdAt;
    }
}