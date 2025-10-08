package com.learnmore.application.service;

import com.learnmore.application.utils.exception.ExcelProcessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Integration tests for FakeDataService
 */
@ExtendWith(MockitoExtension.class)
class FakeDataServiceIntegrationTest {

    @Mock
    private MockDataGenerator mockDataGenerator;

    @InjectMocks
    private FakeDataService fakeDataService;

    @BeforeEach
    void setUp() {
        // Setup common test data
    }

    @Test
    void testGenerateAndExportFakeData_DefaultCounts() throws ExcelProcessException {
        // Arrange
        String expectedFileName = "fake_data_20241201_120000.xlsx";
        
        // Mock the mockDataGenerator to return empty lists for testing
        when(mockDataGenerator.generateAllEntities(1000, 100, 100))
            .thenReturn(Map.of(
                "User", java.util.List.of(),
                "Role", java.util.List.of(),
                "Permission", java.util.List.of()
            ));

        // Act
        String result = fakeDataService.generateAndExportFakeData();

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith("fake_data_"));
        assertTrue(result.endsWith(".xlsx"));
        
        verify(mockDataGenerator, times(1)).generateAllEntities(1000, 100, 100);
    }

    @Test
    void testGenerateAndExportFakeData_CustomCounts() throws ExcelProcessException {
        // Arrange
        int userCount = 500;
        int roleCount = 50;
        int permissionCount = 75;
        
        when(mockDataGenerator.generateAllEntities(userCount, roleCount, permissionCount))
            .thenReturn(Map.of(
                "User", java.util.List.of(),
                "Role", java.util.List.of(),
                "Permission", java.util.List.of()
            ));

        // Act
        String result = fakeDataService.generateAndExportFakeData(userCount, roleCount, permissionCount);

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith("fake_data_"));
        assertTrue(result.endsWith(".xlsx"));
        
        verify(mockDataGenerator, times(1)).generateAllEntities(userCount, roleCount, permissionCount);
    }

    @Test
    void testGenerateAndExportFakeData_WithUserCount() throws ExcelProcessException {
        // Arrange
        int userCount = 2000;
        
        when(mockDataGenerator.generateAllEntities(userCount, 100, 100))
            .thenReturn(Map.of(
                "User", java.util.List.of(),
                "Role", java.util.List.of(),
                "Permission", java.util.List.of()
            ));

        // Act
        String result = fakeDataService.generateAndExportFakeData(userCount);

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith("fake_data_"));
        assertTrue(result.endsWith(".xlsx"));
        
        verify(mockDataGenerator, times(1)).generateAllEntities(userCount, 100, 100);
    }

    @Test
    void testGetGenerationStats() {
        // Arrange
        Map<String, Object> expectedStats = Map.of(
            "uniqueIds", 1000,
            "uniqueIdentityCards", 1000,
            "departments", java.util.List.of("Engineering", "Sales")
        );
        when(mockDataGenerator.getGenerationStats()).thenReturn(expectedStats);

        // Act
        Map<String, Object> result = fakeDataService.getGenerationStats();

        // Assert
        assertNotNull(result);
        assertEquals(expectedStats, result);
        
        verify(mockDataGenerator, times(1)).getGenerationStats();
    }

    @Test
    void testClearCaches() {
        // Act
        fakeDataService.clearCaches();

        // Assert
        verify(mockDataGenerator, times(1)).clearCaches();
    }

    @Test
    void testGenerateAndExportFakeData_ExceptionHandling() throws ExcelProcessException {
        // Arrange
        when(mockDataGenerator.generateAllEntities(anyInt(), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        ExcelProcessException exception = assertThrows(ExcelProcessException.class, () -> {
            fakeDataService.generateAndExportFakeData(100, 10, 10);
        });

        assertTrue(exception.getMessage().contains("Failed to generate and export fake data"));
        assertTrue(exception.getMessage().contains("Test exception"));
        
        verify(mockDataGenerator, times(1)).generateAllEntities(100, 10, 10);
    }
}
