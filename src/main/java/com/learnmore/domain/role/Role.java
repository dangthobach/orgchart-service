package com.learnmore.domain.role;

import com.learnmore.domain.common.BaseEntity;
import com.learnmore.domain.menu.Menu;
import com.learnmore.domain.permission.Permission;
import com.learnmore.domain.api.ApiResource;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class Role extends BaseEntity {
    private final UUID id;
    private String name;
    private String description;
    private final Set<Permission> permissions;
    private final Set<Menu> menus;
    private final Set<ApiResource> apiResources;

    public Role(UUID id, String name, String description) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.permissions = new HashSet<>();
        this.menus = new HashSet<>();
        this.apiResources = new HashSet<>();
    }

    public Role() {
        this.id = UUID.randomUUID();
        this.permissions = new HashSet<>();
        this.menus = new HashSet<>();
        this.apiResources = new HashSet<>();
    }

    public void updateDetails(String description) {
        this.description = description;
    }

    public void assignPermissions(Set<Permission> permissions) {
        this.permissions.addAll(permissions);
    }

    public void unassignMenus(Set<Menu> menus) {
        this.permissions.removeAll(menus);
    }

    public void assignMenus(Set<Menu> menus) {
        this.menus.addAll(menus);
    }

    public void assignApiResources(Set<ApiResource> apiResources) {
        this.apiResources.addAll(apiResources);
    }

    public UUID getId() {
        return id;
    }

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

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public Set<Menu> getMenus() {
        return menus;
    }

    public Set<ApiResource> getApiResources() {
        return apiResources;
    }
}