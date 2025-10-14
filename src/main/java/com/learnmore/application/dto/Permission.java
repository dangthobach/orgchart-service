package com.learnmore.application.dto;

import com.learnmore.application.utils.ExcelColumn;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Permission DTO for Excel processing and fake data generation
 * Contains permission information with Excel column mappings
 */
@Entity
@Table(name = "permissions", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "id"),
           @UniqueConstraint(columnNames = "code")
       })
@Data
@NoArgsConstructor
public class Permission {
    
    @Id
    @ExcelColumn(name = "ID")
    @Column(name = "id", nullable = false, unique = true)
    private String id;
    
    @ExcelColumn(name = "Name")
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @ExcelColumn(name = "Code")
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;
    
    @ExcelColumn(name = "Description")
    @Column(name = "description", length = 500)
    private String description;
    
    @ExcelColumn(name = "Type")
    @Column(name = "type", nullable = false, length = 20)
    private String type;
    
    @ExcelColumn(name = "Resource")
    @Column(name = "resource", length = 100)
    private String resource;
    
    @ExcelColumn(name = "Is Active")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
    
    @ExcelColumn(name = "Created At")
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @ExcelColumn(name = "Updated At")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Permission types enum
     */
    public enum PermissionType {
        READ("READ"),
        WRITE("WRITE"),
        DELETE("DELETE"),
        EXECUTE("EXECUTE"),
        CREATE("CREATE"),
        UPDATE("UPDATE"),
        VIEW("VIEW"),
        MANAGE("MANAGE");
        
        private final String value;
        
        PermissionType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Constructor for easy creation with all fields
     */
    public Permission(String id, String name, String code, String description, 
                     String type, String resource, Boolean isActive,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.description = description;
        this.type = type;
        this.resource = resource;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    /**
     * Constructor for easy creation with basic fields
     */
    public Permission(String name, String code, String description, PermissionType type, String resource) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.code = code;
        this.description = description;
        this.type = type.getValue();
        this.resource = resource;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
    
    @Override
    public String toString() {
        return String.format("Permission{id='%s', name='%s', code='%s', type='%s', resource='%s'}", 
                           id, name, code, type, resource);
    }
}

