package com.learnmore.controller;

import com.learnmore.application.dto.RoleCreateDTO;
import com.learnmore.application.port.input.RoleService;
import com.learnmore.domain.api.ApiResource;
import com.learnmore.domain.menu.Menu;
import com.learnmore.domain.permission.Permission;
import com.learnmore.domain.role.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    private final RoleService roleService;


    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public ResponseEntity<Role> createRole(@RequestBody RoleCreateDTO roleDTO) {
        return ResponseEntity.ok(roleService.createRole(roleDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable UUID id, @RequestBody RoleCreateDTO roleDTO) {
        return ResponseEntity.ok(roleService.updateRole(id, roleDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Role> getRoleById(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @GetMapping
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<Set<Permission>> getPermissionsByRoleId(@PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.getPermissionsByRoleId(roleId));
    }

    @GetMapping("/{roleId}/menus")
    public ResponseEntity<Set<Menu>> getMenusByRoleId(@PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.getMenusByRoleId(roleId));
    }

    @GetMapping("/{roleId}/api-resources")
    public ResponseEntity<Set<ApiResource>> getApiResourcesByRoleId(@PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.getApiResourcesByRoleId(roleId));
    }
} 