package com.learnmore.controller;

import com.learnmore.application.dto.UserCreateDTO;
import com.learnmore.application.port.input.UserService;
import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.domain.menu.Menu;
import com.learnmore.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

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

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportUsersToExcel() {
        List<User> users = userService.getAllUsers();
        
        try {
            byte[] excelBytes = ExcelUtil.writeToExcelBytes(users, 0, 0);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "users.xlsx");
            
            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
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