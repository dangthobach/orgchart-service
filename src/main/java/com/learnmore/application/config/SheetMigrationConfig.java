package com.learnmore.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for multi-sheet migration
 * Loaded from migration-sheet-config.yml
 */
@Configuration
@ConfigurationProperties(prefix = "")
@Data
public class SheetMigrationConfig {

    private List<SheetConfig> sheets = new ArrayList<>();
    private GlobalConfig global = new GlobalConfig();

    /**
     * Get sheet config by name
     */
    public SheetConfig getSheetConfig(String sheetName) {
        return sheets.stream()
                .filter(s -> s.getName().equals(sheetName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get enabled sheets ordered by order field
     */
    public List<SheetConfig> getEnabledSheetsOrdered() {
        return sheets.stream()
                .filter(SheetConfig::isEnabled)
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .toList();
    }

    /**
     * Configuration for individual sheet
     */
    @Data
    public static class SheetConfig {
        private String name;
        private boolean enabled = true;
        private int order;
        private String description;

        // DTO mapping
        private String dtoClass;

        // Staging tables
        private String stagingRawTable;
        private String stagingValidTable;
        private String stagingErrorTable;

        // Master table
        private String masterTable;

        // Processing config
        private int batchSize = 5000;
        private boolean parallelProcessing = false;
        private boolean enableMasking = true;

        // Validation rules
        private List<String> validationRules = new ArrayList<>();
    }

    /**
     * Global configuration
     */
    @Data
    public static class GlobalConfig {
        // Timeout settings (milliseconds)
        private long ingestTimeout = 300000;  // 5 minutes
        private long validationTimeout = 600000;  // 10 minutes
        private long insertionTimeout = 900000;  // 15 minutes

        // Monitoring
        private boolean enableMonitoring = true;
        private long progressUpdateInterval = 5000;  // 5 seconds

        // Error handling
        private boolean stopOnFirstError = false;
        private int maxErrorsPerSheet = 10000;
        private boolean continueOnSheetFailure = true;

        // Performance
        private boolean useParallelSheetProcessing = true;
        private int maxConcurrentSheets = 3;

        // Cleanup
        private boolean autoCleanupOnSuccess = false;
        private boolean autoCleanupOnFailure = false;
        private int retentionDays = 30;
    }
}
