package com.learnmore.application.port.input;

import com.learnmore.application.dto.RoleCreateDTO;
import com.learnmore.domain.role.Role;
import com.learnmore.domain.permission.Permission;
import com.learnmore.domain.menu.Menu;
import com.learnmore.domain.api.ApiResource;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleService {
    Role createRole(RoleCreateDTO roleDTO);
    Role updateRole(UUID id, RoleCreateDTO roleDTO);
    void deleteRole(UUID id);
    Role getRoleById(UUID id);
    List<Role> getAllRoles();
    Set<Permission> getPermissionsByRoleId(UUID roleId);
    Set<Menu> getMenusByRoleId(UUID roleId);
    Set<ApiResource> getApiResourcesByRoleId(UUID roleId);

    Role assignMenusToRole(UUID roleId, Set<UUID> menuIds);

    Role removeMenusFromRole(UUID roleId, Set<UUID> menuIds);
} 