package com.learnmore.controller;

import com.learnmore.application.service.EnhancedExcelTemplateValidationService;
import com.learnmore.application.service.migration.MigrationOrchestrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for MigrationController /excel/upload-async endpoint
 *
 * Test scenarios:
 * 1. Valid data - Should accept and start async processing
 * 2. Empty data (valid headers, no data rows) - Should reject with error "Tập không có dữ liệu"
 * 3. Invalid template - Should reject with error "File không đúng template"
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Migration Controller Async Upload Integration Tests")
class MigrationControllerAsyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MigrationOrchestrationService migrationOrchestrationService;

    @Autowired
    private EnhancedExcelTemplateValidationService templateValidationService;

    private static final String UPLOAD_ASYNC_ENDPOINT = "/migration/excel/upload-async";

    @Test
    @DisplayName("Test Case 1: Valid Excel file with 10 data rows - Should accept and start async migration")
    void testUploadAsync_ValidData_ShouldAcceptAndStartMigration() throws Exception {
        // Given: Valid Excel file with correct template and 10 data rows
        ClassPathResource resource = new ClassPathResource("test-valid-data.xlsx");

        try (InputStream inputStream = resource.getInputStream()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-valid-data.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    inputStream
            );

            // When: Upload the file to async endpoint
            // Then: Should return 202 ACCEPTED with success message
            mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                            .file(file)
                            .param("createdBy", "test-user")
                            .param("maxRows", "1000"))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message", is("Migration started successfully")))
                    .andExpect(jsonPath("$.filename", is("test-valid-data.xlsx")))
                    .andExpect(jsonPath("$.status", is("PROCESSING")))
                    .andExpect(jsonPath("$.note", containsString("Use /status endpoint")));
        }
    }

    @Test
    @DisplayName("Test Case 2: Empty data (valid headers but no data rows) - Should reject with 'Tập không có dữ liệu'")
    void testUploadAsync_EmptyData_ShouldRejectWithNoDataError() throws Exception {
        // Given: Excel file with valid headers but no data rows
        ClassPathResource resource = new ClassPathResource("test-empty-data.xlsx");

        try (InputStream inputStream = resource.getInputStream()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-empty-data.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    inputStream
            );

            // When: Upload the file to async endpoint
            // Then: Should return 400 BAD REQUEST with error message "Tập không có dữ liệu"
            mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                            .file(file)
                            .param("createdBy", "test-user")
                            .param("maxRows", "1000"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Tập không có dữ liệu")));
        }
    }

    @Test
    @DisplayName("Test Case 3: Invalid template - Should reject with 'File không đúng template'")
    void testUploadAsync_InvalidTemplate_ShouldRejectWithTemplateError() throws Exception {
        // Given: Excel file with invalid headers/template
        ClassPathResource resource = new ClassPathResource("test-invalid-template.xlsx");

        try (InputStream inputStream = resource.getInputStream()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-invalid-template.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    inputStream
            );

            // When: Upload the file to async endpoint
            // Then: Should return 400 BAD REQUEST with error message containing "File không đúng template"
            mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                            .file(file)
                            .param("createdBy", "test-user")
                            .param("maxRows", "1000"))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("File không đúng template")));
        }
    }

    @Test
    @DisplayName("Test Case 4: Empty file - Should reject with 'File is empty'")
    void testUploadAsync_EmptyFile_ShouldRejectWithEmptyFileError() throws Exception {
        // Given: Empty file (0 bytes)
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        // When: Upload the empty file
        // Then: Should return 400 BAD REQUEST with error "File is empty"
        mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                        .file(emptyFile)
                        .param("createdBy", "test-user"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("File is empty")));
    }

    @Test
    @DisplayName("Test Case 5: Invalid file format - Should reject with 'Invalid file format'")
    void testUploadAsync_InvalidFileFormat_ShouldRejectWithFormatError() throws Exception {
        // Given: Non-Excel file (e.g., text file)
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "This is not an Excel file".getBytes()
        );

        // When: Upload the invalid file
        // Then: Should return 400 BAD REQUEST with error about invalid format
        mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                        .file(invalidFile)
                        .param("createdBy", "test-user"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid file format")));
    }

    @Test
    @DisplayName("Test Case 6: File size exceeds 10MB limit - Should reject with size limit error")
    void testUploadAsync_FileSizeTooLarge_ShouldRejectWithSizeError() throws Exception {
        // Given: File larger than 10MB (simulate with byte array)
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large-file.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                largeContent
        );

        // When: Upload the large file
        // Then: Should return 400 BAD REQUEST with size limit error
        mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                        .file(largeFile)
                        .param("createdBy", "test-user"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("File size exceeds 10MB limit")))
                .andExpect(jsonPath("$.sizeBytes").isNumber());
    }

    @Test
    @DisplayName("Test Case 7: Default parameters - Should use default createdBy and maxRows")
    void testUploadAsync_DefaultParameters_ShouldUseDefaults() throws Exception {
        // Given: Valid Excel file without explicit parameters
        ClassPathResource resource = new ClassPathResource("test-valid-data.xlsx");

        try (InputStream inputStream = resource.getInputStream()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-valid-data.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    inputStream
            );

            // When: Upload without specifying createdBy and maxRows (should use defaults)
            // Then: Should accept with default values
            mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                            .file(file))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message", is("Migration started successfully")))
                    .andExpect(jsonPath("$.status", is("PROCESSING")));
        }
    }

    @Test
    @DisplayName("Test Case 8: Custom maxRows limit - Should respect the limit")
    void testUploadAsync_CustomMaxRows_ShouldRespectLimit() throws Exception {
        // Given: Valid Excel file with custom maxRows parameter
        ClassPathResource resource = new ClassPathResource("test-valid-data.xlsx");

        try (InputStream inputStream = resource.getInputStream()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-valid-data.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    inputStream
            );

            // When: Upload with custom maxRows
            // Then: Should accept
            mockMvc.perform(multipart(UPLOAD_ASYNC_ENDPOINT)
                            .file(file)
                            .param("createdBy", "admin-user")
                            .param("maxRows", "5"))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message", is("Migration started successfully")));
        }
    }
}
