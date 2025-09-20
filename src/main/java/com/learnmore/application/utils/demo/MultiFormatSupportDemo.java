package com.learnmore.application.utils.demo;

import com.learnmore.application.utils.processor.DataProcessor;
import com.learnmore.application.utils.processor.DataProcessorFactory;
import com.learnmore.application.utils.processor.impl.CsvDataProcessor;
import com.learnmore.application.utils.processor.impl.JsonDataProcessor;
import com.learnmore.application.utils.ExcelColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demonstration of Multi-format Support Extension
 * Shows how to process Excel, CSV, JSON, and other formats using unified API
 */
public class MultiFormatSupportDemo {
    private static final Logger logger = LoggerFactory.getLogger(MultiFormatSupportDemo.class);
    
    public static void main(String[] args) {
        MultiFormatSupportDemo demo = new MultiFormatSupportDemo();
        
        try {
            System.out.println("=== Multi-format Support Demo ===\n");
            
            // Demo 1: Factory and processor discovery
            demo.demonstrateProcessorFactory();
            
            // Demo 2: Excel processing with unified API
            demo.demonstrateExcelProcessing();
            
            // Demo 3: CSV processing
            demo.demonstrateCsvProcessing();
            
            // Demo 4: JSON processing
            demo.demonstrateJsonProcessing();
            
            // Demo 5: Async multi-format processing
            demo.demonstrateAsyncMultiFormatProcessing();
            
            // Demo 6: Format auto-detection
            demo.demonstrateFormatAutoDetection();
            
        } catch (Exception e) {
            logger.error("Demo failed", e);
        }
    }
    
    /**
     * Demonstrate processor factory and discovery
     */
    public void demonstrateProcessorFactory() {
        System.out.println("1. Processor Factory & Discovery Demo");
        System.out.println("=====================================");
        
        DataProcessorFactory factory = new DataProcessorFactory();
        
        // Show supported formats
        System.out.println("Supported formats:");
        for (String format : factory.getSupportedFormats()) {
            System.out.println("- " + format);
        }
        
        // Show processor information
        System.out.println("\nProcessor mappings:");
        Map<String, String> processorInfo = factory.getProcessorInfo();
        for (Map.Entry<String, String> entry : processorInfo.entrySet()) {
            System.out.println("- " + entry.getKey() + " -> " + entry.getValue());
        }
        
        // Test format support
        System.out.println("\nFormat support tests:");
        String[] testFormats = {"xlsx", ".csv", "json", "xml", "pdf"};
        for (String format : testFormats) {
            boolean supported = factory.isFormatSupported(format);
            System.out.println("- " + format + ": " + (supported ? "✓ Supported" : "✗ Not supported"));
        }
        
        factory.shutdown();
        System.out.println();
    }
    
    /**
     * Demonstrate Excel processing with unified API
     */
    public void demonstrateExcelProcessing() {
        System.out.println("2. Excel Processing Demo");
        System.out.println("========================");
        
        DataProcessorFactory factory = new DataProcessorFactory();
        
        try {
            // Get Excel processor
            DataProcessor<SampleRecord> processor = factory.getProcessor("xlsx");
            if (processor == null) {
                System.err.println("Excel processor not available");
                return;
            }
            
            System.out.println("Using processor: " + processor.getProcessorName());
            
            // Create configuration
            var config = factory.createDefaultConfiguration(500, false, 100);
            
            // Sample data processor
            var dataProcessor = createSampleDataProcessor("Excel");
            
            // Create sample Excel data (empty for demo)
            byte[] sampleData = createSampleExcelData();
            
            System.out.println("Processing Excel data...");
            
            // Process with unified API
            var result = processor.process(
                    new ByteArrayInputStream(sampleData),
                    SampleRecord.class,
                    config,
                    dataProcessor
            );
            
            System.out.println("Excel processing result: " + result);
            
        } catch (Exception e) {
            System.err.println("Excel processing error: " + e.getMessage());
        }
        
        factory.shutdown();
        System.out.println();
    }
    
    /**
     * Demonstrate CSV processing
     */
    public void demonstrateCsvProcessing() {
        System.out.println("3. CSV Processing Demo");
        System.out.println("======================");
        
        DataProcessorFactory factory = new DataProcessorFactory();
        
        try {
            // Get CSV processor
            DataProcessor<SampleRecord> processor = factory.getProcessor("csv");
            if (processor == null) {
                System.err.println("CSV processor not available");
                return;
            }
            
            System.out.println("Using processor: " + processor.getProcessorName());
            
            // Create CSV-specific configuration
            CsvDataProcessor.CsvConfig csvConfig = new CsvDataProcessor.CsvConfig();
            csvConfig.setDelimiter(",");
            csvConfig.setHasHeader(true);
            
            var config = factory.createConfiguration(1000, false, 50, csvConfig);
            
            // Sample CSV data
            String csvData = createSampleCsvData();
            
            System.out.println("Processing CSV data:");
            System.out.println(csvData);
            
            // Process CSV
            var result = processor.process(
                    new ByteArrayInputStream(csvData.getBytes()),
                    SampleRecord.class,
                    config,
                    createSampleDataProcessor("CSV")
            );
            
            System.out.println("CSV processing result: " + result);
            
        } catch (Exception e) {
            System.err.println("CSV processing error: " + e.getMessage());
        }
        
        factory.shutdown();
        System.out.println();
    }
    
    /**
     * Demonstrate JSON processing
     */
    public void demonstrateJsonProcessing() {
        System.out.println("4. JSON Processing Demo");
        System.out.println("=======================");
        
        DataProcessorFactory factory = new DataProcessorFactory();
        
        try {
            // Get JSON processor
            DataProcessor<SampleRecord> processor = factory.getProcessor("json");
            if (processor == null) {
                System.err.println("JSON processor not available");
                return;
            }
            
            System.out.println("Using processor: " + processor.getProcessorName());
            
            // Create JSON-specific configuration
            JsonDataProcessor.JsonConfig jsonConfig = new JsonDataProcessor.JsonConfig();
            jsonConfig.setJsonLines(false);
            jsonConfig.setIgnoreUnknownProperties(true);
            
            var config = factory.createConfiguration(500, false, 25, jsonConfig);
            
            // Sample JSON data
            String jsonData = createSampleJsonData();
            
            System.out.println("Processing JSON data:");
            System.out.println(jsonData);
            
            // Process JSON
            var result = processor.process(
                    new ByteArrayInputStream(jsonData.getBytes()),
                    SampleRecord.class,
                    config,
                    createSampleDataProcessor("JSON")
            );
            
            System.out.println("JSON processing result: " + result);
            
        } catch (Exception e) {
            System.err.println("JSON processing error: " + e.getMessage());
        }
        
        factory.shutdown();
        System.out.println();
    }
    
    /**
     * Demonstrate async multi-format processing
     */
    public void demonstrateAsyncMultiFormatProcessing() {
        System.out.println("5. Async Multi-format Processing Demo");
        System.out.println("=====================================");
        
        DataProcessorFactory factory = new DataProcessorFactory();
        
        try {
            // Process multiple formats concurrently
            DataProcessor<SampleRecord> csvProcessor = factory.getProcessor("csv");
            DataProcessor<SampleRecord> jsonProcessor = factory.getProcessor("json");
            
            if (csvProcessor == null || jsonProcessor == null) {
                System.err.println("Required processors not available");
                return;
            }
            
            var csvConfig = factory.createDefaultConfiguration(1000, false, 50);
            var jsonConfig = factory.createDefaultConfiguration(500, false, 25);
            
            System.out.println("Starting async processing of CSV and JSON...");
            
            // Start async processing
            CompletableFuture<DataProcessor.ProcessingResult> csvFuture = csvProcessor.processAsync(
                    new ByteArrayInputStream(createSampleCsvData().getBytes()),
                    SampleRecord.class,
                    csvConfig,
                    createSampleDataProcessor("Async-CSV")
            );
            
            CompletableFuture<DataProcessor.ProcessingResult> jsonFuture = jsonProcessor.processAsync(
                    new ByteArrayInputStream(createSampleJsonData().getBytes()),
                    SampleRecord.class,
                    jsonConfig,
                    createSampleDataProcessor("Async-JSON")
            );
            
            // Wait for completion
            CompletableFuture.allOf(csvFuture, jsonFuture).get();
            
            System.out.println("Async processing completed:");
            System.out.println("- CSV result: " + csvFuture.get());
            System.out.println("- JSON result: " + jsonFuture.get());
            
        } catch (Exception e) {
            System.err.println("Async processing error: " + e.getMessage());
        }
        
        factory.shutdown();
        System.out.println();
    }
    
    /**
     * Demonstrate format auto-detection
     */
    public void demonstrateFormatAutoDetection() {
        System.out.println("6. Format Auto-detection Demo");
        System.out.println("=============================");
        
        DataProcessorFactory factory = new DataProcessorFactory();
        
        // Test different file names
        String[] testFiles = {
                "data.xlsx",
                "export.csv",
                "records.json",
                "file.txt",
                "unknown.pdf"
        };
        
        System.out.println("Auto-detecting formats:");
        for (String fileName : testFiles) {
            DataProcessor<SampleRecord> processor = factory.getProcessorByFileName(fileName);
            
            if (processor != null) {
                System.out.println("- " + fileName + " -> " + processor.getProcessorName() + " processor");
            } else {
                System.out.println("- " + fileName + " -> No processor available");
            }
        }
        
        factory.shutdown();
        System.out.println();
    }
    
    // Helper methods
    
    private java.util.function.Consumer<List<SampleRecord>> createSampleDataProcessor(String processorName) {
        return batch -> {
            System.out.printf("[%s] Processed batch of %d records%n", processorName, batch.size());
            
            // Log first few records for demo
            int logCount = Math.min(3, batch.size());
            for (int i = 0; i < logCount; i++) {
                System.out.printf("[%s] Record %d: %s%n", processorName, i + 1, batch.get(i));
            }
        };
    }
    
    private byte[] createSampleExcelData() {
        // Return empty data for demo (would contain actual Excel data in real scenario)
        return new byte[0];
    }
    
    private String createSampleCsvData() {
        return "name,email,age\n" +
               "John Doe,john@example.com,30\n" +
               "Jane Smith,jane@example.com,25\n" +
               "Bob Johnson,bob@example.com,35";
    }
    
    private String createSampleJsonData() {
        return "[\n" +
               "  {\"name\": \"John Doe\", \"email\": \"john@example.com\", \"age\": 30},\n" +
               "  {\"name\": \"Jane Smith\", \"email\": \"jane@example.com\", \"age\": 25},\n" +
               "  {\"name\": \"Bob Johnson\", \"email\": \"bob@example.com\", \"age\": 35}\n" +
               "]";
    }
    
    /**
     * Sample record class for demo
     */
    public static class SampleRecord {
        @ExcelColumn(name = "name")
        private String name;
        
        @ExcelColumn(name = "email")
        private String email;
        
        @ExcelColumn(name = "age")
        private Integer age;
        
        // Constructors, getters, setters
        public SampleRecord() {}
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        
        @Override
        public String toString() {
            return String.format("SampleRecord{name='%s', email='%s', age=%d}", name, email, age);
        }
    }
}