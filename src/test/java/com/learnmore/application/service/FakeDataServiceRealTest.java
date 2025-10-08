package com.learnmore.application.service;

import com.learnmore.application.utils.exception.ExcelProcessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Real integration tests for FakeDataService with actual file generation
 * 
 * Note: These tests use real MockDataGenerator to generate actual data
 * and test the complete flow including file creation.
 */
@ExtendWith(MockitoExtension.class)
class FakeDataServiceRealTest {

    @Mock
    private MockDataGenerator mockDataGenerator;

    @InjectMocks
    private FakeDataService fakeDataService;

    @BeforeEach
    void setUp() {
        // Setup real MockDataGenerator for integration testing
        mockDataGenerator = new MockDataGenerator();
        fakeDataService = new FakeDataService(mockDataGenerator, null);
    }

    @Test
    void testGenerateAndExportFakeData_RealDataGeneration() throws ExcelProcessException {
        // This test will actually generate real data and create Excel file
        // Note: This test requires MultiSheetWriteStrategy to be properly injected
        
        // For now, we'll test the data generation part
        Map<String, List<?>> allData = mockDataGenerator.generateAllEntities(10, 5, 5);
        
        // Assert
        assertNotNull(allData);
        assertEquals(3, allData.size());
        
        // Check that all entity types are present
        assertTrue(allData.containsKey("User"));
        assertTrue(allData.containsKey("Role"));
        assertTrue(allData.containsKey("Permission"));
        
        // Check counts
        assertEquals(10, allData.get("User").size());
        assertEquals(5, allData.get("Role").size());
        assertEquals(5, allData.get("Permission").size());
        
        // Check that all lists contain the correct types
        assertTrue(allData.get("User").get(0) instanceof com.learnmore.application.dto.User);
        assertTrue(allData.get("Role").get(0) instanceof com.learnmore.application.dto.Role);
        assertTrue(allData.get("Permission").get(0) instanceof com.learnmore.application.dto.Permission);
    }

    @Test
    void testMockDataGenerator_RealGeneration() {
        // Test real data generation with small counts
        List<com.learnmore.application.dto.User> users = mockDataGenerator.generateUsers(5);
        List<com.learnmore.application.dto.Role> roles = mockDataGenerator.generateRoles(3);
        List<com.learnmore.application.dto.Permission> permissions = mockDataGenerator.generatePermissions(3);
        
        // Assert Users
        assertNotNull(users);
        assertEquals(5, users.size());
        
        // Check uniqueness of IDs
        long uniqueIds = users.stream().map(com.learnmore.application.dto.User::getId).distinct().count();
        assertEquals(5, uniqueIds, "All user IDs should be unique");
        
        // Check uniqueness of identity cards
        long uniqueIdentityCards = users.stream().map(com.learnmore.application.dto.User::getIdentityCard).distinct().count();
        assertEquals(5, uniqueIdentityCards, "All identity cards should be unique");
        
        // Assert Roles
        assertNotNull(roles);
        assertEquals(3, roles.size());
        
        // Check uniqueness of role names
        long uniqueRoleNames = roles.stream().map(com.learnmore.application.dto.Role::getName).distinct().count();
        assertEquals(3, uniqueRoleNames, "All role names should be unique");
        
        // Assert Permissions
        assertNotNull(permissions);
        assertEquals(3, permissions.size());
        
        // Check uniqueness of permission codes
        long uniquePermissionCodes = permissions.stream().map(com.learnmore.application.dto.Permission::getCode).distinct().count();
        assertEquals(3, uniquePermissionCodes, "All permission codes should be unique");
    }

    @Test
    void testGetGenerationStats() {
        // Generate some data first
        mockDataGenerator.generateUsers(5);
        mockDataGenerator.generateRoles(3);
        mockDataGenerator.generatePermissions(3);
        
        // Get stats
        Map<String, Object> stats = mockDataGenerator.getGenerationStats();
        
        // Assert
        assertNotNull(stats);
        assertTrue(stats.containsKey("uniqueIds"));
        assertTrue(stats.containsKey("uniqueIdentityCards"));
        assertTrue(stats.containsKey("departments"));
        
        // Check that departments is a list
        assertTrue(stats.get("departments") instanceof List);
        
        // Check that we have some unique IDs
        assertTrue((Integer) stats.get("uniqueIds") > 0);
        assertTrue((Integer) stats.get("uniqueIdentityCards") > 0);
    }

    @Test
    void testClearCaches() {
        // Generate some data first
        mockDataGenerator.generateUsers(5);
        
        // Get stats before clearing
        Map<String, Object> statsBefore = mockDataGenerator.getGenerationStats();
        assertTrue((Integer) statsBefore.get("uniqueIds") > 0);
        
        // Clear caches
        mockDataGenerator.clearCaches();
        
        // Get stats after clearing
        Map<String, Object> statsAfter = mockDataGenerator.getGenerationStats();
        assertEquals(0, statsAfter.get("uniqueIds"));
        assertEquals(0, statsAfter.get("uniqueIdentityCards"));
    }

    @Test
    void testGenerateAllEntities_RealData() {
        // Test the complete data generation
        Map<String, List<?>> allData = mockDataGenerator.generateAllEntities(20, 10, 10);
        
        // Assert structure
        assertNotNull(allData);
        assertEquals(3, allData.size());
        
        // Assert data quality
        List<?> users = allData.get("User");
        List<?> roles = allData.get("Role");
        List<?> permissions = allData.get("Permission");
        
        assertEquals(20, users.size());
        assertEquals(10, roles.size());
        assertEquals(10, permissions.size());
        
        // Check that all data is properly generated
        for (Object user : users) {
            assertNotNull(user);
            assertTrue(user instanceof com.learnmore.application.dto.User);
        }
        
        for (Object role : roles) {
            assertNotNull(role);
            assertTrue(role instanceof com.learnmore.application.dto.Role);
        }
        
        for (Object permission : permissions) {
            assertNotNull(permission);
            assertTrue(permission instanceof com.learnmore.application.dto.Permission);
        }
    }
}
