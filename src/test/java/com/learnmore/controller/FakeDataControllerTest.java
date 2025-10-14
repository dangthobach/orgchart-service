package com.learnmore.controller;

import com.learnmore.application.service.FakeDataService;
import com.learnmore.application.utils.exception.ExcelProcessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FakeDataController
 */
@ExtendWith(MockitoExtension.class)
class FakeDataControllerTest {

    @Mock
    private FakeDataService fakeDataService;

    @InjectMocks
    private FakeDataController fakeDataController;

    @BeforeEach
    void setUp() {
        // Setup common test data
    }

    @Test
    void testGenerateFakeData_Success() throws ExcelProcessException {
        // Arrange
        String expectedFileName = "fake_data_20241201_120000.xlsx";
        when(fakeDataService.generateAndExportFakeData()).thenReturn(expectedFileName);

        // Act
        ResponseEntity<Map<String, Object>> response = fakeDataController.generateFakeData();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals("Fake data generated and exported successfully", body.get("message"));
        assertEquals(expectedFileName, body.get("fileName"));
        assertEquals(1000, body.get("userCount"));
        assertEquals(100, body.get("roleCount"));
        assertEquals(100, body.get("permissionCount"));
        assertNotNull(body.get("timestamp"));

        verify(fakeDataService, times(1)).generateAndExportFakeData();
    }

    @Test
    void testGenerateFakeData_ServiceException() throws ExcelProcessException {
        // Arrange
        when(fakeDataService.generateAndExportFakeData())
            .thenThrow(new ExcelProcessException("Test exception"));

        // Act
        ResponseEntity<Map<String, Object>> response = fakeDataController.generateFakeData();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertTrue(((String) body.get("message")).contains("Failed to generate fake data"));
        assertNotNull(body.get("timestamp"));

        verify(fakeDataService, times(1)).generateAndExportFakeData();
    }

    @Test
    void testGenerateFakeDataWithUserCount_Success() throws ExcelProcessException {
        // Arrange
        int userCount = 500;
        String expectedFileName = "fake_data_20241201_120000.xlsx";
        when(fakeDataService.generateAndExportFakeData(userCount)).thenReturn(expectedFileName);

        // Act
        ResponseEntity<Map<String, Object>> response = fakeDataController.generateFakeDataWithUserCount(userCount);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals(expectedFileName, body.get("fileName"));
        assertEquals(userCount, body.get("userCount"));
        assertEquals(100, body.get("roleCount"));
        assertEquals(100, body.get("permissionCount"));

        verify(fakeDataService, times(1)).generateAndExportFakeData(userCount);
    }

    @Test
    void testGenerateFakeDataWithUserCount_InvalidCount() {
        // Act & Assert
        ResponseEntity<Map<String, Object>> response = fakeDataController.generateFakeDataWithUserCount(0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertTrue(((String) body.get("message")).contains("Invalid user count"));

        verify(fakeDataService, never()).generateAndExportFakeData(anyInt());
    }

    @Test
    void testGenerateFakeDataCustom_Success() throws ExcelProcessException {
        // Arrange
        int userCount = 2000;
        int roleCount = 50;
        int permissionCount = 75;
        String expectedFileName = "fake_data_20241201_120000.xlsx";
        when(fakeDataService.generateAndExportFakeData(userCount, roleCount, permissionCount))
            .thenReturn(expectedFileName);

        // Act
        ResponseEntity<Map<String, Object>> response = fakeDataController.generateFakeDataCustom(
            userCount, roleCount, permissionCount);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals(expectedFileName, body.get("fileName"));
        assertEquals(userCount, body.get("userCount"));
        assertEquals(roleCount, body.get("roleCount"));
        assertEquals(permissionCount, body.get("permissionCount"));

        verify(fakeDataService, times(1)).generateAndExportFakeData(userCount, roleCount, permissionCount);
    }

    @Test
    void testGenerateFakeDataCustom_InvalidParameters() {
        // Act & Assert - Test invalid user count
        ResponseEntity<Map<String, Object>> response = fakeDataController.generateFakeDataCustom(
            -1, 50, 75);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertTrue(((String) body.get("message")).contains("Invalid parameters"));

        verify(fakeDataService, never()).generateAndExportFakeData(anyInt(), anyInt(), anyInt());
    }

    @Test
    void testGetGenerationStats_Success() {
        // Arrange
        Map<String, Object> expectedStats = new HashMap<>();
        expectedStats.put("uniqueIds", 1000);
        expectedStats.put("uniqueIdentityCards", 1000);
        when(fakeDataService.getGenerationStats()).thenReturn(expectedStats);

        // Act
        ResponseEntity<Map<String, Object>> response = fakeDataController.getGenerationStats();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals("Generation statistics retrieved successfully", body.get("message"));
        assertEquals(expectedStats, body.get("stats"));

        verify(fakeDataService, times(1)).getGenerationStats();
    }

    @Test
    void testClearCache_Success() {
        // Act
        ResponseEntity<Map<String, Object>> response = fakeDataController.clearCache();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals("Generation caches cleared successfully", body.get("message"));

        verify(fakeDataService, times(1)).clearCaches();
    }

    @Test
    void testHealthCheck_Success() {
        // Act
        ResponseEntity<Map<String, Object>> response = fakeDataController.healthCheck();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals("Fake data service is healthy", body.get("message"));
        assertEquals("FakeDataController", body.get("service"));
        assertNotNull(body.get("timestamp"));
    }
}

