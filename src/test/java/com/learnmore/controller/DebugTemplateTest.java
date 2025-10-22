package com.learnmore.controller;

import com.learnmore.application.service.EnhancedExcelTemplateValidationService;
import com.learnmore.application.utils.validation.TemplateValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@SpringBootTest
public class DebugTemplateTest {

    @Autowired
    private EnhancedExcelTemplateValidationService templateValidationService;

    @Test
    public void testValidDataTemplate() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-valid-data.xlsx");
        try (InputStream inputStream = resource.getInputStream()) {
            TemplateValidationResult result = templateValidationService.validateMigrationExcel(inputStream);
            System.out.println("Valid: " + result.isValid());
            if (!result.isValid() && result.getErrors() != null) {
                result.getErrors().forEach(error -> {
                    System.out.println("ERROR: " + error.getMessage());
                });
            }
        }
    }

    @Test
    public void testEmptyDataTemplate() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-empty-data.xlsx");
        try (InputStream inputStream = resource.getInputStream()) {
            TemplateValidationResult result = templateValidationService.validateMigrationExcel(inputStream);
            System.out.println("Valid: " + result.isValid());
            if (!result.isValid() && result.getErrors() != null) {
                result.getErrors().forEach(error -> {
                    System.out.println("ERROR: " + error.getMessage());
                });
            }
        }
    }
}
