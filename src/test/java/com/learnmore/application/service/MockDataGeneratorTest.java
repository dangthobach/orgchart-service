package com.learnmore.application.service;

import com.learnmore.application.dto.User;
import com.learnmore.application.dto.Role;
import com.learnmore.application.dto.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockDataGenerator
 */
class MockDataGeneratorTest {

    private MockDataGenerator mockDataGenerator;

    @BeforeEach
    void setUp() {
        mockDataGenerator = new MockDataGenerator();
    }

    @Test
    void testGenerateUsers_SmallCount() {
        // Act
        List<User> users = mockDataGenerator.generateUsers(10);

        // Assert
        assertNotNull(users);
        assertEquals(10, users.size());
        
        // Check uniqueness of IDs
        Set<String> ids = users.stream().map(User::getId).collect(Collectors.toSet());
        assertEquals(10, ids.size(), "All user IDs should be unique");
        
        // Check uniqueness of identity cards
        Set<String> identityCards = users.stream().map(User::getIdentityCard).collect(Collectors.toSet());
        assertEquals(10, identityCards.size(), "All identity cards should be unique");
        
        // Check that all users have required fields
        for (User user : users) {
            assertNotNull(user.getId());
            assertNotNull(user.getIdentityCard());
            assertNotNull(user.getFirstName());
            assertNotNull(user.getLastName());
            assertNotNull(user.getEmail());
            assertNotNull(user.getDepartment());
            assertNotNull(user.getCreatedAt());
        }
    }

    @Test
    void testGenerateRoles_SmallCount() {
        // Act
        List<Role> roles = mockDataGenerator.generateRoles(5);

        // Assert
        assertNotNull(roles);
        assertEquals(5, roles.size());
        
        // Check uniqueness of IDs
        Set<String> ids = roles.stream().map(Role::getId).collect(Collectors.toSet());
        assertEquals(5, ids.size(), "All role IDs should be unique");
        
        // Check uniqueness of names
        Set<String> names = roles.stream().map(Role::getName).collect(Collectors.toSet());
        assertEquals(5, names.size(), "All role names should be unique");
        
        // Check that all roles have required fields
        for (Role role : roles) {
            assertNotNull(role.getId());
            assertNotNull(role.getName());
            assertNotNull(role.getDescription());
            assertNotNull(role.getIsActive());
            assertNotNull(role.getCreatedAt());
        }
    }

    @Test
    void testGeneratePermissions_SmallCount() {
        // Act
        List<Permission> permissions = mockDataGenerator.generatePermissions(5);

        // Assert
        assertNotNull(permissions);
        assertEquals(5, permissions.size());
        
        // Check uniqueness of IDs
        Set<String> ids = permissions.stream().map(Permission::getId).collect(Collectors.toSet());
        assertEquals(5, ids.size(), "All permission IDs should be unique");
        
        // Check uniqueness of codes
        Set<String> codes = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
        assertEquals(5, codes.size(), "All permission codes should be unique");
        
        // Check that all permissions have required fields
        for (Permission permission : permissions) {
            assertNotNull(permission.getId());
            assertNotNull(permission.getName());
            assertNotNull(permission.getCode());
            assertNotNull(permission.getDescription());
            assertNotNull(permission.getType());
            assertNotNull(permission.getResource());
            assertNotNull(permission.getIsActive());
            assertNotNull(permission.getCreatedAt());
        }
    }

    @Test
    void testGenerateAllEntities() {
        // Act
        Map<String, List<?>> allData = mockDataGenerator.generateAllEntities(5, 3, 3);

        // Assert
        assertNotNull(allData);
        assertEquals(3, allData.size());
        
        // Check that all entity types are present
        assertTrue(allData.containsKey("User"));
        assertTrue(allData.containsKey("Role"));
        assertTrue(allData.containsKey("Permission"));
        
        // Check counts
        assertEquals(5, allData.get("User").size());
        assertEquals(3, allData.get("Role").size());
        assertEquals(3, allData.get("Permission").size());
        
        // Check that all lists contain the correct types
        assertTrue(allData.get("User").get(0) instanceof User);
        assertTrue(allData.get("Role").get(0) instanceof Role);
        assertTrue(allData.get("Permission").get(0) instanceof Permission);
    }

    @Test
    void testGetGenerationStats() {
        // Act
        Map<String, Object> stats = mockDataGenerator.getGenerationStats();

        // Assert
        assertNotNull(stats);
        assertTrue(stats.containsKey("uniqueIds"));
        assertTrue(stats.containsKey("uniqueIdentityCards"));
        assertTrue(stats.containsKey("departments"));
        
        // Check that departments is a list
        assertTrue(stats.get("departments") instanceof List);
    }

    @Test
    void testClearCaches() {
        // Act - should not throw exception
        assertDoesNotThrow(() -> mockDataGenerator.clearCaches());
    }

    @Test
    void testGenerateUsers_ZeroCount() {
        // Act
        List<User> users = mockDataGenerator.generateUsers(0);

        // Assert
        assertNotNull(users);
        assertEquals(0, users.size());
    }

    @Test
    void testGenerateRoles_ZeroCount() {
        // Act
        List<Role> roles = mockDataGenerator.generateRoles(0);

        // Assert
        assertNotNull(roles);
        assertEquals(0, roles.size());
    }

    @Test
    void testGeneratePermissions_ZeroCount() {
        // Act
        List<Permission> permissions = mockDataGenerator.generatePermissions(0);

        // Assert
        assertNotNull(permissions);
        assertEquals(0, permissions.size());
    }
}

