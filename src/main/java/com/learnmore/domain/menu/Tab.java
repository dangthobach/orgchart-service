package com.learnmore.domain.menu;

import com.learnmore.domain.common.BaseEntity;
import com.learnmore.domain.permission.Permission;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Tab extends BaseEntity {
    private String name;
    private String path;
    private Integer order;
    private Menu menu;
    private List<Permission> requiredPermissions = new ArrayList<>();

    public void addRequiredPermission(Permission permission) {
        this.requiredPermissions.add(permission);
    }

    public void removeRequiredPermission(Permission permission) {
        this.requiredPermissions.remove(permission);
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public Menu getMenu() {
        return menu;
    }

    public void setMenu(Menu menu) {
        this.menu = menu;
    }

    public List<Permission> getRequiredPermissions() {
        return requiredPermissions;
    }

    public void setRequiredPermissions(List<Permission> requiredPermissions) {
        this.requiredPermissions = requiredPermissions;
    }
}