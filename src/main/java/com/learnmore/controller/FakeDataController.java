package com.learnmore.controller;

import com.learnmore.application.service.FakeDataService;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for fake data generation and Excel export
 * 
 * Provides endpoints to:
 * - Generate fake data for User, Role, and Permission entities
 * - Export data to multi-sheet Excel files
 * - Get generation statistics
 * - Clear caches for memory management
 */
@Slf4j
@RestController
@RequestMapping("/api/fake-data")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FakeDataController {
    
    private final FakeDataService fakeDataService;
    
    /**
     * Generate fake data with default counts and export to Excel
     * 
     * Default counts:
     * - Users: 1000
     * - Roles: 100  
     * - Permissions: 100
     * 
     * @return Response with file path and generation info
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateFakeData() {
        log.info("🚀 API: Generate fake data with default counts");
        
        try {
            String fileName = fakeDataService.generateAndExportFakeData();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Fake data generated and exported successfully");
            response.put("fileName", fileName);
            response.put("userCount", 1000);
            response.put("roleCount", 100);
            response.put("permissionCount", 100);
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ API: Fake data generation completed - {}", fileName);
            return ResponseEntity.ok(response);
            
        } catch (ExcelProcessException e) {
            log.error("❌ API: Failed to generate fake data", e);
            return createErrorResponse("Failed to generate fake data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("❌ API: Unexpected error during fake data generation", e);
            return createErrorResponse("Unexpected error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Generate fake data with custom user count and export to Excel
     * 
     * @param userCount Number of users to generate (1-1000000)
     * @return Response with file path and generation info
     */
    @PostMapping("/generate/users/{userCount}")
    public ResponseEntity<Map<String, Object>> generateFakeDataWithUserCount(
            @PathVariable int userCount) {
        
        log.info("🚀 API: Generate fake data with {} users", userCount);
        
        try {
            validateUserCount(userCount);
            String fileName = fakeDataService.generateAndExportFakeData(userCount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Fake data generated and exported successfully");
            response.put("fileName", fileName);
            response.put("userCount", userCount);
            response.put("roleCount", 100);
            response.put("permissionCount", 100);
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ API: Fake data generation completed - {} users in {}", userCount, fileName);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ API: Invalid user count parameter: {}", userCount);
            return createErrorResponse("Invalid user count: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ExcelProcessException e) {
            log.error("❌ API: Failed to generate fake data", e);
            return createErrorResponse("Failed to generate fake data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("❌ API: Unexpected error during fake data generation", e);
            return createErrorResponse("Unexpected error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Generate fake data with custom counts for all entities
     * 
     * @param userCount Number of users to generate (1-1000000)
     * @param roleCount Number of roles to generate (1-10000)
     * @param permissionCount Number of permissions to generate (1-10000)
     * @return Response with file path and generation info
     */
    @PostMapping("/generate/custom")
    public ResponseEntity<Map<String, Object>> generateFakeDataCustom(
            @RequestParam int userCount,
            @RequestParam int roleCount,
            @RequestParam int permissionCount) {
        
        log.info("🚀 API: Generate fake data with custom counts - Users: {}, Roles: {}, Permissions: {}", 
                userCount, roleCount, permissionCount);
        
        try {
            validateAllCounts(userCount, roleCount, permissionCount);
            String fileName = fakeDataService.generateAndExportFakeData(userCount, roleCount, permissionCount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Fake data generated and exported successfully");
            response.put("fileName", fileName);
            response.put("userCount", userCount);
            response.put("roleCount", roleCount);
            response.put("permissionCount", permissionCount);
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ API: Custom fake data generation completed - {} users, {} roles, {} permissions in {}", 
                    userCount, roleCount, permissionCount, fileName);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ API: Invalid parameters - Users: {}, Roles: {}, Permissions: {}", 
                    userCount, roleCount, permissionCount);
            return createErrorResponse("Invalid parameters: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ExcelProcessException e) {
            log.error("❌ API: Failed to generate fake data", e);
            return createErrorResponse("Failed to generate fake data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("❌ API: Unexpected error during fake data generation", e);
            return createErrorResponse("Unexpected error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get generation statistics
     * 
     * @return Response with generation statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGenerationStats() {
        log.info("📊 API: Get generation statistics");
        
        try {
            Map<String, Object> stats = fakeDataService.getGenerationStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Generation statistics retrieved successfully");
            response.put("stats", stats);
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ API: Generation statistics retrieved");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ API: Failed to get generation statistics", e);
            return createErrorResponse("Failed to get generation statistics: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Clear generation caches for memory management
     * 
     * @return Response confirming cache clearing
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        log.info("🧹 API: Clear generation caches");
        
        try {
            fakeDataService.clearCaches();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Generation caches cleared successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ API: Generation caches cleared");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ API: Failed to clear caches", e);
            return createErrorResponse("Failed to clear caches: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Health check endpoint
     * 
     * @return Response confirming service is healthy
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Fake data service is healthy");
        response.put("service", "FakeDataController");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Validate user count parameter
     * 
     * @param userCount User count to validate
     * @throws IllegalArgumentException if count is invalid
     */
    private void validateUserCount(int userCount) {
        if (userCount < 1) {
            throw new IllegalArgumentException("User count must be at least 1");
        }
        if (userCount > 1_000_000) {
            throw new IllegalArgumentException("User count cannot exceed 1,000,000");
        }
    }
    
    /**
     * Validate all count parameters
     * 
     * @param userCount User count to validate
     * @param roleCount Role count to validate
     * @param permissionCount Permission count to validate
     * @throws IllegalArgumentException if any count is invalid
     */
    private void validateAllCounts(int userCount, int roleCount, int permissionCount) {
        validateUserCount(userCount);
        
        if (roleCount < 1) {
            throw new IllegalArgumentException("Role count must be at least 1");
        }
        if (roleCount > 10_000) {
            throw new IllegalArgumentException("Role count cannot exceed 10,000");
        }
        
        if (permissionCount < 1) {
            throw new IllegalArgumentException("Permission count must be at least 1");
        }
        if (permissionCount > 10_000) {
            throw new IllegalArgumentException("Permission count cannot exceed 10,000");
        }
    }
    
    /**
     * Create error response
     * 
     * @param message Error message
     * @param status HTTP status
     * @return Error response entity
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(status).body(response);
    }
}

