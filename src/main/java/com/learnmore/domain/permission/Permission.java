package com.learnmore.domain.permission;

import com.learnmore.domain.common.BaseEntity;
import lombok.*;


public class Permission extends BaseEntity {
    private String name;
    private String code;
    private String description;
    private PermissionType type;

    public enum PermissionType {
        READ,
        WRITE,
        DELETE,
        EXECUTE
    }

    public Permission() {
    }

    public Permission(String name, String code, String description, PermissionType type) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PermissionType getType() {
        return type;
    }

    public void setType(PermissionType type) {
        this.type = type;
    }
}