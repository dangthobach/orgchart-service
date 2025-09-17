package com.learnmore.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "api_resources")
public class ApiResourceEntity extends AuditEntity {


    @Column(nullable = false)
    private String path;


    @Column(nullable = false)
    private String method;


    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;


    @ManyToMany
    @JoinTable(
        name = "api_permissions",
        joinColumns = @JoinColumn(name = "api_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private List<PermissionEntity> requiredPermissions = new ArrayList<>();


    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    public enum ResourceType {
        DEPARTMENT,    // Đơn vị
        BRANCH,       // Chi nhánh
        USER,         // Người dùng
        ROLE,         // Vai trò
        TEAM,         // Nhóm
        MENU,         // Menu
        OTHER         // Khác
    }


    public void setPath(String path) {
        this.path = path;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public void setRequiredPermissions(List<PermissionEntity> requiredPermissions) {
        this.requiredPermissions = requiredPermissions;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public String getDescription() {
        return description;
    }

    public List<PermissionEntity> getRequiredPermissions() {
        return requiredPermissions;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }
}