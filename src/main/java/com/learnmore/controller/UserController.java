package com.learnmore.controller;

import com.learnmore.application.dto.UserCreateDTO;
import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.port.input.UserService;
import com.learnmore.domain.menu.Menu;
import com.learnmore.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User REST Controller
 *
 * MIGRATION NOTE: Migrated from ExcelUtil to ExcelFacade for Excel export
 * - Uses dependency injection instead of static methods
 * - Better testability (can mock ExcelFacade in tests)
 * - ZERO performance impact (delegates to same optimized implementation)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ExcelFacade excelFacade;

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody UserCreateDTO userDTO) {
        return ResponseEntity.ok(userService.createUser(userDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable UUID id, @RequestBody UserCreateDTO userDTO) {
        return ResponseEntity.ok(userService.updateUser(id, userDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Export all users to Excel file
     *
     * MIGRATION: Changed from ExcelUtil.writeToExcelBytes() to excelFacade.writeExcelToBytes()
     * - Cleaner API (no need to specify rowStart, columnStart)
     * - Uses dependency injection (testable)
     * - ZERO performance impact (same underlying optimized implementation)
     * - Automatic strategy selection based on data size
     *
     * Performance notes:
     * - Small datasets (< 1K users): Uses function-based approach (36% faster)
     * - Medium datasets (1K - 50K users): Uses XSSF workbook
     * - Large datasets (> 50K users): Uses SXSSF streaming (memory efficient)
     *
     * @return Excel file as byte array with appropriate headers
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportUsersToExcel() {
        List<User> users = userService.getAllUsers();

        // ✨ MIGRATED: Use ExcelFacade instead of ExcelUtil
        byte[] excelBytes = excelFacade.writeExcelToBytes(users);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "users.xlsx");

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @PostMapping("/{userId}/roles")
    public ResponseEntity<User> assignRolesToUser(
            @PathVariable UUID userId,
            @RequestBody Set<UUID> roleIds) {
        return ResponseEntity.ok(userService.assignRolesToUser(userId, roleIds));
    }

    @DeleteMapping("/{userId}/roles")
    public ResponseEntity<User> removeRolesFromUser(
            @PathVariable UUID userId,
            @RequestBody Set<UUID> roleIds) {
        return ResponseEntity.ok(userService.removeRolesFromUser(userId, roleIds));
    }

    @GetMapping("/{userId}/menus")
    public ResponseEntity<List<Menu>> getMenuTreeByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getMenuTreeByUserId(userId));
    }
} 