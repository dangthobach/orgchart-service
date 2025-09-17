package com.learnmore.domain.menu;

import com.learnmore.domain.common.BaseEntity;
import com.learnmore.domain.permission.Permission;

import java.util.*;


public class Menu extends BaseEntity {

    private String name;
    private String path;
    private String icon;
    private Integer order;
    private Menu parent;
    private List<Menu> children = new ArrayList<>();
    private Set<Tab> tabs = new HashSet<>();
    private Set<Permission> permissions = new HashSet<>();

    public void addChild(Menu child) {
        child.setParent(this);
        this.children.add(child);
    }

    public void removeChild(Menu child) {
        child.setParent(null);
        this.children.remove(child);
    }

    public void setParent(Menu parent) {
        this.parent = parent;
    }
    public void addTab(Tab tab) {
        this.tabs.add(tab);
    }

    public void removeTab(Tab tab) {
        this.tabs.remove(tab);
    }

    public void addRequiredPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public void removeRequiredPermission(Permission permission) {
        this.permissions.remove(permission);
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public Menu getParent() {
        return parent;
    }

    public List<Menu> getChildren() {
        return children;
    }

    public void setChildren(List<Menu> children) {
        this.children = children;
    }

    public Set<Tab> getTabs() {
        return tabs;
    }

    public void setTabs(Set<Tab> tabs) {
        this.tabs = tabs;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }
}