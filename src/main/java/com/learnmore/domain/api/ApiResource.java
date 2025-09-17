package com.learnmore.domain.api;

import com.learnmore.domain.common.BaseEntity;
import com.learnmore.domain.permission.Permission;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ApiResource extends BaseEntity {
    private String path;
    private String method;
    private String description;
    private boolean isPublic;
    private List<Permission> requiredPermissions = new ArrayList<>();
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

    public void addRequiredPermission(Permission permission) {
        this.requiredPermissions.add(permission);
    }

    public void removeRequiredPermission(Permission permission) {
        this.requiredPermissions.remove(permission);
    }

    public boolean isAccessibleByPublic() {
        return this.isPublic;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getDescription() {
        return description;
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

    public List<Permission> getRequiredPermissions() {
        return requiredPermissions;
    }

    public void setRequiredPermissions(List<Permission> requiredPermissions) {
        this.requiredPermissions = requiredPermissions;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }
}