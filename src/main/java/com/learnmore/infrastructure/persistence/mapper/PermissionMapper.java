package com.learnmore.infrastructure.persistence.mapper;

import com.learnmore.domain.permission.Permission;
import com.learnmore.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper extends AbstractMapper<Permission, PermissionEntity> {

    public Permission entityToDomain(PermissionEntity entity) {
        if (entity == null) {
            return null;
        }

        Permission permission = new Permission();
        setBaseEntityFields(permission, entity);
        permission.setName(entity.getName());
        permission.setCode(entity.getCode());
        permission.setDescription(entity.getDescription());
        permission.setType(Permission.PermissionType.valueOf(entity.getType().name()));
        return permission;
    }

    public PermissionEntity domainToEntity(Permission domain) {
        if (domain == null) {
            return null;
        }

        PermissionEntity entity = new PermissionEntity();
        setBaseEntityFields(entity, domain);
        entity.setName(domain.getName());
        entity.setCode(domain.getCode());
        entity.setDescription(domain.getDescription());
        entity.setType(PermissionEntity.PermissionType.valueOf(domain.getType().name()));
        return entity;
    }
} 