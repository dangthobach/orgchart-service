package com.learnmore.infrastructure.persistence.mapper;

import com.learnmore.domain.api.ApiResource;
import com.learnmore.infrastructure.persistence.entity.ApiResourceEntity;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ApiResourceMapper extends AbstractMapper<ApiResource, ApiResourceEntity> {

    public ApiResource entityToDomain(ApiResourceEntity entity) {
        if (entity == null) {
            return null;
        }

        ApiResource apiResource = new ApiResource();
        setBaseEntityFields(apiResource, entity);
        apiResource.setPath(entity.getPath());
        apiResource.setMethod(entity.getMethod());
        apiResource.setDescription(entity.getDescription());
        apiResource.setPublic(entity.isPublic());
        apiResource.setResourceType(ApiResource.ResourceType.valueOf(entity.getResourceType().name()));
        apiResource.setRequiredPermissions(entity.getRequiredPermissions().stream()
                .map(permissionEntity -> new PermissionMapper().entityToDomain(permissionEntity))
                .collect(Collectors.toList()));
        return apiResource;
    }

    public ApiResourceEntity domainToEntity(ApiResource domain) {
        if (domain == null) {
            return null;
        }

        ApiResourceEntity entity = new ApiResourceEntity();
        setBaseEntityFields(entity, domain);
        entity.setPath(domain.getPath());
        entity.setMethod(domain.getMethod());
        entity.setDescription(domain.getDescription());
        entity.setPublic(domain.isPublic());
        entity.setResourceType(ApiResourceEntity.ResourceType.valueOf(domain.getResourceType().name()));
        entity.setRequiredPermissions(domain.getRequiredPermissions().stream()
                .map(permission -> new PermissionMapper().domainToEntity(permission))
                .collect(Collectors.toList()));
        return entity;
    }
} 