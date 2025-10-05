package com.learnmore.application.dto;


import java.util.Set;
import java.util.UUID;


public class RoleCreateDTO {
    private String name;
    private String description;
    private Set<UUID> permissionIds;
    private Set<UUID> menuIds;
    private Set<UUID> apiResourceIds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<UUID> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(Set<UUID> permissionIds) {
        this.permissionIds = permissionIds;
    }

    public Set<UUID> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(Set<UUID> menuIds) {
        this.menuIds = menuIds;
    }

    public Set<UUID> getApiResourceIds() {
        return apiResourceIds;
    }

    public void setApiResourceIds(Set<UUID> apiResourceIds) {
        this.apiResourceIds = apiResourceIds;
    }
}