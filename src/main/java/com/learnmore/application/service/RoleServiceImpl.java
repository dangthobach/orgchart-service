package com.learnmore.application.service;

import com.learnmore.application.dto.RoleCreateDTO;
import com.learnmore.application.port.input.RoleService;
import com.learnmore.application.port.output.MenuRepository;
import com.learnmore.application.port.output.RoleRepository;
import com.learnmore.domain.api.ApiResource;
import com.learnmore.domain.menu.Menu;
import com.learnmore.domain.permission.Permission;
import com.learnmore.domain.role.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;

    public RoleServiceImpl(RoleRepository roleRepository, MenuRepository menuRepository) {
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
    }

    @Override
    @Transactional
    public Role createRole(RoleCreateDTO roleDTO) {
        if (roleRepository.existsByName(roleDTO.getName())) {
            throw new RuntimeException("Role name already exists");
        }

        Role role = new Role(
            UUID.randomUUID(),
            roleDTO.getName(),
            roleDTO.getDescription()
        );

        if (roleDTO.getMenuIds() != null && !roleDTO.getMenuIds().isEmpty()) {
            Set<Menu> menus = roleDTO.getMenuIds().stream()
                    .<Menu>map(menuId -> menuRepository.findById(menuId)
                            .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId)))
                    .collect(Collectors.toSet());
            role.assignMenus(menus);
        }

        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public Role updateRole(UUID id, RoleCreateDTO roleDTO) {
        Role existingRole = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        
        existingRole.updateDetails(roleDTO.getDescription());
        
        if (roleDTO.getMenuIds() != null) {
            Set<Menu> menus = roleDTO.getMenuIds().stream()
                    .<Menu>map(menuId -> menuRepository.findById(menuId)
                            .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId)))
                    .collect(Collectors.toSet());
            existingRole.assignMenus(menus);
        }
        
        return roleRepository.save(existingRole);
    }

    @Override
    @Transactional
    public void deleteRole(UUID id) {
        roleRepository.delete(id);
    }

    @Override
    public Role getRoleById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    public Set<Permission> getPermissionsByRoleId(UUID roleId) {
        Role role = getRoleById(roleId);
        return role.getPermissions();
    }

    @Override
    public Set<Menu> getMenusByRoleId(UUID roleId) {
        Role role = getRoleById(roleId);
        return role.getMenus();
    }

    @Override
    public Set<ApiResource> getApiResourcesByRoleId(UUID roleId) {
        Role role = getRoleById(roleId);
        return role.getApiResources();
    }

    @Override
    @Transactional
    public Role assignMenusToRole(UUID roleId, Set<UUID> menuIds) {
        Role role = getRoleById(roleId);
        Set<Menu> menus = menuIds.stream()
                .<Menu>map(menuId -> menuRepository.findById(menuId)
                        .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId)))
                .collect(Collectors.toSet());
        role.assignMenus(menus);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public Role removeMenusFromRole(UUID roleId, Set<UUID> menuIds) {
        Role role = getRoleById(roleId);
        Set<Menu> menusToRemove = menuIds.stream()
                .<Menu>map(menuId -> menuRepository.findById(menuId)
                        .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId)))
                .collect(Collectors.toSet());
        role.unassignMenus(menusToRemove);
        return roleRepository.save(role);
    }
} 