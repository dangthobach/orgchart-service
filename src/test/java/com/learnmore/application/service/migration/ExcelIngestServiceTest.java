package com.learnmore.application.service.migration;

import com.learnmore.application.dto.migration.ExcelRowDTO;
import com.learnmore.application.dto.migration.MigrationResultDTO;
import com.learnmore.domain.migration.MigrationJob;
import com.learnmore.infrastructure.repository.MigrationJobRepository;
import com.learnmore.infrastructure.repository.StagingRawRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExcelIngestService
 */
@ExtendWith(MockitoExtension.class)
class ExcelIngestServiceTest {
    
    @Mock
    private MigrationJobRepository migrationJobRepository;
    
    @Mock
    private StagingRawRepository stagingRawRepository;
    
    @InjectMocks
    private ExcelIngestService excelIngestService;
    
    private MigrationJob testJob;
    
    @BeforeEach
    void setUp() {
        testJob = MigrationJob.builder()
                .jobId("TEST_JOB_001")
                .filename("test.xlsx")
                .status("STARTED")
                .createdAt(LocalDateTime.now())
                .createdBy("test-user")
                .build();
    }
    
    @Test
    void startIngestProcess_WithValidInput_ShouldCreateJobAndReturnResult() {
        // Given
        InputStream inputStream = new ByteArrayInputStream("test data".getBytes());
        String filename = "test.xlsx";
        String createdBy = "test-user";
        
        when(migrationJobRepository.save(any(MigrationJob.class))).thenReturn(testJob);
        
        // When
        MigrationResultDTO result = excelIngestService.startIngestProcess(inputStream, filename, createdBy);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJobId()).startsWith("JOB_");
        assertThat(result.getFilename()).isEqualTo(filename);
        
        verify(migrationJobRepository, times(2)).save(any(MigrationJob.class)); // Initial save + update
    }
    
    @Test
    void startIngestProcess_WithNullInputStream_ShouldThrowException() {
        // Given
        InputStream inputStream = null;
        String filename = "test.xlsx";
        String createdBy = "test-user";
        
        when(migrationJobRepository.save(any(MigrationJob.class))).thenReturn(testJob);
        
        // When & Then
        assertThrows(RuntimeException.class, () -> 
            excelIngestService.startIngestProcess(inputStream, filename, createdBy));
    }
}
