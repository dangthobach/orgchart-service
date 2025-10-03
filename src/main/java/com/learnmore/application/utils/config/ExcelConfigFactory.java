package com.learnmore.application.utils.config;

import com.learnmore.application.utils.validation.ValidationRule;
import lombok.extern.slf4j.Slf4j;

/**
 * Production-ready Excel configuration factory
 * Provides optimized configurations for different use cases and environments
 */
@Slf4j
public class ExcelConfigFactory {
    
    /**
     * Configuration for small files (< 50K records)
     * Optimized for speed with relaxed memory constraints
     * NOTE: Caching always enabled, streaming always used in TrueStreamingSAXProcessor
     */
    public static ExcelConfig createSmallFileConfig() {
        return ExcelConfig.builder()
                .batchSize(2000)
                .memoryThreshold(256)
                .maxErrorsBeforeAbort(100)
                .enableProgressTracking(false)  // Disable for cleaner logs on small files
                .enableMemoryMonitoring(false)  // Not needed for small files
                .build();
    }
    
    /**
     * Configuration for medium files (50K - 500K records)
     * Balanced performance and memory usage
     */
    public static ExcelConfig createMediumFileConfig() {
        return ExcelConfig.builder()
                .batchSize(5000)
                .memoryThreshold(512)
                .maxErrorsBeforeAbort(250)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .progressReportInterval(10000)
                .build();
    }

    /**
     * Configuration for large files (500K - 2M records)
     * Optimized for memory efficiency and stability
     */
    public static ExcelConfig createLargeFileConfig() {
        return ExcelConfig.builder()
                .batchSize(10000)
                .memoryThreshold(1024)
                .maxErrorsBeforeAbort(500)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .progressReportInterval(50000)
                .build();
    }

    /**
     * Configuration for production environment
     * Conservative settings with comprehensive monitoring
     */
    public static ExcelConfig createProductionConfig() {
        return ExcelConfig.builder()
                .batchSize(8000)
                .memoryThreshold(512)
                .maxErrorsBeforeAbort(100)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .progressReportInterval(25000)
                .strictValidation(true)
                .build();
    }

    /**
     * Configuration for migration/ETL jobs
     * Optimized for throughput with error tolerance
     */
    public static ExcelConfig createMigrationConfig() {
        return ExcelConfig.builder()
                .batchSize(15000)
                .memoryThreshold(2048)
                .maxErrorsBeforeAbort(1000)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .progressReportInterval(100000)
                .strictValidation(false)
                .failOnFirstError(false)
                .build();
    }

    /**
     * Configuration for development/testing
     * Verbose logging and lenient settings
     */
    public static ExcelConfig createDevelopmentConfig() {
        return ExcelConfig.builder()
                .batchSize(1000)
                .memoryThreshold(128)
                .maxErrorsBeforeAbort(50)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .progressReportInterval(1000)
                .strictValidation(true)
                .failOnFirstError(true)
                .build();
    }
    
    /**
     * Dynamic configuration based on estimated file size
     */
    public static ExcelConfig createConfigForFileSize(long estimatedRecords) {
        log.info("Creating dynamic configuration for estimated {} records", estimatedRecords);
        
        if (estimatedRecords < 50_000) {
            log.info("Using SMALL file configuration");
            return createSmallFileConfig();
        } else if (estimatedRecords < 500_000) {
            log.info("Using MEDIUM file configuration");
            return createMediumFileConfig();
        } else if (estimatedRecords < 2_000_000) {
            log.info("Using LARGE file configuration");
            return createLargeFileConfig();
        } else {
            log.info("Using MIGRATION configuration for very large file");
            return createMigrationConfig();
        }
    }
    
    /**
     * Add common validation rules using builder methods
     */
    public static ExcelConfig.Builder addCommonValidationRules(ExcelConfig.Builder builder) {
        // Add common required fields
        builder.requiredFields("id", "name");
        
        // Add common unique fields  
        builder.uniqueFields("id", "email");
        
        // Add email validation
        builder.addFieldValidation("email", new ValidationRule() {
            @Override
            public ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber) {
                if (value == null) return ValidationResult.success();
                String email = value.toString().trim();
                boolean isValid = email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
                return isValid ? ValidationResult.success() : 
                       ValidationResult.failure("Invalid email format: " + email, fieldName, rowNumber, columnNumber, 
                                               email, "EmailValidation");
            }
            
            @Override
            public String getRuleName() { return "EmailValidation"; }
            
            @Override
            public String getDescription() { return "Validates email format"; }
        });
        
        // Add phone validation
        builder.addFieldValidation("phone", new ValidationRule() {
            @Override
            public ValidationResult validate(String fieldName, Object value, int rowNumber, int columnNumber) {
                if (value == null) return ValidationResult.success();
                String phone = value.toString().replaceAll("[^0-9]", "");
                boolean isValid = phone.length() >= 10 && phone.length() <= 15;
                return isValid ? ValidationResult.success() : 
                       ValidationResult.failure("Invalid phone number: " + value, fieldName, rowNumber, columnNumber, 
                                               value.toString(), "PhoneValidation");
            }
            
            @Override
            public String getRuleName() { return "PhoneValidation"; }
            
            @Override
            public String getDescription() { return "Validates phone number format"; }
        });
        
        return builder;
    }
}