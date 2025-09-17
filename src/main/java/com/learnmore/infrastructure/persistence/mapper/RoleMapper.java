package com.learnmore.infrastructure.persistence.mapper;

import com.learnmore.domain.role.Role;
import com.learnmore.infrastructure.persistence.entity.RoleEntity;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class RoleMapper extends AbstractMapper<Role, RoleEntity> {

    public Role entityToDomain(RoleEntity entity) {
        if (entity == null) {
            return null;
        }

        Role role = new Role();
        setBaseEntityFields(role, entity);
        role.setName(entity.getName());
        role.setDescription(entity.getDescription());
        role.assignPermissions(entity.getPermissions().stream()
                .map(permissionEntity -> new PermissionMapper().entityToDomain(permissionEntity))
                .collect(Collectors.toSet()));
        role.assignMenus(entity.getAccessibleMenus().stream()
                .map(menuEntity -> new MenuMapper().entityToDomain(menuEntity))
                .collect(Collectors.toSet()));
        role.assignApiResources(entity.getAccessibleApis().stream()
                .map(apiEntity -> new ApiResourceMapper().entityToDomain(apiEntity))
                .collect(Collectors.toSet()));
        return role;
    }

    public RoleEntity domainToEntity(Role domain) {
        if (domain == null) {
            return null;
        }

        RoleEntity entity = new RoleEntity();
        setBaseEntityFields(entity, domain);
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setPermissions(domain.getPermissions().stream()
                .map(permission -> new PermissionMapper().domainToEntity(permission))
                .collect(Collectors.toSet()));
        entity.setAccessibleMenus(domain.getMenus().stream()
                .map(menu -> new MenuMapper().domainToEntity(menu))
                .collect(Collectors.toSet()));
        entity.setAccessibleApis(domain.getApiResources().stream()
                .map(api -> new ApiResourceMapper().domainToEntity(api))
                .collect(Collectors.toSet()));
        return entity;
    }
} 