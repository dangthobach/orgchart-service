package com.learnmore.controller;

import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.application.service.migration.MigrationOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for MigrationController
 */
@WebMvcTest(MigrationController.class)
class MigrationControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private MigrationOrchestrationService migrationOrchestrationService;
    
    @Test
    void uploadAndMigrateExcel_WithValidFile_ShouldReturnSuccess() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test.xlsx", 
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "test content".getBytes()
        );
        
        MigrationResultDTO expectedResult = MigrationResultDTO.builder()
                .jobId("TEST_JOB_001")
                .status("COMPLETED")
                .filename("test.xlsx")
                .totalRows(100L)
                .processedRows(100L)
                .validRows(95L)
                .errorRows(5L)
                .insertedRows(95L)
                .build();
        
        when(migrationOrchestrationService.performFullMigration(any(), any(), any()))
                .thenReturn(expectedResult);
        
        // When & Then
        mockMvc.perform(multipart("/api/migration/excel/upload")
                .file(file)
                .param("createdBy", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("TEST_JOB_001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRows").value(100))
                .andExpect(jsonPath("$.validRows").value(95));
    }
    
    @Test
    void uploadAndMigrateExcel_WithEmptyFile_ShouldReturnBadRequest() throws Exception {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", 
                "empty.xlsx", 
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );
        
        // When & Then
        mockMvc.perform(multipart("/api/migration/excel/upload")
                .file(emptyFile)
                .param("createdBy", "test-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("File is empty"));
    }
    
    @Test
    void getJobStatus_WithValidJobId_ShouldReturnStatus() throws Exception {
        // Given
        String jobId = "TEST_JOB_001";
        Map<String, Object> expectedStatus = Map.of(
                "jobId", jobId,
                "status", "COMPLETED",
                "totalRows", 100L,
                "processedRows", 100L,
                "validRows", 95L,
                "errorRows", 5L
        );
        
        when(migrationOrchestrationService.getJobStatistics(jobId))
                .thenReturn(expectedStatus);
        
        // When & Then
        mockMvc.perform(get("/api/migration/job/{jobId}/status", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRows").value(100));
    }
    
    @Test
    void getJobStatus_WithInvalidJobId_ShouldReturnNotFound() throws Exception {
        // Given
        String jobId = "INVALID_JOB";
        
        when(migrationOrchestrationService.getJobStatistics(jobId))
                .thenThrow(new IllegalArgumentException("Job not found"));
        
        // When & Then
        mockMvc.perform(get("/api/migration/job/{jobId}/status", jobId))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void getSystemMetrics_ShouldReturnMetrics() throws Exception {
        // Given
        Map<String, Object> expectedMetrics = Map.of(
                "totalMemoryMB", 1024L,
                "usedMemoryMB", 512L,
                "freeMemoryMB", 512L,
                "memoryUsagePercent", 50.0
        );
        
        when(migrationOrchestrationService.getSystemMetrics())
                .thenReturn(expectedMetrics);
        
        // When & Then
        mockMvc.perform(get("/api/migration/system/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMemoryMB").value(1024))
                .andExpect(jsonPath("$.usedMemoryMB").value(512))
                .andExpect(jsonPath("$.memoryUsagePercent").value(50.0));
    }
}
