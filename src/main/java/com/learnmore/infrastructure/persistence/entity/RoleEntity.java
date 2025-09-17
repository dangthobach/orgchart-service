package com.learnmore.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class RoleEntity extends AuditEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "role_menus",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "menu_id")
    )
    private Set<MenuEntity> accessibleMenus = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "role_apis",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "api_id")
    )
    private Set<ApiResourceEntity> accessibleApis = new HashSet<>();

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

    public Set<PermissionEntity> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<PermissionEntity> permissions) {
        this.permissions = permissions;
    }

    public Set<MenuEntity> getAccessibleMenus() {
        return accessibleMenus;
    }

    public void setAccessibleMenus(Set<MenuEntity> accessibleMenus) {
        this.accessibleMenus = accessibleMenus;
    }

    public Set<ApiResourceEntity> getAccessibleApis() {
        return accessibleApis;
    }

    public void setAccessibleApis(Set<ApiResourceEntity> accessibleApis) {
        this.accessibleApis = accessibleApis;
    }
}